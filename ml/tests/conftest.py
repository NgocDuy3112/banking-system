"""Shared fixtures for all tests in the ml/tests/ directory.

Provides:
- DB mocking so integration tests run without a real PostgreSQL
"""

import os
import sys
from pathlib import Path
from unittest.mock import AsyncMock, MagicMock, patch

import pytest


# ---------------------------------------------------------------------------
# Ensure ml/ is on sys.path
# ---------------------------------------------------------------------------

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))


# ---------------------------------------------------------------------------
# Env defaults (set before any app import)
# ---------------------------------------------------------------------------

os.environ.setdefault("ML_DB_URL", "postgresql://test:test@localhost:5432/testdb")
os.environ.setdefault("ML_MODEL_PATH", str(Path(__file__).resolve().parent.parent / "models" / "fraud_model.onnx"))
os.environ.setdefault("ML_THRESHOLD_SUSPICIOUS", "0.5")
os.environ.setdefault("ML_THRESHOLD_BLOCKED", "0.8")
os.environ.setdefault("ML_LOG_LEVEL", "WARNING")


# ---------------------------------------------------------------------------
# Mock PostgresClient — avoids needing a real DB for integration tests
# ---------------------------------------------------------------------------

@pytest.fixture(autouse=True)
def mock_postgres_client():
    """Replace the real PostgresClient with a mock that returns safe defaults."""
    mock_pool = MagicMock()
    mock_conn = AsyncMock()

    mock_conn.fetchval = AsyncMock(return_value=None)
    mock_conn.fetchrow = AsyncMock(return_value=None)

    mock_pool.acquire.return_value.__aenter__.return_value = mock_conn

    with patch("app.infra.postgres.asyncpg.create_pool", AsyncMock(return_value=mock_pool)):
        yield
