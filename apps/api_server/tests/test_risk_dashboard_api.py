from __future__ import annotations

import uuid

import pytest
from httpx import AsyncClient

import app.api.routes.risk as risk_routes

TEST_PASSWORD = "RiskDashboardTest_123!"

pytestmark = pytest.mark.asyncio


@pytest.fixture(autouse=True)
def disable_automatic_family_alert_creation(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """仪表盘测试使用手动告警，避免自动策略干扰。"""

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
) -> dict[str, str]:
    """注册并登录随机测试用户。"""

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
    assert register_response.status_code in {200, 201}, register_response.text

    login_response = await client.post(
        "/api/v1/auth/login",
        data={
            "username": email,
            "password": TEST_PASSWORD,
        },
    )
    assert login_response.status_code == 200, login_response.text

    return {
        "Authorization": f"Bearer {login_response.json()['access_token']}",
    }


async def create_text_event(
    client: AsyncClient,
    *,
    headers: dict[str, str],
    text: str,
) -> dict:
    """创建一条文本风险事件。"""

    response = await client.post(
        "/api/v1/risk/text/analyze",
        headers=headers,
        json={"text": text},
    )
    assert response.status_code == 200, response.text
    return response.json()


async def create_family(
    client: AsyncClient,
    *,
    headers: dict[str, str],
    name: str,
) -> dict:
    """创建家庭并返回响应。"""

    response = await client.post(
        "/api/v1/families",
        headers=headers,
        json={"name": name},
    )
    assert response.status_code == 201, response.text
    return response.json()


async def test_personal_dashboard_aggregates_risk_events(
    db_client: AsyncClient,
) -> None:
    """个人仪表盘应汇总风险等级、来源与近期趋势。"""

    headers = await register_and_login(
        db_client,
        name="PersonalDashboardOwner",
    )

    await create_text_event(
        db_client,
        headers=headers,
        text="今天下午一起吃饭。",
    )
    await create_text_event(
        db_client,
        headers=headers,
        text=(
            "我是公安局的，你涉嫌洗钱，不能告诉家人，"
            "必须马上把钱转到安全账户。"
        ),
    )

    response = await db_client.get(
        "/api/v1/risk/dashboard",
        headers=headers,
        params={"period_days": 7},
    )
    assert response.status_code == 200, response.text

    body = response.json()

    assert body["scope"] == "personal"
    assert body["family_id"] is None
    assert body["member_count"] == 1
    assert body["overview"]["total_events"] == 2
    assert body["overview"]["period_events"] == 2
    assert body["risk_levels"]["high"] == 1
    assert body["risk_levels"]["low"] == 1
    assert body["source_types"]["text"] == 2
    assert len(body["daily_trend"]) == 7
    assert sum(point["total"] for point in body["daily_trend"]) == 2


async def test_family_dashboard_includes_family_alert_status(
    db_client: AsyncClient,
) -> None:
    """家庭仪表盘应汇总成员风险事件与家庭告警。"""

    headers = await register_and_login(
        db_client,
        name="FamilyDashboardOwner",
    )
    family = await create_family(
        db_client,
        headers=headers,
        name="家庭风险统计测试",
    )
    event = await create_text_event(
        db_client,
        headers=headers,
        text=(
            "我是公安局的，你涉嫌洗钱，不能告诉家人，"
            "必须马上把钱转到安全账户。"
        ),
    )

    alert_response = await db_client.post(
        (
            f"/api/v1/families/{family['id']}"
            f"/alerts/from-events/{event['event_id']}"
        ),
        headers=headers,
    )
    assert alert_response.status_code == 201, alert_response.text

    response = await db_client.get(
        "/api/v1/risk/dashboard",
        headers=headers,
        params={
            "period_days": 30,
            "family_id": family["id"],
        },
    )
    assert response.status_code == 200, response.text

    body = response.json()

    assert body["scope"] == "family"
    assert body["family_id"] == family["id"]
    assert body["family_name"] == "家庭风险统计测试"
    assert body["member_count"] == 1
    assert body["overview"]["total_events"] == 1
    assert body["risk_levels"]["high"] == 1
    assert body["alert_status"]["total"] == 1
    assert body["alert_status"]["pending"] == 1
    assert body["alert_status"]["resolution_rate"] == 0.0


async def test_family_dashboard_rejects_non_member(
    db_client: AsyncClient,
) -> None:
    """非家庭成员不能读取该家庭统计。"""

    owner_headers = await register_and_login(
        db_client,
        name="DashboardFamilyOwner",
    )
    outsider_headers = await register_and_login(
        db_client,
        name="DashboardOutsider",
    )
    family = await create_family(
        db_client,
        headers=owner_headers,
        name="不可访问家庭",
    )

    response = await db_client.get(
        "/api/v1/risk/dashboard",
        headers=outsider_headers,
        params={"family_id": family["id"]},
    )

    assert response.status_code == 404
    assert response.json()["detail"] == "家庭不存在或无权查看该家庭统计。"


async def test_empty_personal_dashboard_returns_zero_values(
    db_client: AsyncClient,
) -> None:
    """没有风险数据时仍返回完整的零值结构。"""

    headers = await register_and_login(
        db_client,
        name="EmptyDashboardOwner",
    )

    response = await db_client.get(
        "/api/v1/risk/dashboard",
        headers=headers,
        params={"period_days": 7},
    )
    assert response.status_code == 200, response.text

    body = response.json()

    assert body["overview"]["total_events"] == 0
    assert body["overview"]["average_score"] == 0.0
    assert body["risk_levels"] == {
        "low": 0,
        "medium": 0,
        "high": 0,
    }
    assert body["source_types"] == {
        "text": 0,
        "image": 0,
        "url": 0,
        "voice": 0,
        "call": 0,
        "other": 0,
    }
    assert body["alert_status"]["total"] == 0
    assert len(body["daily_trend"]) == 7
