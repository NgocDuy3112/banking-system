from fastapi import FastAPI

from app.api.routes import router as api_router

app = FastAPI(
    title="Smart Banking — Fraud Detection",
    version="0.1.0",
    description="Real-time fraud scoring service. See /docs for the OpenAPI schema.",
)

app.include_router(api_router)


@app.get("/health")
def health() -> dict[str, str]:
    """Liveness probe — returns OK as long as the process is running."""
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host="0.0.0.0", port=8000, reload=True)
