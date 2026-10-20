"""Provider routing through official ADK adapters; configuration is server-only.

ADK_MODELS_JSON: {"controller": ["gemini-..."], "helper": [...], "merger": [...]}.
Non-Gemini routes use ADK's LiteLlm adapter (optional requirements-adapters.txt).
FallbackModel preserves the SDK's no-fallback-after-first-stream-event rule.
"""
import json
import os
import re

ROLES = {'controller', 'helper', 'merger'}
PROVIDERS = ('openai/', 'anthropic/', 'ollama/', 'ollama_chat/', 'vllm/', 'openrouter/')


def routes():
    raw = os.environ.get('ADK_MODELS_JSON', '').strip()
    data = json.loads(raw) if raw else {}
    if not isinstance(data, dict) or set(data) - ROLES:
        raise ValueError('ADK_MODELS_JSON must contain only controller/helper/merger routes')
    for values in data.values():
        if not isinstance(values, list) or not 1 <= len(values) <= 3 or any(
                not isinstance(v, str) or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._:/-]{0,199}',v) or '://' in v or not v.startswith(('gemini-',) + PROVIDERS) for v in values):
            raise ValueError('Each ADK route needs 1–3 explicit supported model names')
        if any(v.startswith(('ollama/', 'ollama_chat/', 'vllm/')) for v in values) and os.environ.get('ADK_ALLOW_LOCAL_MODELS') != '1':
            raise ValueError('Local model routes require ADK_ALLOW_LOCAL_MODELS=1 on the server')
    return data


def resolve(role, default):
    names = routes().get(role)
    if names is None:
        return default
    from google.adk.models import Gemini, FallbackModel
    from google.genai import types
    models = []
    for name in names:
        if name.startswith('gemini-'):
            models.append(Gemini(model=name, retry_options=types.HttpRetryOptions(
                attempts=2, initial_delay=0.5, max_delay=2.0,
                http_status_codes=[429, 500, 502, 503, 504])))
        else:
            from google.adk.models.lite_llm import LiteLlm
            # SDK adapters read their provider credentials/endpoints from server env.
            # No raw keys or caller-supplied base URLs enter the request contract.
            models.append(LiteLlm(model=name, num_retries=0))
    return models[0] if len(models) == 1 else FallbackModel(models=models)


def observed(model, trace):
    """Meter actual delegate attempts, including failed fallback providers.

    Streaming usage snapshots are counted once at the end of each attempt. An
    interrupted/error attempt without usage remains unknown, never zero cost.
    """
    from typing import Any
    import time
    import uuid
    from pydantic import Field
    from google.adk.models import BaseLlm, FallbackModel
    from google.adk.models.registry import LLMRegistry
    if isinstance(model,str):model=LLMRegistry.new_llm(model)
    if isinstance(model,FallbackModel):
        model.models=[observed(item,trace) for item in model.models]
        return model

    class Metered(BaseLlm):
        delegate: Any = Field(exclude=True)
        observer: Any = Field(exclude=True)

        async def generate_content_async(self,llm_request,stream=False):
            key=uuid.uuid4().hex
            started=time.monotonic()
            metadata=None
            label=self.delegate.model
            status='ok'
            self.observer.emit('provider.start',model=label)
            try:
                async for response in self.delegate.generate_content_async(llm_request,stream=stream):
                    if response.usage_metadata is not None:metadata=response.usage_metadata
                    label=response.model_version or label
                    yield response
            except BaseException:
                status='interrupted'
                raise
            finally:
                self.observer.usage(key,label,metadata)
                self.observer.emit('provider.end',model=label,status=status,
                    duration_ms=round((time.monotonic()-started)*1000))

    trace.sdk_metered=True
    return Metered(model=model.model,delegate=model,observer=trace)


def check_adapters():
    import importlib.util
    configured=routes()
    if any(not name.startswith('gemini-') for values in configured.values() for name in values):
        if importlib.util.find_spec('litellm') is None:
            raise ValueError('Install requirements-adapters-lock.txt for configured non-Gemini routes')
    return True
