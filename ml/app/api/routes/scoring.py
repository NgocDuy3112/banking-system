from fastapi import APIRouter

from app.schemas.request import ScoreRequest
from app.schemas.response import ScoreResponse

router = APIRouter(prefix="", tags=["scoring"])


@router.post("/score", response_model=ScoreResponse)
def score(request: ScoreRequest) -> ScoreResponse:
    """Evaluate a transaction and return a fraud score.

    Currently returns a hardcoded CLEAR response. The real implementation
    will:
      1. Build features from request + DB history (core/features.py)
      2. Run the ONNX model (core/model.py) with a rule-based fallback (core/rules.py)
      3. Map the score to a status using the NFR thresholds (0.5/0.8)
    """
    return ScoreResponse(
        transaction_id=str(request.transaction_id),
        fraud_score=0.1,
        fraud_status="CLEAR",
        reason_codes=[],
        model_version="stub-v0",
        inference_ms=1,
    )
