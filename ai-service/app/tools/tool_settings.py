"""工具层 Settings 单例持有器。

为什么需要：替代 functools.partial(handler, settings) 模式。
settings 在注册时注入一次，handler 内按需通过 get_tool_settings() 读取，
避免每个 handler 签名中都携带 settings: Settings 参数。
"""

from app.config import Settings

_settings: Settings | None = None


def set_tool_settings(s: Settings) -> None:
    """在 register_all 入口调用一次，写入全局 settings。"""
    global _settings
    _settings = s


def get_tool_settings() -> Settings:
    """获取已初始化的 tool settings。

    必须在 set_tool_settings() 之后调用，否则抛出 RuntimeError。
    """
    if _settings is None:
        raise RuntimeError("Tool settings 尚未初始化，请先调用 register_all")
    return _settings
