from __future__ import annotations

import uuid
from types import SimpleNamespace

import pytest

from app.services.family_alert_service import (
    deliver_family_alert_notifications,
)

pytestmark = pytest.mark.asyncio


def build_recipient(
    *,
    channel: str = "sms",
    destination: str | None = "13800138000",
    delivery_status: str = "pending",
) -> SimpleNamespace:
    return SimpleNamespace(
    id=uuid.uuid4(),
    channel=channel,
    destination=destination,
    delivery_status=delivery_status,
    delivery_provider=None,
    external_message_id=None,
    failure_reason=None,
    sent_at=None,
    )


async def test_delivery_marks_recipient_sent() -> None:
    recipient = build_recipient()

    summary = await (
        deliver_family_alert_notifications(
            [recipient],
            title="风险告警",
            message="检测到高风险事件。",
        )
    )

    assert summary.attempted_count == 1
    assert summary.sent_count == 1
    assert summary.failed_count == 0

    assert recipient.delivery_status == "sent"
    assert recipient.failure_reason is None
    assert recipient.sent_at is not None
    assert (
    recipient.delivery_provider
    == "simulated"
    )
    assert recipient.external_message_id
    assert recipient.external_message_id.startswith(
        "sim-"
    )


async def test_delivery_supports_in_app_without_destination() -> None:
    recipient = build_recipient(
        channel="in_app",
        destination=None,
    )

    summary = await (
        deliver_family_alert_notifications(
            [recipient],
            title="风险告警",
            message="检测到高风险事件。",
        )
    )

    assert summary.sent_count == 1
    assert recipient.delivery_status == "sent"
    assert recipient.sent_at is not None


async def test_delivery_failure_can_be_recorded() -> None:
    recipient = build_recipient()

    summary = await (
        deliver_family_alert_notifications(
            [recipient],
            title="风险告警",
            message="检测到高风险事件。",
            simulated_failure_recipient_ids={
                recipient.id
            },
        )
    )

    assert summary.attempted_count == 1
    assert summary.failed_count == 1

    assert recipient.delivery_status == "failed"
    assert recipient.failure_reason == (
        "模拟发送失败。"
    )
    assert recipient.sent_at is None
    assert (
    recipient.delivery_provider
    == "simulated"
    )
    assert recipient.external_message_id is None


async def test_completed_recipient_is_idempotent() -> None:
    recipient = build_recipient(
        delivery_status="sent",
    )

    summary = await (
        deliver_family_alert_notifications(
            [recipient],
            title="风险告警",
            message="检测到高风险事件。",
        )
    )

    assert summary.attempted_count == 0
    assert summary.sent_count == 0
    assert summary.already_completed_count == 1