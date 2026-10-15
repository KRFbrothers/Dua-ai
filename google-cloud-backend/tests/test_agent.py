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
    # Owner verification is covered by test_owner_auth; agent tests focus on the agent flow.
    monkeypatch.setattr(engine, "verify_owner", lambda authorization: "verified-owner")
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
    assert "".join(e["delta"] for e in evs if e["type"] == "text").startswith("## Ideas\n1. Voice\n\nSources:\n[1]")
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


def test_grounded_reply_ends_with_app_sources_footer(model):
    evs = events(engine.app.test_client().post("/api/chat", json=BODY))
    text = "".join(e["delta"] for e in evs if e["type"] == "text")
    assert text.endswith("\n\nSources:\n[1] example.com\nhttps://example.com/a\n\n[2] AI news\nhttps://news.test/b")
    assert text.count("\n\nSources:\n") == 1
    assert evs[-1]["type"] == "done"


def test_footer_matches_web_format_and_flattens_titles():
    footer = engine.sources_footer([{"id": 1, "title": "A\nB", "url": "https://a.test"}])
    assert footer == "\n\nSources:\n[1] A B\nhttps://a.test"
    assert engine.sources_footer([]) == ""


def test_root_advertises_agent_capability():
    body = engine.app.test_client().get("/").get_json()
    assert body["agent"] is True and body["engine"] == "google-cloud"


def test_defaults_are_gemini_3_8_flash_on_global():
    assert engine.MODEL_NAME == os.environ.get("VERTEX_MODEL", "gemini-3.8-flash")
    assert engine.LOCATION == os.environ.get("VERTEX_LOCATION", "global")


def test_real_genai_tool_thinking_and_global_endpoint(monkeypatch):
    pytest.importorskip("google.genai")
    assert engine.google_search_tool().google_search is not None
    assert engine.thinking_config("gemini-3.8-flash").thinking_level.value == "LOW"
    assert engine.thinking_config("gemini-2.5-flash") is None
    config = engine.GenaiModel("sys").config({"max_output_tokens": 10}, [engine.google_search_tool()])
    assert config.system_instruction == "sys" and config.max_output_tokens == 10 and config.tools
    monkeypatch.setattr(engine, "PROJECT_ID", "p")
    monkeypatch.setattr(engine, "_client", None)
    import google.auth
    monkeypatch.setattr(google.auth, "default", lambda *a, **k: (None, "p"))
    try:
        url = engine.get_client()._api_client._http_options.base_url
    finally:
        engine._client = None
    assert url.startswith("https://aiplatform.googleapis.com")


class RouterModel(AgentModel):
    def __init__(self, route=None, router_fail=False):
        super().__init__()
        self.route, self.router_fail = route, router_fail

    def generate_content(self, contents, stream=False, generation_config=None, tools=None):
        if generation_config and generation_config.get("response_mime_type") == "application/json":
            self.calls.append({"contents": contents, "stream": stream, "config": generation_config, "tools": tools, "router": True})
            if self.router_fail:
                raise RuntimeError("router down")
            return SimpleNamespace(text=json.dumps({"route": self.route}))
        return super().generate_content(contents, stream, generation_config, tools)


def use(monkeypatch, m):
    monkeypatch.setattr(engine, "get_model", lambda system=engine.SYSTEM_PROMPT: m)
    return m


def post(text, history=()):
    messages = [*history, {"role": "user", "text": text}]
    return events(engine.app.test_client().post("/api/chat", json={**BODY, "messages": messages}))


@pytest.mark.parametrize("text", ["hi", "Hello!", "thanks", "kaise ho?", "good morning Dua"])
def test_greeting_gets_one_direct_reply_without_search(model, text):
    evs = post(text)
    assert len(model.calls) == 1 and model.calls[0]["tools"] is None and model.calls[0]["stream"] is True
    kinds = [e["type"] for e in evs]
    assert "thinking" not in kinds and "sources" not in kinds and kinds[-1] == "done"
    assert "Sources:" not in "".join(e.get("delta", "") for e in evs if e["type"] == "text")


def test_direct_reply_keeps_multi_turn_history(model):
    post("thanks", history=[{"role": "user", "text": "My name is Fareed"}, {"role": "model", "text": "Hi Fareed!"}])
    contents = model.calls[0]["contents"]
    assert [c["role"] for c in contents] == ["user", "model", "user"]
    assert contents[0]["parts"][0][1] == "My name is Fareed"


def test_task_words_run_full_pipeline_with_search(model):
    post("Dua AI ke liye latest AI tools aur feature ideas batao")
    assert [c["tools"] for c in model.calls] == [None, ["GOOGLE_SEARCH"], None]


def test_router_call_is_cheap_and_decides(monkeypatch, model):
    m = use(monkeypatch, RouterModel(route="chat"))
    post("Can you explain what a neural network is in simple words?")
    router = m.calls[0]
    assert router["router"] and router["tools"] is None
    assert router["config"]["max_output_tokens"] <= 256 and router["config"]["thinking_level"] == "low"
    assert len(m.calls) == 2 and all(c["tools"] is None for c in m.calls)

    m = use(monkeypatch, RouterModel(route="agent"))
    post("Can you go deep on how other assistant apps handle offline mode these days?")
    assert [c["tools"] for c in m.calls] == [None, None, ["GOOGLE_SEARCH"], None]


def test_router_failure_falls_back_safely(monkeypatch, model):
    m = use(monkeypatch, RouterModel(router_fail=True))
    evs = post("What is two plus two")
    assert [c["tools"] for c in m.calls[1:]] == [None] and evs[-1]["type"] == "done"
    m = use(monkeypatch, RouterModel(router_fail=True))
    evs = post("Please go through how other assistant apps handle offline mode and memory these days for me")
    assert ["GOOGLE_SEARCH"] in [c["tools"] for c in m.calls] and evs[-1]["type"] == "done"


def test_router_thinking_level_override():
    pytest.importorskip("google.genai")
    config = engine.GenaiModel("sys").config({"max_output_tokens": 5, "thinking_level": "low"})
    if engine.MODEL_NAME.startswith("gemini-3"):
        assert config.thinking_config.thinking_level.value == "LOW"


class TitleModel:
    def __init__(self, text='{"title": "  \\"Kotlin Compose Dark Mode Help.\\" "}', fail=False):
        self.text, self.fail, self.calls = text, fail, []

    def generate_content(self, contents, stream=False, generation_config=None, tools=None):
        self.calls.append({"contents": contents, "config": generation_config, "tools": tools})
        if self.fail:
            raise RuntimeError("secret detail")
        return SimpleNamespace(text=self.text)


def test_title_is_cheap_clean_and_owner_only(monkeypatch):
    m = TitleModel()
    monkeypatch.setattr(engine, "get_model", lambda system=engine.SYSTEM_PROMPT: m)
    monkeypatch.delenv("DUA_APP_TOKEN", raising=False)
    monkeypatch.setattr(engine, "verify_owner", lambda authorization: "verified-owner")
    r = engine.app.test_client().post("/api/title", json={"user": "dark mode kaise add karun?", "reply": "Use MaterialTheme..."})
    assert r.status_code == 200 and r.get_json() == {"title": "Kotlin Compose Dark Mode Help"}
    cfg = m.calls[0]["config"]
    assert cfg["max_output_tokens"] <= 256 and cfg["thinking_level"] == "low" and m.calls[0]["tools"] is None
    assert engine.app.test_client().post("/api/title", json={}).status_code == 400


def test_title_failure_is_generic(monkeypatch):
    monkeypatch.setattr(engine, "get_model", lambda system=engine.SYSTEM_PROMPT: TitleModel(fail=True))
    monkeypatch.setattr(engine, "verify_owner", lambda authorization: "verified-owner")
    r = engine.app.test_client().post("/api/title", json={"user": "hi"})
    assert r.status_code == 502 and "secret" not in r.get_data(as_text=True)


def test_title_requires_owner(monkeypatch):
    calls = []
    monkeypatch.setattr(engine, "get_model", lambda system=engine.SYSTEM_PROMPT: calls.append(1))
    monkeypatch.setattr(engine, "owner_access_error", lambda: (engine.jsonify({"error": "Sign in"}), 401))
    assert engine.app.test_client().post("/api/title", json={"user": "hi"}).status_code == 401 and calls == []


def test_clean_title_limits():
    assert engine.clean_title("one two three four five six seven eight nine ten") == "one two three four five six seven eight"
    assert len(engine.clean_title("x" * 200)) <= 60
    assert engine.clean_title("**Hello world!**") == "Hello world!"


def test_agent_emits_structured_progress_in_order(model):
    evs = events(engine.app.test_client().post("/api/chat", json=BODY))
    steps = [(e["step"], e["label"], e["state"]) for e in evs if e["type"] == "progress"]
    assert [s[0] for s in steps] == ["plan", "search", "answer"]
    assert steps[0][1] == "Planning\u2026" and steps[1][1] == "Searching the web\u2026"
    assert steps[2][1] == "Reading 2 sources\u2026" and all(s[2] == "active" for s in steps)
    kinds = [e["type"] for e in evs]
    # Backward compatible: the raw thinking text is still sent, and progress comes before the answer text.
    assert "thinking" in kinds and kinds.index("progress") < kinds.index("text")


def test_casual_agent_message_has_no_progress(model):
    body = dict(BODY, messages=[{"role": "user", "text": "hi"}])
    evs = events(engine.app.test_client().post("/api/chat", json=body))
    assert not any(e["type"] == "progress" for e in evs)
