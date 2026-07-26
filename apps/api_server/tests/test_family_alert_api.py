from __future__ import annotations

import uuid

import pytest
from httpx import AsyncClient

import app.api.routes.risk as risk_routes

TEST_PASSWORD = "FamilyAlertTest_123!"

pytestmark = pytest.mark.asyncio

@pytest.fixture(autouse=True)
def disable_automatic_family_alert_creation(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """
    本测试文件只验证手动家庭告警接口。

    风险分析后的自动告警功能由独立的自动化测试验证，
    因此这里关闭自动触发，避免告警被提前创建。
    """

    async def no_op_trigger(
        db,
        *,
        event,
    ):
        return None

    monkeypatch.setattr(
        risk_routes,
        "trigger_family_alert_automation_safely",
        no_op_trigger,
    )

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
    """创建家庭。"""

    response = await client.post(
        "/api/v1/families",
        headers=headers,
        json={
            "name": "家庭告警测试家庭",
        },
    )

    assert response.status_code == 201, (
        response.text
    )

    return response.json()


async def create_trusted_contact(
    client: AsyncClient,
    *,
    family_id: str,
    headers: dict[str, str],
) -> dict:
    """创建允许接收告警的可信联系人。"""

    response = await client.post(
        (
            f"/api/v1/families/{family_id}"
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

    assert response.status_code == 201, (
        response.text
    )

    return response.json()


async def create_risk_event(
    client: AsyncClient,
    *,
    headers: dict[str, str],
    high_risk: bool,
) -> dict:
    """创建高风险或低风险文本事件。"""

    if high_risk:
        text = (
            "我是公安局的，你涉嫌洗钱，"
            "不能告诉家人，必须马上"
            "把钱转到安全账户。"
        )
    else:
        text = "今天下午一起吃饭。"

    response = await client.post(
        "/api/v1/risk/text/analyze",
        headers=headers,
        json={
            "text": text,
        },
    )

    assert response.status_code == 200, (
        response.text
    )

    return response.json()


async def create_alert(
    client: AsyncClient,
    *,
    family_id: str,
    event_id: str,
    headers: dict[str, str],
) -> dict:
    """根据风险事件创建家庭告警。"""

    response = await client.post(
        (
            f"/api/v1/families/{family_id}"
            f"/alerts/from-events/{event_id}"
        ),
        headers=headers,
    )

    assert response.status_code == 201, (
        response.text
    )

    return response.json()


async def test_high_risk_event_creates_alert(
    client: AsyncClient,
) -> None:
    """高风险事件应成功生成家庭告警。"""

    _, headers = await register_and_login(
        client,
        name="AlertOwner",
    )

    family = await create_family(
        client,
        headers=headers,
    )

    contact = await create_trusted_contact(
        client,
        family_id=family["id"],
        headers=headers,
    )

    event = await create_risk_event(
        client,
        headers=headers,
        high_risk=True,
    )

    alert = await create_alert(
        client,
        family_id=family["id"],
        event_id=event["event_id"],
        headers=headers,
    )

    assert alert["family_id"] == family["id"]
    assert alert["risk_event_id"] == (
        event["event_id"]
    )
    assert alert["risk_level"] == "high"
    assert alert["status"] == "pending"
    assert len(alert["recipients"]) == 1

    recipient = alert["recipients"][0]

    assert recipient["trusted_contact_id"] == (
        contact["id"]
    )
    assert recipient["recipient_name"] == "妈妈"
    assert recipient["channel"] == "sms"
    assert recipient["destination"] == (
        "13800138000"
    )
    assert (
        recipient["delivery_status"]
        == "pending"
    )


async def test_alert_can_exist_without_contacts(
    client: AsyncClient,
) -> None:
    """没有可信联系人时仍应创建家庭告警。"""

    _, headers = await register_and_login(
        client,
        name="NoContactAlertOwner",
    )

    family = await create_family(
        client,
        headers=headers,
    )

    event = await create_risk_event(
        client,
        headers=headers,
        high_risk=True,
    )

    alert = await create_alert(
        client,
        family_id=family["id"],
        event_id=event["event_id"],
        headers=headers,
    )

    assert alert["recipients"] == []


async def test_family_member_can_list_alerts(
    client: AsyncClient,
) -> None:
    """家庭成员可以查询家庭告警列表。"""

    _, headers = await register_and_login(
        client,
        name="AlertListOwner",
    )

    family = await create_family(
        client,
        headers=headers,
    )

    event = await create_risk_event(
        client,
        headers=headers,
        high_risk=True,
    )

    alert = await create_alert(
        client,
        family_id=family["id"],
        event_id=event["event_id"],
        headers=headers,
    )

    response = await client.get(
        (
            f"/api/v1/families/{family['id']}"
            "/alerts"
        ),
        headers=headers,
    )

    assert response.status_code == 200, (
        response.text
    )

    body = response.json()

    assert body["total"] == 1
    assert body["items"][0]["id"] == alert["id"]


async def test_duplicate_alert_is_rejected(
    client: AsyncClient,
) -> None:
    """同一风险事件不能重复生成告警。"""

    _, headers = await register_and_login(
        client,
        name="DuplicateAlertOwner",
    )

    family = await create_family(
        client,
        headers=headers,
    )

    event = await create_risk_event(
        client,
        headers=headers,
        high_risk=True,
    )

    await create_alert(
        client,
        family_id=family["id"],
        event_id=event["event_id"],
        headers=headers,
    )

    response = await client.post(
        (
            f"/api/v1/families/{family['id']}"
            f"/alerts/from-events/"
            f"{event['event_id']}"
        ),
        headers=headers,
    )

    assert response.status_code == 409

    assert response.json()["detail"] == (
        "该风险事件已经生成过家庭告警。"
    )


async def test_low_risk_event_is_rejected(
    client: AsyncClient,
) -> None:
    """低风险事件不能生成家庭告警。"""

    _, headers = await register_and_login(
        client,
        name="LowRiskAlertOwner",
    )

    family = await create_family(
        client,
        headers=headers,
    )

    event = await create_risk_event(
        client,
        headers=headers,
        high_risk=False,
    )

    response = await client.post(
        (
            f"/api/v1/families/{family['id']}"
            f"/alerts/from-events/"
            f"{event['event_id']}"
        ),
        headers=headers,
    )

    assert response.status_code == 409

    assert response.json()["detail"] == (
        "只有 high 风险事件可以生成家庭告警。"
    )


async def test_outside_user_cannot_create_alert(
    client: AsyncClient,
) -> None:
    """非家庭成员不能生成家庭告警。"""

    _, owner_headers = await register_and_login(
        client,
        name="PrivateAlertOwner",
    )

    _, outside_headers = await register_and_login(
        client,
        name="OutsideAlertUser",
    )

    family = await create_family(
        client,
        headers=owner_headers,
    )

    event = await create_risk_event(
        client,
        headers=owner_headers,
        high_risk=True,
    )

    response = await client.post(
        (
            f"/api/v1/families/{family['id']}"
            f"/alerts/from-events/"
            f"{event['event_id']}"
        ),
        headers=outside_headers,
    )

    assert response.status_code == 404

async def test_family_member_can_acknowledge_alert(
    client: AsyncClient,
) -> None:
    """家庭成员可以确认风险告警。"""

    _, headers = await register_and_login(
        client,
        name="AcknowledgeAlertOwner",
    )

    family = await create_family(
        client,
        headers=headers,
    )

    event = await create_risk_event(
        client,
        headers=headers,
        high_risk=True,
    )

    alert = await create_alert(
        client,
        family_id=family["id"],
        event_id=event["event_id"],
        headers=headers,
    )

    response = await client.post(
        (
            f"/api/v1/families/{family['id']}"
            f"/alerts/{alert['id']}"
            "/acknowledge"
        ),
        headers=headers,
    )

    assert response.status_code == 200, (
        response.text
    )

    body = response.json()

    assert body["status"] == "acknowledged"
    assert (
        body["acknowledged_by_user_id"]
        is not None
    )
    assert body["acknowledged_at"] is not None
    assert body["resolved_at"] is None


async def test_subject_user_can_resolve_alert(
    client: AsyncClient,
) -> None:
    """被保护用户可以完成风险告警处理。"""

    _, headers = await register_and_login(
        client,
        name="ResolveAlertOwner",
    )

    family = await create_family(
        client,
        headers=headers,
    )

    event = await create_risk_event(
        client,
        headers=headers,
        high_risk=True,
    )

    alert = await create_alert(
        client,
        family_id=family["id"],
        event_id=event["event_id"],
        headers=headers,
    )

    response = await client.post(
        (
            f"/api/v1/families/{family['id']}"
            f"/alerts/{alert['id']}"
            "/resolve"
        ),
        headers=headers,
        json={
            "resolution_note": (
                "已联系本人确认，没有发生转账，"
                "并已拉黑可疑联系人。"
            ),
        },
    )

    assert response.status_code == 200, (
        response.text
    )

    body = response.json()

    assert body["status"] == "resolved"
    assert body["acknowledged_at"] is not None
    assert body["resolved_at"] is not None
    assert (
        body["resolved_by_user_id"]
        is not None
    )
    assert body["resolution_note"] == (
        "已联系本人确认，没有发生转账，"
        "并已拉黑可疑联系人。"
    )


async def test_resolve_alert_is_idempotent(
    client: AsyncClient,
) -> None:
    """重复完成处理应返回同一告警。"""

    _, headers = await register_and_login(
        client,
        name="IdempotentResolveOwner",
    )

    family = await create_family(
        client,
        headers=headers,
    )

    event = await create_risk_event(
        client,
        headers=headers,
        high_risk=True,
    )

    alert = await create_alert(
        client,
        family_id=family["id"],
        event_id=event["event_id"],
        headers=headers,
    )

    url = (
        f"/api/v1/families/{family['id']}"
        f"/alerts/{alert['id']}/resolve"
    )

    first_response = await client.post(
        url,
        headers=headers,
        json={
            "resolution_note": "已完成核实。",
        },
    )

    assert first_response.status_code == 200

    second_response = await client.post(
        url,
        headers=headers,
        json={
            "resolution_note": "重复提交。",
        },
    )

    assert second_response.status_code == 200

    assert (
        second_response.json()["id"]
        == first_response.json()["id"]
    )

    assert (
        second_response.json()["resolution_note"]
        == "已完成核实。"
    )


async def test_resolved_alert_cannot_be_acknowledged(
    client: AsyncClient,
) -> None:
    """已经处理完成的告警不能再次确认。"""

    _, headers = await register_and_login(
        client,
        name="ResolvedAcknowledgeOwner",
    )

    family = await create_family(
        client,
        headers=headers,
    )

    event = await create_risk_event(
        client,
        headers=headers,
        high_risk=True,
    )

    alert = await create_alert(
        client,
        family_id=family["id"],
        event_id=event["event_id"],
        headers=headers,
    )

    await client.post(
        (
            f"/api/v1/families/{family['id']}"
            f"/alerts/{alert['id']}/resolve"
        ),
        headers=headers,
        json={
            "resolution_note": "已处理。",
        },
    )

    response = await client.post(
        (
            f"/api/v1/families/{family['id']}"
            f"/alerts/{alert['id']}"
            "/acknowledge"
        ),
        headers=headers,
    )

    assert response.status_code == 409

    assert response.json()["detail"] == (
        "该家庭告警已经处理完成。"
    )


async def test_outside_user_cannot_acknowledge_alert(
    client: AsyncClient,
) -> None:
    """非家庭成员不能确认家庭告警。"""

    _, owner_headers = await register_and_login(
        client,
        name="PrivateLifecycleOwner",
    )

    _, outside_headers = await register_and_login(
        client,
        name="OutsideLifecycleUser",
    )

    family = await create_family(
        client,
        headers=owner_headers,
    )

    event = await create_risk_event(
        client,
        headers=owner_headers,
        high_risk=True,
    )

    alert = await create_alert(
        client,
        family_id=family["id"],
        event_id=event["event_id"],
        headers=owner_headers,
    )

    response = await client.post(
        (
            f"/api/v1/families/{family['id']}"
            f"/alerts/{alert['id']}"
            "/acknowledge"
        ),
        headers=outside_headers,
    )

    assert response.status_code == 404

async def dispatch_alert(
    client: AsyncClient,
    *,
    family_id: str,
    alert_id: str,
    headers: dict[str, str],
    simulated_failure_recipient_ids: (
        list[str] | None
    ) = None,
):
    """调用家庭告警模拟发送接口。"""

    return await client.post(
        (
            f"/api/v1/families/{family_id}"
            f"/alerts/{alert_id}/dispatch"
        ),
        headers=headers,
        json={
            "simulated_failure_recipient_ids": (
                simulated_failure_recipient_ids
                or []
            ),
        },
    )


async def test_owner_can_dispatch_alert(
    client: AsyncClient,
) -> None:
    """家庭所有者可以发送家庭告警。"""

    _, headers = await register_and_login(
        client,
        name="DispatchAlertOwner",
    )

    family = await create_family(
        client,
        headers=headers,
    )

    await create_trusted_contact(
        client,
        family_id=family["id"],
        headers=headers,
    )

    event = await create_risk_event(
        client,
        headers=headers,
        high_risk=True,
    )

    alert = await create_alert(
        client,
        family_id=family["id"],
        event_id=event["event_id"],
        headers=headers,
    )

    response = await dispatch_alert(
        client,
        family_id=family["id"],
        alert_id=alert["id"],
        headers=headers,
    )

    assert response.status_code == 200, (
        response.text
    )

    body = response.json()

    assert body["attempted_count"] == 1
    assert body["sent_count"] == 1
    assert body["failed_count"] == 0
    assert body["skipped_count"] == 0

    recipient = body["alert"]["recipients"][0]

    assert recipient["delivery_status"] == "sent"
    assert recipient["sent_at"] is not None
    assert recipient["failure_reason"] is None


async def test_failed_delivery_can_be_retried(
    client: AsyncClient,
) -> None:
    """模拟失败的发送记录可以重新发送。"""

    _, headers = await register_and_login(
        client,
        name="RetryDispatchOwner",
    )

    family = await create_family(
        client,
        headers=headers,
    )

    await create_trusted_contact(
        client,
        family_id=family["id"],
        headers=headers,
    )

    event = await create_risk_event(
        client,
        headers=headers,
        high_risk=True,
    )

    alert = await create_alert(
        client,
        family_id=family["id"],
        event_id=event["event_id"],
        headers=headers,
    )

    recipient_id = alert["recipients"][0]["id"]

    failed_response = await dispatch_alert(
        client,
        family_id=family["id"],
        alert_id=alert["id"],
        headers=headers,
        simulated_failure_recipient_ids=[
            recipient_id,
        ],
    )

    assert failed_response.status_code == 200

    failed_body = failed_response.json()

    assert failed_body["failed_count"] == 1

    failed_recipient = (
        failed_body["alert"]["recipients"][0]
    )

    assert (
        failed_recipient["delivery_status"]
        == "failed"
    )
    assert failed_recipient["sent_at"] is None
    assert failed_recipient["failure_reason"] == (
        "模拟发送失败。"
    )

    retry_response = await dispatch_alert(
        client,
        family_id=family["id"],
        alert_id=alert["id"],
        headers=headers,
    )

    assert retry_response.status_code == 200

    retry_body = retry_response.json()

    assert retry_body["attempted_count"] == 1
    assert retry_body["sent_count"] == 1

    retried_recipient = (
        retry_body["alert"]["recipients"][0]
    )

    assert (
        retried_recipient["delivery_status"]
        == "sent"
    )
    assert retried_recipient["sent_at"] is not None
    assert retried_recipient["failure_reason"] is None


async def test_sent_delivery_is_idempotent(
    client: AsyncClient,
) -> None:
    """已成功发送的接收人不应重复发送。"""

    _, headers = await register_and_login(
        client,
        name="IdempotentDispatchOwner",
    )

    family = await create_family(
        client,
        headers=headers,
    )

    await create_trusted_contact(
        client,
        family_id=family["id"],
        headers=headers,
    )

    event = await create_risk_event(
        client,
        headers=headers,
        high_risk=True,
    )

    alert = await create_alert(
        client,
        family_id=family["id"],
        event_id=event["event_id"],
        headers=headers,
    )

    first_response = await dispatch_alert(
        client,
        family_id=family["id"],
        alert_id=alert["id"],
        headers=headers,
    )

    assert first_response.status_code == 200

    second_response = await dispatch_alert(
        client,
        family_id=family["id"],
        alert_id=alert["id"],
        headers=headers,
    )

    assert second_response.status_code == 200

    second_body = second_response.json()

    assert second_body["attempted_count"] == 0
    assert second_body["sent_count"] == 0
    assert (
        second_body["already_completed_count"]
        == 1
    )


async def test_alert_without_recipients_can_dispatch(
    client: AsyncClient,
) -> None:
    """没有接收人的告警调用发送接口也应成功。"""

    _, headers = await register_and_login(
        client,
        name="EmptyDispatchOwner",
    )

    family = await create_family(
        client,
        headers=headers,
    )

    event = await create_risk_event(
        client,
        headers=headers,
        high_risk=True,
    )

    alert = await create_alert(
        client,
        family_id=family["id"],
        event_id=event["event_id"],
        headers=headers,
    )

    response = await dispatch_alert(
        client,
        family_id=family["id"],
        alert_id=alert["id"],
        headers=headers,
    )

    assert response.status_code == 200

    body = response.json()

    assert body["attempted_count"] == 0
    assert body["sent_count"] == 0
    assert body["failed_count"] == 0
    assert body["alert"]["recipients"] == []


async def test_resolved_alert_cannot_dispatch(
    client: AsyncClient,
) -> None:
    """已经处理完成的告警不能继续发送。"""

    _, headers = await register_and_login(
        client,
        name="ResolvedDispatchOwner",
    )

    family = await create_family(
        client,
        headers=headers,
    )

    event = await create_risk_event(
        client,
        headers=headers,
        high_risk=True,
    )

    alert = await create_alert(
        client,
        family_id=family["id"],
        event_id=event["event_id"],
        headers=headers,
    )

    resolve_response = await client.post(
        (
            f"/api/v1/families/{family['id']}"
            f"/alerts/{alert['id']}/resolve"
        ),
        headers=headers,
        json={
            "resolution_note": "已经完成处理。",
        },
    )

    assert resolve_response.status_code == 200

    response = await dispatch_alert(
        client,
        family_id=family["id"],
        alert_id=alert["id"],
        headers=headers,
    )

    assert response.status_code == 409

    assert response.json()["detail"] == (
        "已经处理完成的家庭告警不能继续发送。"
    )


async def test_outside_user_cannot_dispatch_alert(
    client: AsyncClient,
) -> None:
    """非家庭成员不能发送家庭告警。"""

    _, owner_headers = await register_and_login(
        client,
        name="PrivateDispatchOwner",
    )

    _, outside_headers = await register_and_login(
        client,
        name="OutsideDispatchUser",
    )

    family = await create_family(
        client,
        headers=owner_headers,
    )

    event = await create_risk_event(
        client,
        headers=owner_headers,
        high_risk=True,
    )

    alert = await create_alert(
        client,
        family_id=family["id"],
        event_id=event["event_id"],
        headers=owner_headers,
    )

    response = await dispatch_alert(
        client,
        family_id=family["id"],
        alert_id=alert["id"],
        headers=outside_headers,
    )

    assert response.status_code == 404