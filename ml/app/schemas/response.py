from typing import Literal

from pydantic import BaseModel, Field


FraudStatus = Literal["CLEAR", "SUSPICIOUS", "BLOCKED"]


class ScoreResponse(BaseModel):
    """Body of the POST /score response."""

    transaction_id: str = Field(..., description="Echoes the request's transaction_id")
    fraud_score: float = Field(..., ge=0.0, le=1.0, description="0.0 = safe, 1.0 = fraud")
    fraud_status: FraudStatus
    reason_codes: list[str] = Field(
        default_factory=list,
        description="E.g. ['LARGE_AMOUNT', 'NEW_RECIPIENT'] — for explainability",
    )
    model_version: str = Field(..., description="E.g. 'v1.2.0' or 'rules-v1' for fallback")
    inference_ms: int = Field(..., ge=0, description="Time spent in the model (NFR: <250ms)")
