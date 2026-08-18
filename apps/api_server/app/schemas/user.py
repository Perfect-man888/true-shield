from datetime import datetime
from typing import Annotated
from uuid import UUID

from pydantic import (
    BaseModel,
    ConfigDict,
    EmailStr,
    Field,
    field_validator,
    model_validator,
)


class UserResponse(BaseModel):
    """返回给前端的用户信息，不包含密码哈希。"""

    model_config = ConfigDict(from_attributes=True)

    id: UUID
    email: EmailStr
    phone: str | None
    display_name: str
    status: str
    created_at: datetime
    updated_at: datetime


class UserUpdateRequest(BaseModel):
    """修改当前登录用户的基础资料。"""

    display_name: Annotated[
        str | None,
        Field(min_length=2, max_length=64),
    ] = None

    phone: Annotated[
        str | None,
        Field(min_length=6, max_length=32),
    ] = None

    @field_validator(
        "display_name",
        mode="before",
    )
    @classmethod
    def normalize_display_name(
        cls,
        value: object,
    ) -> object:
        if value is None:
            return None

        if not isinstance(value, str):
            return value

        normalized = value.strip()

        if not normalized:
            raise ValueError("昵称不能为空")

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
        if value is None:
            return None

        if not isinstance(value, str):
            return value

        normalized = value.strip()

        # 空字符串表示用户希望删除手机号。
        return normalized or None

    @model_validator(mode="after")
    def validate_update_fields(
        self,
    ) -> "UserUpdateRequest":
        if not self.model_fields_set:
            raise ValueError("至少需要提交一个可修改字段")

        if (
            "display_name" in self.model_fields_set
            and self.display_name is None
        ):
            raise ValueError("昵称不能为空")

        return self


class PasswordChangeRequest(BaseModel):
    """修改当前登录用户的密码。"""

    current_password: Annotated[
        str,
        Field(min_length=8, max_length=128),
    ]

    new_password: Annotated[
        str,
        Field(min_length=8, max_length=128),
    ]

    @model_validator(mode="after")
    def validate_passwords(
        self,
    ) -> "PasswordChangeRequest":
        if self.current_password == self.new_password:
            raise ValueError("新密码不能与当前密码相同")

        return self
