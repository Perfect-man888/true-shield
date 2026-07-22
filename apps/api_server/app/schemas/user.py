from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, ConfigDict, EmailStr


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
