from __future__ import annotations

import uuid
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest

import app.services.family_alert_automation_service as service

pytestmark = pytest.mark.asyncio


def build_event() -> SimpleNamespace:
    """构造测试风险事件。"""

    return SimpleNamespace(
        id=uuid.uuid4(),
        user_id=uuid.uuid4(),
        source_type="text",
        risk_level="high",
    )


async def test_safe_trigger_returns_execution_result(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """自动化正常执行时应返回执行结果。"""

    event = build_event()
    expected_result = SimpleNamespace(
        event_id=event.id,
        created_count=1,
    )

    async def fake_execute(
        db,
        *,
        event,
    ):
        return expected_result

    monkeypatch.setattr(
        service,
        "execute_family_alert_automation_plan",
        fake_execute,
    )

    db = SimpleNamespace(
        rollback=AsyncMock(),
    )

    result = await (
        service
        .trigger_family_alert_automation_safely(
            db,
            event=event,
        )
    )

    assert result is expected_result
    db.rollback.assert_not_awaited()


async def test_safe_trigger_rolls_back_and_swallows_error(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """自动化失败不应影响风险分析主流程。"""

    event = build_event()

    async def fake_execute(
        db,
        *,
        event,
    ):
        raise RuntimeError(
            "模拟家庭告警自动化失败"
        )

    monkeypatch.setattr(
        service,
        "execute_family_alert_automation_plan",
        fake_execute,
    )

    db = SimpleNamespace(
        rollback=AsyncMock(),
    )

    result = await (
        service
        .trigger_family_alert_automation_safely(
            db,
            event=event,
        )
    )

    assert result is None
    db.rollback.assert_awaited_once()