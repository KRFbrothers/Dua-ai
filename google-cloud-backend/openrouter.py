"""OpenRouter free models for Dua chat (OpenAI-compatible streaming, converted to the app's SSE events).

The key comes only from the OPENROUTER_API_KEY environment variable (Secret Manager on Cloud Run).
Only curated `:free` model ids can be used, so a request can never pick a paid model.
"""
import json
import os
import threading
import time
import urllib.error
import urllib.request

API = "https://openrouter.ai/api/v1"
# Curated free models (verified on https://openrouter.ai/api/v1/models, Oct 2026). Each id ends in ":free".
CURATED = [
    {"id": "google/gemma-4-31b-it:free", "name": "Gemma 4 31B"},
    {"id": "nvidia/nemotron-3-super-120b-a12b:free", "name": "Nemotron 3 Super"},
    {"id": "thinkingmachines/inkling:free", "name": "Inkling"},
    {"id": "poolside/laguna-s-2.1:free", "name": "Laguna S 2.1 (code)"},
]
MAX_TOKENS = int(os.environ.get("OPENROUTER_MAX_TOKENS", "4096"))
LIST_TTL_SECONDS = 3600
TIMEOUT_SECONDS = 120
RATE_LIMITED = ("Free model limit reached for now (OpenRouter free tier: about 20 requests a minute and 50 a day). "
                "Try again later, or switch to Dua in the model picker.")
UNAVAILABLE = "This free model is busy or unavailable right now. Try another free model or switch to Dua."

_cache = {"at": 0.0, "models": None}
_lock = threading.Lock()


def api_key():
    return os.environ.get("OPENROUTER_API_KEY", "").strip()


def enabled():
    return bool(api_key())


def _live_ids(opener):
    with opener(urllib.request.Request(API + "/models", headers={"Accept": "application/json"}), timeout=5) as response:
        data = json.loads(response.read(8 * 1024 * 1024).decode("utf-8"))
    return {item.get("id") for item in data.get("data", []) if isinstance(item, dict)}


def models(opener=None, clock=time.monotonic):
    """Curated free models that OpenRouter still lists (checked hourly); the curated list if the check fails."""
    if not enabled():
        return []
    with _lock:
        if _cache["models"] is not None and clock() - _cache["at"] < LIST_TTL_SECONDS:
            return list(_cache["models"])
    try:
        live = _live_ids(opener or urllib.request.urlopen)
        chosen = [m for m in CURATED if m["id"] in live] or list(CURATED)
    except Exception:
        chosen = list(CURATED)
    with _lock:
        _cache.update(at=clock(), models=chosen)
    return list(chosen)


def allowed(model_id):
    return isinstance(model_id, str) and model_id.endswith(":free") and any(m["id"] == model_id for m in CURATED)


def chat_messages(system, messages):
    """App history -> OpenAI-style messages (text only; file text is kept, binary attachments are not sent)."""
    out = [{"role": "system", "content": system}]
    for message in messages:
        role = "assistant" if message.get("role") == "model" else "user"
        text = message.get("text") or ""
        extra = message.get("attachmentText")
        if role == "user" and isinstance(extra, str) and extra.strip():
            text = (text + "\n\n" + extra).strip()
        if not text.strip():
            continue
        if out[-1]["role"] == role and role != "system":
            out[-1]["content"] += "\n\n" + text
        else:
            out.append({"role": role, "content": text})
    while len(out) > 1 and out[1]["role"] != "user":
        out.pop(1)
    return out


def _friendly(status):
    return RATE_LIMITED if status in (402, 429) else UNAVAILABLE


def stream(model_id, system, messages, enqueue, reasoning=False, opener=None):
    """Producer for app_chat's queue: ("event", text/thinking) items, then ("done") or ("fail", message, status)."""
    body = {"model": model_id, "messages": chat_messages(system, messages), "stream": True, "max_tokens": MAX_TOKENS}
    if not reasoning:
        body["reasoning"] = {"exclude": True}
    request = urllib.request.Request(API + "/chat/completions", data=json.dumps(body).encode("utf-8"), method="POST", headers={
        "Authorization": "Bearer " + api_key(), "Content-Type": "application/json", "Accept": "text/event-stream",
        "X-Title": "Dua AI"})
    try:
        response = (opener or urllib.request.urlopen)(request, timeout=TIMEOUT_SECONDS)
    except urllib.error.HTTPError as error:
        enqueue("fail", (_friendly(error.code), 429 if error.code in (402, 429) else 502))
        return
    sent = False
    truncated = False
    with response:
        for raw in response:
            line = raw.decode("utf-8", "replace").strip()
            if not line.startswith("data:"):
                continue  # ": OPENROUTER PROCESSING" keep-alives
            payload = line[5:].strip()
            if payload == "[DONE]":
                break
            try:
                chunk = json.loads(payload)
            except ValueError:
                continue
            if isinstance(chunk.get("error"), dict):  # mid-stream error (e.g. rate limit after 200)
                code = chunk["error"].get("code")
                enqueue("fail", (_friendly(code if isinstance(code, int) else 502), 429 if code in (402, 429) else 502))
                return
            choice = (chunk.get("choices") or [{}])[0]
            delta = choice.get("delta") or {}
            thinking = delta.get("reasoning")
            if reasoning and isinstance(thinking, str) and thinking:
                if not enqueue("event", {"type": "thinking", "delta": thinking}):
                    return
            text = delta.get("content")
            if isinstance(text, str) and text:
                sent = True
                if not enqueue("event", {"type": "text", "delta": text}):
                    return
            if choice.get("finish_reason") == "length":
                truncated = True
    if not sent:
        enqueue("fail", (UNAVAILABLE, 502))
        return
    enqueue("done", truncated)
