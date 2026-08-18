from __future__ import annotations

import uuid

import pytest
from httpx import AsyncClient

import app.api.routes.risk as risk_routes

TEST_PASSWORD = "RiskReportTest_123!"

pytestmark = pytest.mark.asyncio


@pytest.fixture(autouse=True)
def disable_automatic_family_alert_creation(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    async def no_op_trigger(db, *, event):
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
    response = await client.post(
        "/api/v1/families",
        headers=headers,
        json={"name": name},
    )
    assert response.status_code == 201, response.text
    return response.json()


async def test_personal_report_summary_contains_desensitized_statistics(
    db_client: AsyncClient,
) -> None:
    headers = await register_and_login(
        db_client,
        name="RiskReportOwner",
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
            "我是公安局工作人员，你涉嫌洗钱，"
            "请将 13900139000 账户资金立即转入安全账户。"
        ),
    )

    response = await db_client.get(
        "/api/v1/risk/reports/summary",
        headers=headers,
        params={"period_days": 30},
    )
    assert response.status_code == 200, response.text
    body = response.json()

    assert body["scope"] == "personal"
    assert body["period_days"] == 30
    assert body["overview"]["total_events"] == 2
    assert body["risk_levels"]["high"] == 1
    assert body["risk_levels"]["low"] == 1
    assert body["source_types"]["text"] == 2
    assert body["report_code"].startswith("TS-")
    assert len(body["daily_trend"]) == 30
    assert body["recent_events"]

    serialized = response.text
    assert "13900139000" not in serialized
    assert "139****9000" in serialized


async def test_family_report_includes_alert_status(
    db_client: AsyncClient,
) -> None:
    headers = await register_and_login(
        db_client,
        name="FamilyRiskReportOwner",
    )
    family = await create_family(
        db_client,
        headers=headers,
        name="家庭报告测试",
    )
    event = await create_text_event(
        db_client,
        headers=headers,
        text=(
            "我是公安局工作人员，你涉嫌洗钱，"
            "必须马上转入安全账户。"
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
        "/api/v1/risk/reports/summary",
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
    assert body["family_name"] == "家庭报告测试"
    assert body["member_count"] == 1
    assert body["overview"]["alert_count"] == 1
    assert body["alert_status"]["pending"] == 1
    assert len(body["recent_alerts"]) == 1


async def test_family_report_rejects_non_member(
    db_client: AsyncClient,
) -> None:
    owner_headers = await register_and_login(
        db_client,
        name="ReportFamilyOwner",
    )
    outsider_headers = await register_and_login(
        db_client,
        name="ReportFamilyOutsider",
    )
    family = await create_family(
        db_client,
        headers=owner_headers,
        name="禁止访问家庭报告",
    )

    response = await db_client.get(
        "/api/v1/risk/reports/summary",
        headers=outsider_headers,
        params={"family_id": family["id"]},
    )

    assert response.status_code == 404
    assert response.json()["detail"] == "家庭不存在或无权生成该家庭报告。"


async def test_pdf_report_returns_downloadable_pdf(
    db_client: AsyncClient,
) -> None:
    headers = await register_and_login(
        db_client,
        name="PdfRiskReportOwner",
    )
    await create_text_event(
        db_client,
        headers=headers,
        text=(
            "客服通知退款，请下载远程控制软件并共享验证码。"
        ),
    )

    response = await db_client.get(
        "/api/v1/risk/reports/pdf",
        headers=headers,
        params={"period_days": 7},
    )

    assert response.status_code == 200, response.text
    assert response.headers["content-type"].startswith("application/pdf")
    assert "attachment" in response.headers["content-disposition"]
    assert response.headers["cache-control"] == "no-store"
    assert response.content.startswith(b"%PDF-")
    assert len(response.content) > 2_000
