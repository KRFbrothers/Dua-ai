"""Provider routing through official ADK adapters; configuration is server-only.

ADK_MODELS_JSON: {"controller": ["gemini-..."], "helper": [...], "merger": [...]}.
Non-Gemini routes use ADK's LiteLlm adapter (optional requirements-adapters.txt).
FallbackModel preserves the SDK's no-fallback-after-first-stream-event rule.
"""
import json
import logging
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


def gemini_retry_enabled():
    """ADK_GEMINI_RETRY=1 gives the default Gemini model the same short retry as routed ones (default off)."""
    return os.environ.get('ADK_GEMINI_RETRY', '0').strip() == '1'


def gemini_with_retry(name):
    from google.adk.models import Gemini
    from google.genai import types
    return Gemini(model=name, retry_options=types.HttpRetryOptions(
        attempts=2, initial_delay=0.5, max_delay=2.0,
        http_status_codes=[429, 500, 502, 503, 504]))


def resolve(role, default):
    names = routes().get(role)
    if names is None:
        if gemini_retry_enabled() and isinstance(default, str) and default.startswith('gemini-'):
            return gemini_with_retry(default)
        return default
    from google.adk.models import Gemini, FallbackModel
    from google.genai import types
    models = []
    for name in names:
        if name.startswith('gemini-'):
            models.append(gemini_with_retry(name))
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


# --- GEMINI_AUTH=apikey: Vertex AI express mode with GOOGLE_API_KEY (default "adc" is the rollback) ---
_warned = []
_key_client = None


def key_mode():
    """True only for GEMINI_AUTH=apikey with GOOGLE_API_KEY set.

    A missing key logs one warning and keeps ADC; the key itself is never logged.
    """
    if os.environ.get('GEMINI_AUTH', 'adc').strip().lower() != 'apikey':
        return False
    if os.environ.get('GOOGLE_API_KEY', '').strip():
        return True
    if not _warned:
        _warned.append(1)
        logging.getLogger('dua').warning('[dua-engine] GEMINI_AUTH=apikey but GOOGLE_API_KEY is not set; using ADC')
    return False


def key_client_kwargs():
    return {'vertexai': True, 'api_key': os.environ['GOOGLE_API_KEY'].strip(), 'project': None, 'location': None}


def key_client():
    """One shared google-genai client in express mode (API key, no project/location) for chat/helpers/search."""
    global _key_client
    if _key_client is None:
        from google import genai
        _key_client = genai.Client(**key_client_kwargs())
    return _key_client


def with_key(model):
    """Key mode: make every ADK Gemini model build its client from the API key, never ADC.

    Uses ADK's own Gemini.client_kwargs (keeps its retry options and per-event-loop client cache).
    Plain "gemini-*" names become Gemini objects; FallbackModel members are updated in place.
    """
    if not key_mode():
        return model
    from google.adk.models import Gemini
    if isinstance(model, str):
        return Gemini(model=model, client_kwargs=key_client_kwargs()) if model.startswith('gemini-') else model
    if isinstance(model, Gemini):
        model.client_kwargs = {**(model.client_kwargs or {}), **key_client_kwargs()}
    for member in getattr(model, 'models', None) or ():
        with_key(member)
    return model


_resolve_routes = resolve


def resolve(role, default):  # noqa: F811 (same contract; adds the key client in key mode)
    return with_key(_resolve_routes(role, default))
