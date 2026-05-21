"""知识库模块 —— 为 Agent 提供持久化经验存储和检索。

内置三种后端，通过统一的 KnowledgeStore 接口切换：

    from app.knowledge import JsonKnowledgeStore          # 关键词匹配，零依赖
    from app.knowledge import ChromaKnowledgeStore         # 语义搜索，需要 chromadb
    from app.knowledge import QdrantKnowledgeStore         # 语义搜索，需要 qdrant-client

在 main.py 中选一个实例化，业务代码无需改动。
"""

from app.knowledge.api_embedding import APIEmbedding
from app.knowledge.base import KnowledgeEntry, KnowledgeStore, SearchResult
from app.knowledge.json_store import JsonKnowledgeStore

# chromadb 和 qdrant 是可选的，导入失败时静默跳过
try:
    from app.knowledge.chroma_store import ChromaKnowledgeStore  # noqa: F401
except ImportError as e:
    ChromaKnowledgeStore = None  # type: ignore
    _chroma_err = str(e)

try:
    from app.knowledge.qdrant_store import QdrantKnowledgeStore  # noqa: F401
except ImportError as e:
    QdrantKnowledgeStore = None  # type: ignore
    _qdrant_err = str(e)

__all__ = [
    "KnowledgeEntry", "KnowledgeStore", "SearchResult",
    "APIEmbedding",
    "JsonKnowledgeStore",
    "ChromaKnowledgeStore",
    "QdrantKnowledgeStore",
]
