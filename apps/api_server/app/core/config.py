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

    # Password reset
    password_reset_code_expire_minutes: int = 10
    password_reset_resend_interval_seconds: int = 60
    password_reset_max_failed_attempts: int = 5

    # Notification
    notification_provider: str = "simulated"

    smtp_host: str = ""
    smtp_port: int = 587
    smtp_username: str = ""
    smtp_password: str = ""
    smtp_from_email: str = ""
    smtp_use_tls: bool = True
    smtp_timeout_seconds: float = 10.0

    # Firebase Cloud Messaging
    firebase_push_enabled: bool = False
    firebase_project_id: str = ""
    firebase_credentials_file: str = ""

    # Local voice transcription (FunASR + ModelScope)
    voice_transcription_enabled: bool = True
    voice_funasr_model: str = "iic/SenseVoiceSmall"
    voice_funasr_vad_model: str = (
        "iic/speech_fsmn_vad_zh-cn-16k-common-pytorch"
    )
    voice_funasr_hub: str = "ms"
    voice_funasr_device: str = "cpu"
    voice_funasr_cache_root: str = "models/modelscope"
    voice_funasr_cpu_threads: int = 4
    voice_funasr_batch_size_seconds: int = 60
    voice_audio_max_bytes: int = 20 * 1024 * 1024
    voice_audio_max_duration_seconds: int = 180

    # Risk engine
    rule_version: str = "v0.1.0"
    model_bundle_version: str = "rules-only-v0.1.0"

    # Local semantic AI review (Ollama)
    risk_ai_enabled: bool = False
    risk_ai_provider: str = "ollama"
    risk_ai_base_url: str = "http://127.0.0.1:11434"
    risk_ai_model: str = "qwen3:1.7b"
    risk_ai_timeout_seconds: float = 25.0
    risk_ai_max_text_chars: int = 6_000
    risk_ai_keep_alive: str = "5m"
    risk_ai_min_confidence: float = 0.72
    risk_ai_high_confidence: float = 0.85
    risk_ai_allow_remote: bool = False

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
