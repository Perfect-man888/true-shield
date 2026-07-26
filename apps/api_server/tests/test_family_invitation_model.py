from __future__ import annotations

from sqlalchemy import (
    CheckConstraint,
    Index,
)

from app.models.family import FamilyInvitation


def test_family_invitation_table_name() -> None:
    """邀请模型应使用正确表名。"""

    assert (
        FamilyInvitation.__tablename__
        == "family_invitations"
    )


def test_invitation_foreign_keys_use_cascade() -> None:
    """删除家庭或邀请人时应清理邀请。"""

    family_foreign_key = next(
        iter(
            FamilyInvitation.__table__
            .c.family_id.foreign_keys
        )
    )

    inviter_foreign_key = next(
        iter(
            FamilyInvitation.__table__
            .c.inviter_user_id.foreign_keys
        )
    )

    assert family_foreign_key.ondelete == "CASCADE"
    assert inviter_foreign_key.ondelete == "CASCADE"


def test_invitation_constraints_and_index_exist() -> None:
    """邀请表应具有状态约束和查询索引。"""

    constraint_names = {
        constraint.name
        for constraint
        in FamilyInvitation.__table__.constraints
        if isinstance(
            constraint,
            CheckConstraint,
        )
    }

    assert (
        "ck_family_invitations_role"
        in constraint_names
    )

    assert (
        "ck_family_invitations_status"
        in constraint_names
    )

    index_names = {
        index.name
        for index
        in FamilyInvitation.__table__.indexes
        if isinstance(index, Index)
    }

    assert (
        "ix_family_invitations_lookup"
        in index_names
    )