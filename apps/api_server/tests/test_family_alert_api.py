from __future__ import annotations

import uuid

import pytest
from httpx import AsyncClient

TEST_PASSWORD = "FamilyAlertTest_123!"

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