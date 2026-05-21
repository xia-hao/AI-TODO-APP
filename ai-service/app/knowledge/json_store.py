"""JSON 文件 + 关键词匹配的知识库实现。

零外部依赖，适合起步阶段。数据存为 JSON 文件：
  - 搜索用中文分词 + 英文单词关键词匹配（非语义搜索）
  - 写入是直接 append，O(1)
  - 启动时全量加载到内存

后续切换到 ChromaDB/Qdrant 时，只需新增一个实现类，
业务代码无需改动。
"""

import json
import logging
import os
import re
import time
import uuid

from app.knowledge.base import KnowledgeEntry, KnowledgeStore, SearchResult

logger = logging.getLogger(__name__)

# 英文单词正则（含数字）
_EN_WORD = re.compile(r"[a-zA-Z][a-zA-Z0-9_]*|\d+")


def _tokenize(text: str) -> set[str]:
    """对文本做简单分词，返回 token 集合。

    策略（零依赖，不用 jieba）：
      - 中文拆成**单字**（"本周" → {"本", "周"}）
      - 英文单词转小写（"date_from" → {"date_from"}）
      - 数字保留原样
    单字匹配虽然粗，但对短文本关键词搜索足够。
    后期换向量数据库（ChromaDB/Qdrant）后语义搜索会准得多。
    """
    tokens: set[str] = set()
    text_lower = text.lower()
    i = 0
    while i < len(text_lower):
        ch = text_lower[i]
        # 中文单字（CJK 统一表意文字范围）
        if '一' <= ch <= '鿿':
            tokens.add(ch)
            i += 1
        # 英文或数字
        elif ch.isascii() and (ch.isalpha() or ch.isdigit() or ch == '_'):
            m = _EN_WORD.match(text_lower, i)
            if m:
                tokens.add(m.group())
                i = m.end()
            else:
                i += 1
        else:
            i += 1
    return tokens


def _keyword_score(query_tokens: set[str], entry: KnowledgeEntry) -> float:
    """计算查询与知识条目的单字/单词匹配得分。

    策略：
      - content 权重 0.7，scenario 权重 0.3
      - content 更长，命中概率更高，加对数衰减避免长文本占优
      - 结果范围 [0, 1]
    """
    if not query_tokens:
        return 0.0

    qsize = len(query_tokens)

    def _hit_ratio(text: str) -> float:
        """计算命中比例：匹配 token 数 / 查询 token 总数。"""
        text_tokens = _tokenize(text)
        if not text_tokens:
            return 0.0
        hits = len(query_tokens & text_tokens)
        return hits / qsize

    content_hits = _hit_ratio(entry.content)
    scenario_hits = _hit_ratio(entry.scenario)

    return round(content_hits * 0.7 + scenario_hits * 0.3, 4)


class JsonKnowledgeStore(KnowledgeStore):
    """基于 JSON 文件的知识库存储。

    文件格式：
    ```json
    {
      "version": 1,
      "entries": {
        "uuid": {"content": "...", "scenario": "...", "tags": [], ...}
      }
    }
    ```
    """

    def __init__(self, file_path: str):
        self._file_path = file_path
        self._entries: dict[str, KnowledgeEntry] = {}
        self._load()

    def _load(self):
        """从 JSON 文件加载到内存。文件不存在则从空字典开始。"""
        if not os.path.exists(self._file_path):
            logger.info("知识库文件 %s 不存在，从空库开始", self._file_path)
            return
        try:
            with open(self._file_path, "r", encoding="utf-8") as f:
                raw = json.load(f)
            for eid, data in raw.get("entries", {}).items():
                self._entries[eid] = KnowledgeEntry(**data)
            logger.info("知识库已加载：%d 条经验", len(self._entries))
        except Exception as e:
            logger.warning("知识库文件加载失败（%s），从空库开始：%s", self._file_path, e)

    def _save(self):
        """将内存数据写回 JSON 文件。"""
        raw = {
            "version": 1,
            "entries": {
                eid: {
                    "content": e.content,
                    "scenario": e.scenario,
                    "tags": e.tags,
                    "source": e.source,
                    "created_at": e.created_at,
                }
                for eid, e in self._entries.items()
            },
        }
        tmp_path = self._file_path + ".tmp"
        try:
            with open(tmp_path, "w", encoding="utf-8") as f:
                json.dump(raw, f, ensure_ascii=False, indent=2)
            os.replace(tmp_path, self._file_path)  # 原子写入，防止写一半崩溃
        except Exception as e:
            logger.error("知识库保存失败：%s", e)

    async def add(self, entry: KnowledgeEntry) -> str:
        """追加一条知识，返回生成的 UUID。"""
        entry_id = uuid.uuid4().hex[:12]
        if entry.created_at == 0.0:
            entry.created_at = time.time()
        self._entries[entry_id] = entry
        self._save()
        logger.info("知识已添加 [%s]: %s", entry_id, entry.content[:60])
        return entry_id

    async def search(self, query: str, top_k: int = 5) -> list[SearchResult]:
        """关键词匹配搜索，返回得分最高的 top_k 条。

        策略：
          - 至少命中 1 个 token 才返回
          - 按分数降序排列
          - 分数 < 0.1 的不返回（避免噪声）
        """
        if not query.strip() or not self._entries:
            return []

        query_tokens = _tokenize(query)
        if not query_tokens:
            return []

        scored = [
            SearchResult(entry=e, score=s)
            for e in self._entries.values()
            if (s := _keyword_score(query_tokens, e)) >= 0.1
        ]
        scored.sort(key=lambda r: r.score, reverse=True)
        return scored[:top_k]

    async def remove(self, entry_id: str) -> bool:
        if entry_id not in self._entries:
            return False
        del self._entries[entry_id]
        self._save()
        return True

    async def list_all(self) -> list[KnowledgeEntry]:
        return list(self._entries.values())
