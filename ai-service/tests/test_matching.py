from fastapi.testclient import TestClient

from app.main import app
from app.textmatch import keywords, rank, similarity, tokenize, Candidate

client = TestClient(app)


def test_health_reports_service_metadata():
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["service"] == "campus-lost-found-ai"
    assert body["model_loaded"] is False


def test_tokenize_keeps_bigrams_and_latin_words():
    tokens = tokenize("图书馆三楼捡到一把黑色长柄伞 SAN-01")
    assert "图书" in tokens
    assert "长柄" in tokens
    assert "san" in tokens
    assert "san-01" not in tokens


def test_similarity_rewards_shared_features():
    lost = "在图书馆三楼丢失一把黑色长柄伞，伞柄有划痕"
    found = "图书馆三楼捡到黑色长柄伞，伞柄有划痕"
    unrelated = "操场看台捡到白色耳机"
    assert similarity(lost, found) > similarity(lost, unrelated)
    assert similarity(lost, found) > 0.3


def test_rank_orders_candidates_by_score():
    text = "图书馆捡到黑色长柄伞"
    items = rank(
        text,
        [
            Candidate("unrelated", "东区操场捡到一副蓝牙耳机"),
            Candidate("same", "图书馆捡到一把黑色长柄伞"),
        ],
    )
    assert items
    assert items[0].id == "same"
    assert items[0].shared


def test_keywords_returns_frequent_tokens():
    # “校园”“丢失”在 STOPWORDS 里，不参与打分，这里挑带区分度的词验证。
    tags = keywords("二食堂捡到校园卡，卡面有贴纸", 3)
    assert "食堂" in tags
    assert len(tags) <= 3


def test_similar_endpoint_returns_scored_items():
    response = client.post(
        "/similar",
        json={
            "text": "图书馆捡到黑色长柄伞",
            "candidates": [
                {"id": "a", "text": "图书馆捡到一把黑色长柄伞"},
                {"id": "b", "text": "操场捡到耳机"},
            ],
            "top_k": 2,
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["items"][0]["id"] == "a"
    assert body["items"][0]["score"] > 0
    assert body["items"][0]["shared"]


def test_similar_pair_and_tags_endpoints():
    pair = client.post("/similar/pair", json={"left": "黑色长柄伞", "right": "长柄黑伞"})
    assert pair.status_code == 200
    assert 0 < pair.json()["score"] <= 1

    tags = client.post("/tags", json={"text": "二食堂捡到校园卡", "limit": 4})
    assert tags.status_code == 200
    assert texts_non_empty(tags.json()["tags"])


def texts_non_empty(tags):
    return isinstance(tags, list) and len(tags) > 0