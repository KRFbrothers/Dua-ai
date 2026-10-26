import io
import json
import os
import sys
import urllib.error

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import app as engine  # noqa: E402
import openrouter  # noqa: E402

FREE = "google/gemma-4-31b-it:free"


class Stream(io.BytesIO):
    def __enter__(self):
        return self

    def __exit__(self, *a):
        self.close()


def sse_lines(*chunks):
    out = [b": OPENROUTER PROCESSING\n"]
    for c in chunks:
        out.append(b"data: " + (c if isinstance(c, bytes) else json.dumps(c).encode()) + b"\n\n")
    return Stream(b"".join(out))


def delta(text=None, reasoning=None, finish=None):
    d = {}
    if text is not None:
        d["content"] = text
    if reasoning is not None:
        d["reasoning"] = reasoning
    return {"choices": [{"delta": d, "finish_reason": finish}]}


@pytest.fixture
def owner(monkeypatch):
    monkeypatch.setattr(engine, "verify_owner", lambda authorization: "verified-owner")
    monkeypatch.setattr(engine, "make_part", lambda text=None, data=None, mime_type=None: ("text", text))
    monkeypatch.setattr(engine, "make_content", lambda role, parts: {"role": role, "parts": parts})
    monkeypatch.delenv("DUA_APP_TOKEN", raising=False)
    monkeypatch.setenv("OPENROUTER_API_KEY", "sk-or-test")
    openrouter._cache.update(at=0.0, models=None)


def events(response):
    return [json.loads(l[6:]) for l in response.get_data(as_text=True).splitlines() if l.startswith("data: ")]


def post(body):
    return engine.app.test_client().post("/api/chat", json=body)


BODY = {"model": "Dua", "openrouter": FREE, "stream": True, "mode": "auto",
        "messages": [{"role": "user", "text": "Hi"}, {"role": "model", "text": "Hello!"}, {"role": "user", "text": "2+2?"}]}


def test_free_model_streams_in_app_format_with_history(monkeypatch, owner):
    sent = {}

    def opener(request, timeout=None):
        sent["url"], sent["body"], sent["auth"] = request.full_url, json.loads(request.data), request.headers.get("Authorization")
        return sse_lines(delta(reasoning="hmm"), delta("4"), delta(" it is", finish="stop"), b"[DONE]")
    monkeypatch.setattr(openrouter.urllib.request, "urlopen", opener)
    monkeypatch.setattr(engine, "get_model", lambda system=None: pytest.fail("Gemini must not be called"))
    evs = events(post(BODY))
    assert [e["type"] for e in evs] == ["text", "text", "done"]
    assert "".join(e["delta"] for e in evs if e["type"] == "text") == "4 it is"
    assert sent["url"].endswith("/chat/completions") and sent["auth"] == "Bearer sk-or-test"
    body = sent["body"]
    assert body["model"] == FREE and body["stream"] is True and body["max_tokens"] <= 4096
    assert [m["role"] for m in body["messages"]] == ["system", "user", "assistant", "user"]
    assert body["reasoning"] == {"exclude": True}


def test_reasoning_mode_streams_thinking(monkeypatch, owner):
    monkeypatch.setattr(openrouter.urllib.request, "urlopen", lambda r, timeout=None: sse_lines(delta(reasoning="step"), delta("ok"), b"[DONE]"))
    evs = events(post(dict(BODY, mode="reasoning")))
    assert [e["type"] for e in evs] == ["thinking", "text", "done"]


@pytest.mark.parametrize("code", [429, 402])
def test_rate_limit_is_friendly(monkeypatch, owner, code):
    def opener(request, timeout=None):
        raise urllib.error.HTTPError(request.full_url, code, "secret upstream detail", {}, io.BytesIO(b"{}"))
    monkeypatch.setattr(openrouter.urllib.request, "urlopen", opener)
    evs = events(post(BODY))
    assert evs[-1]["type"] == "error" and evs[-1]["status"] == 429
    assert "50 a day" in evs[-1]["message"] and "secret" not in evs[-1]["message"]


def test_mid_stream_rate_limit(monkeypatch, owner):
    monkeypatch.setattr(openrouter.urllib.request, "urlopen", lambda r, timeout=None: sse_lines(
        {"error": {"code": 429, "message": "Rate limit exceeded"}, "choices": [{"delta": {"content": ""}, "finish_reason": "error"}]}))
    evs = events(post(BODY))
    assert evs[-1]["type"] == "error" and "limit" in evs[-1]["message"]


def test_unknown_or_paid_model_rejected(owner):
    assert post(dict(BODY, openrouter="openai/gpt-5")).status_code == 400
    assert post(dict(BODY, openrouter="google/gemma-4-31b-it")).status_code == 400


def test_no_key_means_no_free_models(monkeypatch, owner):
    monkeypatch.delenv("OPENROUTER_API_KEY")
    assert post(BODY).status_code == 400
    assert engine.app.test_client().get("/").get_json()["freeModels"] == []


def test_agent_and_web_stay_on_gemini(monkeypatch, owner):
    monkeypatch.setattr(engine, "google_search_tool", lambda: "GOOGLE_SEARCH")
    monkeypatch.setattr(openrouter.urllib.request, "urlopen", lambda *a, **k: pytest.fail("OpenRouter must not be called"))

    class Model:
        def generate_content(self, contents, stream=False, generation_config=None, tools=None):
            from types import SimpleNamespace
            return iter([SimpleNamespace(text="gemini", candidates=[])])
    monkeypatch.setattr(engine, "get_model", lambda system=None: Model())
    evs = events(post(dict(BODY, web="search")))
    assert "".join(e.get("delta", "") for e in evs if e["type"] == "text") == "gemini"


def test_requires_owner(monkeypatch, owner):
    def deny(authorization):
        raise engine.OwnerAuthError("Sign in as the owner.", 403)
    monkeypatch.setattr(engine, "verify_owner", deny)
    assert post(BODY).status_code in (401, 403)


def test_model_list_is_curated_and_falls_back(monkeypatch, owner):
    live = {"data": [{"id": FREE}, {"id": "openai/gpt-5"}]}
    ids = [m["id"] for m in openrouter.models(opener=lambda r, timeout=None: Stream(json.dumps(live).encode()))]
    assert ids == [FREE]
    openrouter._cache.update(at=0.0, models=None)

    def broken(r, timeout=None):
        raise OSError("down")
    assert [m["id"] for m in openrouter.models(opener=broken)] == [m["id"] for m in openrouter.CURATED]
    assert all(m["id"].endswith(":free") for m in openrouter.CURATED)
