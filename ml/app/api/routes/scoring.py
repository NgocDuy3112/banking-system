from fastapi import APIRouter, Depends

from app.core.model import ModelService, get_model_service
from app.schemas.request import ScoreRequest
from app.schemas.response import ScoreResponse

router = APIRouter(prefix="", tags=["scoring"])


@router.post("/score", response_model=ScoreResponse)
async def score(
    request: ScoreRequest, 
    model_service: ModelService = Depends(get_model_service)
) -> ScoreResponse:
    return await model_service.score(request)
