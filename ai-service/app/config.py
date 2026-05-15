from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # LLM provider
    llm_provider: str = ""
    llm_api_key: str = ""
    llm_base_url: str = ""
    llm_model: str = ""

    # Generic API base for tool handlers
    api_base_url: str = ""
    api_auth_header: str = "Authorization"
    api_auth_token_prefix: str = "Bearer "

    scope_token: str = ""

    # Security
    internal_api_key: str = ""

    # System prompt for AI chat
    system_prompt: str = (
        "你是一个智能任务管理助手，可以帮助用户管理任务、查询统计信息、生成报告等。"
        "你可以使用提供的工具来执行操作。请用中文回答用户的问题，回答要简洁明了。"
    )

    # Tool module discovery (comma-separated Python module paths)
    # Each module must export register_all(registry, settings)
    tool_packages: str = "app.tools.todo_tools"

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8"}


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
