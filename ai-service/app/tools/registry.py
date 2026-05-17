"""工具注册中心 —— 工具定义存储和执行分发。

Tool Loop 中的角色：
  chat.event_generator → tool_registry.get_tool_defs()  # 发送给 LLM
  chat.event_generator → tool_registry.execute(name, args)  # 执行 LLM 选中的工具
"""

from typing import Any, Callable, Coroutine


class ToolRegistry:
    def __init__(self):
        self._tools: dict[str, dict[str, Any]] = {}

    def register(
        self,
        tool_def: dict[str, Any],
        handler: Callable[..., Coroutine[Any, Any, str]],
    ):
        name = tool_def["function"]["name"]
        self._tools[name] = {"definition": tool_def, "handler": handler}

    def get_tool_defs(self) -> list[dict[str, Any]]:
        """返回所有已注册工具定义 —— 在每次 LLM 调用时发送。"""
        return [t["definition"] for t in self._tools.values()]

    async def execute(self, name: str, arguments: dict[str, Any]) -> str:
        """执行指定工具 —— 由 event_generator 在 Tool Loop 中调用。"""
        tool = self._tools.get(name)
        if tool is None:
            return f"错误：未找到工具 '{name}'"
        try:
            return await tool["handler"](**arguments)
        except Exception as e:
            return f"工具 '{name}' 执行失败: {e}"
