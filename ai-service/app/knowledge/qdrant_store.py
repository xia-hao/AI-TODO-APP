"""Qdrant 向量知识库实现（本地模式 / 远程服务）。

本地模式：数据存本地文件，不需要部署服务。
  store = QdrantKnowledgeStore(path="./qdrant_data")

远程模式：连接已部署的 Qdrant 服务端。
  store = QdrantKnowledgeStore(host="192.168.1.100", port=6333, api_key="...")

嵌入模型可选：
  - API 嵌入：  embedder=APIEmbedding(api_key="...", base_url="...")
  - 本地 ONNX： embedder=YourLocalEmbedder()
  - 不传：      自动用 ChromaDB 内置模型 → 报错
"""

import logging
import time
import uuid
from typing import Optional

from qdrant_client import QdrantClient
from qdrant_client.models import Distance, PointStruct, VectorParams

from app.knowledge.base import KnowledgeEntry, KnowledgeStore, SearchResult

logger = logging.getLogger(__name__)

_COLLECTION_NAME = "experiences"
_EMBED_DIM = 384  # 默认，API 嵌入会动态覆盖


def _get_default_embedder():
    """获取默认嵌入模型：优先用 ChromaDB 内置的。"""
    try:
        from chromadb.utils.embedding_functions import DefaultEmbeddingFunction
        ef = DefaultEmbeddingFunction()
        ef(["验证"])
        logger.info("Qdrant: 使用 ChromaDB 内置嵌入模型")
        return ef, 384
    except Exception as e:
        raise RuntimeError(
            "Qdrant 需要嵌入模型：传 embedder 参数（推荐）或安装 chromadb。错误：%s" % e
        )


class QdrantKnowledgeStore(KnowledgeStore):
    """Qdrant 向量知识库存储，支持本地和远程两种模式。

    本地模式：
        store = QdrantKnowledgeStore(path="./qdrant_data")

    远程模式（线上部署）：
        store = QdrantKnowledgeStore(
            host="qdrant.example.com",
            port=6333,
            api_key="your-api-key",
            https=True,
        )
    """

    def __init__(
        self,
        path: str = "./qdrant_data",
        host: str = "",
        port: int = 6333,
        api_key: str = "",
        https: bool = False,
        prefer_grpc: bool = False,
        embedder: Optional[object] = None,
    ):
        if embedder is not None:
            # 用户指定嵌入器：用 API 或自定义嵌入
            self._embedder = embedder
            embed_dim = _EMBED_DIM  # 默认，APIEmbedding 会自动检测
            # 如果有 dimension 属性则用它
            if hasattr(embedder, "dimension") and embedder.dimension:
                embed_dim = embedder.dimension
            self._embed_dim = embed_dim
            logger.info("Qdrant: 使用自定义嵌入器（维度 %d）", embed_dim)
        else:
            ef, embed_dim = _get_default_embedder()
            self._embedder = ef
            self._embed_dim = embed_dim

        if host:
            # 远程模式：连接 Qdrant 服务端（Docker 部署或 SaaS）
            self._client = QdrantClient(
                host=host,
                port=port,
                api_key=api_key or None,
                https=https,
                prefer_grpc=prefer_grpc,
            )
            logger.info("Qdrant: 连接远程服务 %s:%s", host, port)
        else:
            # 本地模式：文件存储
            self._client = QdrantClient(path=path)
            logger.info("Qdrant: 本地文件模式 %s", path)

        self._init_collection()

    def _init_collection(self):
        collections = self._client.get_collections().collections
        exists = any(c.name == _COLLECTION_NAME for c in collections)
        if not exists:
            dim = self._embed_dim or _EMBED_DIM
            self._client.create_collection(
                collection_name=_COLLECTION_NAME,
                vectors_config=VectorParams(
                    size=dim,
                    distance=Distance.COSINE,
                ),
            )

    def _embed(self, texts: list[str]) -> list[list[float]]:
        result = self._embedder(texts)
        if hasattr(result, "tolist"):
            return result.tolist()
        return result

    async def add(self, entry: KnowledgeEntry) -> str:
        entry_id = str(uuid.uuid4())
        import asyncio
        vector = await asyncio.get_event_loop().run_in_executor(
            None, lambda: self._embed([entry.content])[0],
        )
        point = PointStruct(
            id=entry_id,
            vector=vector,
            payload={
                "content": entry.content,
                "scenario": entry.scenario or "",
                "tags": ",".join(entry.tags) if entry.tags else "",
                "source": entry.source or "",
                "created_at": entry.created_at or time.time(),
            },
        )
        self._client.upsert(collection_name=_COLLECTION_NAME, points=[point])
        logger.info("知识已写入 Qdrant [%s]: %s", entry_id, entry.content[:60])
        return entry_id

    async def search(self, query: str, top_k: int = 5) -> list[SearchResult]:
        if not query.strip():
            return []
        import asyncio
        vector = await asyncio.get_event_loop().run_in_executor(
            None, lambda: self._embed([query])[0],
        )
        try:
            if hasattr(self._client, "query_points"):
                results = self._client.query_points(
                    collection_name=_COLLECTION_NAME,
                    query=vector,
                    limit=top_k,
                    with_payload=True,
                ).points
            else:
                results = self._client.search(
                    collection_name=_COLLECTION_NAME,
                    query_vector=vector,
                    limit=top_k,
                    with_payload=True,
                )
        except Exception as e:
            logger.warning("Qdrant 查询失败：%s", e)
            return []
        entries = []
        for point in results:
            payload = point.payload or {}
            entry = KnowledgeEntry(
                content=payload.get("content", ""),
                scenario=payload.get("scenario", ""),
                tags=payload.get("tags", "").split(",") if payload.get("tags") else [],
                source=payload.get("source", ""),
                created_at=float(payload.get("created_at", 0) or 0),
            )
            entries.append(SearchResult(entry=entry, score=round(point.score, 4)))
        return entries

    async def remove(self, entry_id: str) -> bool:
        try:
            self._client.delete(
                collection_name=_COLLECTION_NAME,
                points_selector=[entry_id],
            )
            return True
        except Exception as e:
            logger.warning("Qdrant 删除失败：%s", e)
            return False

    async def list_all(self) -> list[KnowledgeEntry]:
        try:
            scroll_result = self._client.scroll(
                collection_name=_COLLECTION_NAME,
                limit=1000,
                with_payload=True,
            )
        except Exception as e:
            logger.warning("Qdrant 读取失败：%s", e)
            return []
        entries = []
        for point in scroll_result[0]:
            payload = point.payload or {}
            entries.append(KnowledgeEntry(
                content=payload.get("content", ""),
                scenario=payload.get("scenario", ""),
                tags=payload.get("tags", "").split(",") if payload.get("tags") else [],
                source=payload.get("source", ""),
                created_at=float(payload.get("created_at", 0) or 0),
            ))
        return entries
