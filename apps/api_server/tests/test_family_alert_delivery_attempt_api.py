from __future__ import annotations

import uuid

import pytest
from httpx import AsyncClient

TEST_PASSWORD = "RiskTestPassword_123!"

pytestmark = pytest.mark.asyncio


async def register_and_login(
    client: AsyncClient,
    *,
    name: str,
) -> tuple[str, dict[str, str]]:
    """注册测试用户并返回登录请求头。"""

    random_part = uuid.uuid4().hex

    email = (
        f"{name.lower()}-{random_part}"
        "@example.com"
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

    assert register_response.status_code == 201, (
        register_response.text
    )

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

    access_token = login_response.json()[
        "access_token"
    ]

    return email, {
        "Authorization": f"Bearer {access_token}",
    }


async def create_family(
    client: AsyncClient,
    *,
    headers: dict[str, str],
) -> str:
    """创建一个测试家庭。"""

    response = await client.post(
        "/api/v1/families",
        headers=headers,
        json={
            "name": (
                f"发送记录测试家庭-"
                f"{uuid.uuid4().hex[:8]}"
            ),
        },
    )

    assert response.status_code == 201, response.text

    return response.json()["id"]


async def create_trusted_contact(
    client: AsyncClient,
    *,
    family_id: str,
    headers: dict[str, str],
) -> None:
    """创建一个可以接收短信告警的联系人。"""

    phone_suffix = (
        str(uuid.uuid4().int % 1_000_000_000)
        .zfill(9)
    )

    response = await client.post(
        (
            f"/api/v1/families/{family_id}"
            "/trusted-contacts"
        ),
        headers=headers,
        json={
            "subject_user_id": None,
            "display_name": "测试联系人",
            "relationship_label": "家人",
            "phone": f"13{phone_suffix}",
            "email": None,
            "preferred_channel": "sms",
            "priority": 1,
            "can_receive_alerts": True,
        },
    )

    assert response.status_code == 201, response.text


async def configure_alert_policy(
    client: AsyncClient,
    *,
    family_id: str,
    headers: dict[str, str],
) -> None:
    """开启自动创建告警，但关闭自动发送。"""

    response = await client.patch(
        (
            f"/api/v1/families/{family_id}"
            "/alert-policy"
        ),
        headers=headers,
        json={
            "auto_create_enabled": True,
            "auto_dispatch_enabled": False,
            "minimum_risk_level": "high",
            "enabled_source_types": [
                "text",
                "image",
                "url",
            ],
            "max_recipients": 3,
        },
    )

    assert response.status_code == 200, response.text


async def create_high_risk_event(
    client: AsyncClient,
    *,
    headers: dict[str, str],
) -> str:
    """创建一个高风险文本事件。"""

    response = await client.post(
        "/api/v1/risk/text/analyze",
        headers=headers,
        json={
            "text": (
                "我是公安局工作人员，你涉嫌洗钱，"
                "必须马上把资金转到安全账户，"
                "不能告诉家人。"
            ),
        },
    )

    assert response.status_code == 200, response.text

    body = response.json()

    assert body["risk_level"] == "high"

    return body["event_id"]


async def find_alert_for_event(
    client: AsyncClient,
    *,
    family_id: str,
    event_id: str,
    headers: dict[str, str],
) -> dict:
    """根据风险事件 ID 查找自动创建的家庭告警。"""

    response = await client.get(
        f"/api/v1/families/{family_id}/alerts",
        headers=headers,
    )

    assert response.status_code == 200, response.text

    alert = next(
        (
            item
            for item in response.json()["items"]
            if item["risk_event_id"] == event_id
        ),
        None,
    )

    assert alert is not None, response.text

    return alert


async def prepare_pending_alert(
    client: AsyncClient,
    *,
    user_name: str,
) -> tuple[
    dict[str, str],
    str,
    dict,
]:
    """准备一个尚未发送的家庭告警。"""

    _, headers = await register_and_login(
        client,
        name=user_name,
    )

    family_id = await create_family(
        client,
        headers=headers,
    )

    await create_trusted_contact(
        client,
        family_id=family_id,
        headers=headers,
    )

    await configure_alert_policy(
        client,
        family_id=family_id,
        headers=headers,
    )

    event_id = await create_high_risk_event(
        client,
        headers=headers,
    )

    alert = await find_alert_for_event(
        client,
        family_id=family_id,
        event_id=event_id,
        headers=headers,
    )

    return headers, family_id, alert


async def test_member_can_query_empty_attempts(
    client: AsyncClient,
) -> None:
    """尚未发送时，发送记录应为空。"""

    headers, family_id, alert = (
        await prepare_pending_alert(
            client,
            user_name="EmptyAttemptOwner",
        )
    )

    response = await client.get(
        (
            f"/api/v1/families/{family_id}"
            f"/alerts/{alert['id']}"
            "/delivery-attempts"
        ),
        headers=headers,
    )

    assert response.status_code == 200, response.text

    body = response.json()

    assert body["family_id"] == family_id
    assert body["alert_id"] == alert["id"]
    assert body["items"] == []
    assert body["total"] == 0


async def test_successful_dispatch_creates_attempt(
    client: AsyncClient,
) -> None:
    """发送成功后应生成一条发送记录。"""

    headers, family_id, alert = (
        await prepare_pending_alert(
            client,
            user_name="SuccessAttemptOwner",
        )
    )

    dispatch_response = await client.post(
        (
            f"/api/v1/families/{family_id}"
            f"/alerts/{alert['id']}/dispatch"
        ),
        headers=headers,
        json={
            "simulated_failure_recipient_ids": [],
        },
    )

    assert dispatch_response.status_code == 200, (
        dispatch_response.text
    )

    assert dispatch_response.json()["sent_count"] == 1

    response = await client.get(
        (
            f"/api/v1/families/{family_id}"
            f"/alerts/{alert['id']}"
            "/delivery-attempts"
        ),
        headers=headers,
    )

    assert response.status_code == 200, response.text

    body = response.json()

    assert body["total"] == 1

    attempt = body["items"][0]

    assert attempt["attempt_number"] == 1
    assert attempt["provider"] == "simulated"
    assert attempt["status"] == "sent"
    assert attempt["external_message_id"].startswith(
        "sim-"
    )
    assert attempt["failure_reason"] is None


async def test_retry_returns_two_attempts(
    client: AsyncClient,
) -> None:
    """第一次失败、第二次成功时应返回两条记录。"""

    headers, family_id, alert = (
        await prepare_pending_alert(
            client,
            user_name="RetryAttemptOwner",
        )
    )

    recipient_id = alert["recipients"][0]["id"]

    first_response = await client.post(
        (
            f"/api/v1/families/{family_id}"
            f"/alerts/{alert['id']}/dispatch"
        ),
        headers=headers,
        json={
            "simulated_failure_recipient_ids": [
                recipient_id,
            ],
        },
    )

    assert first_response.status_code == 200, (
        first_response.text
    )
    assert first_response.json()["failed_count"] == 1

    second_response = await client.post(
        (
            f"/api/v1/families/{family_id}"
            f"/alerts/{alert['id']}/dispatch"
        ),
        headers=headers,
        json={
            "simulated_failure_recipient_ids": [],
        },
    )

    assert second_response.status_code == 200, (
        second_response.text
    )
    assert second_response.json()["sent_count"] == 1

    response = await client.get(
        (
            f"/api/v1/families/{family_id}"
            f"/alerts/{alert['id']}"
            "/delivery-attempts"
        ),
        headers=headers,
    )

    assert response.status_code == 200, response.text

    body = response.json()

    assert body["total"] == 2

    assert [
        item["attempt_number"]
        for item in body["items"]
    ] == [2, 1]

    assert [
        item["status"]
        for item in body["items"]
    ] == ["sent", "failed"]

    successful_attempt = body["items"][0]
    failed_attempt = body["items"][1]

    assert successful_attempt[
        "external_message_id"
    ].startswith("sim-")

    assert failed_attempt[
        "external_message_id"
    ] is None

    assert failed_attempt["failure_reason"] is not None


async def test_outside_user_cannot_query_attempts(
    client: AsyncClient,
) -> None:
    """家庭外用户不能查看发送记录。"""

    _, family_id, alert = (
        await prepare_pending_alert(
            client,
            user_name="PrivateAttemptOwner",
        )
    )

    _, outside_headers = await register_and_login(
        client,
        name="OutsideAttemptUser",
    )

    response = await client.get(
        (
            f"/api/v1/families/{family_id}"
            f"/alerts/{alert['id']}"
            "/delivery-attempts"
        ),
        headers=outside_headers,
    )

    assert response.status_code in {
        403,
        404,
    }

async def test_failed_recipient_can_be_retried(
    client: AsyncClient,
) -> None:
    """失败通知应能通过专用接口重试成功。"""

    headers, family_id, alert = (
        await prepare_pending_alert(
            client,
            user_name="RetryFailedOwner",
        )
    )

    recipient_id = alert["recipients"][0]["id"]

    failed_response = await client.post(
        (
            f"/api/v1/families/{family_id}"
            f"/alerts/{alert['id']}/dispatch"
        ),
        headers=headers,
        json={
            "simulated_failure_recipient_ids": [
                recipient_id,
            ],
        },
    )

    assert failed_response.status_code == 200, (
        failed_response.text
    )
    assert failed_response.json()["failed_count"] == 1

    retry_response = await client.post(
        (
            f"/api/v1/families/{family_id}"
            f"/alerts/{alert['id']}"
            "/retry-failed"
        ),
        headers=headers,
    )

    assert retry_response.status_code == 200, (
        retry_response.text
    )

    retry_body = retry_response.json()

    assert retry_body["attempted_count"] == 1
    assert retry_body["sent_count"] == 1
    assert retry_body["failed_count"] == 0

    recipient = retry_body["alert"][
        "recipients"
    ][0]

    assert recipient["delivery_status"] == "sent"
    assert (
        recipient["delivery_provider"]
        == "simulated"
    )
    assert recipient[
        "external_message_id"
    ].startswith("sim-")

    attempts_response = await client.get(
        (
            f"/api/v1/families/{family_id}"
            f"/alerts/{alert['id']}"
            "/delivery-attempts"
        ),
        headers=headers,
    )

    assert attempts_response.status_code == 200, (
        attempts_response.text
    )

    attempts = attempts_response.json()

    assert attempts["total"] == 2

    assert [
        item["status"]
        for item in attempts["items"]
    ] == ["sent", "failed"]

    assert [
        item["attempt_number"]
        for item in attempts["items"]
    ] == [2, 1]


async def test_retry_rejected_when_no_failed_recipient(
    client: AsyncClient,
) -> None:
    """没有失败接收人时不应执行重试。"""

    headers, family_id, alert = (
        await prepare_pending_alert(
            client,
            user_name="NoFailedRetryOwner",
        )
    )

    response = await client.post(
        (
            f"/api/v1/families/{family_id}"
            f"/alerts/{alert['id']}"
            "/retry-failed"
        ),
        headers=headers,
    )

    assert response.status_code == 409, response.text

    assert response.json()["detail"] == (
        "当前没有需要重试的失败接收人。"
    )


async def test_outside_user_cannot_retry_failed_delivery(
    client: AsyncClient,
) -> None:
    """家庭外用户不能重试家庭告警通知。"""

    owner_headers, family_id, alert = (
        await prepare_pending_alert(
            client,
            user_name="PrivateRetryOwner",
        )
    )

    recipient_id = alert["recipients"][0]["id"]

    failed_response = await client.post(
        (
            f"/api/v1/families/{family_id}"
            f"/alerts/{alert['id']}/dispatch"
        ),
        headers=owner_headers,
        json={
            "simulated_failure_recipient_ids": [
                recipient_id,
            ],
        },
    )

    assert failed_response.status_code == 200, (
        failed_response.text
    )

    _, outside_headers = await register_and_login(
        client,
        name="OutsideRetryUser",
    )

    response = await client.post(
        (
            f"/api/v1/families/{family_id}"
            f"/alerts/{alert['id']}"
            "/retry-failed"
        ),
        headers=outside_headers,
    )

    assert response.status_code in {
        403,
        404,
    }