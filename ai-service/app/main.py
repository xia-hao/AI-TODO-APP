import asyncio
import logging

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware

from app.config import create_settings
from app.middleware import verify_api_key
from app.routes import chat as chat_router
from app.tools import load_tool_packages

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="Todo AI Service", docs_url="/docs")

_settings = None


@app.middleware("http")
async def auth_middleware(request: Request, call_next):
    # Skip auth for health check and docs
    if request.url.path in ("/api/health", "/docs", "/openapi.json"):
        return await call_next(request)
    result = await verify_api_key(request, _settings.internal_api_key if _settings else "")
    if result is not None:
        return result
    return await call_next(request)


app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.on_event("startup")
async def startup():
    global _settings
    settings = create_settings()
    _settings = settings
    deps = chat_router.setup_deps(settings)

    # Register tools (loaded from settings.tool_packages)
    tool_registry = deps["tool_registry"]
    load_tool_packages(settings, tool_registry)
    logger.info(
        "AI Service started — provider=%s model=%s tools=%d packages=%s internal_key=%s",
        settings.llm_provider,
        settings.llm_model,
        len(tool_registry.get_tool_defs()),
        settings.tool_packages,
        "set" if settings.internal_api_key else "NOT_SET",
    )

    # Start periodic session cleanup
    async def cleanup_loop():
        while True:
            await asyncio.sleep(3600)  # every hour
            deps["session_mgr"].cleanup_old()

    asyncio.create_task(cleanup_loop())


app.include_router(chat_router.router, prefix="/api")


@app.get("/api/health")
async def health():
    return {"status": "ok"}
