from typing import Annotated

from pydantic import BaseModel, EmailStr, Field, field_validator


class PasswordResetCodeRequest(BaseModel):
    """申请发送密码重置验证码。"""

    email: EmailStr

    @field_validator("email")
    @classmethod
    def normalize_email(cls, value: EmailStr) -> str:
        return str(value).strip().lower()


class PasswordResetCodeResponse(BaseModel):
    """密码重置验证码申请结果。"""

    message: str
    expires_in: int
    debug_code: str | None = None


class PasswordResetConfirmRequest(BaseModel):
    """使用邮箱验证码设置新密码。"""

    email: EmailStr
    code: Annotated[
        str,
        Field(pattern=r"^\d{6}$"),
    ]
    new_password: Annotated[
        str,
        Field(min_length=8, max_length=128),
    ]

    @field_validator("email")
    @classmethod
    def normalize_email(cls, value: EmailStr) -> str:
        return str(value).strip().lower()

    @field_validator("code")
    @classmethod
    def normalize_code(cls, value: str) -> str:
        return value.strip()
