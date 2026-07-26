from fastapi import APIRouter

from app.api.routes.auth import router as auth_router
from app.api.routes.families import router as families_router
from app.api.routes.health import router as health_router
from app.api.routes.risk import router as risk_router
from app.api.routes.users import router as users_router

api_router = APIRouter()

api_router.include_router(
    health_router,
)

api_router.include_router(
    auth_router,
)

api_router.include_router(
    users_router,
)

api_router.include_router(
    risk_router,
)

api_router.include_router(
    families_router,
)