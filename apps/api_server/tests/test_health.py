from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_root() -> None:
    response = client.get("/")

    assert response.status_code == 200

    data = response.json()

    assert data["application"] == "真信盾 API"
    assert data["status"] == "running"
    assert data["version"] == "0.1.0"


def test_health_check() -> None:
    response = client.get("/api/v1/health")

    assert response.status_code == 200

    data = response.json()

    assert data["status"] == "ok"
    assert data["application"] == "true-shield"
    assert data["environment"] == "development"
    assert data["version"] == "0.1.0"
    assert "timestamp" in data
