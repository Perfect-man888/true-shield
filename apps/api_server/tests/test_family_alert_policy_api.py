from __future__ import annotations

import uuid

import pytest
from httpx import AsyncClient

TEST_PASSWORD = "AlertPolicyTest_123!"

pytestmark = pytest.mark.asyncio


async def register_and_login(
    client: AsyncClient,
    *,
    name: str,
) -> dict[str, str]:
    """注册并登录测试用户。"""

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

    # 当前登录接口接收表单数据。
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

    token = login_response.json()["access_token"]

    return {
        "Authorization": f"Bearer {token}",
    }

async def create_family(
    client: AsyncClient,
    *,
    headers: dict[str, str],
) -> dict:
    """创建测试家庭。"""

    response = await client.post(
        "/api/v1/families",
        headers=headers,
        json={
            "name": "告警策略测试家庭",
        },
    )

    assert response.status_code == 201, (
        response.text
    )

    return response.json()


async def test_default_alert_policy(
    client: AsyncClient,
) -> None:
    """首次查询应创建默认告警策略。"""

    headers = await register_and_login(
        client,
        name="DefaultPolicyOwner",
    )

    family = await create_family(
        client,
        headers=headers,
    )

    response = await client.get(
        (
            f"/api/v1/families/{family['id']}"
            "/alert-policy"
        ),
        headers=headers,
    )

    assert response.status_code == 200, (
        response.text
    )

    body = response.json()

    assert body["family_id"] == family["id"]
    assert body["auto_create_enabled"] is True
    assert body["auto_dispatch_enabled"] is False
    assert body["minimum_risk_level"] == "high"
    assert body["enabled_source_types"] == [
        "text",
        "image",
        "url",
    ]
    assert body["max_recipients"] == 3


async def test_owner_can_update_alert_policy(
    client: AsyncClient,
) -> None:
    """家庭所有者可以修改策略。"""

    headers = await register_and_login(
        client,
        name="UpdatePolicyOwner",
    )

    family = await create_family(
        client,
        headers=headers,
    )

    response = await client.patch(
        (
            f"/api/v1/families/{family['id']}"
            "/alert-policy"
        ),
        headers=headers,
        json={
            "auto_create_enabled": True,
            "auto_dispatch_enabled": True,
            "minimum_risk_level": "medium",
            "enabled_source_types": [
                "text",
                "image",
            ],
            "max_recipients": 5,
        },
    )

    assert response.status_code == 200, (
        response.text
    )

    body = response.json()

    assert body["auto_dispatch_enabled"] is True
    assert body["minimum_risk_level"] == "medium"
    assert body["enabled_source_types"] == [
        "text",
        "image",
    ]
    assert body["max_recipients"] == 5


async def test_policy_update_is_persisted(
    client: AsyncClient,
) -> None:
    """策略修改后再次查询应保留。"""

    headers = await register_and_login(
        client,
        name="PersistPolicyOwner",
    )

    family = await create_family(
        client,
        headers=headers,
    )

    url = (
        f"/api/v1/families/{family['id']}"
        "/alert-policy"
    )

    update_response = await client.patch(
        url,
        headers=headers,
        json={
            "auto_create_enabled": False,
            "enabled_source_types": [
                "url",
            ],
            "max_recipients": 2,
        },
    )

    assert update_response.status_code == 200

    get_response = await client.get(
        url,
        headers=headers,
    )

    assert get_response.status_code == 200

    body = get_response.json()

    assert body["auto_create_enabled"] is False
    assert body["enabled_source_types"] == [
        "url",
    ]
    assert body["max_recipients"] == 2


async def test_empty_source_types_rejected(
    client: AsyncClient,
) -> None:
    """来源类型不允许为空。"""

    headers = await register_and_login(
        client,
        name="EmptySourcesPolicyOwner",
    )

    family = await create_family(
        client,
        headers=headers,
    )

    response = await client.patch(
        (
            f"/api/v1/families/{family['id']}"
            "/alert-policy"
        ),
        headers=headers,
        json={
            "enabled_source_types": [],
        },
    )

    assert response.status_code == 422


async def test_outside_user_cannot_read_policy(
    client: AsyncClient,
) -> None:
    """非家庭成员不能读取告警策略。"""

    owner_headers = await register_and_login(
        client,
        name="PrivatePolicyOwner",
    )

    outside_headers = await register_and_login(
        client,
        name="OutsidePolicyUser",
    )

    family = await create_family(
        client,
        headers=owner_headers,
    )

    response = await client.get(
        (
            f"/api/v1/families/{family['id']}"
            "/alert-policy"
        ),
        headers=outside_headers,
    )

    assert response.status_code == 404