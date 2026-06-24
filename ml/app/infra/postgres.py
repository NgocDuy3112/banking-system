import logging
from typing import Optional

import asyncpg
from app.config import get_settings

logger = logging.getLogger(__name__)

class PostgresClient:
    def __init__(self):
        self.settings = get_settings()
        self.pool: Optional[asyncpg.Pool] = None

    async def connect(self):
        if self.pool is None:
            try:
                self.pool = await asyncpg.create_pool(
                    dsn=self.settings.db_url,
                    min_size=1,
                    max_size=10,
                )
                logger.info("Connected to PostgreSQL")
            except Exception as e:
                logger.error(f"Failed to connect to PostgreSQL: {e}")
                raise

    async def disconnect(self):
        if self.pool:
            await self.pool.close()
            self.pool = None
            logger.info("Disconnected from PostgreSQL")

    async def fetch_val(self, query: str, *args):
        if self.pool is None:
            await self.connect()
        async with self.pool.acquire() as conn:
            return await conn.fetchval(query, *args)

    async def fetch_row(self, query: str, *args):
        if self.pool is None:
            await self.connect()
        async with self.pool.acquire() as conn:
            return await conn.fetchrow(query, *args)

# Singleton instance
_pg_client = None

def get_pg_client() -> PostgresClient:
    global _pg_client
    if _pg_client is None:
        _pg_client = PostgresClient()
    return _pg_client
