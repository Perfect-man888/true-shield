import pytest
from httpx import AsyncClient

from app.core.config import settings

REGISTER_URL = "/api/v1/auth/register"
LOGIN_URL = "/api/v1/auth/login"
ME_URL = "/api/v1/users/me"
RESET_REQUEST_URL = "/api/v1/auth/password-reset/request"
RESET_CONFIRM_URL = "/api/v1/auth/password-reset/confirm"

EMAIL = "password-reset@example.com"
OLD_PASSWORD = "ResetOldPassword_123!"
NEW_PASSWORD = "ResetNewPassword_456!"


async def register_user(client: AsyncClient) -> None:
    response = await client.post(
        REGISTER_URL,
        json={
            "email": EMAIL,
            "phone": None,
            "display_name": "密码找回测试",
            "password": OLD_PASSWORD,
        },
    )
    assert response.status_code == 201


async def login(
    client: AsyncClient,
    password: str,
) -> str:
    response = await client.post(
        LOGIN_URL,
        data={
            "username": EMAIL,
            "password": password,
        },
    )
    assert response.status_code == 200
    return response.json()["access_token"]


@pytest.mark.asyncio
async def test_request_and_confirm_password_reset(
    db_client: AsyncClient,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(settings, "notification_provider", "simulated")
    monkeypatch.setattr(settings, "debug", True)
    monkeypatch.setattr(settings, "app_env", "development")

    await register_user(db_client)
    old_access_token = await login(db_client, OLD_PASSWORD)

    request_response = await db_client.post(
        RESET_REQUEST_URL,
        json={"email": EMAIL},
    )

    assert request_response.status_code == 202
    request_body = request_response.json()
    assert request_body["expires_in"] > 0
    assert request_body["debug_code"].isdigit()
    assert len(request_body["debug_code"]) == 6

    reset_response = await db_client.post(
        RESET_CONFIRM_URL,
        json={
            "email": EMAIL,
            "code": request_body["debug_code"],
            "new_password": NEW_PASSWORD,
        },
    )

    assert reset_response.status_code == 204

    old_login_response = await db_client.post(
        LOGIN_URL,
        data={
            "username": EMAIL,
            "password": OLD_PASSWORD,
        },
    )
    assert old_login_response.status_code == 401

    new_access_token = await login(db_client, NEW_PASSWORD)

    old_token_me_response = await db_client.get(
        ME_URL,
        headers={
            "Authorization": f"Bearer {old_access_token}",
        },
    )
    assert old_token_me_response.status_code == 401

    new_token_me_response = await db_client.get(
        ME_URL,
        headers={
            "Authorization": f"Bearer {new_access_token}",
        },
    )
    assert new_token_me_response.status_code == 200


@pytest.mark.asyncio
async def test_password_reset_code_is_single_use(
    db_client: AsyncClient,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(settings, "notification_provider", "simulated")
    monkeypatch.setattr(settings, "debug", True)
    monkeypatch.setattr(settings, "app_env", "development")

    await register_user(db_client)

    request_response = await db_client.post(
        RESET_REQUEST_URL,
        json={"email": EMAIL},
    )
    code = request_response.json()["debug_code"]

    first_response = await db_client.post(
        RESET_CONFIRM_URL,
        json={
            "email": EMAIL,
            "code": code,
            "new_password": NEW_PASSWORD,
        },
    )
    assert first_response.status_code == 204

    second_response = await db_client.post(
        RESET_CONFIRM_URL,
        json={
            "email": EMAIL,
            "code": code,
            "new_password": "AnotherPassword_789!",
        },
    )
    assert second_response.status_code == 400


@pytest.mark.asyncio
async def test_reject_invalid_password_reset_code(
    db_client: AsyncClient,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(settings, "notification_provider", "simulated")
    monkeypatch.setattr(settings, "debug", True)
    monkeypatch.setattr(settings, "app_env", "development")

    await register_user(db_client)
    await db_client.post(
        RESET_REQUEST_URL,
        json={"email": EMAIL},
    )

    response = await db_client.post(
        RESET_CONFIRM_URL,
        json={
            "email": EMAIL,
            "code": "000000",
            "new_password": NEW_PASSWORD,
        },
    )

    assert response.status_code == 400
    assert response.json()["detail"] == "验证码无效、已过期或尝试次数过多"


@pytest.mark.asyncio
async def test_unknown_email_gets_generic_response(
    db_client: AsyncClient,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(settings, "notification_provider", "simulated")
    monkeypatch.setattr(settings, "debug", True)
    monkeypatch.setattr(settings, "app_env", "development")

    response = await db_client.post(
        RESET_REQUEST_URL,
        json={"email": "unknown@example.com"},
    )

    assert response.status_code == 202
    body = response.json()
    assert body["debug_code"] is None
    assert "如果该邮箱已注册" in body["message"]
