from __future__ import annotations

import uuid
from datetime import datetime
from enum import Enum

from pydantic import (
    BaseModel,
    ConfigDict,
    EmailStr,
    Field,
    field_validator,
    model_validator,
)


class TrustedContactStatus(str, Enum):
    """可信联系人状态。"""

    ACTIVE = "active"
    DISABLED = "disabled"


class TrustedContactChannel(str, Enum):
    """可信联系渠道。"""

    PHONE = "phone"
    SMS = "sms"
    EMAIL = "email"


class TrustedContactCreate(BaseModel):
    """创建可信联系人。"""

    subject_user_id: uuid.UUID | None = Field(
        default=None,
        description=(
            "联系人所保护的家庭成员；"
            "为空时默认表示当前用户"
        ),
    )

    display_name: str = Field(
        min_length=1,
        max_length=64,
        description="可信联系人姓名或称呼",
    )

    relationship_label: str = Field(
        min_length=1,
        max_length=50,
        description="与被保护人的关系",
    )

    phone: str | None = Field(
        default=None,
        max_length=32,
    )

    email: EmailStr | None = None

    preferred_channel: (
        TrustedContactChannel | None
    ) = None

    priority: int = Field(
        default=3,
        ge=1,
        le=5,
        description="联系优先级，1 最高，5 最低",
    )

    can_receive_alerts: bool = Field(
        default=True,
        description="是否允许接收风险提醒",
    )

    @field_validator(
        "display_name",
        "relationship_label",
        mode="before",
    )
    @classmethod
    def normalize_text(
        cls,
        value: object,
    ) -> object:
        """清理名称和关系描述中的多余空白。"""

        if not isinstance(value, str):
            return value

        normalized = " ".join(
            value.strip().split()
        )

        return normalized

    @field_validator(
        "phone",
        mode="before",
    )
    @classmethod
    def normalize_phone(
        cls,
        value: object,
    ) -> object:
        """清理电话号码两侧空白。"""

        if value is None:
            return None

        if not isinstance(value, str):
            return value

        normalized = value.strip()

        return normalized or None

    @field_validator(
        "email",
        mode="before",
    )
    @classmethod
    def normalize_email(
        cls,
        value: object,
    ) -> object:
        """统一邮箱大小写。"""

        if value is None:
            return None

        if not isinstance(value, str):
            return value

        normalized = value.strip().lower()

        return normalized or None

    @model_validator(mode="after")
    def validate_contact_method(
        self,
    ) -> TrustedContactCreate:
        """至少提供一种联系方式并校验首选渠道。"""

        if self.phone is None and self.email is None:
            raise ValueError(
                "必须至少提供手机号或邮箱。"
            )

        if self.preferred_channel is None:
            if self.phone is not None:
                self.preferred_channel = (
                    TrustedContactChannel.PHONE
                )
            else:
                self.preferred_channel = (
                    TrustedContactChannel.EMAIL
                )

        if (
            self.preferred_channel
            == TrustedContactChannel.EMAIL
            and self.email is None
        ):
            raise ValueError(
                "首选邮箱联系时必须提供 email。"
            )

        if (
            self.preferred_channel
            in {
                TrustedContactChannel.PHONE,
                TrustedContactChannel.SMS,
            }
            and self.phone is None
        ):
            raise ValueError(
                "首选电话或短信联系时必须提供 phone。"
            )

        return self


class TrustedContactResponse(BaseModel):
    """可信联系人响应。"""

    model_config = ConfigDict(
        from_attributes=True,
    )

    id: uuid.UUID

    family_id: uuid.UUID

    subject_user_id: uuid.UUID

    linked_user_id: uuid.UUID | None

    created_by_user_id: uuid.UUID

    display_name: str

    relationship_label: str

    phone: str | None

    email: EmailStr | None

    preferred_channel: TrustedContactChannel

    priority: int

    can_receive_alerts: bool

    status: TrustedContactStatus

    created_at: datetime

    updated_at: datetime