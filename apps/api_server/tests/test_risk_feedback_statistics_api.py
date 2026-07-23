from __future__ import annotations

import uuid

import pytest
from httpx import AsyncClient

TEST_PASSWORD = "RiskStatisticsTest_123!"

pytestmark = pytest.mark.asyncio


async def register_and_login(
    client: AsyncClient,
    *,
    name: str,
) -> dict[str, str]:
    """注册并登录随机测试用户。"""

    random_part = uuid.uuid4().hex

    email = (
        f"{name.lower()}-{random_part}@example.com"
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

    access_token = login_response.json()[
        "access_token"
    ]

    return {
        "Authorization": f"Bearer {access_token}",
    }


async def create_risk_event(
    client: AsyncClient,
    *,
    headers: dict[str, str],
    text: str,
) -> str:
    """创建风险分析事件并返回事件 ID。"""

    response = await client.post(
        "/api/v1/risk/text/analyze",
        headers=headers,
        json={
            "text": text,
        },
    )

    assert response.status_code == 200, response.text

    return response.json()["event_id"]


async def submit_feedback(
    client: AsyncClient,
    *,
    headers: dict[str, str],
    event_id: str,
    feedback_type: str,
    expected_risk_level: str | None = None,
) -> None:
    """为风险事件提交反馈。"""

    payload: dict[str, str] = {
        "feedback_type": feedback_type,
    }

    if expected_risk_level is not None:
        payload["expected_risk_level"] = (
            expected_risk_level
        )

    response = await client.put(
        f"/api/v1/risk/events/{event_id}/feedback",
        headers=headers,
        json=payload,
    )

    assert response.status_code == 200, response.text


async def test_user_can_read_own_feedback_statistics(
    client: AsyncClient,
) -> None:
    """用户可以读取自己的风险反馈统计。"""

    headers = await register_and_login(
        client,
        name="StatisticsOwner",
    )

    accurate_event_id = await create_risk_event(
        client,
        headers=headers,
        text="请谨慎核实对方身份。",
    )

    false_positive_event_id = (
        await create_risk_event(
            client,
            headers=headers,
            text="请立即给对方转账。",
        )
    )

    await submit_feedback(
        client,
        headers=headers,
        event_id=accurate_event_id,
        feedback_type="accurate",
    )

    await submit_feedback(
        client,
        headers=headers,
        event_id=false_positive_event_id,
        feedback_type="false_positive",
    )

    response = await client.get(
        "/api/v1/risk/feedback/statistics",
        headers=headers,
    )

    assert response.status_code == 200, response.text

    body = response.json()

    assert body["total_feedbacks"] == 2

    assert body["type_counts"]["accurate"] == 1

    assert (
        body["type_counts"]["false_positive"]
        == 1
    )

    assert (
        body["type_counts"]["false_negative"]
        == 0
    )

    assert (
        body["expected_risk_level_counts"]["low"]
        == 1
    )

    assert body["accurate_rate"] == 0.5
    assert body["correction_rate"] == 0.5


async def test_statistics_are_isolated_by_user(
    client: AsyncClient,
) -> None:
    """每个用户只能统计自己的反馈。"""

    first_headers = await register_and_login(
        client,
        name="FirstStatisticsUser",
    )

    second_headers = await register_and_login(
        client,
        name="SecondStatisticsUser",
    )

    first_event_id = await create_risk_event(
        client,
        headers=first_headers,
        text="请谨慎核实信息。",
    )

    second_event_id = await create_risk_event(
        client,
        headers=second_headers,
        text="这是一条没有识别出的高风险信息。",
    )

    await submit_feedback(
        client,
        headers=first_headers,
        event_id=first_event_id,
        feedback_type="accurate",
    )

    await submit_feedback(
        client,
        headers=second_headers,
        event_id=second_event_id,
        feedback_type="false_negative",
        expected_risk_level="high",
    )

    first_response = await client.get(
        "/api/v1/risk/feedback/statistics",
        headers=first_headers,
    )

    second_response = await client.get(
        "/api/v1/risk/feedback/statistics",
        headers=second_headers,
    )

    assert first_response.status_code == 200
    assert second_response.status_code == 200

    first_body = first_response.json()
    second_body = second_response.json()

    assert first_body["total_feedbacks"] == 1

    assert (
        first_body["type_counts"]["accurate"]
        == 1
    )

    assert (
        first_body["type_counts"][
            "false_negative"
        ]
        == 0
    )

    assert second_body["total_feedbacks"] == 1

    assert (
        second_body["type_counts"]["accurate"]
        == 0
    )

    assert (
        second_body["type_counts"][
            "false_negative"
        ]
        == 1
    )


async def test_user_without_feedback_gets_zero_statistics(
    client: AsyncClient,
) -> None:
    """没有提交反馈的用户应得到全零统计。"""

    headers = await register_and_login(
        client,
        name="EmptyStatisticsUser",
    )

    response = await client.get(
        "/api/v1/risk/feedback/statistics",
        headers=headers,
    )

    assert response.status_code == 200, response.text

    body = response.json()

    assert body["total_feedbacks"] == 0

    assert body["type_counts"] == {
        "accurate": 0,
        "false_positive": 0,
        "false_negative": 0,
        "risk_too_high": 0,
        "risk_too_low": 0,
    }

    assert body["expected_risk_level_counts"] == {
        "low": 0,
        "medium": 0,
        "high": 0,
    }

    assert body["accurate_rate"] == 0.0
    assert body["correction_rate"] == 0.0