from __future__ import annotations

from sqlalchemy import (
    CheckConstraint,
    Index,
    UniqueConstraint,
)

from app.models.family_alert import (
    FamilyAlert,
    FamilyAlertRecipient,
)


def test_family_alert_constraints_exist() -> None:
    """家庭告警表应具有状态约束和唯一约束。"""

    assert FamilyAlert.__tablename__ == (
        "family_alerts"
    )

    constraint_names = {
        constraint.name
        for constraint
        in FamilyAlert.__table__.constraints
        if isinstance(
            constraint,
            (
                CheckConstraint,
                UniqueConstraint,
            ),
        )
    }

    assert (
        "ck_family_alerts_risk_level"
        in constraint_names
    )

    assert (
        "ck_family_alerts_status"
        in constraint_names
    )

    assert (
        "uq_family_alerts_family_event"
        in constraint_names
    )

    index_names = {
        index.name
        for index
        in FamilyAlert.__table__.indexes
        if isinstance(index, Index)
    }

    assert (
        "ix_family_alerts_family_status_created"
        in index_names
    )


def test_family_alert_foreign_key_rules() -> None:
    """家庭告警外键删除规则应正确。"""

    family_fk = next(
        iter(
            FamilyAlert.__table__
            .c.family_id.foreign_keys
        )
    )

    event_fk = next(
        iter(
            FamilyAlert.__table__
            .c.risk_event_id.foreign_keys
        )
    )

    acknowledge_fk = next(
        iter(
            FamilyAlert.__table__
            .c.acknowledged_by_user_id
            .foreign_keys
        )
    )

    assert family_fk.ondelete == "CASCADE"
    assert event_fk.ondelete == "CASCADE"
    assert acknowledge_fk.ondelete == "SET NULL"


def test_alert_recipient_constraints_exist() -> None:
    """告警接收人表应具有发送状态约束。"""

    assert (
        FamilyAlertRecipient.__tablename__
        == "family_alert_recipients"
    )

    constraint_names = {
        constraint.name
        for constraint
        in FamilyAlertRecipient
        .__table__.constraints
        if isinstance(
            constraint,
            (
                CheckConstraint,
                UniqueConstraint,
            ),
        )
    }

    assert (
        "ck_family_alert_recipients_channel"
        in constraint_names
    )

    assert (
        "ck_family_alert_recipients_delivery_status"
        in constraint_names
    )

    assert (
        "uq_family_alert_recipients_contact_channel"
        in constraint_names
    )


def test_alert_recipient_foreign_keys() -> None:
    """告警接收人外键规则应正确。"""

    alert_fk = next(
        iter(
            FamilyAlertRecipient.__table__
            .c.alert_id.foreign_keys
        )
    )

    contact_fk = next(
        iter(
            FamilyAlertRecipient.__table__
            .c.trusted_contact_id.foreign_keys
        )
    )

    assert alert_fk.ondelete == "CASCADE"
    assert contact_fk.ondelete == "SET NULL"