"""工具注册中心 —— 工具定义存储、执行分发、自动重试。

Tool Loop 中的角色：
  chat.event_generator → tool_registry.get_tool_defs()  # 发送给 LLM
  chat.event_generator → tool_registry.execute(name, args)  # 执行 LLM 选中的工具
"""

import asyncio
import logging
from typing import Any, Callable, Coroutine

logger = logging.getLogger(__name__)

# 最大重试次数 —— 临时性错误（网络抖动、5xx）自动重试
_MAX_RETRIES = 2

# 退避间隔（秒）：第 1 次重试等 1s，第 2 次等 2s（线性）
_RETRY_BACKOFF = [1, 2]


def _is_retryable(error: Exception) -> bool:
    """判断错误是否值得重试。

    可重试（临时性问题）：
      - ConnectionError / TimeoutError（网络抖动）
      - httpx 的 5xx / 连接超时

    不重试（逻辑错误）：
      - ValueError / TypeError / KeyError（参数问题，重试也白搭）
      - AssertionError
    """
    # httpx 等 HTTP 库的父类
    if isinstance(error, (ConnectionError, TimeoutError, OSError)):
        return True
    # httpx.HTTPStatusError → 检查状态码
    err_str = str(error).lower()
    if "50" in err_str and ("error" in err_str or "fail" in err_str or "server" in err_str):
        return True
    if "timeout" in err_str or "timed out" in err_str:
        return True
    if "connection" in err_str and ("refused" in err_str or "reset" in err_str or "abort" in err_str):
        return True
    return False


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
        """执行指定工具 —— 自动重试临时性错误。

        重试策略：
          - 网络/超时/5xx 类错误 → 最多重试 2 次（1s + 2s 退避）
          - 参数/逻辑错误 → 不重试，直接返回错误信息
          - 所有重试均失败 → 返回人类可读的错误提示
        """
        tool = self._tools.get(name)
        if tool is None:
            return f"错误：未找到工具 '{name}'"

        last_error: Exception | None = None
        handler = tool["handler"]

        for attempt in range(_MAX_RETRIES + 1):
            try:
                return await handler(**arguments)
            except asyncio.CancelledError:
                # 客户端断开不重试，直接透传
                raise
            except Exception as e:
                last_error = e
                if attempt < _MAX_RETRIES and _is_retryable(e):
                    wait = _RETRY_BACKOFF[attempt]
                    logger.warning(
                        "工具 '%s' 执行失败（第 %d 次），%.1fs 后重试：%s",
                        name, attempt + 1, wait, e,
                    )
                    await asyncio.sleep(wait)
                else:
                    logger.error("工具 '%s' 执行失败（已放弃）：%s", name, e)
                    break

        # 所有重试耗尽或不可重试错误
        err_msg = str(last_error) if last_error else "未知错误"
        return f"工具 '{name}' 执行失败，请稍后重试。错误：{err_msg}"
