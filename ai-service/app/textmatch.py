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