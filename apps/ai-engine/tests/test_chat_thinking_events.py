"""Plain (non-agent) streaming chat forwards Gemini thought summaries as "thinking" SSE events."""
import json
from pathlib import Path
import sys
from types import SimpleNamespace

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import app as engine


def part(text, thought=False):
    return SimpleNamespace(text=text, thought=thought)


def chunk(*parts):
    answer = "".join(p.text for p in parts if not p.thought)
    return SimpleNamespace(text=answer, candidates=[SimpleNamespace(content=SimpleNamespace(parts=list(parts)),
                                                                    finish_reason=None, grounding_metadata=None)])


def run(monkeypatch, flag="1", stream=True):
    seen = []
    def generate(contents, stream=False, generation_config=None, tools=None):
        seen.append(generation_config or {})
        if not stream:
            return SimpleNamespace(text="answer", candidates=[])
        return iter([chunk(part("Weighing options", True)), chunk(part(" carefully", True), part("Hi")), chunk(part(" there"))])
    monkeypatch.setattr(engine, "CHAT_THINKING_EVENTS", flag)
    monkeypatch.setattr(engine, "verify_owner", lambda _: "owner")
    monkeypatch.delenv("DUA_APP_TOKEN", raising=False)
    monkeypatch.setattr(engine, "make_part", lambda **kw: kw)
    monkeypatch.setattr(engine, "make_content", lambda role, parts: (role, parts))
    monkeypatch.setattr(engine, "get_model", lambda system: SimpleNamespace(generate_content=generate))
    response = engine.app.test_client().post("/api/chat", json={"mode": "reasoning", "stream": stream,
                                                                "messages": [{"role": "user", "text": "hi"}]})
    body = response.get_data(as_text=True)
    return seen, [json.loads(l[6:]) for l in body.splitlines() if l.startswith("data: ")], response


def test_thinking_events_before_answer(monkeypatch):
    seen, events, _ = run(monkeypatch)
    assert seen[0].get("include_thoughts") is True
    assert events == [{"type": "thinking", "delta": "Weighing options"}, {"type": "thinking", "delta": " carefully"},
                      {"type": "text", "delta": "Hi"}, {"type": "text", "delta": " there"}, {"type": "done"}]


def test_rollback_flag_hides_thinking(monkeypatch):
    seen, events, _ = run(monkeypatch, flag="0")
    assert "include_thoughts" not in seen[0]
    assert [e["type"] for e in events] == ["text", "text", "done"]


def test_non_stream_never_asks_for_thoughts(monkeypatch):
    seen, _, response = run(monkeypatch, stream=False)
    assert "include_thoughts" not in seen[0] and response.get_json() == {"text": "answer"}


def test_chunk_thoughts_ignores_answer_and_bad_chunks():
    assert engine.chunk_thoughts(chunk(part("a", True), part("b"))) == "a"
    assert engine.chunk_thoughts(SimpleNamespace(candidates=[])) == ""
    assert engine.chunk_thoughts(None) == ""


def test_sdk_config_sets_include_thoughts():
    pytest.importorskip("google.genai.types")
    model = engine.GenaiModel("sys", "gemini-3.8-flash")
    cfg = model.config({"thinking_level": "low", "include_thoughts": True}).thinking_config
    assert cfg.include_thoughts is True and cfg.thinking_level == "LOW"
    assert engine.GenaiModel("sys", "gemini-2.5-flash").config({"include_thoughts": True}).thinking_config is None
