"""FastAPI entry point for the AI service.

W1 只有健康检查；后续在 W8 接入 CLIP embedding 做图像相似度。当前提供的是
纯文本匹配（``/similar``）与关键词抽取（``/tags``），不依赖任何模型文件，
目的是让后端与前端可以先把“候选匹配”这条链路跑通。
"""

from datetime import datetime, timezone
from typing import List

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from .textmatch import Candidate, keywords, rank, similarity

SERVICE_NAME = "campus-lost-found-ai"
SERVICE_VERSION = "0.2.0"


class HealthResponse(BaseModel):
    status: str
    service: str
    version: str
    model_loaded: bool
    timestamp: str


class CandidatePayload(BaseModel):
    id: str
    text: str


class SimilarRequest(BaseModel):
    text: str = Field(min_length=1, max_length=2000)
    candidates: List[CandidatePayload] = Field(default_factory=list, max_length=200)
    top_k: int = Field(default=5, ge=1, le=20)


class SimilarItem(BaseModel):
    id: str
    score: float
    shared: List[str]


class SimilarResponse(BaseModel):
    top_k: int
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


app = FastAPI(
    title="Campus Lost & Found AI Service",
    description="Image embedding and similarity helpers for the campus lost and found platform.",
    version=SERVICE_VERSION,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://127.0.0.1:5173"],
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
)

# W8 载入模型后由启动流程改写这两个字段。
app.state.model_loaded = False
app.state.model_name = None


@app.get("/health", response_model=HealthResponse, tags=["system"])
def health() -> HealthResponse:
    return HealthResponse(
        status="ok",
        service=SERVICE_NAME,
        version=SERVICE_VERSION,
        model_loaded=bool(app.state.model_loaded),
        timestamp=datetime.now(timezone.utc).isoformat(),
    )


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


@app.post("/tags", response_model=TagsResponse, tags=["matching"])
def tags(request: TagsRequest) -> TagsResponse:
    """抽取关键词，供表单提示与联想使用。"""
    return TagsResponse(tags=keywords(request.text, request.limit))

