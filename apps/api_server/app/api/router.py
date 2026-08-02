from fastapi import APIRouter

from app.api.routes.auth import router as auth_router
from app.api.routes.call_guard import router as call_guard_router
from app.api.routes.families import router as families_router
from app.api.routes.family_alerts import (
    router as family_alerts_router,
)
from app.api.routes.health import router as health_router
from app.api.routes.risk import router as risk_router
from app.api.routes.trusted_contacts import (
    router as trusted_contacts_router,
)
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
    call_guard_router,
)

api_router.include_router(
    risk_router,
)

api_router.include_router(
    families_router,
)

api_router.include_router(
    trusted_contacts_router,
)

api_router.include_router(
    family_alerts_router,
)