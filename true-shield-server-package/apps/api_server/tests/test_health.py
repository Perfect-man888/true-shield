import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_root(client: AsyncClient) -> None:
    response = await client.get("/")

    assert response.status_code == 200

    data = response.json()

    assert data["application"] == "真信盾 API"
    assert data["status"] == "running"
    assert data["version"] == "0.1.0"


@pytest.mark.asyncio
async def test_health_check(client: AsyncClient) -> None:
    response = await client.get("/api/v1/health")

    assert response.status_code == 200

    data = response.json()

    assert data["status"] == "ok"
    assert data["application"] == "true-shield"
    assert data["environment"] == "development"
    assert data["version"] == "0.1.0"
    assert "timestamp" in data
