from __future__ import annotations

import uuid

import pytest
from httpx import AsyncClient

TEST_PASSWORD = "FamilyAPITest_123!"

pytestmark = pytest.mark.asyncio


async def register_and_login(
    client: AsyncClient,
    *,
    name: str,
) -> tuple[str, dict[str, str]]:
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

    return email, {
        "Authorization": f"Bearer {token}",
    }


async def create_family(
    client: AsyncClient,
    *,
    headers: dict[str, str],
    name: str,
) -> dict:
    """创建家庭并返回响应体。"""

    response = await client.post(
        "/api/v1/families",
        headers=headers,
        json={
            "name": name,
        },
    )

    assert response.status_code == 201, (
        response.text
    )

    return response.json()


async def test_user_can_create_family(
    client: AsyncClient,
) -> None:
    """用户创建家庭后应自动成为 owner。"""

    _, headers = await register_and_login(
        client,
        name="FamilyOwner",
    )

    body = await create_family(
        client,
        headers=headers,
        name="  我的   安全家庭  ",
    )

    assert body["name"] == "我的 安全家庭"
    assert body["status"] == "active"
    assert body["my_role"] == "owner"

    assert body["id"]
    assert body["owner_user_id"]
    assert body["created_at"]
    assert body["updated_at"]


async def test_user_can_list_own_families(
    client: AsyncClient,
) -> None:
    """用户可以查询自己加入的家庭列表。"""

    _, headers = await register_and_login(
        client,
        name="FamilyListUser",
    )

    await create_family(
        client,
        headers=headers,
        name="第一个家庭",
    )

    await create_family(
        client,
        headers=headers,
        name="第二个家庭",
    )

    response = await client.get(
        "/api/v1/families",
        headers=headers,
    )

    assert response.status_code == 200, (
        response.text
    )

    body = response.json()

    assert body["total"] == 2
    assert len(body["items"]) == 2

    names = {
        item["name"]
        for item in body["items"]
    }

    assert names == {
        "第一个家庭",
        "第二个家庭",
    }

    assert all(
        item["my_role"] == "owner"
        for item in body["items"]
    )


async def test_family_owner_can_read_members(
    client: AsyncClient,
) -> None:
    """家庭所有者可以查询成员列表。"""

    email, headers = await register_and_login(
        client,
        name="MemberListOwner",
    )

    family = await create_family(
        client,
        headers=headers,
        name="成员查询家庭",
    )

    response = await client.get(
        (
            "/api/v1/families/"
            f"{family['id']}/members"
        ),
        headers=headers,
    )

    assert response.status_code == 200, (
        response.text
    )

    body = response.json()

    assert body["family_id"] == family["id"]
    assert body["total"] == 1

    member = body["items"][0]

    assert member["email"] == email
    assert (
        member["display_name"]
        == "MemberListOwner"
    )
    assert member["role"] == "owner"
    assert member["status"] == "active"


async def test_user_cannot_read_other_family_members(
    client: AsyncClient,
) -> None:
    """非家庭成员不能读取成员列表。"""

    _, owner_headers = await register_and_login(
        client,
        name="PrivateFamilyOwner",
    )

    _, other_headers = await register_and_login(
        client,
        name="OutsideFamilyUser",
    )

    family = await create_family(
        client,
        headers=owner_headers,
        name="私有家庭",
    )

    response = await client.get(
        (
            "/api/v1/families/"
            f"{family['id']}/members"
        ),
        headers=other_headers,
    )

    assert response.status_code == 404

    assert response.json()["detail"] == (
        "家庭不存在或无权访问。"
    )