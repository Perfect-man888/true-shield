from __future__ import annotations

import pytest
from pydantic import ValidationError

from app.schemas.family import (
    FamilyInvitationCreate,
    FamilyInvitationStatus,
    FamilyRole,
)


def test_invitation_email_is_normalized() -> None:
    """邀请邮箱应去除空格并转换为小写。"""

    payload = FamilyInvitationCreate(
        invitee_email=(
            "  Family.Member@Example.COM  "
        ),
    )

    assert str(payload.invitee_email) == (
        "family.member@example.com"
    )

    assert payload.role == FamilyRole.MEMBER


def test_invitation_cannot_grant_owner_role() -> None:
    """邀请不能直接授予所有者角色。"""

    with pytest.raises(
        ValidationError,
        match="不能通过邀请授予 owner 角色",
    ):
        FamilyInvitationCreate(
            invitee_email="member@example.com",
            role=FamilyRole.OWNER,
        )


def test_invitation_status_values_are_stable() -> None:
    """邀请状态枚举值应保持稳定。"""

    assert {
        item.value
        for item in FamilyInvitationStatus
    } == {
        "pending",
        "accepted",
        "declined",
        "cancelled",
        "expired",
    }