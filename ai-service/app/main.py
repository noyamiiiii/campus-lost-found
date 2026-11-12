"""FastAPI entry point for the AI service.

三类能力：

* 文本（``/similar``、``/similar/pair``、``/tags``、``/embed/text``）——不依赖模型，
  纯词元重合 + 特征哈希向量，服务启动即可用；
* 图片（``/analyze``、``/embed/image``）——MobileNetV2 分类 + K 均值主色 + YuNet
  人脸与证件启发式，模型首次调用时自动下载到 ``models/``，失败则逐项降级；
* 生成（``/describe``、``/title``）——结构化拼装，不调用大模型。
"""

from __future__ import annotations

import threading
from contextlib import asynccontextmanager
import time
from datetime import datetime, timezone
from typing import Any, List

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from . import describe as describe_module
from . import vision
from .catalog import CATEGORY_LABELS
from .textmatch import Candidate, cosine, embed as text_embed, keywords, rank, similarity

SERVICE_NAME = "campus-lost-found-ai"
SERVICE_VERSION = "0.3.0"
MAX_IMAGE_BYTES = 8 * 1024 * 1024


# --------------------------------------------------------------------------- 模型


class EmbeddingRequest(BaseModel):
    text: str = Field(min_length=1, max_length=2000)


class EmbeddingResponse(BaseModel):
    dim: int
    vector: List[float]


class CandidatePayload(BaseModel):
    id: str
    text: str


class SimilarRequest(BaseModel):
    text: str = Field(min_length=1, max_length=2000)
    candidates: List[CandidatePayload] = Field(default_factory=list, max_length=200)
    # 后端会一次提交整批候选（最多 80 条）拿全量分数，因此这里放宽到 200
    top_k: int = Field(default=5, ge=1, le=200)


class SimilarItem(BaseModel):
    id: str
    score: float
    shared: List[str]


class SimilarResponse(BaseModel):
    top_k: int
    items: List[SimilarItem]


class VectorCandidate(BaseModel):
    id: str
    vector: List[float]


class VectorSearchRequest(BaseModel):
    vector: List[float] = Field(min_length=1, max_length=4096)
    candidates: List[VectorCandidate] = Field(default_factory=list, max_length=500)
    top_k: int = Field(default=5, ge=1, le=500)
    min_score: float = Field(default=0.0, ge=0.0, le=1.0)


class VectorSearchResponse(BaseModel):
    items: List[SimilarItem]


class TagsRequest(BaseModel):
    text: str = Field(min_length=1, max_length=2000)
    limit: int = Field(default=5, ge=1, le=20)


class TagsResponse(BaseModel):
    tags: List[str]


class PairRequest(BaseModel):
    left: str = Field(min_length=1, max_length=2000)
    right: str = Field(min_length=1, max_length=2000)


class PairResponse(BaseModel):
    score: float


class ColorItem(BaseModel):
    hex: str
    name: str
    ratio: float


class CategoryItem(BaseModel):
    id: int
    label: str
    confidence: float


class LabelItem(BaseModel):
    name: str
    score: float


class RegionItem(BaseModel):
    type: str
    x: int
    y: int
    w: int
    h: int
    score: float


class SensitiveInfo(BaseModel):
    has_face: bool = False
    has_id_card: bool = False
    boxes: List[RegionItem] = Field(default_factory=list)


class AnalyzeResponse(BaseModel):
    width: int
    height: int
    category: CategoryItem
    labels: List[LabelItem] = Field(default_factory=list)
    colors: List[ColorItem] = Field(default_factory=list)
    sensitive: SensitiveInfo = Field(default_factory=SensitiveInfo)
    description: str = ""
    title: str = ""
    embedding: List[float] = Field(default_factory=list)
    model: str = ""
    degraded: bool = False
    elapsed_ms: dict[str, int] = Field(default_factory=dict)


class DescribeRequest(BaseModel):
    kind: str | None = None
    location: str | None = None
    category_label: str = "物品"
    colors: List[ColorItem] = Field(default_factory=list)
    sensitive: List[RegionItem] = Field(default_factory=list)


class DescribeResponse(BaseModel):
    description: str
    title: str


class HealthResponse(BaseModel):
    status: str
    service: str
    version: str
    model_loaded: bool
    models: dict[str, bool]
    timestamp: str


@asynccontextmanager
async def lifespan(_: FastAPI):
    """后台线程预下载模型，别让首次上传卡住 30 秒。"""

    def run() -> None:
        try:
            ready = vision.ensure_models()
            app.state.model_loaded = bool(ready.get("classifier"))
        except Exception as exc:  # noqa: BLE001 - 模型加载失败不影响文本能力
            app.state.model_loaded = False
            app.state.model_error = str(exc)

    threading.Thread(target=run, name="model-warmup", daemon=True).start()
    yield


app = FastAPI(
    lifespan=lifespan,
    title="Campus Lost & Found AI Service",
    description="Image understanding, text vectors and similarity helpers for the campus lost and found platform.",
    version=SERVICE_VERSION,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://127.0.0.1:5173"],
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
)

app.state.model_loaded = False
app.state.model_name = "mobilenetv2-7"
app.state.model_error: str | None = None


# --------------------------------------------------------------------------- 系统


@app.get("/health", response_model=HealthResponse, tags=["system"])
def health() -> HealthResponse:
    return HealthResponse(
        status="ok",
        service=SERVICE_NAME,
        version=SERVICE_VERSION,
        model_loaded=bool(app.state.model_loaded),
        models=vision.models_ready(),
        timestamp=datetime.now(timezone.utc).isoformat(),
    )


# --------------------------------------------------------------------------- 文本


@app.post("/similar", response_model=SimilarResponse, tags=["matching"])
def similar(request: SimilarRequest) -> SimilarResponse:
    """给一组候选记录打分，返回最像的若干条。"""
    candidates = [Candidate(item.id, item.text) for item in request.candidates]
    ranked = rank(request.text, candidates, request.top_k)
    return SimilarResponse(
        top_k=request.top_k,
        items=[SimilarItem(id=item.id, score=item.score, shared=item.shared) for item in ranked],
    )


@app.post("/similar/pair", response_model=PairResponse, tags=["matching"])
def similar_pair(request: PairRequest) -> PairResponse:
    """两条文本的相似度，后端在发布时用它给已有记录打候选分。"""
    return PairResponse(score=round(similarity(request.left, request.right), 4))


@app.post("/similar/vectors", response_model=VectorSearchResponse, tags=["matching"])
def search_vectors(request: VectorSearchRequest) -> VectorSearchResponse:
    """向量检索：把查询向量与候选向量逐一算余弦，按分数返回。"""
    scored = []
    for item in request.candidates:
        score = cosine(request.vector, item.vector)
        if score <= 0 or score < request.min_score:
            continue
        scored.append(SimilarItem(id=item.id, score=round(score, 4), shared=[]))
    scored.sort(key=lambda item: item.score, reverse=True)
    return VectorSearchResponse(items=scored[: request.top_k])


@app.post("/tags", response_model=TagsResponse, tags=["matching"])
def tags(request: TagsRequest) -> TagsResponse:
    """抽取关键词，供表单提示与联想使用。"""
    return TagsResponse(tags=keywords(request.text, request.limit))


@app.post("/embed/text", response_model=EmbeddingResponse, tags=["matching"])
def embed_text(request: EmbeddingRequest) -> EmbeddingResponse:
    """文本向量（256 维，L2 归一化）。"""
    vector = text_embed(request.text)
    return EmbeddingResponse(dim=len(vector), vector=vector)


# --------------------------------------------------------------------------- 图片


@app.post("/analyze", response_model=AnalyzeResponse, tags=["vision"])
async def analyze(
    file: UploadFile = File(..., description="待分析的图片"),
    kind: str | None = Form(default=None, description="LOST / FOUND，用于生成标题与描述"),
    location: str | None = Form(default=None, description="地点名称，用于生成标题与描述"),
    with_embedding: bool = Form(default=True, description="是否返回图像向量"),
) -> AnalyzeResponse:
    """上传一张图，返回分类、主色、敏感区域、描述与标题。"""
    started = time.perf_counter()
    payload = await file.read()
    if not payload:
        raise HTTPException(status_code=400, detail="图片为空")
    if len(payload) > MAX_IMAGE_BYTES:
        raise HTTPException(status_code=413, detail="图片超过 8MB")

    try:
        image = vision.decode(payload)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    decode_ms = int((time.perf_counter() - started) * 1000)

    vision.ensure_models()

    moment = time.perf_counter()
    labels = vision.classify(image)
    classify_ms = int((time.perf_counter() - moment) * 1000)

    moment = time.perf_counter()
    colors = vision.dominant_colors(image)
    colors_ms = int((time.perf_counter() - moment) * 1000)

    moment = time.perf_counter()
    sensitive = vision.detect_sensitive(image)
    sensitive_ms = int((time.perf_counter() - moment) * 1000)

    category_id, confidence = vision.guess_category(labels)
    category_label = CATEGORY_LABELS.get(category_id, "其他")

    moment = time.perf_counter()
    vector = vision.embedding(image) if with_embedding else []
    embed_ms = int((time.perf_counter() - moment) * 1000)

    width, height = vision.size_of(image)
    description = describe_module.describe(
        kind=kind,
        location=location,
        category_label=category_label,
        colors=colors,
        sensitive=sensitive,
        labels=labels,
    )
    suggested_title = describe_module.title(
        kind=kind,
        category_label=category_label,
        colors=colors,
        location=location,
    )

    degraded = not labels
    return AnalyzeResponse(
        width=width,
        height=height,
        category=CategoryItem(id=category_id, label=category_label, confidence=round(confidence, 4)),
        labels=[LabelItem(name=item["name"], score=item["score"]) for item in labels],
        colors=[ColorItem(**item) for item in colors],
        sensitive=SensitiveInfo(**sensitive),
        description=description,
        title=suggested_title,
        embedding=vector,
        model=app.state.model_name if not degraded else "",
        degraded=degraded,
        elapsed_ms={
            "decode": decode_ms,
            "classify": classify_ms,
            "colors": colors_ms,
            "sensitive": sensitive_ms,
            "embedding": embed_ms,
            "total": int((time.perf_counter() - started) * 1000),
        },
    )


@app.post("/embed/image", response_model=EmbeddingResponse, tags=["vision"])
async def embed_image(file: UploadFile = File(...)) -> EmbeddingResponse:
    """只取图像向量（1000 维，L2 归一化）。"""
    payload = await file.read()
    if not payload:
        raise HTTPException(status_code=400, detail="图片为空")
    try:
        image = vision.decode(payload)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    vision.ensure_models()
    vector = vision.embedding(image)
    if not vector:
        raise HTTPException(status_code=503, detail="图像模型不可用")
    return EmbeddingResponse(dim=len(vector), vector=vector)


# --------------------------------------------------------------------------- 生成


@app.post("/describe", response_model=DescribeResponse, tags=["generate"])
def describe_text(request: DescribeRequest) -> DescribeResponse:
    """按分类、主色、敏感区域拼一段描述与标题。"""
    colors: list[dict[str, Any]] = [item.model_dump() for item in request.colors]
    regions: list[dict[str, Any]] = [item.model_dump() for item in request.sensitive]
    sensitive = {
        "has_face": any(item["type"] == "face" for item in regions),
        "has_id_card": any(item["type"] == "id_card" for item in regions),
        "boxes": regions,
    }
    return DescribeResponse(
        description=describe_module.describe(
            kind=request.kind,
            location=request.location,
            category_label=request.category_label,
            colors=colors,
            sensitive=sensitive,
        ),
        title=describe_module.title(
            kind=request.kind,
            category_label=request.category_label,
            colors=colors,
            location=request.location,
        ),
    )