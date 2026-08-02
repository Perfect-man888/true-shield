from __future__ import annotations

import uuid

import pytest
from httpx import AsyncClient

TEST_PASSWORD = "CallGuardTest_123!"

pytestmark = pytest.mark.asyncio


async def register_and_login(
    client: AsyncClient,
    *,
    name: str,
) -> dict[str, str]:
    email = f"{name.lower()}-{uuid.uuid4().hex}@example.com"

    register_response = await client.post(
        "/api/v1/auth/register",
        json={
            "email": email,
            "phone": None,
            "display_name": name,
            "password": TEST_PASSWORD,
        },
    )
    assert register_response.status_code in {200, 201}, (
        register_response.text
    )

    login_response = await client.post(
        "/api/v1/auth/login",
        data={
            "username": email,
            "password": TEST_PASSWORD,
        },
    )
    assert login_response.status_code == 200, login_response.text

    return {
        "Authorization": (
            f"Bearer {login_response.json()['access_token']}"
        )
    }


async def create_family(
    client: AsyncClient,
    *,
    headers: dict[str, str],
) -> dict:
    response = await client.post(
        "/api/v1/families",
        headers=headers,
        json={"name": "通话护航测试家庭"},
    )
    assert response.status_code == 201, response.text
    return response.json()


async def test_call_guard_requires_login(
    db_client: AsyncClient,
) -> None:
    client = db_client
    response = await client.post(
        "/api/v1/call-guard/analyze-number",
        json={
            "phone_number": "+8613800138000",
            "direction": "incoming",
            "verification_status": "not_verified",
        },
    )

    assert response.status_code == 401


async def test_test_number_is_high_risk_and_not_persisted(
    db_client: AsyncClient,
) -> None:
    client = db_client
    headers = await register_and_login(
        client,
        name="CallGuardRiskUser",
    )

    response = await client.post(
        "/api/v1/call-guard/analyze-number",
        headers=headers,
        json={
            "phone_number": "+8612345678901",
            "direction": "incoming",
            "verification_status": "not_verified",
        },
    )

    assert response.status_code == 200, response.text
    body = response.json()

    assert body["risk_level"] == "high"
    assert body["score"] >= 70
    assert body["persisted"] is False
    assert body["masked_number"] != "+8612345678901"
    assert any(
        signal["signal_id"] == "CALL-RULE-EXACT"
        for signal in body["signals"]
    )

    history_response = await client.get(
        "/api/v1/risk/events",
        headers=headers,
    )
    assert history_response.status_code == 200
    assert history_response.json()["total"] == 0


async def test_trusted_contact_is_low_risk(
    db_client: AsyncClient,
) -> None:
    client = db_client
    headers = await register_and_login(
        client,
        name="CallGuardTrustedUser",
    )
    family = await create_family(
        client,
        headers=headers,
    )

    contact_response = await client.post(
        (
            f"/api/v1/families/{family['id']}"
            "/trusted-contacts"
        ),
        headers=headers,
        json={
            "display_name": "妈妈",
            "relationship_label": "母亲",
            "phone": "13800138000",
            "preferred_channel": "sms",
            "priority": 1,
            "can_receive_alerts": True,
        },
    )
    assert contact_response.status_code == 201, (
        contact_response.text
    )

    response = await client.post(
        "/api/v1/call-guard/analyze-number",
        headers=headers,
        json={
            "phone_number": "+86 138-0013-8000",
            "direction": "incoming",
            "verification_status": "not_verified",
        },
    )

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["risk_level"] == "low"
    assert body["score"] == 0
    assert body["is_trusted_contact"] is True
    assert body["trusted_contact_name"] == "妈妈"


async def test_failed_verification_overrides_trusted_contact(
    db_client: AsyncClient,
) -> None:
    client = db_client
    headers = await register_and_login(
        client,
        name="CallGuardSpoofedTrustedUser",
    )
    family = await create_family(
        client,
        headers=headers,
    )

    contact_response = await client.post(
        (
            f"/api/v1/families/{family['id']}"
            "/trusted-contacts"
        ),
        headers=headers,
        json={
            "display_name": "爸爸",
            "relationship_label": "父亲",
            "phone": "13900139000",
            "preferred_channel": "sms",
            "priority": 1,
            "can_receive_alerts": True,
        },
    )
    assert contact_response.status_code == 201

    response = await client.post(
        "/api/v1/call-guard/analyze-number",
        headers=headers,
        json={
            "phone_number": "+8613900139000",
            "direction": "incoming",
            "verification_status": "failed",
        },
    )

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["is_trusted_contact"] is True
    assert body["trusted_contact_name"] == "爸爸"
    assert body["risk_level"] == "high"
    assert any(
        signal["signal_id"] == "CALL-002"
        for signal in body["signals"]
    )


async def test_user_can_create_call_guard_family_help(
    db_client: AsyncClient,
) -> None:
    client = db_client
    headers = await register_and_login(
        client,
        name="CallGuardHelpUser",
    )
    family = await create_family(
        client,
        headers=headers,
    )

    response = await client.post(
        "/api/v1/call-guard/help",
        headers=headers,
        json={
            "family_id": family["id"],
            "phone_number": "+8612345678901",
            "direction": "incoming",
            "verification_status": "failed",
            "risk_level": "high",
            "score": 100,
            "summary": "疑似冒充公安机关并要求立即转账。",
        },
    )

    assert response.status_code == 201, response.text
    body = response.json()
    assert body["family_id"] == family["id"]
    assert body["event_id"]
    assert body["alert_id"]

    detail_response = await client.get(
        f"/api/v1/risk/events/{body['event_id']}",
        headers=headers,
    )
    assert detail_response.status_code == 200, (
        detail_response.text
    )
    detail = detail_response.json()

    assert detail["source_type"] == "call"
    metadata = detail["source_metadata"]["call_guard"]
    assert metadata["raw_audio_stored"] is False
    assert metadata["call_log_stored"] is False
    assert metadata["phone_number_masked"] != (
        "+8612345678901"
    )
    assert "+8612345678901" not in detail["source_text"]

    alert_response = await client.get(
        (
            f"/api/v1/families/{family['id']}"
            f"/alerts/{body['alert_id']}"
        ),
        headers=headers,
    )
    assert alert_response.status_code == 200, (
        alert_response.text
    )
    assert alert_response.json()["title"] == (
        "通话中一键家庭求助"
    )
