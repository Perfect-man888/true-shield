from __future__ import annotations

import uuid
from types import SimpleNamespace

import pytest

import app.services.family_alert_automation_service as service

pytestmark = pytest.mark.asyncio


def build_event() -> SimpleNamespace:
    """构造测试风险事件。"""

    return SimpleNamespace(
        id=uuid.uuid4(),
        user_id=uuid.uuid4(),
        risk_level="high",
        source_type="text",
        summary="检测到高风险诈骗话术。",
    )


def build_plan(
    *,
    event: SimpleNamespace,
    family_id: uuid.UUID,
    should_create: bool = True,
    should_dispatch: bool = False,
    max_recipients: int = 3,
    reason: str = "policy_matched",
) -> service.FamilyAlertAutomationPlan:
    """构造自动化计划。"""

    return service.FamilyAlertAutomationPlan(
        event_id=event.id,
        subject_user_id=event.user_id,
        items=(
            service
            .FamilyAlertAutomationPlanItem(
                family_id=family_id,
                should_create=should_create,
                should_dispatch=(
                    should_dispatch
                ),
                reason=reason,
                max_recipients=max_recipients,
            ),
        ),
    )


def build_recipient_payloads(
    contacts: list[SimpleNamespace],
) -> list[dict[str, object]]:
    """构造接收人数据。"""

    return [
        {
            "trusted_contact_id": contact.id,
            "linked_user_id": None,
            "recipient_name": (
                f"联系人-{index}"
            ),
            "channel": "sms",
            "destination": (
                f"1380000000{index}"
            ),
        }
        for index, contact in enumerate(
            contacts
        )
    ]


async def test_execution_creates_alert_with_limit(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """自动创建时应遵守最大接收人数。"""

    event = build_event()
    family_id = uuid.uuid4()
    alert_id = uuid.uuid4()

    contacts = [
        SimpleNamespace(id=uuid.uuid4())
        for _ in range(4)
    ]

    async def fake_get_existing(
        db,
        *,
        family_id,
        risk_event_id,
    ):
        return None

    async def fake_list_contacts(
        db,
        *,
        family_id,
        subject_user_id,
    ):
        return contacts

    async def fake_create_alert(
        db,
        **kwargs,
    ):
        assert len(
            kwargs["recipients"]
        ) == 2

        return SimpleNamespace(
            id=alert_id,
            recipients=[
                SimpleNamespace(
                    id=uuid.uuid4(),
                    delivery_status="pending",
                )
                for _ in kwargs["recipients"]
            ],
        )

    monkeypatch.setattr(
        service,
        "get_existing_family_alert",
        fake_get_existing,
    )
    monkeypatch.setattr(
        service,
        "list_eligible_trusted_contacts",
        fake_list_contacts,
    )
    monkeypatch.setattr(
        service,
        "build_alert_recipients",
        build_recipient_payloads,
    )
    monkeypatch.setattr(
        service,
        "create_family_alert",
        fake_create_alert,
    )
    monkeypatch.setattr(
        service,
        "build_family_alert_title",
        lambda event: "自动风险告警",
    )
    monkeypatch.setattr(
        service,
        "build_family_alert_summary",
        lambda event: event.summary,
    )

    result = await (
        service
        .execute_family_alert_automation_plan(
            object(),
            event=event,
            plan=build_plan(
                event=event,
                family_id=family_id,
                max_recipients=2,
            ),
        )
    )

    assert result.created_count == 1
    assert result.dispatched_count == 0
    assert result.skipped_count == 0

    item = result.items[0]

    assert item.alert_id == alert_id
    assert item.created is True
    assert item.dispatched is False
    assert item.recipient_count == 2
    assert item.reason == "created"


async def test_execution_dispatches_alert(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """开启自动发送时应创建并发送告警。"""

    event = build_event()
    family_id = uuid.uuid4()
    alert_id = uuid.uuid4()

    contacts = [
        SimpleNamespace(id=uuid.uuid4())
    ]

    async def fake_get_existing(
        db,
        *,
        family_id,
        risk_event_id,
    ):
        return None

    async def fake_record_delivery_attempts(
        db,
        *,
        recipients,
        attempted_recipient_ids,
    ):
        assert attempted_recipient_ids == {
            recipient.id
            for recipient in recipients
        }

        return []
    async def fake_list_contacts(
        db,
        *,
        family_id,
        subject_user_id,
    ):
        return contacts

    async def fake_create_alert(
        db,
        **kwargs,
    ):
        return SimpleNamespace(
            id=alert_id,
            recipients=[
                SimpleNamespace(
                    id=uuid.uuid4(),
                    delivery_status="pending",
                )
            ],
        )

    delivery_called = False

    async def fake_deliver_notifications(
        recipients,
        *,
        title,
        message,
        simulated_failure_recipient_ids,
    ):
        nonlocal delivery_called

        delivery_called = True

        assert title == "自动风险告警"
        assert message == event.summary

        for recipient in recipients:
            recipient.delivery_status = "sent"

        return SimpleNamespace(
            attempted_count=len(recipients),
            sent_count=len(recipients),
            failed_count=0,
            skipped_count=0,
            already_completed_count=0,
            attempted_recipient_ids=[
                recipient.id
                for recipient in recipients
            ],
        )
    async def fake_save_delivery(
        db,
        *,
        alert,
    ):
        return alert

    monkeypatch.setattr(
        service,
        "get_existing_family_alert",
        fake_get_existing,
    )
    monkeypatch.setattr(
        service,
        "list_eligible_trusted_contacts",
        fake_list_contacts,
    )
    monkeypatch.setattr(
        service,
        "build_alert_recipients",
        build_recipient_payloads,
    )
    monkeypatch.setattr(
        service,
        "create_family_alert",
        fake_create_alert,
    )
    monkeypatch.setattr(
        service,
        "deliver_family_alert_notifications",
        fake_deliver_notifications,
    )
    monkeypatch.setattr(
        service,
        "save_family_alert_delivery_state",
        fake_save_delivery,
    )
    monkeypatch.setattr(
        service,
        "build_family_alert_title",
        lambda event: "自动风险告警",
    )
    monkeypatch.setattr(
        service,
        "build_family_alert_summary",
        lambda event: event.summary,
    )
    monkeypatch.setattr(
    service,
    "record_family_alert_delivery_attempts",
    fake_record_delivery_attempts,
    )

    result = await (
        service
        .execute_family_alert_automation_plan(
            object(),
            event=event,
            plan=build_plan(
                event=event,
                family_id=family_id,
                should_dispatch=True,
            ),
        )
    )

    assert delivery_called is True
    assert result.created_count == 1
    assert result.dispatched_count == 1

    item = result.items[0]

    assert item.created is True
    assert item.dispatched is True
    assert (
        item.reason
        == "created_and_dispatched"
    )


async def test_existing_alert_is_not_duplicated(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """已经存在的告警不应重复创建。"""

    event = build_event()
    family_id = uuid.uuid4()
    existing_alert_id = uuid.uuid4()

    async def fake_get_existing(
        db,
        *,
        family_id,
        risk_event_id,
    ):
        return SimpleNamespace(
            id=existing_alert_id,
            recipients=[
                SimpleNamespace(
                    id=uuid.uuid4()
                )
            ],
        )

    monkeypatch.setattr(
        service,
        "get_existing_family_alert",
        fake_get_existing,
    )

    result = await (
        service
        .execute_family_alert_automation_plan(
            object(),
            event=event,
            plan=build_plan(
                event=event,
                family_id=family_id,
            ),
        )
    )

    assert result.created_count == 0
    assert result.dispatched_count == 0
    assert result.skipped_count == 1

    item = result.items[0]

    assert item.alert_id == existing_alert_id
    assert item.created is False
    assert item.reason == "already_exists"


async def test_no_contacts_skips_creation(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """没有可用联系人时不创建空告警。"""

    event = build_event()
    family_id = uuid.uuid4()

    async def fake_get_existing(
        db,
        *,
        family_id,
        risk_event_id,
    ):
        return None

    async def fake_list_contacts(
        db,
        *,
        family_id,
        subject_user_id,
    ):
        return []

    monkeypatch.setattr(
        service,
        "get_existing_family_alert",
        fake_get_existing,
    )
    monkeypatch.setattr(
        service,
        "list_eligible_trusted_contacts",
        fake_list_contacts,
    )

    result = await (
        service
        .execute_family_alert_automation_plan(
            object(),
            event=event,
            plan=build_plan(
                event=event,
                family_id=family_id,
            ),
        )
    )

    assert result.created_count == 0
    assert result.skipped_count == 1

    item = result.items[0]

    assert item.alert_id is None
    assert item.created is False
    assert (
        item.reason
        == "no_eligible_recipients"
    )