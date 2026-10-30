"""FastAPI entry point for the AI service.

W1 only exposes a health endpoint. Image embedding (CLIP) is added in W8
behind a go/no-go check, so this service stays light and starts in seconds.
"""

from datetime import datetime, timezone

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

SERVICE_NAME = "campus-lost-found-ai"
SERVICE_VERSION = "0.1.0"


class HealthResponse(BaseModel):
    status: str
    service: str
    version: str
    model_loaded: bool
    timestamp: str


app = FastAPI(
    title="Campus Lost & Found AI Service",
    description="Image embedding and similarity helpers for the campus lost and found platform.",
    version=SERVICE_VERSION,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://127.0.0.1:5173"],
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
)

# W8 载入模型后由启动流程改写这两个字段。
app.state.model_loaded = False
app.state.model_name = None


@app.get("/health", response_model=HealthResponse, tags=["system"])
def health() -> HealthResponse:
    return HealthResponse(
        status="ok",
        service=SERVICE_NAME,
        version=SERVICE_VERSION,
        model_loaded=bool(app.state.model_loaded),
        timestamp=datetime.now(timezone.utc).isoformat(),
    )

