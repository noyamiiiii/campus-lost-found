"""图片与生成能力的测试：不依赖模型下载也能跑，缺模型时只验证降级路径。"""

from __future__ import annotations

import io

import numpy as np
import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app import describe, vision
from app.catalog import CATEGORY_LABELS
from app.main import app
from app.textmatch import cosine, embed


def _png(color: tuple[int, int, int], size: tuple[int, int] = (240, 180)) -> bytes:
    buffer = io.BytesIO()
    Image.new("RGB", size, color).save(buffer, format="PNG")
    return buffer.getvalue()


def _solid(color: tuple[int, int, int], size: tuple[int, int] = (240, 180)) -> np.ndarray:
    image = np.zeros((size[1], size[0], 3), dtype=np.uint8)
    image[:, :] = (color[2], color[1], color[0])  # BGR
    return image


def test_decode_rejects_non_image() -> None:
    with pytest.raises(ValueError):
        vision.decode(b"not an image")


def test_dominant_color_red() -> None:
    colors = vision.dominant_colors(_solid((200, 30, 30)))
    assert colors, "至少要返回一个主色"
    assert colors[0]["name"] == "红色"
    assert colors[0]["hex"].startswith("#")
    assert colors[0]["ratio"] > 0.9


def test_dominant_color_dark_is_black() -> None:
    colors = vision.dominant_colors(_solid((12, 12, 12)))
    assert colors[0]["name"] == "黑色"


def test_dominant_color_bright_is_white() -> None:
    colors = vision.dominant_colors(_solid((248, 248, 246)))
    assert colors[0]["name"] == "白色"


def test_dominant_color_ignores_white_background() -> None:
    """白底图上的黑色物体应报黑色，而不是背景的白色。"""
    image = np.full((240, 240, 3), 250, dtype=np.uint8)
    image[60:180, 60:180] = (18, 18, 20)
    colors = vision.dominant_colors(image)
    assert colors[0]["name"] == "黑色", colors


def test_detect_sensitive_on_plain_image() -> None:
    result = vision.detect_sensitive(_solid((180, 180, 180)))
    assert result["has_face"] is False
    assert result["boxes"] == []


def test_size_of_returns_width_height() -> None:
    assert vision.size_of(_solid((10, 20, 30), (320, 200))) == (320, 200)


def test_describe_mentions_category_and_color() -> None:
    text = describe.describe(
        kind="FOUND",
        location="图书馆",
        category_label="雨伞",
        colors=[{"name": "黑色", "hex": "#111111", "ratio": 0.8}],
        sensitive={"has_face": False, "has_id_card": False, "boxes": []},
    )
    assert "图书馆" in text
    assert "黑色雨伞" in text
    assert "可以直接发布" in text


def test_describe_warns_about_face_and_card() -> None:
    text = describe.describe(
        kind="LOST",
        location=None,
        category_label="其他",
        colors=[],
        sensitive={
            "has_face": True,
            "has_id_card": True,
            "boxes": [{"type": "face", "x": 0, "y": 0, "w": 10, "h": 10, "score": 0.9}],
        },
    )
    assert "人脸" in text
    assert "证件" in text


def test_title_shapes() -> None:
    colors = [{"name": "白色", "hex": "#ffffff", "ratio": 0.7}]
    assert describe.title(kind="FOUND", category_label="水杯", colors=colors, location="第二食堂") == (
        "第二食堂捡到白色水杯"
    )
    assert describe.title(kind="LOST", category_label="水杯", colors=colors, location=None) == "丢了白色水杯"
    assert describe.title(kind=None, category_label="其他", colors=[], location=None) == "一件物品"


def test_text_embedding_is_normalized_and_comparable() -> None:
    left = embed("图书馆三楼捡到一把黑色长柄伞")
    right = embed("图书馆三楼捡到一把黑色长柄伞")
    other = embed("东区操场捡到一副白色耳机")
    assert len(left) == 256
    assert abs(sum(value * value for value in left) - 1.0) < 0.01
    assert cosine(left, right) > 0.99
    assert cosine(left, other) < cosine(left, right)


def test_embed_text_endpoint() -> None:
    client = TestClient(app)
    response = client.post("/embed/text", json={"text": "黑色雨伞"})
    assert response.status_code == 200
    body = response.json()
    assert body["dim"] == 256
    assert len(body["vector"]) == 256


def test_search_vectors_endpoint() -> None:
    client = TestClient(app)
    first = client.post("/embed/text", json={"text": "黑色长柄雨伞"}).json()["vector"]
    second = client.post("/embed/text", json={"text": "黑色长柄雨伞"}).json()["vector"]
    third = client.post("/embed/text", json={"text": "白色无线耳机"}).json()["vector"]
    response = client.post(
        "/similar/vectors",
        json={
            "vector": first,
            "candidates": [
                {"id": "same", "vector": second},
                {"id": "other", "vector": third},
            ],
            "top_k": 2,
        },
    )
    assert response.status_code == 200
    items = response.json()["items"]
    assert items and items[0]["id"] == "same"


def test_analyze_endpoint_accepts_png() -> None:
    client = TestClient(app)
    response = client.post(
        "/analyze",
        files={"file": ("umbrella.png", _png((30, 30, 30)), "image/png")},
        data={"kind": "FOUND", "location": "图书馆", "with_embedding": "false"},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["width"] == 240 and body["height"] == 180
    assert body["category"]["label"] in CATEGORY_LABELS.values()
    assert body["colors"][0]["name"] == "黑色"
    assert body["title"].startswith("图书馆捡到")
    assert body["sensitive"]["has_face"] is False


def test_analyze_endpoint_rejects_empty() -> None:
    client = TestClient(app)
    response = client.post("/analyze", files={"file": ("empty.png", b"", "image/png")})
    assert response.status_code == 400


def test_health_reports_models() -> None:
    client = TestClient(app)
    body = client.get("/health").json()
    assert body["status"] == "ok"
    assert body["models"]["labels"] in (True, False)
    assert body["version"] == "0.3.0"