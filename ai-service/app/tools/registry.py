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
        return [t["definition"] for t in self._tools.values()]

    async def execute(self, name: str, arguments: dict[str, Any]) -> str:
        tool = self._tools.get(name)
        if tool is None:
            return f"错误：未找到工具 '{name}'"
        try:
            return await tool["handler"](**arguments)
        except Exception as e:
            return f"工具 '{name}' 执行失败: {e}"
