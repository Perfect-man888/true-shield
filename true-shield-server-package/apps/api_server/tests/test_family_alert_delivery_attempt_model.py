from __future__ import annotations

from app.models.family_alert import (
    FamilyAlertDeliveryAttempt,
)


def test_delivery_attempt_has_required_columns() -> None:
    """发送尝试记录应包含完整审计字段。"""

    column_names = set(
        FamilyAlertDeliveryAttempt
        .__table__
        .columns
        .keys()
    )

    assert {
        "id",
        "recipient_id",
        "attempt_number",
        "channel",
        "destination",
        "provider",
        "status",
        "external_message_id",
        "failure_reason",
        "attempted_at",
    }.issubset(column_names)


def test_delivery_attempt_recipient_foreign_key() -> None:
    """发送记录应关联家庭告警接收人。"""

    recipient_column = (
        FamilyAlertDeliveryAttempt
        .__table__
        .columns["recipient_id"]
    )

    foreign_key = next(
        iter(recipient_column.foreign_keys)
    )

    assert foreign_key.target_fullname == (
        "family_alert_recipients.id"
    )


def test_delivery_attempt_constraints_exist() -> None:
    """发送次数、状态和唯一性约束必须存在。"""

    constraint_names = {
        constraint.name
        for constraint in (
            FamilyAlertDeliveryAttempt
            .__table__
            .constraints
        )
        if constraint.name is not None
    }

    assert (
        "ck_family_alert_delivery_attempts_"
        "attempt_number"
        in constraint_names
    )

    assert (
        "ck_family_alert_delivery_attempts_"
        "status"
        in constraint_names
    )

    assert (
        "uq_family_alert_delivery_attempts_"
        "recipient_number"
        in constraint_names
    )