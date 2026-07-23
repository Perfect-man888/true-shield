from __future__ import annotations

import uuid

import pytest
from httpx import AsyncClient

TEST_PASSWORD = "RiskTestPassword_123!"

# 本文件中的异步测试统一由 pytest-asyncio 执行。
pytestmark = pytest.mark.asyncio


async def register_and_login(
    client: AsyncClient,
    *,
    name: str,
) -> tuple[str, dict[str, str]]:
    """
    注册一个随机测试用户并登录。

    返回：
    1. 用户邮箱
    2. 带 Bearer Token 的请求头
    """

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
        headers={
            "Content-Type": "application/x-www-form-urlencoded",
        },
    )

    assert login_response.status_code == 200, (
        login_response.text
    )

    token_data = login_response.json()

    assert "access_token" in token_data
    assert token_data["token_type"].lower() == "bearer"

    headers = {
        "Authorization": (
            f"Bearer {token_data['access_token']}"
        ),
    }

    return email, headers


async def create_risk_event(
    client: AsyncClient,
    *,
    headers: dict[str, str],
    text: str,
) -> dict:
    """创建并保存一条文本风险分析事件。"""

    response = await client.post(
        "/api/v1/risk/text/analyze",
        json={
            "text": text,
        },
        headers=headers,
    )

    assert response.status_code == 200, response.text

    result = response.json()

    assert "event_id" in result
    assert "created_at" in result

    return result


async def test_risk_history_requires_authentication(
    db_client: AsyncClient,
) -> None:
    """未登录用户不能查询风险历史。"""

    response = await db_client.get(
        "/api/v1/risk/events",
    )

    assert response.status_code in {
        401,
        403,
    }


async def test_risk_history_pagination(
    db_client: AsyncClient,
) -> None:
    """limit 和 offset 应正确控制分页结果。"""

    _, headers = await register_and_login(
        db_client,
        name="PaginationUser",
    )

    first_event = await create_risk_event(
        db_client,
        headers=headers,
        text="第一条普通聊天消息。",
    )

    second_event = await create_risk_event(
        db_client,
        headers=headers,
        text="第二条普通聊天消息。",
    )

    third_event = await create_risk_event(
        db_client,
        headers=headers,
        text="第三条普通聊天消息。",
    )

    first_page_response = await db_client.get(
        "/api/v1/risk/events",
        params={
            "limit": 1,
            "offset": 0,
        },
        headers=headers,
    )

    assert first_page_response.status_code == 200

    first_page = first_page_response.json()

    assert first_page["total"] == 3
    assert first_page["limit"] == 1
    assert first_page["offset"] == 0
    assert len(first_page["items"]) == 1

    second_page_response = await db_client.get(
        "/api/v1/risk/events",
        params={
            "limit": 1,
            "offset": 1,
        },
        headers=headers,
    )

    assert second_page_response.status_code == 200

    second_page = second_page_response.json()

    assert second_page["total"] == 3
    assert second_page["limit"] == 1
    assert second_page["offset"] == 1
    assert len(second_page["items"]) == 1

    first_page_event_id = (
        first_page["items"][0]["event_id"]
    )

    second_page_event_id = (
        second_page["items"][0]["event_id"]
    )

    assert first_page_event_id != second_page_event_id

    created_event_ids = {
        first_event["event_id"],
        second_event["event_id"],
        third_event["event_id"],
    }

    assert first_page_event_id in created_event_ids
    assert second_page_event_id in created_event_ids


async def test_user_can_read_own_risk_event(
    db_client: AsyncClient,
) -> None:
    """用户可以查看自己创建的风险事件。"""

    _, headers = await register_and_login(
        db_client,
        name="OwnerUser",
    )

    source_text = (
        "我是你儿子，手机坏了，"
        "马上给我转账，别告诉家里人。"
    )

    created_event = await create_risk_event(
        db_client,
        headers=headers,
        text=source_text,
    )

    event_id = created_event["event_id"]

    detail_response = await db_client.get(
        f"/api/v1/risk/events/{event_id}",
        headers=headers,
    )

    assert detail_response.status_code == 200

    detail = detail_response.json()

    assert detail["event_id"] == event_id
    assert detail["source_text"] == source_text
    assert detail["risk_level"] == "high"
    assert detail["score"] >= 60
    assert len(detail["evidence"]) >= 2
    assert len(detail["actions"]) >= 3


async def test_user_cannot_read_another_users_event(
    db_client: AsyncClient,
) -> None:
    """另一个用户不能查看不属于自己的事件。"""

    _, owner_headers = await register_and_login(
        db_client,
        name="EventOwner",
    )

    _, other_headers = await register_and_login(
        db_client,
        name="OtherUser",
    )

    owner_event = await create_risk_event(
        db_client,
        headers=owner_headers,
        text="马上转账，并且不要告诉家里人。",
    )

    event_id = owner_event["event_id"]

    other_detail_response = await db_client.get(
        f"/api/v1/risk/events/{event_id}",
        headers=other_headers,
    )

    # 越权资源统一返回 404，
    # 避免泄露该事件是否真实存在。
    assert other_detail_response.status_code == 404

    assert other_detail_response.json() == {
        "detail": "Risk event not found",
    }

    other_list_response = await db_client.get(
        "/api/v1/risk/events",
        headers=other_headers,
    )

    assert other_list_response.status_code == 200

    other_list = other_list_response.json()

    other_event_ids = {
        item["event_id"]
        for item in other_list["items"]
    }

    assert event_id not in other_event_ids


async def test_nonexistent_risk_event_returns_404(
    db_client: AsyncClient,
) -> None:
    """查询不存在的风险事件应返回 404。"""

    _, headers = await register_and_login(
        db_client,
        name="NotFoundUser",
    )

    nonexistent_event_id = uuid.uuid4()

    response = await db_client.get(
        f"/api/v1/risk/events/{nonexistent_event_id}",
        headers=headers,
    )

    assert response.status_code == 404

    assert response.json() == {
        "detail": "Risk event not found",
    }