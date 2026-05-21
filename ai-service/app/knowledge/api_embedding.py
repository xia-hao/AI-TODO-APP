"""API 嵌入模型 —— 通过 OpenAI 兼容 API 调用嵌入服务。

支持 DeepSeek、OpenAI 等所有兼容 OpenAI API 格式的嵌入服务。
与 ai-service 的 LLM 配置共用同一个 API Key 和 Base URL。

用法：
    embedder = APIEmbedding(
        api_key="sk-xxx",
        base_url="https://api.deepseek.com",
        model="deepseek-embedding",
    )
    vectors = embedder.embed(["你好", "世界"])
"""

import logging
from typing import List, Optional

logger = logging.getLogger(__name__)


class APIEmbedding:
    """基于 OpenAI 兼容 API 的远程嵌入模型。

    自动检测返回向量的维度，适配不同模型。
    """

    def __init__(self, api_key: str, base_url: str = "", model: str = "deepseek-embedding"):
        from openai import OpenAI

        self._client = OpenAI(
            api_key=api_key or "sk-placeholder",
            base_url=base_url or "https://api.deepseek.com",
        )
        self._model = model
        self._dim: Optional[int] = None

    def embed(self, texts: List[str]) -> List[List[float]]:
        """将文本列表转为向量。"""
        if not texts:
            return []

        # OpenAI SDK 的 embedding 调用是同步的，不支持流式
        resp = self._client.embeddings.create(model=self._model, input=texts)

        # 按输入顺序提取向量
        indexed = [(d.index, d.embedding) for d in resp.data]
        indexed.sort(key=lambda x: x[0])
        vectors = [emb for _, emb in indexed]

        # 自动检测维度（取第一个结果）
        if self._dim is None and vectors:
            self._dim = len(vectors[0])
            logger.info("嵌入模型 %s 维度: %d", self._model, self._dim)

        return vectors

    @property
    def dimension(self) -> int:
        """嵌入向量维度，首次 embed 后自动获取。"""
        return self._dim or 0
