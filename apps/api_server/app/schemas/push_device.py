from datetime import datetime
from typing import Annotated, Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator


class PushDeviceRegisterRequest(BaseModel):
    """注册或刷新当前设备的 FCM 安装标识。"""

    installation_id: Annotated[
        str,
        Field(min_length=10, max_length=255),
    ]

    platform: Literal["android", "ios"] = "android"

    device_name: Annotated[
        str | None,
        Field(max_length=128),
    ] = None

    app_version: Annotated[
        str | None,
        Field(max_length=32),
    ] = None

    @field_validator(
        "installation_id",
        "device_name",
        "app_version",
        mode="before",
    )
    @classmethod
    def normalize_text(
        cls,
        value: object,
    ) -> object:
        if value is None:
            return None

        if not isinstance(value, str):
            return value

        normalized = value.strip()

        if not normalized:
            return None

        return normalized


class PushDeviceResponse(BaseModel):
    """已注册推送设备。"""

    model_config = ConfigDict(from_attributes=True)

    id: UUID
    user_id: UUID
    installation_id: str
    platform: str
    device_name: str | None
    app_version: str | None
    status: str
    last_seen_at: datetime
    created_at: datetime
    updated_at: datetime
