import pytest
from httpx import AsyncClient

REGISTER_URL = "/api/v1/auth/register"
LOGIN_URL = "/api/v1/auth/login"
ME_URL = "/api/v1/users/me"


@pytest.mark.asyncio
async def test_register_login_and_get_current_user(
    db_client: AsyncClient,
) -> None:
    register_data = {
        "email": "user@example.com",
        "phone": "13800138000",
        "display_name": "测试用户",
        "password": "SecurePassword123!",
    }

    register_response = await db_client.post(
        REGISTER_URL,
        json=register_data,
    )

    assert register_response.status_code == 201

    registered_user = register_response.json()

    assert registered_user["email"] == "user@example.com"
    assert registered_user["display_name"] == "测试用户"
    assert registered_user["status"] == "active"
    assert "password" not in registered_user
    assert "password_hash" not in registered_user

    duplicate_response = await db_client.post(
        REGISTER_URL,
        json=register_data,
    )

    assert duplicate_response.status_code == 409

    wrong_password_response = await db_client.post(
        LOGIN_URL,
        data={
            "username": "user@example.com",
            "password": "wrong-password",
        },
    )

    assert wrong_password_response.status_code == 401

    login_response = await db_client.post(
        LOGIN_URL,
        data={
            "username": "user@example.com",
            "password": "SecurePassword123!",
        },
    )

    assert login_response.status_code == 200

    token_data = login_response.json()

    assert token_data["token_type"] == "bearer"
    assert token_data["access_token"]
    assert token_data["expires_in"] > 0

    me_response = await db_client.get(
        ME_URL,
        headers={"Authorization": (f"Bearer {token_data['access_token']}")},
    )

    assert me_response.status_code == 200

    current_user = me_response.json()

    assert current_user["email"] == "user@example.com"
    assert current_user["display_name"] == "测试用户"


@pytest.mark.asyncio
async def test_get_current_user_without_token(
    db_client: AsyncClient,
) -> None:
    response = await db_client.get(ME_URL)

    assert response.status_code == 401
