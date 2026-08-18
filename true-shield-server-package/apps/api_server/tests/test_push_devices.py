import pytest
from httpx import AsyncClient

REGISTER_URL = "/api/v1/auth/register"
LOGIN_URL = "/api/v1/auth/login"
PUSH_DEVICES_URL = "/api/v1/users/me/push-devices"

PASSWORD = "PushDevicePassword_123!"
INSTALLATION_ID = "cQ8Yx2T5n0ExampleFirebaseInstallationId"


async def register_and_login(
    client: AsyncClient,
    *,
    email: str = "push-device@example.com",
) -> dict[str, str]:
    register_response = await client.post(
        REGISTER_URL,
        json={
            "email": email,
            "phone": None,
            "display_name": "推送设备测试",
            "password": PASSWORD,
        },
    )

    assert register_response.status_code == 201

    login_response = await client.post(
        LOGIN_URL,
        data={
            "username": email,
            "password": PASSWORD,
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
async def test_register_refresh_and_revoke_push_device(
    db_client: AsyncClient,
) -> None:
    headers = await register_and_login(db_client)

    first_response = await db_client.post(
        PUSH_DEVICES_URL,
        headers=headers,
        json={
            "installation_id": INSTALLATION_ID,
            "platform": "android",
            "device_name": "Google sdk_gphone64_x86_64",
            "app_version": "1.0",
        },
    )

    assert first_response.status_code == 200
    first_device = first_response.json()
    assert first_device["installation_id"] == INSTALLATION_ID
    assert first_device["status"] == "active"
    assert first_device["platform"] == "android"

    refresh_response = await db_client.post(
        PUSH_DEVICES_URL,
        headers=headers,
        json={
            "installation_id": INSTALLATION_ID,
            "platform": "android",
            "device_name": "Updated emulator",
            "app_version": "1.1",
        },
    )

    assert refresh_response.status_code == 200
    refreshed_device = refresh_response.json()
    assert refreshed_device["id"] == first_device["id"]
    assert refreshed_device["device_name"] == "Updated emulator"
    assert refreshed_device["app_version"] == "1.1"

    delete_response = await db_client.delete(
        f"{PUSH_DEVICES_URL}/{INSTALLATION_ID}",
        headers=headers,
    )

    assert delete_response.status_code == 204

    # 删除接口幂等，再次调用仍返回 204。
    second_delete_response = await db_client.delete(
        f"{PUSH_DEVICES_URL}/{INSTALLATION_ID}",
        headers=headers,
    )

    assert second_delete_response.status_code == 204

    reactivate_response = await db_client.post(
        PUSH_DEVICES_URL,
        headers=headers,
        json={
            "installation_id": INSTALLATION_ID,
            "platform": "android",
        },
    )

    assert reactivate_response.status_code == 200
    assert reactivate_response.json()["status"] == "active"


@pytest.mark.asyncio
async def test_push_device_registration_requires_login(
    db_client: AsyncClient,
) -> None:
    response = await db_client.post(
        PUSH_DEVICES_URL,
        json={
            "installation_id": INSTALLATION_ID,
            "platform": "android",
        },
    )

    assert response.status_code == 401


@pytest.mark.asyncio
async def test_reject_short_installation_id(
    db_client: AsyncClient,
) -> None:
    headers = await register_and_login(
        db_client,
        email="push-device-invalid@example.com",
    )

    response = await db_client.post(
        PUSH_DEVICES_URL,
        headers=headers,
        json={
            "installation_id": "short",
            "platform": "android",
        },
    )

    assert response.status_code == 422
