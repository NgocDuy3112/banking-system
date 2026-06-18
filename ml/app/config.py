"""Application configuration loaded from environment variables.

Uses pydantic-settings to read env vars (with the ML_ prefix) and a
.env file at startup. All settings are typed and validated — if a
required var is missing or malformed, the service fails fast at
startup rather than at request time.

The `@lru_cache` on `get_settings()` means env is read once per process,
not once per request.
"""

from functools import lru_cache
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Service configuration. All env vars are prefixed with `ML_`."""

    db_url: str = Field(
        ...,
        description="asyncpg connection string, e.g. postgresql://user:pass@host:5432/db",
    )

    # --- Model artifacts ---
    model_path: Path = Field(
        default=Path("models/fraud_model.onnx"),
        description="Path to the ONNX model file. If missing at startup, "
        "the service falls back to rules-based scoring.",
    )

    # --- Threshold bands (from docs/nonfunctional-requirements.md) ---
    threshold_suspicious: float = Field(
        default=0.5,
        ge=0.0,
        le=1.0,
        description="Score >= this AND < threshold_blocked → SUSPICIOUS (extra OTP)",
    )
    threshold_blocked: float = Field(
        default=0.8,
        ge=0.0,
        le=1.0,
        description="Score >= this → BLOCKED",
    )

    # --- Logging ---
    log_level: str = Field(
        default="INFO",
        description="Python log level: DEBUG, INFO, WARNING, ERROR",
    )

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        env_prefix="ML_",
        case_sensitive=False,
        extra="ignore",
    )


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """Read env once and return a cached Settings instance."""
    return Settings()  # type: ignore[call-arg]
