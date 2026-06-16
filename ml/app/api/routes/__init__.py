"""HTTP routes aggregator for the fraud detection service.

This package contains one router per feature (scoring, health, admin, ...).
The aggregator pattern: each submodule defines its own APIRouter(), and
this __init__.py mounts them onto a single top-level router that main.py
includes in the FastAPI app.

Why a package and not a single file?
  - Splits the surface area: scoring vs health vs admin grow independently.
  - Each submodule can be tested with its own router in isolation.
  - Imports stay unchanged: `from app.api.routes import router` still works.
"""

from fastapi import APIRouter

from app.api.routes.scoring import router as scoring_router

router = APIRouter()
router.include_router(scoring_router)
