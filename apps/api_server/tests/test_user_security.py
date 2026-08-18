import pytest
from httpx import AsyncClient

REGISTER_URL = "/api/v1/auth/register"
LOGIN_URL = "/api/v1/auth/login"
PASSWORD_URL = "/api/v1/users/me/password"
ME_URL = "/api/v1/users/me"

EMAIL = "security@example.com"
OLD_PASSWORD = "SecurityOldPassword_123!"
NEW_PASSWORD = "SecurityNewPassword_456!"


async def register_and_login(
    client: AsyncClient,
) -> dict[str, str]:
    register_response = await client.post(
        REGISTER_URL,
        json={
            "email": EMAIL,
            "phone": None,
            "display_name": "安全测试用户",
            "password": OLD_PASSWORD,
        },
    )

    assert register_response.status_code == 201

    login_response = await client.post(
        LOGIN_URL,
        data={
            "username": EMAIL,
            "password": OLD_PASSWORD,
        },
    )

    assert login_response.status_code == 200

    return {
        "Authorization": (
            "Bearer "
            + login_response.json()["access_token"]
        ),
    }


@pytest.mark.asyncio
async def test_change_current_user_password(
    db_client: AsyncClient,
) -> None:
    headers = await register_and_login(db_client)

    response = await db_client.patch(
        PASSWORD_URL,
        headers=headers,
        json={
            "current_password": OLD_PASSWORD,
            "new_password": NEW_PASSWORD,
        },
    )

    assert response.status_code == 204

    old_token_response = await db_client.get(
        ME_URL,
        headers=headers,
    )
    assert old_token_response.status_code == 401

    old_login_response = await db_client.post(
        LOGIN_URL,
        data={
            "username": EMAIL,
            "password": OLD_PASSWORD,
        },
    )

    assert old_login_response.status_code == 401

    new_login_response = await db_client.post(
        LOGIN_URL,
        data={
            "username": EMAIL,
            "password": NEW_PASSWORD,
        },
    )

    assert new_login_response.status_code == 200


@pytest.mark.asyncio
async def test_reject_incorrect_current_password(
    db_client: AsyncClient,
) -> None:
    headers = await register_and_login(db_client)

    response = await db_client.patch(
        PASSWORD_URL,
        headers=headers,
        json={
            "current_password": "IncorrectPassword_123!",
            "new_password": NEW_PASSWORD,
        },
    )

    assert response.status_code == 400
    assert response.json()["detail"] == "当前密码不正确"


@pytest.mark.asyncio
async def test_reject_reusing_current_password(
    db_client: AsyncClient,
) -> None:
    headers = await register_and_login(db_client)

    response = await db_client.patch(
        PASSWORD_URL,
        headers=headers,
        json={
            "current_password": OLD_PASSWORD,
            "new_password": OLD_PASSWORD,
        },
    )

    assert response.status_code == 422
