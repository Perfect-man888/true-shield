from typing import Annotated, Literal

from pydantic import BaseModel, EmailStr, Field, field_validator


class UserRegister(BaseModel):
    """用户注册请求。"""

    email: EmailStr
    phone: Annotated[
        str | None,
        Field(min_length=6, max_length=32),
    ] = None
    display_name: Annotated[
        str,
        Field(min_length=2, max_length=64),
    ]
    password: Annotated[
        str,
        Field(min_length=8, max_length=128),
    ]

    @field_validator("email")
    @classmethod
    def normalize_email(cls, value: EmailStr) -> str:
        return str(value).strip().lower()

    @field_validator("display_name")
    @classmethod
    def normalize_display_name(cls, value: str) -> str:
        return value.strip()

    @field_validator("phone")
    @classmethod
    def normalize_phone(cls, value: str | None) -> str | None:
        if value is None:
            return None

        normalized = value.strip()

        return normalized or None


class TokenResponse(BaseModel):
    """登录成功后的令牌响应。"""

    access_token: str
    token_type: Literal["bearer"] = "bearer"
    expires_in: int
