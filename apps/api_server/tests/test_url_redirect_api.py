from __future__ import annotations

import uuid

import pytest
from httpx import AsyncClient

import app.api.routes.risk as risk_routes
from app.schemas.url_redirect import (
    URLRedirectHop,
    URLRedirectResolutionResponse,
)
from app.services.url_redirect_resolver import (
    UnsafeURLTargetError,
)

TEST_PASSWORD = "URLRedirectAPITest_123!"

pytestmark = pytest.mark.asyncio


async def register_and_login(
    client: AsyncClient,
    *,
    name: str,
) -> dict[str, str]:
    """注册并登录随机测试用户。"""

    email = (
        f"{name.lower()}-"
        f"{uuid.uuid4().hex}@example.com"
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

    token = login_response.json()[
        "access_token"
    ]

    return {
        "Authorization": f"Bearer {token}",
    }


async def test_url_analysis_can_include_redirect_chain(
    client: AsyncClient,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """URL 分析可以返回并保存重定向风险。"""

    headers = await register_and_login(
        client,
        name="RedirectChainUser",
    )

    async def fake_resolve(
        url: str,
    ) -> URLRedirectResolutionResponse:
        assert url == (
            "https://example.com/start"
        )

        return URLRedirectResolutionResponse(
            original_url=url,
            final_url=(
                "https://final.example/home"
            ),
            final_status_code=200,
            redirect_count=2,
            completed=True,
            hops=[
                URLRedirectHop(
                    index=0,
                    url=url,
                    host="example.com",
                    status_code=302,
                    location=(
                        "https://middle.example/next"
                    ),
                ),
                URLRedirectHop(
                    index=1,
                    url=(
                        "https://middle.example/next"
                    ),
                    host="middle.example",
                    status_code=302,
                    location=(
                        "https://final.example/home"
                    ),
                ),
                URLRedirectHop(
                    index=2,
                    url=(
                        "https://final.example/home"
                    ),
                    host="final.example",
                    status_code=200,
                    location=None,
                ),
            ],
        )

    monkeypatch.setattr(
        risk_routes.url_redirect_resolver,
        "resolve",
        fake_resolve,
    )

    response = await client.post(
        "/api/v1/risk/url/analyze",
        headers=headers,
        json={
            "url": "https://example.com/start",
            "resolve_redirects": True,
        },
    )

    assert response.status_code == 200, response.text

    body = response.json()

    assert body["risk_level"] == "medium"
    assert body["score"] == 25

    inspection = body[
        "redirect_inspection"
    ]

    assert inspection["requested"] is True
    assert inspection["status"] == "completed"
    assert inspection["message"] is None

    assert (
        inspection["resolution"][
            "redirect_count"
        ]
        == 2
    )

    signal_ids = {
        signal["signal_id"]
        for signal in body["signals"]
    }

    assert "URL-013" in signal_ids
    assert "URL-014" in signal_ids

    event_id = body["event_id"]

    detail_response = await client.get(
        f"/api/v1/risk/events/{event_id}",
        headers=headers,
    )

    assert detail_response.status_code == 200

    detail_rule_ids = {
        evidence["rule_id"]
        for evidence
        in detail_response.json()["evidence"]
    }

    assert "URL-013" in detail_rule_ids
    assert "URL-014" in detail_rule_ids


async def test_blocked_redirect_still_returns_local_analysis(
    client: AsyncClient,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """重定向检查被阻止时仍返回本地分析。"""

    headers = await register_and_login(
        client,
        name="BlockedRedirectUser",
    )

    async def fake_resolve(
        url: str,
    ) -> URLRedirectResolutionResponse:
        raise UnsafeURLTargetError(
            "不允许访问本机、内网或特殊用途地址。"
        )

    monkeypatch.setattr(
        risk_routes.url_redirect_resolver,
        "resolve",
        fake_resolve,
    )

    response = await client.post(
        "/api/v1/risk/url/analyze",
        headers=headers,
        json={
            "url": "https://example.com/news",
            "resolve_redirects": True,
        },
    )

    assert response.status_code == 200, response.text

    body = response.json()

    assert body["risk_level"] == "low"
    assert body["score"] == 0
    assert body["event_id"]

    inspection = body[
        "redirect_inspection"
    ]

    assert inspection["requested"] is True
    assert inspection["status"] == "blocked"
    assert inspection["resolution"] is None

    assert (
        "不允许访问"
        in inspection["message"]
    )