from __future__ import annotations

import uuid

import pytest
from httpx import AsyncClient

TEST_PASSWORD = "TrustedContactTest_123!"

pytestmark = pytest.mark.asyncio


async def register_and_login(
    client: AsyncClient,
    *,
    name: str,
) -> tuple[str, dict[str, str]]:
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

    return email, {
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
            "name": "可信联系人测试家庭",
        },
    )

    assert response.status_code == 201, (
        response.text
    )

    return response.json()


async def create_contact(
    client: AsyncClient,
    *,
    family_id: str,
    headers: dict[str, str],
    phone: str = "13800138000",
) -> dict:
    """创建测试可信联系人。"""

    response = await client.post(
        (
            f"/api/v1/families/{family_id}"
            "/trusted-contacts"
        ),
        headers=headers,
        json={
            "display_name": "妈妈",
            "relationship_label": "母亲",
            "phone": phone,
            "priority": 1,
            "can_receive_alerts": True,
        },
    )

    assert response.status_code == 201, (
        response.text
    )

    return response.json()


async def test_user_can_create_trusted_contact(
    client: AsyncClient,
) -> None:
    """家庭成员可以创建自己的可信联系人。"""

    _, headers = await register_and_login(
        client,
        name="ContactOwner",
    )

    family = await create_family(
        client,
        headers=headers,
    )

    contact = await create_contact(
        client,
        family_id=family["id"],
        headers=headers,
    )

    assert contact["family_id"] == family["id"]
    assert contact["display_name"] == "妈妈"
    assert contact["relationship_label"] == "母亲"
    assert contact["phone"] == "13800138000"
    assert contact["preferred_channel"] == "phone"
    assert contact["priority"] == 1
    assert contact["status"] == "active"


async def test_user_can_list_trusted_contacts(
    client: AsyncClient,
) -> None:
    """家庭成员可以查询自己的可信联系人。"""

    _, headers = await register_and_login(
        client,
        name="ContactListOwner",
    )

    family = await create_family(
        client,
        headers=headers,
    )

    contact = await create_contact(
        client,
        family_id=family["id"],
        headers=headers,
    )

    response = await client.get(
        (
            f"/api/v1/families/{family['id']}"
            "/trusted-contacts"
        ),
        headers=headers,
    )

    assert response.status_code == 200, (
        response.text
    )

    body = response.json()

    assert body["total"] == 1
    assert body["items"][0]["id"] == contact["id"]


async def test_user_can_update_trusted_contact(
    client: AsyncClient,
) -> None:
    """家庭成员可以修改自己的可信联系人。"""

    _, headers = await register_and_login(
        client,
        name="ContactUpdateOwner",
    )

    family = await create_family(
        client,
        headers=headers,
    )

    contact = await create_contact(
        client,
        family_id=family["id"],
        headers=headers,
    )

    response = await client.patch(
        (
            f"/api/v1/families/{family['id']}"
            f"/trusted-contacts/{contact['id']}"
        ),
        headers=headers,
        json={
            "display_name": "母亲",
            "priority": 2,
            "can_receive_alerts": False,
        },
    )

    assert response.status_code == 200, (
        response.text
    )

    body = response.json()

    assert body["display_name"] == "母亲"
    assert body["priority"] == 2
    assert body["can_receive_alerts"] is False


async def test_user_can_disable_trusted_contact(
    client: AsyncClient,
) -> None:
    """停用后默认列表不再返回该联系人。"""

    _, headers = await register_and_login(
        client,
        name="ContactDisableOwner",
    )

    family = await create_family(
        client,
        headers=headers,
    )

    contact = await create_contact(
        client,
        family_id=family["id"],
        headers=headers,
    )

    disable_response = await client.post(
        (
            f"/api/v1/families/{family['id']}"
            f"/trusted-contacts/{contact['id']}"
            "/disable"
        ),
        headers=headers,
    )

    assert disable_response.status_code == 200
    assert (
        disable_response.json()["status"]
        == "disabled"
    )

    default_list_response = await client.get(
        (
            f"/api/v1/families/{family['id']}"
            "/trusted-contacts"
        ),
        headers=headers,
    )

    assert (
        default_list_response.json()["total"]
        == 0
    )

    full_list_response = await client.get(
        (
            f"/api/v1/families/{family['id']}"
            "/trusted-contacts"
            "?include_disabled=true"
        ),
        headers=headers,
    )

    assert full_list_response.status_code == 200
    assert full_list_response.json()["total"] == 1
    assert (
        full_list_response.json()["items"][0][
            "status"
        ]
        == "disabled"
    )


async def test_duplicate_contact_is_rejected(
    client: AsyncClient,
) -> None:
    """相同电话号码不能重复创建。"""

    _, headers = await register_and_login(
        client,
        name="DuplicateContactOwner",
    )

    family = await create_family(
        client,
        headers=headers,
    )

    await create_contact(
        client,
        family_id=family["id"],
        headers=headers,
    )

    response = await client.post(
        (
            f"/api/v1/families/{family['id']}"
            "/trusted-contacts"
        ),
        headers=headers,
        json={
            "display_name": "重复联系人",
            "relationship_label": "亲属",
            "phone": "13800138000",
        },
    )

    assert response.status_code == 409


async def test_outside_user_cannot_read_contacts(
    client: AsyncClient,
) -> None:
    """非家庭成员不能查询可信联系人。"""

    _, owner_headers = await register_and_login(
        client,
        name="PrivateContactOwner",
    )

    _, outside_headers = await register_and_login(
        client,
        name="OutsideContactUser",
    )

    family = await create_family(
        client,
        headers=owner_headers,
    )

    response = await client.get(
        (
            f"/api/v1/families/{family['id']}"
            "/trusted-contacts"
        ),
        headers=outside_headers,
    )

    assert response.status_code == 404