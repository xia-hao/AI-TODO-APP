"""ChromaDB 向量知识库实现。

数据存储在本地目录，不依赖外部服务。
使用 ChromaDB 内置的 ONNX 嵌入模型（all-MiniLM-L6-v2），首次使用自动下载。

与 JSON 版本的区别：
  - 语义搜索（"本周任务" ≈ "这周任务"），不是关键词匹配
  - 自动管理向量索引，数据存在本地 chroma_data/ 目录

切换方式（main.py）：
    from app.knowledge import ChromaKnowledgeStore
    store = ChromaKnowledgeStore("./chroma_data")
"""

import logging
import time
import uuid

import chromadb
from chromadb.config import Settings as ChromaSettings

from app.knowledge.base import KnowledgeEntry, KnowledgeStore, SearchResult

logger = logging.getLogger(__name__)

_COLLECTION_NAME = "experiences"


class ChromaKnowledgeStore(KnowledgeStore):
    """基于 ChromaDB 的向量知识库存储（嵌入式，零部署）。"""

    def __init__(self, path: str = "./chroma_data"):
        self._client = chromadb.PersistentClient(
            path=path,
            settings=ChromaSettings(anonymized_telemetry=False),
        )
        self._collection = self._client.get_or_create_collection(
            name=_COLLECTION_NAME,
            metadata={"hnsw:space": "cosine"},
        )
        logger.info("ChromaDB 知识库已就绪：%s", path)

    async def add(self, entry: KnowledgeEntry) -> str:
        entry_id = uuid.uuid4().hex[:12]
        metadata = {
            "scenario": entry.scenario or "",
            "tags": ",".join(entry.tags) if entry.tags else "",
            "source": entry.source or "",
            "created_at": str(entry.created_at or time.time()),
        }
        self._collection.add(
            documents=[entry.content],
            metadatas=[metadata],
            ids=[entry_id],
        )
        logger.info("知识已写入 ChromaDB [%s]: %s", entry_id, entry.content[:60])
        return entry_id

    async def search(self, query: str, top_k: int = 5) -> list[SearchResult]:
        if not query.strip():
            return []
        try:
            results = self._collection.query(
                query_texts=[query],
                n_results=top_k,
            )
        except Exception as e:
            logger.warning("ChromaDB 查询失败：%s", e)
            return []

        if not results["documents"] or not results["documents"][0]:
            return []

        entries = []
        for i, doc in enumerate(results["documents"][0]):
            meta = results["metadatas"][0][i] if results["metadatas"] else {}
            distance = results["distances"][0][i] if results.get("distances") else 0.0
            # cosine 距离转相似度分数（0~1，越高越相关）
            score = round(1.0 - distance, 4)

            entry = KnowledgeEntry(
                content=doc,
                scenario=meta.get("scenario", ""),
                tags=meta.get("tags", "").split(",") if meta.get("tags") else [],
                source=meta.get("source", ""),
                created_at=float(meta.get("created_at", 0) or 0),
            )
            entries.append(SearchResult(entry=entry, score=score))

        entries.sort(key=lambda r: r.score, reverse=True)
        return entries

    async def remove(self, entry_id: str) -> bool:
        try:
            self._collection.delete(ids=[entry_id])
            return True
        except Exception as e:
            logger.warning("ChromaDB 删除失败：%s", e)
            return False

    async def list_all(self) -> list[KnowledgeEntry]:
        try:
            all_data = self._collection.get()
        except Exception as e:
            logger.warning("ChromaDB 读取失败：%s", e)
            return []

        entries = []
        for i, doc in enumerate(all_data.get("documents", []) or []):
            meta = (all_data.get("metadatas") or [{}])[i] or {}
            entries.append(KnowledgeEntry(
                content=doc or "",
                scenario=meta.get("scenario", ""),
                tags=meta.get("tags", "").split(",") if meta.get("tags") else [],
                source=meta.get("source", ""),
                created_at=float(meta.get("created_at", 0) or 0),
            ))
        return entries
