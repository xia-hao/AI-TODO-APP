"""AI Service 应用入口 —— FastAPI 生命周期管理。

启动流程：
  .env 加载 → 注册工具包+收集提示词 → 合并提示词 → 构建依赖 → 启动清理任务 → 监听请求

关键设计：
  - _settings 用模块级全局变量传递给 auth_middleware，
    因为 Starlette BaseHTTPMiddleware 没有 FastAPI 依赖注入
  - cleanup_loop 用 asyncio.create_task 在后台运行，不停机
"""

import asyncio
import logging

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware

from app.config import create_settings
from app.knowledge import ChromaKnowledgeStore

# 知识库备选后端（按需取消注释）：
# from app.knowledge import ChromaKnowledgeStore, QdrantKnowledgeStore, APIEmbedding
from app.middleware import verify_api_key
from app.routes import chat as chat_router
from app.tools import load_tool_packages
from app.tools.registry import ToolRegistry

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# ============================
# 1. 创建应用
# ============================
# docs_url="/docs" → FastAPI 自动生成 Swagger UI 交互文档，方便调试
app = FastAPI(title="Todo AI Service", docs_url="/docs")

# 全局持有 settings，供 auth_middleware 闭包读取
_settings = None


# ============================
# 2. 中间件 —— 先 auth 后 CORS
# ============================
@app.middleware("http")
async def auth_middleware(request: Request, call_next):
    # 放行无需认证的路径
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


# ============================
# 3. 启动 —— 顺序不能乱
# ============================
@app.on_event("startup")
async def startup():
    global _settings

    # 3.1 加载配置
    settings = create_settings()
    _settings = settings

    # 3.2 先加载工具包，收集提示词后缀
    tool_registry = ToolRegistry()
    tool_prompt_suffix = load_tool_packages(settings, tool_registry)

    # 3.3 将工具包提示词合并到 settings，后续 SessionManager 自动拿到完整版本
    if tool_prompt_suffix:
        settings.system_prompt = settings.system_prompt.rstrip() + "\n\n" + tool_prompt_suffix
        logger.info("Merged tool prompt suffixes (%d chars)", len(tool_prompt_suffix))

    # 3.4 初始化知识库（经验自动积累）
    # 可选后端（统一 KnowledgeStore 接口，换后端只需改这 1 行）：
    #   ChromaKnowledgeStore("./chroma_data")                                     # 语义搜索（推荐）
    #   QdrantKnowledgeStore("./qdrant_data")                                     # 语义搜索，本地模型
    #   QdrantKnowledgeStore("./qdrant_data", embedder=APIEmbedding(              # 语义搜索，DeepSeek API
    #       api_key=settings.llm_api_key, base_url=settings.llm_base_url))
    #   JsonKnowledgeStore("experiences.json")                                    # 关键词匹配，零依赖
    knowledge_store = ChromaKnowledgeStore("./chroma_data")

    # 3.5 初始化 LLM 客户端、会话管理器
    deps = chat_router.setup_deps(settings, tool_registry, knowledge_store)

    logger.info(
        "AI Service started — provider=%s model=%s tools=%d packages=%s internal_key=%s",
        settings.llm_provider,
        settings.llm_model,
        len(tool_registry.get_tool_defs()),
        settings.tool_packages,
        "set" if settings.internal_api_key else "NOT_SET",
    )

    # 3.4 启动定时清理过期会话的后台任务
    async def cleanup_loop():
        while True:
            await asyncio.sleep(3600)  # 每小时清理一次
            deps.session_mgr.cleanup_old()

    asyncio.create_task(cleanup_loop())


# ============================
# 4. 路由
# ============================
# 路由注册 —— 每新增一个路由文件，import 后加一行 include_router
app.include_router(chat_router.router, prefix="/api")


# 健康检查 —— 无需认证，K8s/负载均衡器用
@app.get("/api/health")
async def health():
    return {"status": "ok"}
