from fastapi import APIRouter

from app.schemas.request import ScoreRequest
from app.schemas.response import ScoreResponse

router = APIRouter(prefix="", tags=["scoring"])


@router.post("/score", response_model=ScoreResponse)
def score(request: ScoreRequest) -> ScoreResponse:
    return ScoreResponse(
        transaction_id=request.transaction_id,
        fraud_score=0.1,
        fraud_status="CLEAR",
        reason_codes=[],
        model_version="stub-v0",
        inference_ms=1,
        risk_level="LOW",
    )
