from __future__ import annotations

import pytest
from pydantic import ValidationError

from app.schemas.trusted_contact import (
    TrustedContactChannel,
    TrustedContactCreate,
    TrustedContactStatus,
)


def test_phone_contact_is_normalized() -> None:
    """电话联系人应规范化并自动选择电话渠道。"""

    payload = TrustedContactCreate(
        display_name="  妈妈  ",
        relationship_label="  母亲  ",
        phone="  13800138000  ",
    )

    assert payload.display_name == "妈妈"
    assert payload.relationship_label == "母亲"
    assert payload.phone == "13800138000"

    assert (
        payload.preferred_channel
        == TrustedContactChannel.PHONE
    )


def test_email_contact_is_normalized() -> None:
    """邮箱联系人应转换为小写并选择邮箱渠道。"""

    payload = TrustedContactCreate(
        display_name="姐姐",
        relationship_label="姐姐",
        email="  Family.Member@Example.COM  ",
    )

    assert str(payload.email) == (
        "family.member@example.com"
    )

    assert (
        payload.preferred_channel
        == TrustedContactChannel.EMAIL
    )


def test_contact_method_is_required() -> None:
    """至少需要手机号或邮箱。"""

    with pytest.raises(
        ValidationError,
        match="必须至少提供手机号或邮箱",
    ):
        TrustedContactCreate(
            display_name="家人",
            relationship_label="亲属",
        )


def test_preferred_channel_must_have_value() -> None:
    """首选渠道必须具有对应联系方式。"""

    with pytest.raises(
        ValidationError,
        match="必须提供 email",
    ):
        TrustedContactCreate(
            display_name="联系人",
            relationship_label="朋友",
            phone="13800138000",
            preferred_channel=(
                TrustedContactChannel.EMAIL
            ),
        )


def test_trusted_contact_enum_values() -> None:
    """可信联系人枚举值应保持稳定。"""

    assert {
        item.value
        for item in TrustedContactStatus
    } == {
        "active",
        "disabled",
    }

    assert {
        item.value
        for item in TrustedContactChannel
    } == {
        "phone",
        "sms",
        "email",
    }