from typing import Literal
from uuid import UUID

from pydantic import BaseModel, Field


FraudStatus = Literal["CLEAR", "SUSPICIOUS", "BLOCKED"]


class ReasonCode(BaseModel):
    """A single feature's contribution to the fraud score, computed via SHAP."""

    code: str = Field(
        ...,
        description="Feature code, e.g. 'BALANCE_EMPTYING', 'NEW_RECIPIENT'. "
        "Maps to a human-readable label in the Audit UI.",
    )
    weight: float = Field(
        ...,
        description="SHAP value — positive pushes the score up (risk), "
        "negative pushes it down (safe). Only features with |weight| > 0.05 are included.",
    )


class ScoreResponse(BaseModel):
    transaction_id: UUID = Field(..., description="Echoes the request's transaction_id")
    fraud_score: float = Field(..., ge=0.0, le=1.0, description="0.0 = safe, 1.0 = fraud")
    fraud_status: FraudStatus
    reason_codes: list[ReasonCode] = Field(
        default_factory=list,
        description="Per-feature SHAP contributions, sorted by |weight| descending. "
        "Empty list = no feature crossed the |weight| > 0.05 threshold, "
        "or model is in rules-only fallback mode.",
    )
    model_version: str = Field(..., description="E.g. 'v1.2.0' or 'rules-v1' for fallback")
    inference_ms: int = Field(..., ge=0, description="Time spent in the model (NFR: <250ms)")
    risk_level: Literal["LOW", "MEDIUM", "HIGH"] = Field(
        ..., description="Categorical risk level based on thresholds"
    )
