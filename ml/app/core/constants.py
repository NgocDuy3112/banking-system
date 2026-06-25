"""Constants for the ML service."""

from typing import Final

# Model configuration
MODEL_FILE_PATH: Final = "models/fraud_model.onnx"
METADATA_FILE_NAME: Final = "feature_metadata.json"
ONNX_EXECUTION_PROVIDER: Final = "CPUExecutionProvider"
ONNX_INPUT_NAME: Final = "float_input"

# Feature engineering constants
DEFAULT_VELOCITY_1H: Final = 0.0
DEFAULT_VELOCITY_24H: Final = 0.0
DEFAULT_VELOCITY_3D: Final = 0.0
DEFAULT_AMOUNT_ZSCORE: Final = 0.0
DEFAULT_NEW_RECIPIENT_FLAG: Final = 1.0
DEFAULT_TIME_SINCE_LAST_TXN: Final = 2592000.0  # 30 days in seconds

# Time calculations
HOURS_IN_DAY: Final = 24
DAYS_IN_WEEK: Final = 7
SECONDS_IN_DAY: Final = 86400
DAYS_IN_MONTH: Final = 30

# Thresholds for feature calculations
BALANCE_EMPTYING_THRESHOLD: Final = 0.9
LARGE_AMOUNT_THRESHOLD_VND: Final = 100_000_000  # 100M VND
TOTAL_DRAIN_THRESHOLD: Final = 0.99  # account drained to <=1% of original

# Round-amount detection thresholds (VND)
# Fraudsters often use round numbers; legit users send arbitrary amounts.
ROUND_AMOUNT_DIVISORS: Final = (1_000_000, 10_000_000, 50_000_000, 100_000_000, 500_000_000, 1_000_000_000)

# Time bucket boundaries (inclusive ranges, in 24h clock)
NIGHT_HOUR_START: Final = 0   # 00:00
NIGHT_HOUR_END: Final = 5     # up to 05:59
OFFICE_HOUR_START: Final = 9  # 09:00
OFFICE_HOUR_END: Final = 17   # up to 17:59
WEEKEND_DAY_START: Final = 5  # Sat (Mon=0 .. Sun=6)

# SHAP explainability
SHAP_WEIGHT_THRESHOLD: Final = 0.05  # Only include features with |weight| > 0.05
MAX_REASON_CODES: Final = 5  # Maximum number of reason codes to return

# Maps model feature names → human-readable reason codes.
# Features sharing the same code have their SHAP values summed.
SHAP_FEATURE_TO_CODE: Final = {
    # NOTE: "newbalanceOrig" dropped in v3 — it was `oldbalanceOrg - amount`,
    # a deterministic post-transaction field. PaySim's fraud simulator leaves
    # it at 0 for fraud, giving the model a perfect discriminator that does
    # NOT generalize to real banking data. See docs/mlops.md.
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
    # --- Round-number features (added v2) ---
    "is_round_amount": "ROUND_AMOUNT",
    "round_amount_log": "ROUND_AMOUNT",
    # --- Amount-balance derivatives (added v2; post_balance_ratio dropped v3
    #     because it encoded PaySim simulator artifacts — see docs/mlops.md) ---
    "amount_to_balance_pct": "BALANCE_EMPTYING",
    "is_total_drain": "TOTAL_DRAIN",
    # --- v4 amount-tier features (added) ---
    "amount_tier_micro": "MICRO_AMOUNT",
    "amount_tier_small": "SMALL_AMOUNT",
    "amount_tier_medium": "MEDIUM_AMOUNT",
    # --- v4 interaction features (added) ---
    "is_transfer_and_draining": "TRANSFER_DRAIN",
    "is_medium_and_draining": "MEDIUM_DRAIN",
    # --- Time bucket features (added v2) ---
    "is_night": "OFF_HOURS",
    "is_office_hours": "OFF_HOURS",
    "is_weekend": "UNUSUAL_DAY",
}

# Paths for SHAP artifacts
SHAP_EXPLAINER_FILE_NAME: Final = "shap_explainer.pkl"
NATIVE_MODEL_FILE_NAME: Final = "fraud_model_native.pkl"

# Fallback scoring
FALLBACK_BASE_SCORE: Final = 0.1
FALLBACK_SUSPICIOUS_SCORE: Final = 0.6
FALLBACK_BLOCKED_SCORE: Final = 0.9
FALLBACK_MODEL_VERSION: Final = "rules-v1"

# Inference
INFERENCE_MULTIPLIER_MS: Final = 1000  # Convert seconds to milliseconds
