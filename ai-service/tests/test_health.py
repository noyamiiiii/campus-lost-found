from fastapi.testclient import TestClient

from app.main import app


def test_health_reports_service_metadata() -> None:
    response = TestClient(app).get("/health")

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["service"] == "campus-lost-found-ai"
    assert body["version"] == "0.3.0"
    # 模型是后台线程加载的，健康检查只保证字段存在、类型正确
    assert isinstance(body["model_loaded"], bool)
    assert isinstance(body["models"], dict)
    assert set(body["models"]) == {"classifier", "face", "labels"}