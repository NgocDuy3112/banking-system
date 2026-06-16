from datetime import datetime
from decimal import Decimal
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field

# Mirrors the Transaction entity's transaction_type ENUM in the backend.
# The backend sends these exact strings; we reject anything else with a 422.
TransactionType = Literal["INTERNAL", "INTERBANK"]


class ScoreRequest(BaseModel):
    """Body of POST /score. One transaction to be evaluated."""

    transaction_id: UUID
    from_account_id: UUID
    to_account_id: UUID
    amount: Decimal = Field(
        ...,
        gt=Decimal("0"),
        max_digits=19,
        decimal_places=4,
        description="Amount in VND, must be positive. Sent as JSON string for precision.",
    )
    transaction_type: TransactionType
    occurred_at: datetime

    # Serialize Decimal fields as JSON strings, not JSON numbers.
    # Required for Pydantic v2 (which otherwise refuses to emit Decimal as
    # JSON at all). Pair this with a Java BigDecimal parser on the backend.
    model_config = ConfigDict(json_encoders={Decimal: str})
