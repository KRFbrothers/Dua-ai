import json
import os
import sys
from types import SimpleNamespace

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import app as engine  # noqa: E402


def grounded(text, urls):
    chunks = [SimpleNamespace(web=SimpleNamespace(uri=u, title=t, domain="")) for u, t in urls]
    return SimpleNamespace(text=text, candidates=[SimpleNamespace(grounding_metadata=SimpleNamespace(grounding_chunks=chunks))])


class AgentModel:
    def __init__(self, fail_research=False):
        self.calls, self.fail_research = [], fail_research

    def generate_content(self, contents, stream=False, generation_config=None, tools=None):
        self.calls.append({"contents": contents, "stream": stream, "config": generation_config, "tools": tools})
        if tools:
            if self.fail_research:
                raise RuntimeError("secret quota detail")
            return grounded("Gemini 3 Flash launched.", [("https://example.com/a", "example.com"), ("https://example.com/a", "dup"), ("http://insecure.test", "x"), ("https://news.test/b", "AI news")])
        if stream:
            return iter([SimpleNamespace(text=t, candidates=[]) for t in ("## Ideas", "\n1. Voice")])
        return SimpleNamespace(text="- look up AI news")


@pytest.fixture
def model(monkeypatch):
    m = AgentModel()
    monkeypatch.setattr(engine, "get_model", lambda system=engine.SYSTEM_PROMPT: m)
    monkeypatch.setattr(engine, "make_part", lambda text=None, data=None, mime_type=None: ("text", text))
    monkeypatch.setattr(engine, "make_content", lambda role, parts: {"role": role, "parts": parts})
    monkeypatch.setattr(engine, "google_search_tool", lambda: "GOOGLE_SEARCH")
    monkeypatch.delenv("DUA_APP_TOKEN", raising=False)
    return m


def events(response):
    return [json.loads(l[6:]) for l in response.get_data(as_text=True).splitlines() if l.startswith("data: ")]


BODY = {"model": "Dua", "messages": [{"role": "user", "text": "Latest AI news?"}], "stream": True, "mode": "agent"}


def test_agent_plans_researches_once_and_streams_ideas_with_sources(model):
    r = engine.app.test_client().post("/api/chat", json=BODY)
    evs = events(r)
    kinds = [e["type"] for e in evs]
    assert kinds[-1] == "done" and "sources" in kinds and "thinking" in kinds
    assert kinds.index("sources") < kinds.index("text")
    sources = next(e for e in evs if e["type"] == "sources")["sources"]
    assert [s["url"] for s in sources] == ["https://example.com/a", "https://news.test/b"]
    assert sources[0]["domain"] == "example.com" and sources[1]["domain"] == "news.test"
    assert "".join(e["delta"] for e in evs if e["type"] == "text") == "## Ideas\n1. Voice"
    # Cost caps: 3 calls, exactly one grounded, every call token-capped.
    assert len(model.calls) == 3
    assert [c["tools"] for c in model.calls] == [None, ["GOOGLE_SEARCH"], None]
    assert all(c["config"]["max_output_tokens"] <= 4096 for c in model.calls)
    final_text = model.calls[2]["contents"][-1]["parts"][0][1]
    assert "Gemini 3 Flash launched." in final_text and "[1]" in final_text


def test_agent_failure_does_not_leak(model):
    model.fail_research = True
    r = engine.app.test_client().post("/api/chat", json=BODY)
    evs = events(r)
    assert evs[-1] == {"type": "error", "message": engine.GENERIC_ERROR, "status": 502}
    assert "secret" not in r.get_data(as_text=True)


def test_agent_requires_streaming(model):
    r = engine.app.test_client().post("/api/chat", json={**BODY, "stream": False})
    assert r.status_code == 400 and model.calls == []


def test_real_google_search_tool_shape():
    pytest.importorskip("vertexai")
    assert engine.google_search_tool().to_dict() == {"google_search": {}}
