from __future__ import annotations

import uuid

import pytest
from httpx import AsyncClient

TEST_PASSWORD = "RiskFeedbackTest_123!"

pytestmark = pytest.mark.asyncio


async def register_and_login(
    client: AsyncClient,
    *,
    name: str,
) -> tuple[str, dict[str, str]]:
    """注册并登录一个随机测试用户。"""

    random_part = uuid.uuid4().hex
    email = f"{name.lower()}-{random_part}@example.com"

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

    access_token = login_response.json()[
        "access_token"
    ]

    headers = {
        "Authorization": f"Bearer {access_token}",
    }

    return email, headers

async def create_risk_event(
    client: AsyncClient,
    *,
    headers: dict[str, str],
    text: str = "请立即给对方转账。",
) -> str:
    """调用文本分析接口并返回风险事件 ID。"""

    response = await client.post(
        "/api/v1/risk/text/analyze",
        headers=headers,
        json={
            "text": text,
        },
    )

    assert response.status_code == 200, response.text

    event_id = response.json()["event_id"]

    assert event_id

    return event_id


async def test_user_can_submit_feedback_for_own_event(
    client: AsyncClient,
) -> None:
    """用户可以给自己的风险事件提交反馈。"""

    _, headers = await register_and_login(
        client,
        name="FeedbackOwner",
    )

    event_id = await create_risk_event(
        client,
        headers=headers,
    )

    response = await client.put(
        f"/api/v1/risk/events/{event_id}/feedback",
        headers=headers,
        json={
            "feedback_type": "false_positive",
            "comment": "这是一条正常的转账提醒。",
        },
    )

    assert response.status_code == 200, response.text

    body = response.json()

    assert body["event_id"] == event_id
    assert body["feedback_type"] == "false_positive"
    assert body["expected_risk_level"] == "low"
    assert body["comment"] == "这是一条正常的转账提醒。"

    assert body["id"]
    assert body["user_id"]
    assert body["created_at"]
    assert body["updated_at"]


async def test_user_can_read_own_feedback(
    client: AsyncClient,
) -> None:
    """用户可以读取自己提交的风险反馈。"""

    _, headers = await register_and_login(
        client,
        name="FeedbackReader",
    )

    event_id = await create_risk_event(
        client,
        headers=headers,
    )

    create_response = await client.put(
        f"/api/v1/risk/events/{event_id}/feedback",
        headers=headers,
        json={
            "feedback_type": "accurate",
            "comment": "系统判断准确。",
        },
    )

    assert create_response.status_code == 200, (
        create_response.text
    )

    created_feedback = create_response.json()

    read_response = await client.get(
        f"/api/v1/risk/events/{event_id}/feedback",
        headers=headers,
    )

    assert read_response.status_code == 200, (
        read_response.text
    )

    read_feedback = read_response.json()

    assert read_feedback["id"] == created_feedback["id"]
    assert read_feedback["event_id"] == event_id
    assert read_feedback["feedback_type"] == "accurate"
    assert read_feedback["expected_risk_level"] is None
    assert read_feedback["comment"] == "系统判断准确。"


async def test_second_submission_updates_existing_feedback(
    client: AsyncClient,
) -> None:
    """
    同一个用户再次提交反馈时更新原记录，
    不应创建新的反馈记录。
    """

    _, headers = await register_and_login(
        client,
        name="FeedbackUpdater",
    )

    event_id = await create_risk_event(
        client,
        headers=headers,
    )

    first_response = await client.put(
        f"/api/v1/risk/events/{event_id}/feedback",
        headers=headers,
        json={
            "feedback_type": "accurate",
            "comment": "最初认为结果准确。",
        },
    )

    assert first_response.status_code == 200, (
        first_response.text
    )

    first_body = first_response.json()

    second_response = await client.put(
        f"/api/v1/risk/events/{event_id}/feedback",
        headers=headers,
        json={
            "feedback_type": "risk_too_high",
            "expected_risk_level": "low",
            "comment": "重新核实后认为风险等级过高。",
        },
    )

    assert second_response.status_code == 200, (
        second_response.text
    )

    second_body = second_response.json()

    # ID 不变，说明更新了原记录，而不是创建新记录。
    assert second_body["id"] == first_body["id"]

    assert second_body["event_id"] == event_id
    assert second_body["feedback_type"] == (
        "risk_too_high"
    )
    assert second_body["expected_risk_level"] == "low"
    assert second_body["comment"] == (
        "重新核实后认为风险等级过高。"
    )

    read_response = await client.get(
        f"/api/v1/risk/events/{event_id}/feedback",
        headers=headers,
    )

    assert read_response.status_code == 200

    read_body = read_response.json()

    assert read_body["id"] == first_body["id"]
    assert read_body["feedback_type"] == (
        "risk_too_high"
    )


async def test_user_cannot_submit_feedback_for_another_users_event(
    client: AsyncClient,
) -> None:
    """用户不能给其他用户的风险事件提交反馈。"""

    _, owner_headers = await register_and_login(
        client,
        name="EventOwner",
    )

    _, other_headers = await register_and_login(
        client,
        name="OtherFeedbackUser",
    )

    event_id = await create_risk_event(
        client,
        headers=owner_headers,
    )

    response = await client.put(
        f"/api/v1/risk/events/{event_id}/feedback",
        headers=other_headers,
        json={
            "feedback_type": "accurate",
        },
    )

    assert response.status_code == 404

    assert response.json()["detail"] == (
        "风险事件不存在或无权访问。"
    )


async def test_user_cannot_read_another_users_feedback(
    client: AsyncClient,
) -> None:
    """用户不能读取其他用户的风险反馈。"""

    _, owner_headers = await register_and_login(
        client,
        name="FeedbackEventOwner",
    )

    _, other_headers = await register_and_login(
        client,
        name="UnauthorizedReader",
    )

    event_id = await create_risk_event(
        client,
        headers=owner_headers,
    )

    create_response = await client.put(
        f"/api/v1/risk/events/{event_id}/feedback",
        headers=owner_headers,
        json={
            "feedback_type": "accurate",
            "comment": "仅事件所有者可查看。",
        },
    )

    assert create_response.status_code == 200

    read_response = await client.get(
        f"/api/v1/risk/events/{event_id}/feedback",
        headers=other_headers,
    )

    assert read_response.status_code == 404

    assert read_response.json()["detail"] == (
        "风险事件不存在或无权访问。"
    )


async def test_reading_missing_feedback_returns_404(
    client: AsyncClient,
) -> None:
    """风险事件存在但尚未提交反馈时返回 404。"""

    _, headers = await register_and_login(
        client,
        name="NoFeedbackUser",
    )

    event_id = await create_risk_event(
        client,
        headers=headers,
    )

    response = await client.get(
        f"/api/v1/risk/events/{event_id}/feedback",
        headers=headers,
    )

    assert response.status_code == 404

    assert response.json()["detail"] == (
        "当前风险事件尚未提交反馈。"
    )