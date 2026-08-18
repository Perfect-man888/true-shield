from __future__ import annotations

from app.schemas.family_alert import (
    AlertDeliveryChannel,
    AlertDeliveryStatus,
    FamilyAlertStatus,
)


def test_family_alert_status_values() -> None:
    """家庭告警状态值应保持稳定。"""

    assert {
        item.value
        for item in FamilyAlertStatus
    } == {
        "pending",
        "acknowledged",
        "resolved",
        "cancelled",
    }


def test_alert_delivery_channel_values() -> None:
    """发送渠道枚举值应保持稳定。"""

    assert {
        item.value
        for item in AlertDeliveryChannel
    } == {
        "in_app",
        "phone",
        "sms",
        "email",
    }


def test_alert_delivery_status_values() -> None:
    """发送状态枚举值应保持稳定。"""

    assert {
        item.value
        for item in AlertDeliveryStatus
    } == {
        "pending",
        "sent",
        "failed",
        "skipped",
    }