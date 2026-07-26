from __future__ import annotations

import uuid
from datetime import datetime
from enum import Enum

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    field_validator,
)


class FamilyStatus(str, Enum):
    """家庭组状态。"""

    ACTIVE = "active"
    ARCHIVED = "archived"


class FamilyRole(str, Enum):
    """家庭成员角色。"""

    OWNER = "owner"
    ADMIN = "admin"
    MEMBER = "member"


class FamilyMemberStatus(str, Enum):
    """家庭成员状态。"""

    ACTIVE = "active"
    REMOVED = "removed"


class FamilyCreate(BaseModel):
    """创建家庭组。"""

    name: str = Field(
        min_length=1,
        max_length=100,
        description="家庭组名称",
    )

    @field_validator(
        "name",
        mode="before",
    )
    @classmethod
    def normalize_name(
        cls,
        value: object,
    ) -> object:
        """清理家庭名称中的多余空白。"""

        if not isinstance(value, str):
            return value

        normalized = " ".join(
            value.strip().split()
        )

        if not normalized:
            raise ValueError(
                "家庭名称不能为空。"
            )

        return normalized


class FamilyResponse(BaseModel):
    """家庭组基本信息。"""

    model_config = ConfigDict(
        from_attributes=True,
    )

    id: uuid.UUID

    name: str

    owner_user_id: uuid.UUID

    status: FamilyStatus

    created_at: datetime

    updated_at: datetime


class FamilyMemberResponse(BaseModel):
    """家庭成员信息。"""

    model_config = ConfigDict(
        from_attributes=True,
    )

    id: uuid.UUID

    family_id: uuid.UUID

    user_id: uuid.UUID

    role: FamilyRole

    status: FamilyMemberStatus

    joined_at: datetime

    created_at: datetime

    updated_at: datetime

class MyFamilyItem(BaseModel):
    """当前用户加入的家庭组。"""

    id: uuid.UUID

    name: str

    owner_user_id: uuid.UUID

    status: FamilyStatus

    my_role: FamilyRole

    created_at: datetime

    updated_at: datetime


class MyFamilyListResponse(BaseModel):
    """当前用户的家庭组列表。"""

    items: list[MyFamilyItem] = Field(
        default_factory=list,
    )

    total: int = Field(
        ge=0,
    )


class FamilyMemberListItem(BaseModel):
    """家庭成员列表项。"""

    id: uuid.UUID

    family_id: uuid.UUID

    user_id: uuid.UUID

    display_name: str

    email: str

    phone: str | None = None

    role: FamilyRole

    status: FamilyMemberStatus

    joined_at: datetime


class FamilyMemberListResponse(BaseModel):
    """家庭成员列表响应。"""

    family_id: uuid.UUID

    items: list[FamilyMemberListItem] = Field(
        default_factory=list,
    )

    total: int = Field(
        ge=0,
    )