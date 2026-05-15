import contextvars

scope_token: contextvars.ContextVar[str] = contextvars.ContextVar("scope_token", default="")
