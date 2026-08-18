from __future__ import annotations

from sqlalchemy import UniqueConstraint

from app.models.family import (
    Family,
    FamilyMember,
)


def test_family_table_names() -> None:
    """家庭模型应映射到正确的数据表。"""

    assert Family.__tablename__ == "families"

    assert (
        FamilyMember.__tablename__
        == "family_members"
    )


def test_family_member_has_unique_membership() -> None:
    """同一用户不能重复加入同一个家庭。"""

    unique_constraints = [
        constraint
        for constraint
        in FamilyMember.__table__.constraints
        if isinstance(
            constraint,
            UniqueConstraint,
        )
    ]

    matching_constraint = next(
        (
            constraint
            for constraint
            in unique_constraints
            if tuple(
                constraint.columns.keys()
            )
            == (
                "family_id",
                "user_id",
            )
        ),
        None,
    )

    assert matching_constraint is not None

    assert (
        matching_constraint.name
        == "uq_family_members_family_user"
    )


def test_family_member_foreign_keys_use_cascade() -> None:
    """删除家庭或用户时应清理成员关系。"""

    family_foreign_key = next(
        iter(
            FamilyMember.__table__
            .c.family_id.foreign_keys
        )
    )

    user_foreign_key = next(
        iter(
            FamilyMember.__table__
            .c.user_id.foreign_keys
        )
    )

    assert (
        family_foreign_key.ondelete
        == "CASCADE"
    )

    assert (
        user_foreign_key.ondelete
        == "CASCADE"
    )