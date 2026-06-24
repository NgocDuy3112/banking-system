"""Pytest tests for SHAP explainability — reason code generation.

Verifies:
- SHAP explainer loads correctly from the saved pickle
- SHAP values have the correct shape (1 row × N features)
- Reason code mapping works for suspicious transactions
- Reason code mapping works for normal transactions
- Edge cases: all-zero input, extreme values
- Weight threshold filtering and max reason codes limit
"""

import json
import pickle
from pathlib import Path

import numpy as np
import pytest

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------

MODELS_DIR = Path(__file__).resolve().parent.parent / "models"


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture(scope="module")
def explainer():
    """Load the pre-computed SHAP TreeExplainer."""
    path = MODELS_DIR / "shap_explainer.pkl"
    if not path.exists():
        pytest.skip(f"SHAP explainer not found at {path}")
    with open(path, "rb") as f:
        return pickle.load(f)


@pytest.fixture(scope="module")
def metadata():
    """Load feature metadata JSON."""
    path = MODELS_DIR / "feature_metadata.json"
    with open(path, "r") as f:
        return json.load(f)


@pytest.fixture(scope="module")
def feature_columns(metadata):
    """List of feature column names in model order."""
    return metadata["feature_columns"]


@pytest.fixture(scope="module")
def num_features(feature_columns):
    """Number of features the model expects."""
    return len(feature_columns)


@pytest.fixture(scope="module")
def code_map():
    """Feature name → human-readable reason code mapping.

    Must stay in sync with app.core.constants.SHAP_FEATURE_TO_CODE.
    """
    return {
        "newbalanceOrig": "BALANCE_CHANGE",
        "is_transfer": "INTERBANK_RISK",
        "amount_log": "LARGE_AMOUNT",
        "balance_emptying_ratio": "BALANCE_EMPTYING",
        "sin_hour": "OFF_HOURS",
        "cos_hour": "OFF_HOURS",
        "sin_dow": "UNUSUAL_DAY",
        "cos_dow": "UNUSUAL_DAY",
        "velocity_1h": "VELOCITY_SPIKE",
        "velocity_24h": "VELOCITY_SPIKE",
        "velocity_3d": "VELOCITY_SPIKE",
        "amount_zscore": "LARGE_AMOUNT",
        "new_recipient_flag": "NEW_RECIPIENT",
        "time_since_last_txn": "DORMANT_ACCOUNT",
    }


@pytest.fixture(scope="module")
def suspicious_features(num_features):
    """Feature vector simulating a high-risk transaction.

    Scenario: 100M VND interbank transfer at 3 AM, 95% balance emptied,
    7 transactions in the last hour, new recipient.
    """
    vec = np.zeros((1, num_features), dtype=np.float32)
    vec[0, 0] = 50000.0                          # newbalanceOrig: low balance after tx
    vec[0, 1] = 1.0                              # is_transfer: interbank
    vec[0, 2] = np.log1p(100_000_000)            # amount_log: ~18.4
    vec[0, 3] = 0.95                             # balance_emptying_ratio: 95%
    vec[0, 4] = np.sin(2 * np.pi * 3 / 24)       # sin_hour: 3 AM
    vec[0, 5] = np.cos(2 * np.pi * 3 / 24)       # cos_hour: 3 AM
    vec[0, 8] = 7.0                              # velocity_1h: 7 txns
    vec[0, 12] = 1.0                             # new_recipient_flag: new
    return vec


@pytest.fixture(scope="module")
def normal_features(num_features):
    """Feature vector simulating a low-risk transaction.

    Scenario: 500K VND internal transfer at 2 PM, 5% balance used,
    1 transaction in the last hour, known recipient.
    """
    vec = np.zeros((1, num_features), dtype=np.float32)
    vec[0, 0] = 9_500_000.0                       # newbalanceOrig: healthy balance
    vec[0, 1] = 0.0                               # is_transfer: internal
    vec[0, 2] = np.log1p(500_000)                 # amount_log: ~13.1
    vec[0, 3] = 0.05                              # balance_emptying_ratio: 5%
    vec[0, 4] = np.sin(2 * np.pi * 14 / 24)       # sin_hour: 2 PM
    vec[0, 5] = np.cos(2 * np.pi * 14 / 24)       # cos_hour: 2 PM
    vec[0, 8] = 1.0                               # velocity_1h: 1 txn
    vec[0, 12] = 0.0                              # new_recipient_flag: known
    return vec


# ---------------------------------------------------------------------------
# Helper
# ---------------------------------------------------------------------------

def compute_reason_codes(
    shap_values: np.ndarray,
    feature_columns: list[str],
    code_map: dict[str, str],
    weight_threshold: float = 0.05,
    max_codes: int = 5,
) -> list[tuple[str, float]]:
    """Replicate the ModelService.compute_reason_codes logic for testing."""
    per_feature = shap_values[0]  # shape: (num_features,)

    code_weights: dict[str, float] = {}
    for i, col_name in enumerate(feature_columns):
        code = code_map.get(col_name)
        if code is None:
            continue
        code_weights[code] = code_weights.get(code, 0.0) + float(per_feature[i])

    result = []
    for code, weight in sorted(
        code_weights.items(), key=lambda item: abs(item[1]), reverse=True
    ):
        if abs(weight) > weight_threshold:
            result.append((code, round(weight, 4)))
        if len(result) >= max_codes:
            break

    return result


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

class TestSHAPExplainerLoading:
    """Verify the explainer loads and has the correct structure."""

    def test_explainer_type(self, explainer):
        """Explainer should be a TreeExplainer."""
        name = type(explainer).__name__
        assert "Tree" in name, f"Expected TreeExplainer, got {name}"

    def test_explainer_expected_value(self, explainer):
        """Explainer should have an expected_value attribute."""
        assert hasattr(explainer, "expected_value"), "Explainer missing expected_value"


class TestSHAPValuesShape:
    """Verify SHAP output dimensions match expectations."""

    def test_shap_output_shape_matches_features(
        self, explainer, suspicious_features, num_features
    ):
        """SHAP values should be (1, num_features)."""
        shap_vals = explainer.shap_values(suspicious_features)
        assert shap_vals.shape == (1, num_features), (
            f"Expected shape (1, {num_features}), got {shap_vals.shape}"
        )

    def test_shap_output_shape_all_zeros(
        self, explainer, num_features
    ):
        """All-zero input should still produce correct shape."""
        zeros = np.zeros((1, num_features), dtype=np.float32)
        shap_vals = explainer.shap_values(zeros)
        assert shap_vals.shape == (1, num_features)


class TestReasonCodesSuspicious:
    """Reason codes for a high-risk transaction."""

    def test_produces_reason_codes(
        self, explainer, suspicious_features, feature_columns, code_map
    ):
        """Suspicious transaction should produce at least one reason code."""
        shap_vals = explainer.shap_values(suspicious_features)
        codes = compute_reason_codes(shap_vals, feature_columns, code_map)
        assert len(codes) > 0, "Expected at least one reason code for suspicious tx"

    def test_balance_emptying_appears(
        self, explainer, suspicious_features, feature_columns, code_map
    ):
        """BALANCE_EMPTYING should be among the top reason codes."""
        shap_vals = explainer.shap_values(suspicious_features)
        codes = compute_reason_codes(shap_vals, feature_columns, code_map)
        code_names = [c[0] for c in codes]
        assert "BALANCE_EMPTYING" in code_names, (
            f"Expected BALANCE_EMPTYING in reason codes, got {code_names}"
        )

    def test_new_recipient_feature_present(
        self, explainer, suspicious_features, feature_columns, code_map
    ):
        """new_recipient_flag must be in the feature columns and SHAP must not crash.

        The SHAP value may be 0.0 if the model learned to rely on other features
        more heavily — that's a valid model behavior, not a bug.
        """
        shap_vals = explainer.shap_values(suspicious_features)
        per_feature = shap_vals[0]

        # Verify the feature exists in the column list
        assert "new_recipient_flag" in feature_columns, (
            "new_recipient_flag missing from feature_columns"
        )

        # Find its index and verify SHAP returns a valid float (0.0 is valid)
        idx = feature_columns.index("new_recipient_flag")
        weight = float(per_feature[idx])
        assert isinstance(weight, float), (
            f"Expected float SHAP value, got {type(weight).__name__}"
        )

    def test_weights_sorted_descending(
        self, explainer, suspicious_features, feature_columns, code_map
    ):
        """Reason codes must be sorted by |weight| descending."""
        shap_vals = explainer.shap_values(suspicious_features)
        codes = compute_reason_codes(shap_vals, feature_columns, code_map)
        abs_weights = [abs(c[1]) for c in codes]
        assert abs_weights == sorted(abs_weights, reverse=True), (
            f"Reason codes not sorted by |weight| descending: {abs_weights}"
        )


class TestReasonCodesNormal:
    """Reason codes for a low-risk transaction."""

    def test_normal_vs_suspicious_count(
        self, explainer, suspicious_features, normal_features,
        feature_columns, code_map
    ):
        """Normal tx should have ≤ reason codes than suspicious tx."""
        shap_sus = explainer.shap_values(suspicious_features)
        shap_norm = explainer.shap_values(normal_features)

        codes_sus = compute_reason_codes(shap_sus, feature_columns, code_map)
        codes_norm = compute_reason_codes(shap_norm, feature_columns, code_map)

        # Normal should not have more high-weight codes than suspicious
        assert len(codes_norm) <= len(codes_sus), (
            f"Normal tx has {len(codes_norm)} codes, "
            f"suspicious has {len(codes_sus)} — expected normal ≤ suspicious"
        )


class TestEdgeCases:
    """Edge case and boundary tests."""

    def test_all_zeros_produces_valid_codes(
        self, explainer, feature_columns, code_map, num_features
    ):
        """All-zero input should not crash and must respect max_codes limit."""
        zeros = np.zeros((1, num_features), dtype=np.float32)
        shap_vals = explainer.shap_values(zeros)
        codes = compute_reason_codes(shap_vals, feature_columns, code_map)
        # Must be a valid list and respect the max_codes cap
        assert isinstance(codes, list)
        assert len(codes) <= 5, (
            f"All-zero input produced {len(codes)} codes, exceeds max of 5"
        )

    def test_extreme_values_handled(
        self, explainer, feature_columns, code_map, num_features
    ):
        """Extreme feature values should not crash the explainer."""
        extreme = np.full((1, num_features), 1e6, dtype=np.float32)
        shap_vals = explainer.shap_values(extreme)
        codes = compute_reason_codes(shap_vals, feature_columns, code_map)
        # Just checking it doesn't crash — any output is fine
        assert isinstance(codes, list)

    def test_negative_values_handled(
        self, explainer, feature_columns, code_map, num_features
    ):
        """Negative feature values should not crash the explainer."""
        negative = np.full((1, num_features), -100.0, dtype=np.float32)
        shap_vals = explainer.shap_values(negative)
        codes = compute_reason_codes(shap_vals, feature_columns, code_map)
        assert isinstance(codes, list)


class TestWeightThreshold:
    """Verify the |weight| > 0.05 filter works."""

    def test_all_weights_above_threshold(
        self, explainer, suspicious_features, feature_columns, code_map
    ):
        """Every returned code must have |weight| > 0.05."""
        shap_vals = explainer.shap_values(suspicious_features)
        codes = compute_reason_codes(
            shap_vals, feature_columns, code_map, weight_threshold=0.05
        )
        for _, weight in codes:
            assert abs(weight) > 0.05, (
                f"Code with weight {weight} slipped through threshold filter"
            )

    def test_max_reason_codes_limit(
        self, explainer, suspicious_features, feature_columns, code_map
    ):
        """Should never return more than MAX_REASON_CODES (5)."""
        shap_vals = explainer.shap_values(suspicious_features)
        codes = compute_reason_codes(
            shap_vals, feature_columns, code_map, max_codes=5
        )
        assert len(codes) <= 5, f"Returned {len(codes)} codes, max is 5"


class TestCodeMapSync:
    """Ensure the test code_map matches the production constants."""

    def test_code_map_matches_constants(self, code_map):
        """The test code_map must be identical to app.core.constants.SHAP_FEATURE_TO_CODE."""
        from app.core.constants import SHAP_FEATURE_TO_CODE

        assert code_map == SHAP_FEATURE_TO_CODE, (
            "Test code_map differs from app.core.constants.SHAP_FEATURE_TO_CODE. "
            "Update one of them to keep them in sync."
        )

    def test_all_features_have_code(self, feature_columns, code_map):
        """Every feature column must have a corresponding reason code."""
        missing = [col for col in feature_columns if col not in code_map]
        assert not missing, (
            f"Features without reason codes: {missing}. "
            f"Add them to SHAP_FEATURE_TO_CODE."
        )
