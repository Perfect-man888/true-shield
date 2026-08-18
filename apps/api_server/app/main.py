from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.router import api_router
from app.core.config import settings


def create_application() -> FastAPI:
    """创建并配置 FastAPI 应用。"""

    application = FastAPI(
        title="真信盾 API",
        description=(
            "家庭级 AI 反诈与可信联系系统后端服务。"
            "系统输出仅作为风险提示和核验建议，不是绝对真假结论。"
        ),
        version="0.1.0",
        debug=settings.debug,
        docs_url="/docs",
        redoc_url="/redoc",
        openapi_url="/openapi.json",
    )

    application.add_middleware(
        CORSMiddleware,
        allow_origins=[settings.frontend_url],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    application.include_router(
        api_router,
        prefix=settings.api_v1_prefix,
    )

    @application.get(
        "/",
        tags=["System"],
        summary="API 首页",
    )
    async def root() -> dict[str, str]:
        return {
            "application": "真信盾 API",
            "status": "running",
            "version": "0.1.0",
            "documentation": "/docs",
        }

    return application


app = create_application()
