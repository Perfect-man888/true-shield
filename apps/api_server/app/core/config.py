from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

# config.py 位于：
# true-shield/apps/api_server/app/core/config.py
# parents[4] 对应项目根目录 true-shield
PROJECT_ROOT = Path(__file__).resolve().parents[4]
ENV_FILE = PROJECT_ROOT / ".env"


class Settings(BaseSettings):
    """应用配置。

    配置优先从系统环境变量读取，本地开发时从项目根目录的 .env 读取。
    """

    model_config = SettingsConfigDict(
        env_file=ENV_FILE,
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    # Application
    app_name: str = "true-shield"
    app_env: str = "development"
    debug: bool = True
    api_v1_prefix: str = "/api/v1"

    # FastAPI
    api_host: str = "0.0.0.0"
    api_port: int = 8000

    # PostgreSQL
    postgres_host: str = "localhost"
    postgres_port: int = 5432
    postgres_db: str = "true_shield"
    postgres_user: str = "true_shield"
    postgres_password: str

    # Redis
    redis_host: str = "localhost"
    redis_port: int = 6379
    redis_password: str = ""

    # MinIO
    minio_endpoint: str = "localhost:9000"
    minio_access_key: str
    minio_secret_key: str
    minio_bucket: str = "true-shield"
    minio_secure: bool = False

    # Authentication
    jwt_secret_key: str
    jwt_algorithm: str = "HS256"
    jwt_access_token_expire_minutes: int = 30
    jwt_refresh_token_expire_days: int = 7

    # Risk engine
    rule_version: str = "v0.1.0"
    model_bundle_version: str = "rules-only-v0.1.0"

    # Frontend
    frontend_url: str = "http://localhost:5173"

    @property
    def database_url(self) -> str:
        """供未来 SQLAlchemy 异步数据库连接使用。"""
        return (
            f"postgresql+asyncpg://{self.postgres_user}:"
            f"{self.postgres_password}@{self.postgres_host}:"
            f"{self.postgres_port}/{self.postgres_db}"
        )


@lru_cache
def get_settings() -> Settings:
    """缓存配置，避免每次请求重新读取环境变量。"""
    return Settings()


settings = get_settings()
