import pytest
from httpx import AsyncClient

REGISTER_URL = "/api/v1/auth/register"
LOGIN_URL = "/api/v1/auth/login"
ME_URL = "/api/v1/users/me"


async def register_and_login(
    client: AsyncClient,
    *,
    email: str,
    phone: str | None,
    display_name: str,
) -> dict[str, str]:
    password = "ProfileTestPassword_123!"

    register_response = await client.post(
        REGISTER_URL,
        json={
            "email": email,
            "phone": phone,
            "display_name": display_name,
            "password": password,
        },
    )

    assert register_response.status_code == 201

    login_response = await client.post(
        LOGIN_URL,
        data={
            "username": email,
            "password": password,
        },
    )

    assert login_response.status_code == 200

    token = login_response.json()["access_token"]

    return {
        "Authorization": f"Bearer {token}",
    }


@pytest.mark.asyncio
async def test_update_current_user_profile(
    db_client: AsyncClient,
) -> None:
    headers = await register_and_login(
        db_client,
        email="profile@example.com",
        phone="13800138001",
        display_name="原昵称",
    )

    update_response = await db_client.patch(
        ME_URL,
        headers=headers,
        json={
            "display_name": "  新昵称  ",
            "phone": "13900139001",
        },
    )

    assert update_response.status_code == 200

    updated_user = update_response.json()

    assert updated_user["display_name"] == "新昵称"
    assert updated_user["phone"] == "13900139001"
    assert updated_user["email"] == "profile@example.com"

    me_response = await db_client.get(
        ME_URL,
        headers=headers,
    )

    assert me_response.status_code == 200
    assert me_response.json()["display_name"] == "新昵称"
    assert me_response.json()["phone"] == "13900139001"


@pytest.mark.asyncio
async def test_clear_current_user_phone(
    db_client: AsyncClient,
) -> None:
    headers = await register_and_login(
        db_client,
        email="clear-phone@example.com",
        phone="13800138002",
        display_name="清除手机号",
    )

    response = await db_client.patch(
        ME_URL,
        headers=headers,
        json={
            "phone": None,
        },
    )

    assert response.status_code == 200
    assert response.json()["phone"] is None


@pytest.mark.asyncio
async def test_reject_phone_used_by_another_user(
    db_client: AsyncClient,
) -> None:
    first_headers = await register_and_login(
        db_client,
        email="first-profile@example.com",
        phone="13800138003",
        display_name="第一位用户",
    )

    await register_and_login(
        db_client,
        email="second-profile@example.com",
        phone="13800138004",
        display_name="第二位用户",
    )

    response = await db_client.patch(
        ME_URL,
        headers=first_headers,
        json={
            "phone": "13800138004",
        },
    )

    assert response.status_code == 409
    assert response.json()["detail"] == "该手机号已被其他账户使用"


@pytest.mark.asyncio
async def test_reject_empty_profile_update(
    db_client: AsyncClient,
) -> None:
    headers = await register_and_login(
        db_client,
        email="empty-update@example.com",
        phone=None,
        display_name="空更新测试",
    )

    response = await db_client.patch(
        ME_URL,
        headers=headers,
        json={},
    )

    assert response.status_code == 422
