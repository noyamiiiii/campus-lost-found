"""图片侧的几个能力：物品分类、主色提取、敏感区域检测、图像向量。

全部基于 OpenCV + NumPy，不需要 GPU，也不依赖联网推理服务：

* 分类用 MobileNetV2（ONNX，13MB）跑 ImageNet 1000 类，再用 ``catalog`` 里的
  关键词表映射到平台分类；
* 敏感区域用 YuNet 做人脸检测（220KB），再用边缘密度 + 矩形形状做证件启发式；
* 图像向量取分类网络的全局池化输出（1000 维），L2 归一化后可直接做余弦检索。

模型文件按需下载到 ``models/``（可用 ``AI_MODEL_DIR`` 改位置），落地失败时
各能力独立降级：分类返回空、人脸检测跳过，主色与描述不受影响。
"""

from __future__ import annotations

import os
import shutil
import tempfile
import urllib.request
from pathlib import Path
from typing import Any, Iterable

import cv2
import numpy as np

from .catalog import CATEGORY_LABELS, HUE_NAMES, IMAGENET_RULES

# 模型下载地址（都放在 GitHub，无需 HuggingFace，国内网络可达）
MODEL_SOURCES = {
    "mobilenetv2-7.onnx": (
        "https://github.com/onnx/models/raw/main/validated/vision/"
        "classification/mobilenet/model/mobilenetv2-7.onnx"
    ),
    "face_detection_yunet_2023mar.onnx": (
        "https://github.com/opencv/opencv_zoo/raw/main/models/"
        "face_detection_yunet/face_detection_yunet_2023mar.onnx"
    ),
    "imagenet_labels.txt": (
        "https://raw.githubusercontent.com/onnx/models/main/validated/vision/"
        "classification/synset.txt"
    ),
}

# ImageNet 的归一化参数（torchvision 默认值）
_MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
_STD = np.array([0.229, 0.224, 0.225], dtype=np.float32)

# MobileNetV2 ONNX 里的全局池化层，输出 (1, 1000, 1, 1)
_EMBEDDING_LAYER = "mobilenetv20_features_pool0_fwd"

_MAX_SIDE = 1280  # 超过这个边长先缩一下，避免 4K 图拖慢检测


def model_dir() -> Path:
    """模型目录：优先环境变量，其次 ai-service/models。"""
    configured = os.getenv("AI_MODEL_DIR")
    if configured:
        return Path(configured).expanduser()
    return Path(__file__).resolve().parent.parent / "models"


class _State:
    """模型只会加载一次，这里放进程内的懒加载状态。"""

    mobilenet: Any = None
    face: Any = None
    labels: list[str] | None = None
    download_disabled = os.getenv("AI_DOWNLOAD_MODELS", "1") == "0"


_state = _State()


def models_ready() -> dict[str, bool]:
    """给健康检查用：当前进程里哪些模型已就绪。"""
    return {
        "classifier": _state.mobilenet is not None,
        "face": _state.face is not None,
        "labels": bool(_state.labels),
    }


def ensure_models() -> dict[str, bool]:
    """按需下载并加载模型；任何一步失败都不抛异常，只降级。"""
    directory = model_dir()
    directory.mkdir(parents=True, exist_ok=True)

    if _state.labels is None:
        path = _ensure_file("imagenet_labels.txt", directory)
        if path is not None:
            _state.labels = _load_labels(path)

    if _state.mobilenet is None:
        path = _ensure_file("mobilenetv2-7.onnx", directory)
        if path is not None:
            try:
                # 从内存读，绕开 OpenCV 对非 ASCII 路径的限制（模型目录可能含中文）
                buffer = np.frombuffer(path.read_bytes(), dtype=np.uint8)
                _state.mobilenet = cv2.dnn.readNetFromONNX(buffer)
            except cv2.error:
                _state.mobilenet = None

    if _state.face is None:
        path = _ensure_file("face_detection_yunet_2023mar.onnx", directory)
        if path is not None:
            try:
                _state.face = cv2.FaceDetectorYN_create(str(path), "", (320, 320), 0.7, 0.3, 5000)
            except cv2.error:
                _state.face = None

    return models_ready()


def _ascii_path(path: Path) -> Path:
    """OpenCV 的 FaceDetectorYN 只接受路径字符串，遇到中文目录就复制到临时目录。"""
    try:
        str(path).encode("ascii")
        return path
    except UnicodeEncodeError:
        temp_dir = Path(tempfile.gettempdir()) / "clf-ai-models"
        temp_dir.mkdir(parents=True, exist_ok=True)
        target = temp_dir / path.name
        if not target.exists() or target.stat().st_size != path.stat().st_size:
            shutil.copyfile(path, target)
        return target


def _ensure_file(name: str, directory: Path) -> Path | None:
    target = directory / name
    if target.exists() and target.stat().st_size > 0:
        return _ascii_path(target)
    if _state.download_disabled:
        return None
    url = MODEL_SOURCES.get(name)
    if not url:
        return None
    tmp = target.with_suffix(target.suffix + ".part")
    try:
        with urllib.request.urlopen(url, timeout=120) as response, open(tmp, "wb") as handle:
            shutil.copyfileobj(response, handle)
        tmp.replace(target)
        return _ascii_path(target)
    except Exception:  # noqa: BLE001 - 网络问题一律降级，不阻断服务启动
        if tmp.exists():
            tmp.unlink(missing_ok=True)
        return None


def _load_labels(path: Path) -> list[str]:
    labels: list[str] = []
    for line in path.read_text(encoding="utf-8", errors="ignore").splitlines():
        parts = line.strip().split(" ", 1)
        if len(parts) == 2:
            labels.append(parts[1].split(",")[0].strip())
    return labels


# --------------------------------------------------------------------------- 基础


def decode(image_bytes: bytes) -> np.ndarray:
    """把上传的字节解码成 BGR 数组，失败抛 ValueError。"""
    data = np.frombuffer(image_bytes, dtype=np.uint8)
    image = cv2.imdecode(data, cv2.IMREAD_COLOR)
    if image is None:
        raise ValueError("图片无法解码")
    return _shrink(image)


def _shrink(image: np.ndarray) -> np.ndarray:
    height, width = image.shape[:2]
    longest = max(height, width)
    if longest <= _MAX_SIDE:
        return image
    scale = _MAX_SIDE / float(longest)
    return cv2.resize(image, (int(width * scale), int(height * scale)), interpolation=cv2.INTER_AREA)


def size_of(image: np.ndarray) -> tuple[int, int]:
    height, width = image.shape[:2]
    return int(width), int(height)


# --------------------------------------------------------------------------- 主色


def dominant_colors(image: np.ndarray, k: int = 3) -> list[dict[str, Any]]:
    """K 均值取主色，返回 [{hex, name, ratio}]，按占比降序。

    在 96x96 的缩略图上做，单张 20ms 内，足够稳。
    """
    small = cv2.resize(image, (96, 96), interpolation=cv2.INTER_AREA)
    pixels = small.reshape(-1, 3).astype(np.float32)
    count = min(k, len(pixels))
    if count <= 0:
        return []

    # 用均匀分位数初始化，避免随机种子带来的不稳定
    order = np.argsort(pixels.sum(axis=1))
    centers = pixels[order[np.linspace(0, len(order) - 1, count).astype(int)]].copy()

    for _ in range(12):
        distances = ((pixels[:, None, :] - centers[None, :, :]) ** 2).sum(axis=2)
        assignment = distances.argmin(axis=1)
        for index in range(count):
            members = pixels[assignment == index]
            if len(members):
                centers[index] = members.mean(axis=0)

    distances = ((pixels[:, None, :] - centers[None, :, :]) ** 2).sum(axis=2)
    assignment = distances.argmin(axis=1)

    result: list[dict[str, Any]] = []
    merged: list[tuple[np.ndarray, float]] = []
    for index in range(count):
        ratio = float((assignment == index).mean())
        if ratio < 0.05:  # 低于 5% 的当作噪点
            continue
        center = centers[index]
        if any(np.abs(center - exist).max() < 18 for exist, _ in merged):
            continue
        merged.append((center, ratio))

    merged.sort(key=lambda item: item[1], reverse=True)
    for center, ratio in merged:
        b, g, r = (int(round(value)) for value in center)
        result.append(
            {
                "hex": f"#{r:02x}{g:02x}{b:02x}",
                "name": color_name(np.uint8([[[b, g, r]]])),
                "ratio": round(ratio, 3),
            }
        )
    return result


def color_name(bgr_pixel: np.ndarray) -> str:
    """把一个 BGR 像素翻译成中文颜色名。"""
    hsv = cv2.cvtColor(bgr_pixel, cv2.COLOR_BGR2HSV)[0][0]
    hue, sat, val = int(hsv[0]), int(hsv[1]), int(hsv[2])
    if val < 50:
        return "黑色"
    if sat < 30:
        return "白色" if val > 200 else "灰色"
    if 10 <= hue < 30 and val < 140:
        return "棕色"
    for low, high, name in HUE_NAMES:
        if low <= hue < high:
            return name
    return "其他颜色"


# --------------------------------------------------------------------------- 分类


def classify(image: np.ndarray, top: int = 5) -> list[dict[str, Any]]:
    """ImageNet 前 top 个标签，带概率。模型没就绪时返回空列表。"""
    if _state.mobilenet is None or not _state.labels:
        return []

    rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
    resized = cv2.resize(rgb, (224, 224), interpolation=cv2.INTER_AREA).astype(np.float32) / 255.0
    normalized = (resized - _MEAN) / _STD
    blob = normalized.transpose(2, 0, 1)[np.newaxis, ...].astype(np.float32)

    _state.mobilenet.setInput(blob)
    logits = _state.mobilenet.forward()[0]
    probabilities = np.exp(logits - logits.max())
    probabilities /= probabilities.sum()

    order = np.argsort(-probabilities)[:top]
    return [
        {"name": _state.labels[int(index)], "score": round(float(probabilities[index]), 4)}
        for index in order
    ]


def guess_category(labels: Iterable[dict[str, Any]]) -> tuple[int, float]:
    """把模型的标签映射成平台分类，返回 (分类 id, 置信度)。"""
    for item in labels:
        name = str(item.get("name", "")).lower()
        for keywords, category_id in IMAGENET_RULES:
            if any(keyword in name for keyword in keywords):
                return category_id, float(item.get("score", 0.0))
    return 20, float(next(iter(labels), {}).get("score", 0.0)) if labels else 0.0


def category_of(category_id: int) -> str:
    return CATEGORY_LABELS.get(category_id, "其他")


# --------------------------------------------------------------------------- 敏感区域


def detect_sensitive(image: np.ndarray) -> dict[str, Any]:
    """人脸 + 证件（矩形、边缘密集）检测，返回框与布尔标记。"""
    boxes: list[dict[str, Any]] = []
    has_face = False
    has_id_card = False

    if _state.face is not None:
        height, width = image.shape[:2]
        _state.face.setInputSize((width, height))
        try:
            _, faces = _state.face.detect(image)
        except cv2.error:
            faces = None
        if faces is not None:
            for face in faces:
                x, y, w, h = (int(value) for value in face[:4])
                if w * h < width * height * 0.002:  # 太小的框忽略
                    continue
                has_face = True
                boxes.append(
                    {
                        "type": "face",
                        "x": x,
                        "y": y,
                        "w": w,
                        "h": h,
                        "score": round(float(face[-1]), 3),
                    }
                )

    card = _find_card_like(image)
    if card is not None:
        has_id_card = True
        boxes.append(card)

    return {"has_face": has_face, "has_id_card": has_id_card, "boxes": boxes}


def _find_card_like(image: np.ndarray) -> dict[str, Any] | None:
    """证件启发式：找面积够大、长宽比接近 1.4-1.9 且内部边缘密集的矩形。"""
    height, width = image.shape[:2]
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    gray = cv2.GaussianBlur(gray, (3, 3), 0)
    edges = cv2.Canny(gray, 60, 160)
    edges = cv2.dilate(edges, np.ones((3, 3), np.uint8), iterations=1)

    contours, _ = cv2.findContours(edges, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    best: dict[str, Any] | None = None
    frame = float(width * height)
    for contour in sorted(contours, key=cv2.contourArea, reverse=True)[:15]:
        area = cv2.contourArea(contour)
        if area < frame * 0.05 or area > frame * 0.55:
            continue
        x, y, w, h = cv2.boundingRect(contour)
        box_area = float(w * h)
        if box_area <= 0:
            continue
        # 矩形度：轮廓本身要贴着外接矩形，弯弯曲曲的色块不算证件
        if area / box_area < 0.80:
            continue
        ratio = max(w, h) / float(min(w, h) or 1)
        if not 1.4 <= ratio <= 1.8:
            continue
        perimeter = cv2.arcLength(contour, True)
        approx = cv2.approxPolyDP(contour, 0.02 * perimeter, True)
        if len(approx) != 4 or not cv2.isContourConvex(approx):
            continue
        region = edges[y : y + h, x : x + w]
        density = float((region > 0).mean())
        if density < 0.12:
            continue
        best = {
            "type": "id_card",
            "x": int(x),
            "y": int(y),
            "w": int(w),
            "h": int(h),
            "score": round(min(0.95, density * 2.5), 3),
        }
        break
    return best


# --------------------------------------------------------------------------- 向量


def embedding(image: np.ndarray) -> list[float]:
    """图像语义向量（1000 维，L2 归一化）。模型不可用时返回空列表。"""
    if _state.mobilenet is None:
        return []
    rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
    resized = cv2.resize(rgb, (224, 224), interpolation=cv2.INTER_AREA).astype(np.float32) / 255.0
    normalized = (resized - _MEAN) / _STD
    blob = normalized.transpose(2, 0, 1)[np.newaxis, ...].astype(np.float32)
    _state.mobilenet.setInput(blob)
    try:
        features = _state.mobilenet.forward(_EMBEDDING_LAYER).reshape(-1)
    except cv2.error:
        features = _state.mobilenet.forward().reshape(-1)
    norm = float(np.linalg.norm(features))
    if norm <= 0:
        return []
    return [round(float(value), 5) for value in (features / norm).tolist()]