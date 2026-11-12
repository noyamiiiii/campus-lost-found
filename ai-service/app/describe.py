"""描述与标题的生成（AI-03 / AI-07）。

平台不调用大模型，这两项用「结构化拼装」实现：把识别出的分类、主色、敏感
区域、地点、启事类型按固定句式组织成一段话。好处是零成本、可控、不会编造
图片里不存在的细节；接入真正的视觉描述模型时替换这两个函数即可。
"""

from __future__ import annotations

from typing import Any, Iterable

from .catalog import label_zh

KIND_LABELS = {"LOST": "寻物启事", "FOUND": "失物招领"}


def _main_color(colors: Iterable[dict[str, Any]]) -> str:
    for item in colors:
        name = str(item.get("name") or "").strip()
        if name:
            return name
    return ""


def _extra_colors(colors: Iterable[dict[str, Any]]) -> list[str]:
    names: list[str] = []
    for index, item in enumerate(colors):
        if index == 0:
            continue
        name = str(item.get("name") or "").strip()
        if name and name not in names:
            names.append(name)
    return names[:2]


def _subject(category_label: str, main_color: str) -> str:
    """把「主色 + 分类」拼成自然的主语，分类不明时说「一件…的物品」。"""
    label = (category_label or "").strip()
    if not label or label == "其他":
        return f"一件{main_color}的物品" if main_color else "一件物品"
    return f"{main_color}{label}" if main_color else label


def describe(
    *,
    kind: str | None,
    location: str | None,
    category_label: str,
    colors: list[dict[str, Any]],
    sensitive: dict[str, Any] | None,
    labels: list[dict[str, Any]] | None = None,
) -> str:
    """给出一段可直接放进详情页的图文描述。"""
    main_color = _main_color(colors)
    extras = _extra_colors(colors)
    subject = _subject(category_label, main_color)
    place = f"在{location}" if location else "在校园里"
    kind_label = KIND_LABELS.get(kind or "", "")

    head = f"这张图是{place}拍的，主体看起来像{subject}"
    if kind_label:
        head += f"，适合放在{kind_label}里"
    parts = [head + "。"]

    if extras:
        parts.append(f"画面以{main_color}为主，还能看到{'、'.join(extras)}。")

    if labels:
        english = labels[0].get("name")
        if english:
            shown = label_zh(english) or english
            parts.append(f"模型给出的最可能标签是「{shown}」，可作为选分类时的参考。")

    if sensitive:
        boxes = sensitive.get("boxes") or []
        faces = [box for box in boxes if box.get("type") == "face"]
        has_card = bool(sensitive.get("has_id_card"))
        if faces:
            parts.append(f"画面里检测到 {len(faces)} 张人脸，建议打码，或者换一张只拍物品的图。")
        if has_card:
            parts.append("画面里疑似有证件类卡片，卡面上的信息请先遮挡。")
        if not faces and not has_card:
            parts.append("没有检测到人脸和证件信息，可以直接发布。")

    return "".join(parts)


def title(
    *,
    kind: str | None,
    category_label: str,
    colors: list[dict[str, Any]],
    location: str | None,
) -> str:
    """生成 12-24 字的标题，形状跟平台现有帖子的写法一致。"""
    label = (category_label or "").strip() or "其他"
    subject = _subject(label, _main_color(colors))
    place = (location or "").strip()

    if kind == "FOUND":
        return f"{place}捡到{subject}" if place else f"捡到{subject}"
    if kind == "LOST":
        return f"{place}丢失{subject}" if place else f"丢了{subject}"
    return subject