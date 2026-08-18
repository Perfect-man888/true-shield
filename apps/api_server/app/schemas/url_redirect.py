from __future__ import annotations

from pydantic import BaseModel, Field


class URLRedirectHop(BaseModel):
    """重定向链中的一次 HTTP 响应。"""

    index: int = Field(
        ge=0,
        description="当前请求在重定向链中的序号",
    )

    url: str

    host: str

    status_code: int = Field(
        ge=100,
        le=599,
    )

    location: str | None = None


class URLRedirectResolutionResponse(BaseModel):
    """链接重定向解析结果。"""

    original_url: str

    final_url: str

    final_status_code: int = Field(
        ge=100,
        le=599,
    )

    redirect_count: int = Field(
        ge=0,
    )

    completed: bool = True

    hops: list[URLRedirectHop] = Field(
        default_factory=list,
    )