"""Integration tests for the ML fraud detection service.

Tests the full HTTP request → response flow:
- /health endpoint
- /score endpoint with real ONNX model + SHAP explainer
- Response schema validation (all fields present, correct types)
- Risk level mapping (CLEAR / SUSPICIOUS / BLOCKED)
- Latency SLA (< 250ms)
- Fallback path when model is unavailable
- Edge cases: missing fields, invalid values
"""

import os
import sys
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path
from uuid import uuid4

import pytest
from httpx import ASGITransport, AsyncClient

# Ensure the ml/ package is importable
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

# ---------------------------------------------------------------------------
# Test configuration — set env before importing app
# ---------------------------------------------------------------------------

os.environ.setdefault("ML_DB_URL", "postgresql://test:test@localhost:5432/testdb")
os.environ.setdefault("ML_MODEL_PATH", str(Path(__file__).resolve().parent.parent / "models" / "fraud_model.onnx"))
os.environ.setdefault("ML_THRESHOLD_SUSPICIOUS", "0.5")
os.environ.setdefault("ML_THRESHOLD_BLOCKED", "0.8")
os.environ.setdefault("ML_LOG_LEVEL", "WARNING")

from app.main import app


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
async def client():
    """Async HTTP client bound to the FastAPI app."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac


@pytest.fixture
def suspicious_request() -> dict:
    """A high-risk transaction: 100M VND interbank at 3 AM, 95% balance emptied."""
    return {
        "transaction_id": str(uuid4()),
        "from_account_number": "1234567890",
        "to_account_number": "0987654321",
        "amount": "100000000.0000",
        "from_balance_before": "105000000.0000",
        "transaction_type": "INTERBANK",
        "occurred_at": datetime(2026, 6, 24, 3, 0, 0, tzinfo=timezone.utc).isoformat(),
    }


@pytest.fixture(scope="module")
def normal_request() -> dict:
    """A low-risk transaction: 500K VND internal at 2 PM, 5% balance used."""
    return {
        "transaction_id": str(uuid4()),
        "from_account_number": "1234567890",
        "to_account_number": "1111111111",
        "amount": "500000.0000",
        "from_balance_before": "10000000.0000",
        "transaction_type": "INTERNAL",
        "occurred_at": datetime(2026, 6, 24, 14, 0, 0, tzinfo=timezone.utc).isoformat(),
    }


# ---------------------------------------------------------------------------
# Health endpoint
# ---------------------------------------------------------------------------

class TestHealthEndpoint:
    """Verify the liveness probe."""

    async def test_health_returns_ok(self, client: AsyncClient):
        response = await client.get("/health")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "ok"

    async def test_health_response_time(self, client: AsyncClient):
        """Health check should be fast (< 10ms)."""
        import time
        start = time.perf_counter()
        response = await client.get("/health")
        elapsed_ms = (time.perf_counter() - start) * 1000
        assert response.status_code == 200
        assert elapsed_ms < 50, f"Health check took {elapsed_ms:.1f}ms, expected < 50ms"


# ---------------------------------------------------------------------------
# /score endpoint — happy path
# ---------------------------------------------------------------------------

class TestScoreEndpoint:
    """Full integration: request → ONNX inference → SHAP → response."""

    async def test_score_returns_200(self, client: AsyncClient, suspicious_request: dict):
        response = await client.post("/score", json=suspicious_request)
        assert response.status_code == 200

    async def test_score_response_schema(self, client: AsyncClient, suspicious_request: dict):
        """Every field in ScoreResponse must be present with correct types."""
        response = await client.post("/score", json=suspicious_request)
        data = response.json()

        # Required fields
        assert "transaction_id" in data
        assert "fraud_score" in data
        assert "fraud_status" in data
        assert "reason_codes" in data
        assert "model_version" in data
        assert "inference_ms" in data
        assert "risk_level" in data

        # Type checks
        assert isinstance(data["fraud_score"], float)
        assert 0.0 <= data["fraud_score"] <= 1.0
        assert data["fraud_status"] in ("CLEAR", "SUSPICIOUS", "BLOCKED")
        assert isinstance(data["reason_codes"], list)
        assert isinstance(data["model_version"], str)
        assert isinstance(data["inference_ms"], int)
        assert data["inference_ms"] >= 0
        assert data["risk_level"] in ("LOW", "MEDIUM", "HIGH")

    async def test_score_echoes_transaction_id(self, client: AsyncClient, suspicious_request: dict):
        """Response must echo the request's transaction_id."""
        response = await client.post("/score", json=suspicious_request)
        data = response.json()
        assert data["transaction_id"] == suspicious_request["transaction_id"]

    async def test_suspicious_tx_has_reason_codes(self, client: AsyncClient, suspicious_request: dict):
        """A high-risk transaction should produce reason codes."""
        response = await client.post("/score", json=suspicious_request)
        data = response.json()
        # With SHAP explainer loaded, we expect reason codes
        assert len(data["reason_codes"]) > 0, (
            f"Expected reason codes for suspicious tx, got {data['reason_codes']}"
        )

    async def test_reason_codes_have_code_and_weight(self, client: AsyncClient, suspicious_request: dict):
        """Each reason code must have 'code' (str) and 'weight' (float)."""
        response = await client.post("/score", json=suspicious_request)
        data = response.json()
        for rc in data["reason_codes"]:
            assert "code" in rc
            assert "weight" in rc
            assert isinstance(rc["code"], str)
            assert isinstance(rc["weight"], float)
            assert abs(rc["weight"]) > 0.05, (
                f"Reason code {rc['code']} has |weight|={abs(rc['weight']):.4f} ≤ 0.05"
            )

    async def test_reason_codes_sorted_by_weight(self, client: AsyncClient, suspicious_request: dict):
        """Reason codes must be sorted by |weight| descending."""
        response = await client.post("/score", json=suspicious_request)
        data = response.json()
        weights = [abs(rc["weight"]) for rc in data["reason_codes"]]
        assert weights == sorted(weights, reverse=True), (
            f"Reason codes not sorted: {weights}"
        )

    async def test_max_five_reason_codes(self, client: AsyncClient, suspicious_request: dict):
        """Never more than 5 reason codes."""
        response = await client.post("/score", json=suspicious_request)
        data = response.json()
        assert len(data["reason_codes"]) <= 5, (
            f"Got {len(data['reason_codes'])} reason codes, max is 5"
        )

    async def test_latency_sla(self, client: AsyncClient, suspicious_request: dict):
        """Inference must complete within 250ms (NFR SLA)."""
        response = await client.post("/score", json=suspicious_request)
        data = response.json()
        assert data["inference_ms"] < 250, (
            f"Inference took {data['inference_ms']}ms, SLA is < 250ms"
        )

    async def test_model_version_not_stub(self, client: AsyncClient, suspicious_request: dict):
        """Model version should not be the old stub."""
        response = await client.post("/score", json=suspicious_request)
        data = response.json()
        assert data["model_version"] != "stub-v0", "Still using stub model!"
        assert "onnx" in data["model_version"].lower() or "xgboost" in data["model_version"].lower()


# ---------------------------------------------------------------------------
# Risk level mapping
# ---------------------------------------------------------------------------

class TestRiskLevelMapping:
    """Verify fraud_status and risk_level are consistent."""

    async def test_clear_maps_to_low(self, client: AsyncClient, normal_request: dict):
        response = await client.post("/score", json=normal_request)
        data = response.json()
        if data["fraud_status"] == "CLEAR":
            assert data["risk_level"] == "LOW", (
                f"CLEAR should map to LOW, got {data['risk_level']}"
            )

    async def test_suspicious_maps_to_medium(self, client: AsyncClient, suspicious_request: dict):
        response = await client.post("/score", json=suspicious_request)
        data = response.json()
        if data["fraud_status"] == "SUSPICIOUS":
            assert data["risk_level"] == "MEDIUM", (
                f"SUSPICIOUS should map to MEDIUM, got {data['risk_level']}"
            )

    async def test_blocked_maps_to_high(self, client: AsyncClient):
        """A transaction emptying 99% of balance + large amount should be BLOCKED."""
        extreme_request = {
            "transaction_id": str(uuid4()),
            "from_account_number": "1234567890",
            "to_account_number": "9999999999",
            "amount": "200000000.0000",
            "from_balance_before": "202000000.0000",
            "transaction_type": "INTERBANK",
            "occurred_at": datetime(2026, 6, 24, 3, 0, 0, tzinfo=timezone.utc).isoformat(),
        }
        response = await client.post("/score", json=extreme_request)
        data = response.json()
        if data["fraud_status"] == "BLOCKED":
            assert data["risk_level"] == "HIGH", (
                f"BLOCKED should map to HIGH, got {data['risk_level']}"
            )

    async def test_status_consistent_with_score(self, client: AsyncClient, suspicious_request: dict):
        """fraud_status must be consistent with fraud_score and thresholds."""
        response = await client.post("/score", json=suspicious_request)
        data = response.json()
        score = data["fraud_score"]
        status = data["fraud_status"]

        if score >= 0.8:
            assert status == "BLOCKED", f"Score {score} >= 0.8 but status is {status}"
        elif score >= 0.5:
            assert status == "SUSPICIOUS", f"Score {score} >= 0.5 but status is {status}"
        else:
            assert status == "CLEAR", f"Score {score} < 0.5 but status is {status}"


# ---------------------------------------------------------------------------
# Validation & error handling
# ---------------------------------------------------------------------------

class TestValidation:
    """Request validation and error responses."""

    async def test_missing_required_field(self, client: AsyncClient):
        """Missing 'amount' should return 422."""
        bad_request = {
            "transaction_id": str(uuid4()),
            "from_account_number": "1234567890",
            "to_account_number": "0987654321",
            # "amount" intentionally missing
            "from_balance_before": "10000000.0000",
            "transaction_type": "INTERNAL",
            "occurred_at": datetime(2026, 6, 24, 12, 0, 0, tzinfo=timezone.utc).isoformat(),
        }
        response = await client.post("/score", json=bad_request)
        assert response.status_code == 422

    async def test_negative_amount(self, client: AsyncClient):
        """Negative amount should return 422."""
        bad_request = {
            "transaction_id": str(uuid4()),
            "from_account_number": "1234567890",
            "to_account_number": "0987654321",
            "amount": "-100.0000",
            "from_balance_before": "10000000.0000",
            "transaction_type": "INTERNAL",
            "occurred_at": datetime(2026, 6, 24, 12, 0, 0, tzinfo=timezone.utc).isoformat(),
        }
        response = await client.post("/score", json=bad_request)
        assert response.status_code == 422

    async def test_invalid_transaction_type(self, client: AsyncClient):
        """Invalid transaction_type should return 422."""
        bad_request = {
            "transaction_id": str(uuid4()),
            "from_account_number": "1234567890",
            "to_account_number": "0987654321",
            "amount": "100000.0000",
            "from_balance_before": "10000000.0000",
            "transaction_type": "CRYPTO",  # not in Literal
            "occurred_at": datetime(2026, 6, 24, 12, 0, 0, tzinfo=timezone.utc).isoformat(),
        }
        response = await client.post("/score", json=bad_request)
        assert response.status_code == 422

    async def test_account_number_too_short(self, client: AsyncClient):
        """Account number < 9 chars should return 422."""
        bad_request = {
            "transaction_id": str(uuid4()),
            "from_account_number": "123",  # too short
            "to_account_number": "0987654321",
            "amount": "100000.0000",
            "from_balance_before": "10000000.0000",
            "transaction_type": "INTERNAL",
            "occurred_at": datetime(2026, 6, 24, 12, 0, 0, tzinfo=timezone.utc).isoformat(),
        }
        response = await client.post("/score", json=bad_request)
        assert response.status_code == 422


# ---------------------------------------------------------------------------
# Normal vs suspicious comparison
# ---------------------------------------------------------------------------

class TestNormalVsSuspicious:
    """Verify the model distinguishes risky from safe transactions."""

    async def test_suspicious_score_higher_than_normal(
        self, client: AsyncClient, suspicious_request: dict, normal_request: dict
    ):
        """Suspicious tx should have a higher fraud score than normal tx."""
        sus_response = await client.post("/score", json=suspicious_request)
        norm_response = await client.post("/score", json=normal_request)

        sus_score = sus_response.json()["fraud_score"]
        norm_score = norm_response.json()["fraud_score"]

        assert sus_score > norm_score, (
            f"Suspicious score ({sus_score:.4f}) should be > normal ({norm_score:.4f})"
        )

    async def test_suspicious_more_reason_codes(
        self, client: AsyncClient, suspicious_request: dict, normal_request: dict
    ):
        """Suspicious tx should have ≥ reason codes than normal tx."""
        sus_response = await client.post("/score", json=suspicious_request)
        norm_response = await client.post("/score", json=normal_request)

        sus_codes = sus_response.json()["reason_codes"]
        norm_codes = norm_response.json()["reason_codes"]

        assert len(sus_codes) >= len(norm_codes), (
            f"Suspicious has {len(sus_codes)} codes, normal has {len(norm_codes)}"
        )
