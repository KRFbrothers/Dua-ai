"""Audit fixes: hide unconfigured runner/checkpoint tools, ADK actions helper + documents, daily token cap."""
import asyncio
import os
import sys
from types import SimpleNamespace

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import dua_engine.agent.agent_adk as agent_adk  # noqa: E402
import dua_engine.agent.agent_tools as agent_tools  # noqa: E402
import app as engine  # noqa: E402
import dua_engine.coding.coding_checkpoints as coding_checkpoints  # noqa: E402
import dua_engine.coding.coding_terminal as coding_terminal  # noqa: E402

RUNNER = set(agent_tools.RUNNER_TOOLS)
CHECKPOINT = set(agent_tools.CHECKPOINT_TOOLS)


def names(decls):
    return {d["name"] for d in decls}


def configure(monkeypatch, runner, checkpoints):
    monkeypatch.setattr(coding_terminal, "status", lambda: {"configured": runner})
    monkeypatch.setattr(coding_checkpoints, "enabled", lambda: checkpoints)


async def call(name, args):
    return {"name": name, "args": args}


# ------------------------------------------------------------------ 1. tool hiding
def test_declarations_hide_unconfigured_runner_and_checkpoints(monkeypatch):
    configure(monkeypatch, False, False)
    got = names(agent_tools.declarations(True))
    assert not (got & RUNNER) and not (got & CHECKPOINT)
    assert {"read_file", "edit_file", "workspace_diff"} <= got


def test_declarations_offer_configured_tools(monkeypatch):
    configure(monkeypatch, True, True)
    got = names(agent_tools.declarations(True))
    assert RUNNER <= got and CHECKPOINT <= got
    configure(monkeypatch, True, False)
    got = names(agent_tools.declarations(True))
    assert RUNNER <= got and not (got & CHECKPOINT)


def test_status_error_hides_runner(monkeypatch):
    def boom():
        raise RuntimeError("bad config")
    monkeypatch.setattr(coding_terminal, "status", boom)
    monkeypatch.setattr(coding_checkpoints, "enabled", lambda: True)
    got = names(agent_tools.declarations(True))
    assert not (got & RUNNER) and CHECKPOINT <= got


def test_adk_code_helper_filters_tools(monkeypatch):
    code = agent_adk.SPECIALISTS["code"][1]
    configure(monkeypatch, False, False)
    got = {f.__name__ for f in agent_adk.make_tools(code, call)}
    assert not (got & RUNNER) and not (got & CHECKPOINT) and "edit_file" in got
    configure(monkeypatch, True, True)
    got = {f.__name__ for f in agent_adk.make_tools(code, call)}
    assert RUNNER <= got and CHECKPOINT <= got


# ------------------------------------------------------------------ 2. helpers + documents
def test_actions_helper_has_image_and_memory():
    title, tools, _ = agent_adk.SPECIALISTS["actions"]
    assert {"generate_image", "remember", "recall"} <= set(tools)
    fns = {f.__name__: f for f in agent_adk.make_tools(tools, call)}
    assert asyncio.run(fns["generate_image"]("a cat", "16:9")) == {
        "name": "generate_image", "args": {"prompt": "a cat", "aspect_ratio": "16:9"}}
    assert asyncio.run(fns["remember"]("likes chai"))["args"] == {"fact": "likes chai"}


def test_parse_plan_accepts_actions_kind():
    plan = '{"split": true, "subtasks": [{"kind": "research", "task": "news"}, {"kind": "actions", "task": "draw a cat"}]}'
    subs = agent_adk.parse_plan(plan, ["research", "actions"])
    assert [s["kind"] for s in subs] == ["research", "actions"]


def test_tool_loop_declares_search_documents_only_when_enabled(monkeypatch):
    configure(monkeypatch, False, False)
    assert "search_documents" not in names(agent_tools.declarations(False))
    assert "search_documents" in names(agent_tools.declarations(False, documents=True))


# ------------------------------------------------------------------ 3. daily token cap
def fake_inner(tokens):
    def factory(messages, attachments, system, owner=None, cancelled=None, observe=False):
        def produce(enqueue):
            produce.trace = SimpleNamespace(calls=[{"total": tokens}]) if observe else None
            enqueue("chunk", engine._Text("hi"))
            enqueue("done")
        produce.trace = None
        factory.observe = observe
        return produce
    return factory


def run(owner):
    out = []
    engine.agent_producer([], [], "sys", owner)(lambda kind, value=None: out.append((kind, value)) or True)
    return out


def test_token_cap_off_by_default(monkeypatch):
    monkeypatch.delenv("AGENT_DAILY_TOKEN_LIMIT", raising=False)
    monkeypatch.setattr(engine, "_TOKEN_USAGE", {})
    inner = fake_inner(10 ** 9)
    monkeypatch.setattr(engine, "_agent_producer", inner)
    for _ in range(3):
        assert run({"id": "o1"})[0][1].text == "hi"
    assert inner.observe is False and engine._TOKEN_USAGE == {}


def test_token_cap_rejects_after_limit(monkeypatch):
    monkeypatch.setenv("AGENT_DAILY_TOKEN_LIMIT", "1000")
    monkeypatch.setattr(engine, "_TOKEN_USAGE", {})
    monkeypatch.setattr(engine, "_agent_producer", fake_inner(600))
    assert run({"id": "o1"})[0][1].text == "hi"
    assert run({"id": "o1"})[0][1].text == "hi"  # 600 < 1000 still allowed
    blocked = run({"id": "o1"})
    assert blocked[0][1].text == engine.TOKEN_LIMIT_MESSAGE and blocked[-1][0] == "done"
    assert "limit" in engine.TOKEN_LIMIT_MESSAGE
    assert run({"id": "o2"})[0][1].text == "hi"  # per owner


def test_token_cap_invalid_value_is_off(monkeypatch):
    monkeypatch.setenv("AGENT_DAILY_TOKEN_LIMIT", "lots")
    assert engine.daily_token_limit() == 0
    monkeypatch.setenv("AGENT_DAILY_TOKEN_LIMIT", "-5")
    assert engine.daily_token_limit() == 0
