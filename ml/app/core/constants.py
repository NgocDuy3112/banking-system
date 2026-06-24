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

# SHAP explainability
SHAP_WEIGHT_THRESHOLD: Final = 0.05  # Only include features with |weight| > 0.05
MAX_REASON_CODES: Final = 5  # Maximum number of reason codes to return

# Maps model feature names → human-readable reason codes.
# Features sharing the same code have their SHAP values summed.
SHAP_FEATURE_TO_CODE: Final = {
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
