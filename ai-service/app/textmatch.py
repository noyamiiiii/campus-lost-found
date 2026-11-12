"""Lightweight Chinese/English text matching helpers.

Deliberately dependency-free: the service must start in well under a second on a
laptop, so instead of a vector model we tokenize into CJK bigrams plus latin
words and rank by overlap. The interface is intentionally the same shape a
future embedding backend would expose (``/similar``, ``/tags``), so swapping the
implementation later does not touch the callers.
"""

from __future__ import annotations

import re
from collections import Counter
from dataclasses import dataclass

TOKEN_PATTERN = re.compile(r"[\u4e00-\u9fa5]+|[A-Za-z0-9]+")

# 校园场景里几乎不携带区分度的词，先滤掉，避免“校园”“丢失”把每条记录都连起来。
STOPWORDS = {
    "校园",
    "丢失",
    "丢了",
    "失物",
    "招领",
    "捡到",
    "捡了",
    "拾到",
    "寻找",
    "联系",
    "同学",
    "同学",
    "谢谢",
    "已经",
    "可以",
    "有一",
    "我们",
    "the",
    "and",
    "for",
}


def tokenize(text: str) -> list[str]:
    """把一段文本切成可用于重合度计算的词元。"""
    tokens: list[str] = []
    for piece in TOKEN_PATTERN.findall(text or ""):
        if piece[0] < "\u4e00":  # 拉丁字母 / 数字
            lowered = piece.lower()
            if len(lowered) > 1 and lowered not in STOPWORDS:
                tokens.append(lowered)
            continue
        if len(piece) == 1:
            if piece not in STOPWORDS:
                tokens.append(piece)
            continue
        for index in range(len(piece) - 1):
            gram = piece[index : index + 2]
            if gram not in STOPWORDS:
                tokens.append(gram)
    return tokens


def similarity(left: str, right: str) -> float:
    """返回 0~1 的相似度：Jaccard 系数，对短描述比对长描述更宽容。"""
    left_tokens = set(tokenize(left))
    right_tokens = set(tokenize(right))
    if not left_tokens or not right_tokens:
        return 0.0
    shared = len(left_tokens & right_tokens)
    return shared / len(left_tokens | right_tokens)


@dataclass(frozen=True)
class Candidate:
    id: str
    text: str


@dataclass(frozen=True)
class ScoredCandidate:
    id: str
    score: float
    shared: list[str]


def rank(text: str, candidates: list[Candidate], top_k: int = 5) -> list[ScoredCandidate]:
    """按相似度排序候选，返回带共享词元的解释，便于前端展示“为什么像”。"""
    base_tokens = set(tokenize(text))
    scored: list[ScoredCandidate] = []
    for candidate in candidates:
        other = set(tokenize(candidate.text))
        if not base_tokens or not other:
            continue
        shared = base_tokens & other
        if not shared:
            continue
        score = len(shared) / len(base_tokens | other)
        scored.append(ScoredCandidate(candidate.id, round(score, 4), sorted(shared)[:5]))
    scored.sort(key=lambda item: item.score, reverse=True)
    return scored[:top_k]


def keywords(text: str, limit: int = 5) -> list[str]:
    """高频词元，用于给记录打标签或做筛选提示。"""
    counter = Counter(tokenize(text))
    return [word for word, _ in counter.most_common(limit)]


EMBEDDING_DIM = 256


def embed(text: str, dim: int = EMBEDDING_DIM) -> list[float]:
    """把文本压成定长向量（特征哈希），L2 归一化，可直接做余弦检索。

    做法是把 tokenize 出来的词元用 md5 哈希到 dim 个桶里累加频次，再开方压一
    下高频词。它没有语义泛化能力（“雨伞”和“伞”仍然是两个词元），但零模型、
    零依赖、结果稳定，足够支撑「相似检索」这条链路的接口与阈值联调；换成句
    向量模型时只需要保持返回同样长度的归一化向量。
    """
    import hashlib
    import math

    vector = [0.0] * dim
    counter = Counter(tokenize(text))
    if not counter:
        return vector
    for token, count in counter.items():
        digest = hashlib.md5(token.encode("utf-8")).digest()
        bucket = int.from_bytes(digest[:4], "big") % dim
        sign = 1.0 if digest[4] % 2 == 0 else -1.0
        vector[bucket] += sign * (1.0 + math.log(count))
    norm = math.sqrt(sum(value * value for value in vector))
    if norm <= 0:
        return vector
    return [round(value / norm, 5) for value in vector]


def cosine(left: list[float], right: list[float]) -> float:
    """两个已归一化向量的余弦相似度。"""
    if not left or not right or len(left) != len(right):
        return 0.0
    return max(0.0, min(1.0, sum(a * b for a, b in zip(left, right))))