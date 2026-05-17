"""LLM 客户端抽象基类。

定义流式聊天接口的契约：所有 LLM 客户端实现必须返回统一的事件流
（text / tool_call / done），上层 chat.py 的 event_generator 仅依赖此接口。
"""

from abc import ABC, abstractmethod
from typing import Generator


class LLMClient(ABC):
    @abstractmethod
    def chat_stream(
        self, messages: list, tools: list, **kwargs
    ) -> Generator[dict, None, None]:
        """流式聊天完成，支持工具调用。

        Yields dict with type: 'text' | 'tool_call' | 'done'
        """
        pass
