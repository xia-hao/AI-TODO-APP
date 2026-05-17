"""应用配置 —— 基于 pydantic-settings 从 .env 加载。

_PROVIDER_DEFAULTS 的设计意图：
用户只需设置 LLM_PROVIDER + LLM_API_KEY 两个环境变量即可切换提供商。
每个提供商预设了 base_url 和默认 model，降低配置摩擦。
"""

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # LLM 提供商配置
    llm_provider: str = ""
    llm_api_key: str = ""
    llm_base_url: str = ""
    llm_model: str = ""

    # Java 后端 API 配置
    api_base_url: str = ""
    api_auth_header: str = "Authorization"
    api_auth_token_prefix: str = "Bearer "

    scope_token: str = ""

    # API 密钥 —— 服务间认证
    internal_api_key: str = ""

    # 系统提示词 —— 角色定义，工具能力由各工具包的 PROMPT_SUFFIX 自动拼接
    system_prompt: str = (
        "你是一个智能助手。涉及数据查询或操作时，必须先调用工具获取真实数据，"
        "严禁编造任何数据（如任务数量、项目名称、截止日期等）。"
        "纯闲聊时可以直接回复。请用中文回答，回答要简洁明了。"
    )

    # 工具模块发现（逗号分隔的 Python 模块路径）
    # 每个模块必须导出 register_all(registry, settings)
    tool_packages: str = "app.tools.todo_tools"

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8"}


# 提供商默认配置 —— 用户只需设置 LLM_PROVIDER + LLM_API_KEY
_PROVIDER_DEFAULTS = {
    "deepseek": {"base_url": "https://api.deepseek.com", "model": "deepseek-chat"},
    "openai": {"base_url": "https://api.openai.com/v1", "model": "gpt-4o-mini"},
    "ollama": {"base_url": "http://127.0.0.1:11434/v1", "model": "llama3"},
    "openrouter": {"base_url": "https://openrouter.ai/api/v1", "model": "openai/gpt-4o-mini"},
}


def create_settings() -> Settings:
    settings = Settings()
    defaults = _PROVIDER_DEFAULTS.get(settings.llm_provider, _PROVIDER_DEFAULTS["deepseek"])
    if not settings.llm_base_url:
        settings.llm_base_url = defaults["base_url"]
    if not settings.llm_model:
        settings.llm_model = defaults["model"]
    return settings
