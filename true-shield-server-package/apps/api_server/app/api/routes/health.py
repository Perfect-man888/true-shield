from datetime import UTC, datetime
from typing import Literal

from fastapi import APIRouter
from pydantic import BaseModel

from app.core.config import settings

router = APIRouter()


class HealthResponse(BaseModel):
    """健康检查响应。"""

    status: Literal["ok"]
    application: str
    environment: str
    version: str
    timestamp: datetime


@router.get(
    "/health",
    response_model=HealthResponse,
    summary="检查 API 服务状态",
)
async def health_check() -> HealthResponse:
    """返回 API 进程的基础运行状态。"""

    return HealthResponse(
        status="ok",
        application=settings.app_name,
        environment=settings.app_env,
        version="0.1.0",
        timestamp=datetime.now(UTC),
    )
