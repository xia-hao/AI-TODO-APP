import importlib
import logging

from app.tools.registry import ToolRegistry

logger = logging.getLogger(__name__)


def load_tool_packages(settings, registry: ToolRegistry):
    """Load tool packages specified in settings.tool_packages.

    Each package must export a ``register_all(registry, settings)`` function.
    Multiple packages are separated by comma.
    """
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
        logger.info("Loaded tool package: %s", pkg)


__all__ = ["ToolRegistry", "load_tool_packages"]
