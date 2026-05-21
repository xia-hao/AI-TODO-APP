"""知识库抽象基类 —— 定义 KnowledgeStore 接口。

所有知识库实现（JSON/ChromaDB/Qdrant）必须继承此类，
上层业务代码只依赖这个接口，切换后端只需改一行实例化代码。
"""

from abc import ABC, abstractmethod
from dataclasses import dataclass, field


@dataclass
class KnowledgeEntry:
    """一条经验知识的结构化表示。"""
    content: str                              # 经验内容，如"用户说本周时 date_range 应传..."
    scenario: str = ""                        # 触发场景，如"查询本周任务"
    tags: list[str] = field(default_factory=list)  # 标签，如 ["date", "search", "todo"]
    source: str = ""                          # 来源，如 "auto-record" / "manual"
    created_at: float = 0.0                   # 创建时间戳


@dataclass
class SearchResult:
    """检索结果 —— 包含匹配的知识条目和相关性分数。"""
    entry: KnowledgeEntry
    score: float                              # 0.0 ~ 1.0，越高越相关


class KnowledgeStore(ABC):
    """知识存储抽象接口。"""

    @abstractmethod
    async def add(self, entry: KnowledgeEntry) -> str:
        """添加一条知识，返回 entry ID。"""
        ...

    @abstractmethod
    async def search(self, query: str, top_k: int = 5) -> list[SearchResult]:
        """根据查询文本检索最相关的 top_k 条知识。"""
        ...

    @abstractmethod
    async def remove(self, entry_id: str) -> bool:
        """删除指定 ID 的知识，返回是否成功。"""
        ...

    @abstractmethod
    async def list_all(self) -> list[KnowledgeEntry]:
        """列出所有知识（用于管理/查看）。"""
        ...
