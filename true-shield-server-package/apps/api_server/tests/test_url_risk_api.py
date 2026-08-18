from __future__ import annotations

import uuid

import pytest
from httpx import AsyncClient

TEST_PASSWORD = "URLRiskTest_123!"

pytestmark = pytest.mark.asyncio


async def register_and_login(
    client: AsyncClient,
    *,
    name: str,
) -> dict[str, str]:
    """注册并登录随机测试用户。"""

    random_part = uuid.uuid4().hex

    email = (
        f"{name.lower()}-{random_part}@example.com"
    )

    register_response = await client.post(
        "/api/v1/auth/register",
        json={
            "email": email,
            "phone": None,
            "display_name": name,
            "password": TEST_PASSWORD,
        },
    )

    assert register_response.status_code in {
        200,
        201,
    }, register_response.text

    login_response = await client.post(
        "/api/v1/auth/login",
        data={
            "username": email,
            "password": TEST_PASSWORD,
        },
    )

    assert login_response.status_code == 200, (
        login_response.text
    )

    access_token = login_response.json()[
        "access_token"
    ]

    return {
        "Authorization": f"Bearer {access_token}",
    }


async def test_user_can_analyze_and_save_safe_url(
    client: AsyncClient,
) -> None:
    """用户可以分析并保存普通 HTTPS 链接。"""

    headers = await register_and_login(
        client,
        name="SafeURLUser",
    )

    response = await client.post(
        "/api/v1/risk/url/analyze",
        headers=headers,
        json={
            "url": "https://www.example.com/news",
        },
    )

    assert response.status_code == 200, response.text

    body = response.json()

    assert body["source_type"] == "url"
    assert body["original_url"] == (
        "https://www.example.com/news"
    )

    assert body["host"] == "www.example.com"
    assert body["risk_level"] == "low"
    assert body["score"] == 0
    assert body["signals"] == []
    assert body["rule_version"] == "url-2.0.0"

    assert body["event_id"]
    assert body["created_at"]


async def test_suspicious_url_is_high_risk(
    client: AsyncClient,
) -> None:
    """可疑链接应返回高风险信号。"""

    headers = await register_and_login(
        client,
        name="HighRiskURLUser",
    )

    response = await client.post(
        "/api/v1/risk/url/analyze",
        headers=headers,
        json={
            "url": (
                "http://admin:123456@"
                "192.168.1.10/login/verify"
            ),
        },
    )

    assert response.status_code == 200, response.text

    body = response.json()

    assert body["risk_level"] == "high"
    assert body["score"] >= 60
    assert body["signals"]

    signal_ids = {
        signal["signal_id"]
        for signal in body["signals"]
    }

    assert "URL-002" in signal_ids
    assert "URL-004" in signal_ids
    assert "URL-005" in signal_ids
    assert "URL-008" in signal_ids


async def test_url_event_appears_in_history(
    client: AsyncClient,
) -> None:
    """URL 分析结果应出现在风险历史中。"""

    headers = await register_and_login(
        client,
        name="URLHistoryUser",
    )

    create_response = await client.post(
        "/api/v1/risk/url/analyze",
        headers=headers,
        json={
            "url": (
                "https://xn--paypa-4ve.com/"
                "login/verify"
            ),
        },
    )

    assert create_response.status_code == 200, (
        create_response.text
    )

    event_id = create_response.json()["event_id"]

    list_response = await client.get(
        "/api/v1/risk/events",
        headers=headers,
    )

    assert list_response.status_code == 200, (
        list_response.text
    )

    list_body = list_response.json()

    matching_items = [
        item
        for item in list_body["items"]
        if item["event_id"] == event_id
    ]

    assert len(matching_items) == 1

    item = matching_items[0]

    assert item["source_type"] == "url"
    assert item["risk_level"] == "high"

    detail_response = await client.get(
        f"/api/v1/risk/events/{event_id}",
        headers=headers,
    )

    assert detail_response.status_code == 200, (
        detail_response.text
    )

    detail = detail_response.json()

    assert detail["source_type"] == "url"

    assert detail["source_text"] == (
        "https://xn--paypa-4ve.com/"
        "login/verify"
    )

    rule_ids = {
        evidence["rule_id"]
        for evidence in detail["evidence"]
    }

    assert "URL-006" in rule_ids
    assert "URL-008" in rule_ids


async def test_invalid_url_returns_422(
    client: AsyncClient,
) -> None:
    """无有效主机名的 URL 应返回 422。"""

    headers = await register_and_login(
        client,
        name="InvalidURLUser",
    )

    response = await client.post(
        "/api/v1/risk/url/analyze",
        headers=headers,
        json={
            "url": "https:///only-path",
        },
    )

    assert response.status_code == 422, response.text

    assert response.json()["detail"] == (
        "URL 格式无效，无法识别主机名。"
    )