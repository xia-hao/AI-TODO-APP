from abc import ABC, abstractmethod
from typing import Generator


class LLMClient(ABC):
    @abstractmethod
    def chat_stream(
        self, messages: list, tools: list, **kwargs
    ) -> Generator[dict, None, None]:
        """Stream chat completion with tool support.

        Yields dicts with type: 'text', 'tool_call', 'done'.
        """
        pass
