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


class FamilyAlertPolicyRiskLevel(str, Enum):
    """自动告警最低风险等级。"""

    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"


class FamilyAlertPolicySourceType(str, Enum):
    """允许自动触发告警的风险来源。"""

    TEXT = "text"
    IMAGE = "image"
    URL = "url"


class FamilyAlertPolicyUpdate(BaseModel):
    """更新家庭告警策略。"""

    auto_create_enabled: bool | None = None

    auto_dispatch_enabled: bool | None = None

    minimum_risk_level: (
        FamilyAlertPolicyRiskLevel | None
    ) = None

    enabled_source_types: list[
        FamilyAlertPolicySourceType
    ] | None = None

    max_recipients: int | None = Field(
        default=None,
        ge=1,
        le=20,
    )

    @field_validator("enabled_source_types")
    @classmethod
    def validate_source_types(
        cls,
        value: (
            list[FamilyAlertPolicySourceType]
            | None
        ),
    ) -> (
        list[FamilyAlertPolicySourceType]
        | None
    ):
        """来源类型不能为空或重复。"""

        if value is None:
            return None

        if not value:
            raise ValueError(
                "enabled_source_types 不能为空。"
            )

        if len(value) != len(set(value)):
            raise ValueError(
                "enabled_source_types 不能包含重复值。"
            )

        return value


class FamilyAlertPolicyResponse(BaseModel):
    """家庭告警策略响应。"""

    model_config = ConfigDict(
        from_attributes=True,
    )

    id: uuid.UUID

    family_id: uuid.UUID

    auto_create_enabled: bool

    auto_dispatch_enabled: bool

    minimum_risk_level: (
        FamilyAlertPolicyRiskLevel
    )

    enabled_source_types: list[
        FamilyAlertPolicySourceType
    ]

    max_recipients: int

    created_at: datetime

    updated_at: datetime