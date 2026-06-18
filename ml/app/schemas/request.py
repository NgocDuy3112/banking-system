from datetime import datetime
from decimal import Decimal
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


TransactionType = Literal["INTERNAL", "INTERBANK"]


class ScoreRequest(BaseModel):
    transaction_id: UUID
    from_account_number: str = Field(..., min_length=9, max_length=15)
    to_account_number: str = Field(..., min_length=9, max_length=15)
    amount: Decimal = Field(
        ...,
        gt=Decimal("0"),
        max_digits=19,
        decimal_places=4,
        description="Amount in VND, must be positive. Sent as JSON string for precision.",
    )
    from_balance_before: Decimal = Field(
        ...,
        max_digits=19,
        decimal_places=4,
        description="Balance of the sender account before the transaction.",
    )
    transaction_type: TransactionType
    occurred_at: datetime
    model_config = ConfigDict(json_encoders={Decimal: str})
