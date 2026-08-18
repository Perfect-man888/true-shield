from __future__ import annotations

from sqlalchemy import (
    CheckConstraint,
    Index,
    UniqueConstraint,
)

from app.models.trusted_contact import TrustedContact


def test_trusted_contact_constraints_exist() -> None:
    """可信联系人表应包含必要约束和索引。"""

    assert (
        TrustedContact.__tablename__
        == "trusted_contacts"
    )

    constraint_names = {
        constraint.name
        for constraint
        in TrustedContact.__table__.constraints
        if isinstance(
            constraint,
            (
                CheckConstraint,
                UniqueConstraint,
            ),
        )
    }

    assert "ck_trusted_contacts_status" in (
        constraint_names
    )

    assert "ck_trusted_contacts_priority" in (
        constraint_names
    )

    assert (
        "ck_trusted_contacts_contact_method"
        in constraint_names
    )

    assert (
        "uq_trusted_contacts_subject_phone"
        in constraint_names
    )

    assert (
        "uq_trusted_contacts_subject_email"
        in constraint_names
    )

    index_names = {
        index.name
        for index
        in TrustedContact.__table__.indexes
        if isinstance(index, Index)
    }

    assert (
        "ix_trusted_contacts_family_subject_status"
        in index_names
    )


def test_trusted_contact_foreign_key_rules() -> None:
    """可信联系人外键删除规则应正确。"""

    family_fk = next(
        iter(
            TrustedContact.__table__
            .c.family_id.foreign_keys
        )
    )

    subject_fk = next(
        iter(
            TrustedContact.__table__
            .c.subject_user_id.foreign_keys
        )
    )

    linked_fk = next(
        iter(
            TrustedContact.__table__
            .c.linked_user_id.foreign_keys
        )
    )

    creator_fk = next(
        iter(
            TrustedContact.__table__
            .c.created_by_user_id.foreign_keys
        )
    )

    assert family_fk.ondelete == "CASCADE"
    assert subject_fk.ondelete == "CASCADE"
    assert linked_fk.ondelete == "SET NULL"
    assert creator_fk.ondelete == "CASCADE"