from collections.abc import AsyncGenerator

import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from sqlalchemy.ext.asyncio import (
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)
from sqlalchemy.pool import StaticPool

from app.db.base import Base
from app.db.session import get_db
from app.main import app
from app.models import RiskEvent, RiskSignal, User  # noqa: F401

SLOW_TEST_MODULES = {
    "test_ai_semantic_risk_recheck.py",
    "test_ocr_service.py",
    "test_push_notification.py",
    "test_risk_image_api.py",
    "test_risk_report_pdf_renderer.py",
    "test_risk_voice_api.py",
    "test_voice_transcription.py",
}


def pytest_collection_modifyitems(items: list[pytest.Item]) -> None:
    """Classify tests so local and CI runs can select a predictable scope."""

    for item in items:
        module_name = item.path.name
        if module_name in SLOW_TEST_MODULES:
            item.add_marker(pytest.mark.slow)
        elif "_api" in module_name or "auth" in module_name or "users" in module_name:
            item.add_marker(pytest.mark.integration)
        else:
            item.add_marker(pytest.mark.unit)


@pytest_asyncio.fixture
async def client() -> AsyncGenerator[AsyncClient, None]:
    """
    不需要数据库的普通 API 测试客户端。

    注意：使用该客户端访问数据库接口时，
    会连接项目配置中的真实数据库。
    """

    transport = ASGITransport(
        app=app,
        raise_app_exceptions=True,
    )

    async with AsyncClient(
        transport=transport,
        base_url="http://test",
    ) as async_client:
        yield async_client


@pytest_asyncio.fixture
async def db_client() -> AsyncGenerator[AsyncClient, None]:
    """
    使用独立内存数据库的 API 测试客户端。

    每个测试都会：
    1. 创建新的 SQLite 内存数据库；
    2. 创建所有数据表；
    3. 覆盖 FastAPI 的 get_db；
    4. 测试结束后删除表并释放引擎。
    """

    test_engine = create_async_engine(
        "sqlite+aiosqlite://",
        poolclass=StaticPool,
        connect_args={
            "check_same_thread": False,
        },
    )

    testing_session_local = async_sessionmaker(
        bind=test_engine,
        class_=AsyncSession,
        expire_on_commit=False,
        autoflush=False,
    )

    async with test_engine.begin() as connection:
        await connection.run_sync(
            Base.metadata.create_all
        )

    async def override_get_db(
    ) -> AsyncGenerator[AsyncSession, None]:
        async with testing_session_local() as session:
            try:
                yield session
            except Exception:
                await session.rollback()
                raise

    app.dependency_overrides[get_db] = override_get_db

    transport = ASGITransport(
        app=app,
        raise_app_exceptions=True,
    )

    try:
        async with AsyncClient(
            transport=transport,
            base_url="http://test",
        ) as async_client:
            yield async_client
    finally:
        app.dependency_overrides.pop(
            get_db,
            None,
        )

        async with test_engine.begin() as connection:
            await connection.run_sync(
                Base.metadata.drop_all
            )

        await test_engine.dispose()
