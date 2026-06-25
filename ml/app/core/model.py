import json
import logging
import pickle
import time
from pathlib import Path
from typing import Any

import numpy as np
import onnxruntime as ort
import shap
from app.config import get_settings
from app.core import constants
from app.infra.features import get_feature_repository
from app.schemas.request import ScoreRequest
from app.schemas.response import FraudStatus, ReasonCode, ScoreResponse

logger = logging.getLogger(__name__)


class ModelService:
    def __init__(self):
        self.settings = get_settings()
        self.model_path = self.settings.model_path
        self.metadata_path = self.model_path.parent / constants.METADATA_FILE_NAME
        self.explainer_path = self.model_path.parent / constants.SHAP_EXPLAINER_FILE_NAME
        
        self.session = None
        self.explainer = None
        self.metadata = {}
        self.feature_columns = []
        self.input_name = None
        
        self.load_model()

    def load_model(self):
        """Load ONNX model, metadata, and SHAP explainer."""
        if not self.model_path.exists():
            logger.warning(f"Model file not found at {self.model_path}. Falling back to rules-based scoring.")
            return

        try:
            # Load metadata
            if self.metadata_path.exists():
                with open(self.metadata_path, "r") as f:
                    self.metadata = json.load(f)
                self.feature_columns = self.metadata.get("feature_columns", [])
                self.input_name = self.metadata.get("onnx_input_name", constants.ONNX_INPUT_NAME)
            
            # Load ONNX session
            self.session = ort.InferenceSession(
                str(self.model_path),
                providers=[constants.ONNX_EXECUTION_PROVIDER]
            )
            logger.info(f"Loaded ONNX model from {self.model_path}")
            
            # Load pre-computed SHAP TreeExplainer
            if self.explainer_path.exists():
                with open(self.explainer_path, "rb") as f:
                    self.explainer = pickle.load(f)
                logger.info(f"Loaded SHAP explainer from {self.explainer_path}")
            else:
                logger.warning(f"SHAP explainer not found at {self.explainer_path}. Reason codes will be empty.")
        except Exception as e:
            logger.error(f"Failed to load model: {e}")
            self.session = None
            self.explainer = None

    async def prepare_features(self, request: ScoreRequest) -> np.ndarray:
        """
        Transform ScoreRequest into the feature vector expected by the model.
        Fetches historical features from PostgreSQL.
        """
        repo = get_feature_repository()

        # 1. Get account IDs
        from_account_id = await repo.get_account_id(request.from_account_number)
        to_account_id = await repo.get_account_id(request.to_account_number)

        # 2. Fetch historical features if accounts exist
        hist_features = {
            "velocity_1h": constants.DEFAULT_VELOCITY_1H,
            "velocity_24h": constants.DEFAULT_VELOCITY_24H,
            "velocity_3d": constants.DEFAULT_VELOCITY_3D,
            "amount_zscore": constants.DEFAULT_AMOUNT_ZSCORE,
            "new_recipient_flag": constants.DEFAULT_NEW_RECIPIENT_FLAG,
            "time_since_last_txn": constants.DEFAULT_TIME_SINCE_LAST_TXN,
        }

        if from_account_id and to_account_id:
            hist_features = await repo.get_historical_features(
                from_account_id,
                to_account_id,
                float(request.amount),
            )

        # 3. Request-time features (pure: same inputs → same outputs)
        #    See app/core/features_runtime.py for definitions; kept in lockstep
        #    with notebook 03 to avoid training/serving skew.
        from app.core.features_runtime import compute_request_features

        request_features = compute_request_features(
            amount=float(request.amount),
            from_balance=float(request.from_balance_before),
            transaction_type=request.transaction_type,
            occurred_at=request.occurred_at,
        )

        # 4. Assemble in the exact order declared in feature_metadata.json.
        #    Unknown columns default to 0.0 so missing features don't crash
        #    inference — this matches XGBoost's behaviour during training.
        feature_map = {**request_features, **hist_features}

        features = [feature_map.get(col, 0.0) for col in self.feature_columns]
        return np.array([features], dtype=np.float32)

    async def score(self, request: ScoreRequest) -> ScoreResponse:
        start_time = time.perf_counter()
        
        if self.session is None:
            return self.fallback_score(request, start_time)
            
        try:
            input_data = await self.prepare_features(request)
            
            # Run ONNX inference
            outputs = self.session.run(None, {self.input_name: input_data})
            
            if len(outputs) > 1:
                probs = outputs[1]
                if isinstance(probs, list) and len(probs) > 0 and isinstance(probs[0], dict):
                    fraud_score = float(probs[0].get(1, 0.0))
                else:
                    fraud_score = float(probs[0][1])
            else:
                fraud_score = 0.0
            
            # Compute SHAP reason codes
            reason_codes = self.compute_reason_codes(input_data)
                
            inference_ms = int((time.perf_counter() - start_time) * constants.INFERENCE_MULTIPLIER_MS)
            status, risk = self.get_risk_levels(fraud_score)
            
            return ScoreResponse(
                transaction_id=request.transaction_id,
                fraud_score=fraud_score,
                fraud_status=status,
                reason_codes=reason_codes,
                model_version=self.metadata.get("model_version", "onnx-v1"),
                inference_ms=inference_ms,
                risk_level=risk
            )
            
        except Exception as e:
            logger.error(f"Inference failed: {e}")
            return self.fallback_score(request, start_time)

    def compute_reason_codes(self, feature_vector: np.ndarray) -> list[ReasonCode]:
        """
        Compute SHAP values for a single feature vector and map them to
        human-readable reason codes.

        Steps:
        1. Run shap.Explainer on the feature vector → per-feature SHAP values
        2. Map each feature to its reason code (from constants.SHAP_FEATURE_TO_CODE)
        3. Sum SHAP values for features sharing the same code
        4. Filter by |weight| > SHAP_WEIGHT_THRESHOLD
        5. Sort by |weight| descending, take top MAX_REASON_CODES
        """
        if self.explainer is None:
            return []

        try:
            # Get SHAP values for this single prediction
            shap_vals = self.explainer.shap_values(feature_vector)
            # shap_vals shape: (1, num_features) — one row per sample
            per_feature_values = shap_vals[0]  # shape: (num_features,)

            # Aggregate by reason code
            code_weights: dict[str, float] = {}
            for i, col_name in enumerate(self.feature_columns):
                code = constants.SHAP_FEATURE_TO_CODE.get(col_name)
                if code is None:
                    continue
                code_weights[code] = code_weights.get(code, 0.0) + float(per_feature_values[i])

            # Filter by threshold and sort by absolute weight descending
            reason_codes = []
            for code, weight in sorted(
                code_weights.items(),
                key=lambda item: abs(item[1]),
                reverse=True,
            ):
                if abs(weight) > constants.SHAP_WEIGHT_THRESHOLD:
                    reason_codes.append(ReasonCode(code=code, weight=round(weight, 4)))
                if len(reason_codes) >= constants.MAX_REASON_CODES:
                    break

            return reason_codes

        except Exception as e:
            logger.warning(f"SHAP explanation failed: {e}")
            return []

    def get_risk_levels(self, score: float) -> tuple[FraudStatus, str]:
        if score >= self.settings.threshold_blocked:
            return "BLOCKED", "HIGH"
        elif score >= self.settings.threshold_suspicious:
            return "SUSPICIOUS", "MEDIUM"
        else:
            return "CLEAR", "LOW"

    def fallback_score(self, request: ScoreRequest, start_time: float) -> ScoreResponse:
        """Simple rules-based fallback if model is unavailable."""
        amount = float(request.amount)
        from_balance = float(request.from_balance_before)
        
        # Simple rule: if amount > 90% of balance, it's suspicious
        ratio = amount / from_balance if from_balance > 0 else 0.0
        
        fraud_score = constants.FALLBACK_BASE_SCORE
        if ratio > constants.BALANCE_EMPTYING_THRESHOLD:
            fraud_score = constants.FALLBACK_SUSPICIOUS_SCORE
        if amount > constants.LARGE_AMOUNT_THRESHOLD_VND:
            fraud_score = constants.FALLBACK_BLOCKED_SCORE
            
        status, risk = self.get_risk_levels(fraud_score)
        inference_ms = int((time.perf_counter() - start_time) * constants.INFERENCE_MULTIPLIER_MS)
        
        return ScoreResponse(
            transaction_id=request.transaction_id,
            fraud_score=fraud_score,
            fraud_status=status,
            reason_codes=[],
            model_version=constants.FALLBACK_MODEL_VERSION,
            inference_ms=inference_ms,
            risk_level=risk
        )



model_service = None

def get_model_service() -> ModelService:
    global model_service
    if model_service is None:
        model_service = ModelService()
    return model_service
