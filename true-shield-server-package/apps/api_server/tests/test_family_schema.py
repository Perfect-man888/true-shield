from __future__ import annotations

import pytest
from pydantic import ValidationError

from app.schemas.family import (
    FamilyCreate,
    FamilyMemberStatus,
    FamilyRole,
    FamilyStatus,
)


def test_family_name_is_normalized() -> None:
    """家庭名称应清理首尾和连续空格。"""

    payload = FamilyCreate(
        name="  我的   家庭  ",
    )

    assert payload.name == "我的 家庭"


def test_blank_family_name_is_rejected() -> None:
    """空白家庭名称应被拒绝。"""

    with pytest.raises(
        ValidationError,
        match="家庭名称不能为空",
    ):
        FamilyCreate(
            name="   ",
        )


def test_family_enum_values_are_stable() -> None:
    """家庭相关枚举值应保持稳定。"""

    assert {
        item.value
        for item in FamilyStatus
    } == {
        "active",
        "archived",
    }

    assert {
        item.value
        for item in FamilyRole
    } == {
        "owner",
        "admin",
        "member",
    }

    assert {
        item.value
        for item in FamilyMemberStatus
    } == {
        "active",
        "removed",
    }