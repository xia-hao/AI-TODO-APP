import importlib
import logging

from app.tools.registry import ToolRegistry

logger = logging.getLogger(__name__)


def load_tool_packages(settings, registry: ToolRegistry) -> str:
    """加载 settings.tool_packages 中所有工具包，返回拼接后的系统提示词后缀。

    每个包必须导出 register_all(registry, settings)。
    可选导出 PROMPT_SUFFIX: str 作为系统提示词扩展片段。
    """
    suffixes: list[str] = []
    packages = [p.strip() for p in settings.tool_packages.split(",") if p.strip()]
    for pkg in packages:
        try:
            mod = importlib.import_module(pkg)
        except ImportError:
            logger.warning("Tool package not found, skipping: %s", pkg)
            continue
        if not hasattr(mod, "register_all"):
            logger.warning("Tool package %s has no register_all() function, skipping", pkg)
            continue
        mod.register_all(registry, settings)
        # 收集提示词扩展片段
        if hasattr(mod, "PROMPT_SUFFIX") and mod.PROMPT_SUFFIX:
            suffixes.append(mod.PROMPT_SUFFIX)
        logger.info("Loaded tool package: %s", pkg)
    return "\n".join(suffixes)


__all__ = ["ToolRegistry", "load_tool_packages"]
