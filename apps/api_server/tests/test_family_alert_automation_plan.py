from __future__ import annotations

import uuid
from types import SimpleNamespace

import pytest

import app.services.family_alert_automation_service as service

pytestmark = pytest.mark.asyncio


def build_event(
    *,
    risk_level: str = "high",
    source_type: str = "text",
) -> SimpleNamespace:
    """构造测试风险事件。"""

    return SimpleNamespace(
        id=uuid.uuid4(),
        user_id=uuid.uuid4(),
        risk_level=risk_level,
        source_type=source_type,
    )


def build_policy(
    *,
    auto_create_enabled: bool = True,
    auto_dispatch_enabled: bool = False,
    minimum_risk_level: str = "high",
    enabled_source_types: (
        list[str] | None
    ) = None,
    max_recipients: int = 3,
) -> SimpleNamespace:
    """构造测试告警策略。"""

    return SimpleNamespace(
        auto_create_enabled=(
            auto_create_enabled
        ),
        auto_dispatch_enabled=(
            auto_dispatch_enabled
        ),
        minimum_risk_level=(
            minimum_risk_level
        ),
        enabled_source_types=(
            enabled_source_types
            if enabled_source_types is not None
            else [
                "text",
                "image",
                "url",
            ]
        ),
        max_recipients=max_recipients,
    )


async def test_matching_family_generates_plan(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """符合条件的家庭应生成自动创建计划。"""

    family_id = uuid.uuid4()
    event = build_event()

    async def fake_list_family_ids(
        db,
        *,
        user_id,
    ):
        assert user_id == event.user_id
        return [family_id]

    async def fake_get_policy(
        db,
        *,
        family_id: uuid.UUID,
    ):
        return build_policy()

    monkeypatch.setattr(
        service,
        "list_active_family_ids_for_user",
        fake_list_family_ids,
    )

    monkeypatch.setattr(
        service,
        "get_or_create_family_alert_policy",
        fake_get_policy,
    )

    plan = (
        await service
        .build_family_alert_automation_plan(
            object(),
            event=event,
        )
    )

    assert plan.event_id == event.id
    assert plan.subject_user_id == event.user_id
    assert plan.family_count == 1
    assert plan.matched_count == 1
    assert plan.dispatch_count == 0

    item = plan.items[0]

    assert item.family_id == family_id
    assert item.should_create is True
    assert item.should_dispatch is False
    assert item.reason == "policy_matched"
    assert item.max_recipients == 3


async def test_auto_dispatch_generates_dispatch_plan(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """开启自动发送后应生成发送计划。"""

    family_id = uuid.uuid4()
    event = build_event()

    async def fake_list_family_ids(
        db,
        *,
        user_id,
    ):
        return [family_id]

    async def fake_get_policy(
        db,
        *,
        family_id: uuid.UUID,
    ):
        return build_policy(
            auto_dispatch_enabled=True,
            max_recipients=5,
        )

    monkeypatch.setattr(
        service,
        "list_active_family_ids_for_user",
        fake_list_family_ids,
    )

    monkeypatch.setattr(
        service,
        "get_or_create_family_alert_policy",
        fake_get_policy,
    )

    plan = (
        await service
        .build_family_alert_automation_plan(
            object(),
            event=event,
        )
    )

    assert plan.family_count == 1
    assert plan.matched_count == 1
    assert plan.dispatch_count == 1

    item = plan.items[0]

    assert item.should_create is True
    assert item.should_dispatch is True
    assert item.max_recipients == 5


async def test_disabled_source_is_in_plan_but_not_matched(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """来源关闭时保留决策记录，但不创建告警。"""

    family_id = uuid.uuid4()

    event = build_event(
        source_type="text",
    )

    async def fake_list_family_ids(
        db,
        *,
        user_id,
    ):
        return [family_id]

    async def fake_get_policy(
        db,
        *,
        family_id: uuid.UUID,
    ):
        return build_policy(
            enabled_source_types=[
                "image",
                "url",
            ],
        )

    monkeypatch.setattr(
        service,
        "list_active_family_ids_for_user",
        fake_list_family_ids,
    )

    monkeypatch.setattr(
        service,
        "get_or_create_family_alert_policy",
        fake_get_policy,
    )

    plan = (
        await service
        .build_family_alert_automation_plan(
            object(),
            event=event,
        )
    )

    assert plan.family_count == 1
    assert plan.matched_count == 0
    assert plan.dispatch_count == 0

    item = plan.items[0]

    assert item.should_create is False
    assert item.should_dispatch is False
    assert item.reason == "source_type_disabled"


async def test_user_without_family_has_empty_plan(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """未加入家庭的用户应得到空计划。"""

    event = build_event()

    async def fake_list_family_ids(
        db,
        *,
        user_id,
    ):
        return []

    monkeypatch.setattr(
        service,
        "list_active_family_ids_for_user",
        fake_list_family_ids,
    )

    plan = (
        await service
        .build_family_alert_automation_plan(
            object(),
            event=event,
        )
    )

    assert plan.family_count == 0
    assert plan.matched_count == 0
    assert plan.dispatch_count == 0
    assert plan.items == ()