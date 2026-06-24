import logging
from datetime import datetime, timedelta
from typing import Optional
from uuid import UUID

from app.infra.postgres import get_pg_client

logger = logging.getLogger(__name__)

class FeatureRepository:
    def __init__(self):
        self.pg = get_pg_client()

    async def get_account_id(self, account_number: str) -> Optional[UUID]:
        query = "SELECT id FROM account WHERE account_number = $1"
        return await self.pg.fetch_val(query, account_number)

    async def get_historical_features(
        self, 
        from_account_id: UUID, 
        to_account_id: UUID,
        current_amount: float
    ) -> dict:
        """Fetch all historical features for a given transaction context."""
        
        # 1. Velocity features
        velocity_query = """
            SELECT 
                count(*) FILTER (WHERE created_at > $2) as v1h,
                count(*) FILTER (WHERE created_at > $3) as v24h,
                count(*) FILTER (WHERE created_at > $4) as v3d
            FROM transaction 
            WHERE from_account_id = $1
        """
        now = datetime.utcnow()
        v_row = await self.pg.fetch_row(
            velocity_query, 
            from_account_id,
            now - timedelta(hours=1),
            now - timedelta(days=1),
            now - timedelta(days=3)
        )
        
        # 2. Amount Z-Score (30 days)
        zscore_query = """
            SELECT avg(amount), stddev(amount) 
            FROM transaction 
            WHERE from_account_id = $1 AND created_at > $2
        """
        z_row = await self.pg.fetch_row(zscore_query, from_account_id, now - timedelta(days=30))
        avg_amount = float(z_row['avg'] or 0.0)
        std_amount = float(z_row['stddev'] or 1.0) # Avoid div by zero
        amount_zscore = (current_amount - avg_amount) / std_amount if std_amount > 0 else 0.0
        
        # 3. New Recipient Flag
        recipient_query = """
            SELECT NOT EXISTS (
                SELECT 1 FROM transaction 
                WHERE from_account_id = $1 AND to_account_id = $2
            )
        """
        new_recipient_flag = await self.pg.fetch_val(recipient_query, from_account_id, to_account_id)
        
        # 4. Time since last transaction
        last_txn_query = """
            SELECT created_at FROM transaction 
            WHERE from_account_id = $1 
            ORDER BY created_at DESC LIMIT 1
        """
        last_txn_at = await self.pg.fetch_val(last_txn_query, from_account_id)
        if last_txn_at:
            time_since_last_txn = (now - last_txn_at).total_seconds()
        else:
            time_since_last_txn = 86400.0 * 30 # Default to 30 days if no history
            
        return {
            "velocity_1h": float(v_row['v1h'] or 0),
            "velocity_24h": float(v_row['v24h'] or 0),
            "velocity_3d": float(v_row['v3d'] or 0),
            "amount_zscore": amount_zscore,
            "new_recipient_flag": 1.0 if new_recipient_flag else 0.0,
            "time_since_last_txn": time_since_last_txn
        }

# Singleton instance
_feature_repo = None

def get_feature_repository() -> FeatureRepository:
    global _feature_repo
    if _feature_repo is None:
        _feature_repo = FeatureRepository()
    return _feature_repo
