"""ADK parallel mode. Fake-ADK tests always run (CI has no ADK); a real-ADK test runs when google-adk is installed,
driving the real ParallelAgent/SequentialAgent with a scripted offline model (no network)."""
import asyncio
import json
import os
import sys
import threading
import time
from types import SimpleNamespace

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import agent_adk  # noqa: E402
import agent_tools  # noqa: E402

PLAN = {"split": True, "subtasks": [{"kind": "research", "task": "find gemini news"},
                                    {"kind": "notes", "task": "check reply preference"},
                                    {"kind": "research", "task": "duplicate kind ignored"}]}


class FakeNotes:
    def search(self, query):
        return [{"id": "n1", "note": "User likes short Hinglish answers", "kind": "preference"}]


def make_env(events, github=None, notes=True):
    def search_tool():
        return "SEARCH"
    env = {"progress": lambda step, label, state="active": events.append((step, label, state)),
           "thinking": lambda d: events.append(("thinking", d, "")), "enqueue": lambda *a: True,
           "chunk_text": lambda r: r.text, "request_text": "news and my preference", "github": github,
           "notes": FakeNotes() if notes else None, "memory": "", "search_tool": search_tool,
           "contents_for": lambda text: [text],
           "grounding_sources": lambda r: [{"title": "Gemini blog", "url": "https://blog.test/gemini"}]}

    class SubModel:
        def generate_content(self, contents, **kw):
            return SimpleNamespace(text="Gemini 4 launched")
    env["model"] = SubModel()
    return env


# ------------------------------------------------------------------ fake ADK (mirrors the parts we use)
class FakeAgent:
    def __init__(self, **kw):
        self.__dict__.update(kw)


def fake_adk(plan_text, scripts, merge_text="MERGED [1]", fail=None, slow=None):
    """scripts: kind -> list of (tool_name, args) then a final text."""
    class Runner:
        def __init__(self, agent, app_name):
            self.agent = agent
            self.session_service = SimpleNamespace(create_session_sync=lambda **k: SimpleNamespace(id="s"))

        def run(self, user_id, session_id, new_message, run_config):
            if fail:
                raise fail
            agent = self.agent
            if agent.name == "controller":
                assert "controller" in agent.instruction(None)
                return [SimpleNamespace(author="controller", content=SimpleNamespace(parts=[SimpleNamespace(text=plan_text)]))]
            parallel, merger = agent.sub_agents
            state = {}

            async def helper(sub):
                kind = sub.name.replace("_agent", "")
                tools = {t.__name__: t for t in sub.tools}
                for name, args in scripts[kind][:-1]:
                    await tools[name](**args)
                if slow == kind:
                    await asyncio.sleep(5)
                state[sub.output_key] = scripts[kind][-1]

            async def all_helpers():
                await asyncio.gather(*(helper(s) for s in parallel.sub_agents))
            asyncio.run(all_helpers())
            prompt = merger.instruction(SimpleNamespace(state=state))
            Runner.merge_prompt = prompt
            return [SimpleNamespace(author="merger", content=SimpleNamespace(parts=[SimpleNamespace(text=merge_text)]))]

    return SimpleNamespace(LlmAgent=FakeAgent, ParallelAgent=FakeAgent, SequentialAgent=FakeAgent,
                           RunConfig=lambda **k: k, InMemoryRunner=Runner,
                           types=SimpleNamespace(Content=lambda **k: k, Part=lambda **k: k)), Runner


def cfg(model, tokens, json_out=False):
    return {"model": model, "tokens": tokens, "json": json_out}


def test_flag(monkeypatch):
    monkeypatch.delenv("AGENT_ENGINE", raising=False)
    assert not agent_adk.enabled()
    monkeypatch.setenv("AGENT_ENGINE", "adk")
    assert agent_adk.enabled()


def test_parse_plan_unique_allowed_kinds():
    subs = agent_adk.parse_plan(json.dumps(PLAN), ["research", "notes"])
    assert [s["kind"] for s in subs] == ["research", "notes"]
    assert agent_adk.parse_plan(json.dumps(PLAN), ["research"]) == []  # only one valid part
    assert agent_adk.parse_plan('{"split": false}', ["research", "notes"]) == []


def test_parallel_run_streams_chips_sources_and_merges():
    events = []
    adk, runner = fake_adk(json.dumps(PLAN), {
        "research": [("google_search", {"query": "gemini news"}), "Gemini 4 launched [1]"],
        "notes": [("search_notes", {"query": "preference"}), "User likes short Hinglish answers"]})
    text, result = agent_adk.run(make_env(events), "news and my preference", "flash", "argon", cfg, adk=adk,
                                 log=lambda m: None)
    assert text == "MERGED [1]"
    assert result.sources == [{"title": "Gemini blog", "url": "https://blog.test/gemini", "id": 1}]
    assert ("sub_a", "Research: Searching the web\u2026", "active") in events
    assert ("sub_b", "Notes: Checking notes\u2026", "active") in events
    assert ("sub_a", "Research: done", "done") in events and ("merge", "Combining results\u2026", "active") in events
    assert "Gemini 4 launched [1]" in runner.merge_prompt and "Hinglish" in runner.merge_prompt
    assert any(e[0] == "thinking" and e[1].startswith("[A] - google_search") for e in events)
    assert len(result.steps) == 2


def test_no_split_returns_none_for_old_loop():
    adk, _ = fake_adk('{"split": false}', {})
    assert agent_adk.run(make_env([]), "hi", "f", "a", cfg, adk=adk, log=lambda m: None) is None


def test_adk_error_before_tools_falls_back():
    adk, _ = fake_adk("{}", {}, fail=RuntimeError("adk broke"))
    logs = []
    assert agent_adk.run(make_env([]), "x", "f", "a", cfg, adk=adk, log=logs.append) is None
    assert "using the normal loop" in logs[-1]


def test_missing_adk_falls_back(monkeypatch):
    monkeypatch.setattr(agent_adk, "load_adk", lambda: (_ for _ in ()).throw(ImportError("no adk")))
    assert agent_adk.run(make_env([]), "x", "f", "a", cfg, log=lambda m: None) is None


def test_total_deadline_stops_run(monkeypatch):
    monkeypatch.setattr(agent_adk, "TOTAL_SECONDS", 0.5)
    adk, _ = fake_adk(json.dumps(PLAN), {"research": ["r"], "notes": ["n"]}, slow="notes")
    started = time.monotonic()
    text, _ = agent_adk.run(make_env([]), "x", "f", "a", cfg, adk=adk, log=lambda m: None)
    assert time.monotonic() - started < 3 and "time limit" in text


def test_code_helper_opens_one_pr_and_link_is_kept(monkeypatch):
    import coding_agent
    calls = []
    monkeypatch.setattr(coding_agent, "finish", lambda gh, emit, args, req, **kw: calls.append(args) or
                        "Pull request ready: **#9** \u2014 https://github.com/o/r/pull/9")
    monkeypatch.setattr(coding_agent, "CodingSession", lambda *a: None)
    plan = {"split": True, "subtasks": [{"kind": "research", "task": "r"}, {"kind": "code", "task": "c"}]}
    pc = ("propose_changes", {"title": "T", "description": "D", "paths": ["a.txt"], "contents": ["hi"]})
    adk, _ = fake_adk(json.dumps(plan), {"research": ["r"], "code": [pc, pc, "opened"]}, merge_text="All done.")
    text, _ = agent_adk.run(make_env([], github="GH"), "x", "f", "a", cfg, adk=adk, log=lambda m: None)
    assert len(calls) == 1 and calls[0]["files"] == [{"path": "a.txt", "content": "hi"}]
    assert "https://github.com/o/r/pull/9" in text


def test_tools_run_concurrently(monkeypatch):
    barrier = threading.Barrier(2, timeout=5)
    monkeypatch.setattr(agent_tools.AgentRun, "run_tool_timed", lambda self, name, args: barrier.wait() and {} or {})
    adk, _ = fake_adk(json.dumps(PLAN), {"research": [("calculator", {"expression": "1+1"}), "r"],
                                         "notes": [("search_notes", {"query": "q"}), "n"]})
    text, _ = agent_adk.run(make_env([]), "x", "f", "a", cfg, adk=adk, log=lambda m: None)
    assert text == "MERGED [1]"  # a blocking tool would deadlock the barrier and hit the 5 s timeout


def test_tool_loop_uses_adk_when_flag_on(monkeypatch):
    import app as engine
    import agent_notes
    monkeypatch.setenv("AGENT_ENGINE", "adk")
    monkeypatch.delenv("GITHUB_TOKEN", raising=False)
    monkeypatch.setattr(agent_notes, "for_owner", lambda owner, opener=None: None)
    monkeypatch.setattr(engine, "get_agent_model", lambda system: "ARGON")
    result = SimpleNamespace(sources=[{"id": 1, "title": "T", "url": "https://t.test"}], steps=[])
    monkeypatch.setattr(agent_adk, "run", lambda env, last, flash, agent, config: ("combined [1]", result))
    monkeypatch.setattr(agent_tools, "run", lambda *a, **k: (_ for _ in ()).throw(AssertionError("old loop used")))
    events = []
    engine.tool_loop([{"role": "user", "text": "research A and notes B"}], [], "sys",
                     lambda kind, ev=None: events.append((kind, ev)) or True, {})
    assert [ev.text for kind, ev in events if kind == "chunk"] == ["combined [1]"] and events[-1][0] == "done"


def test_tool_loop_falls_back_to_old_loop(monkeypatch):
    import app as engine
    import agent_notes
    monkeypatch.setenv("AGENT_ENGINE", "adk")
    monkeypatch.delenv("GITHUB_TOKEN", raising=False)
    monkeypatch.setattr(agent_notes, "for_owner", lambda owner, opener=None: None)
    monkeypatch.setattr(engine, "get_agent_model", lambda system: "ARGON")
    monkeypatch.setattr(agent_adk, "run", lambda *a: None)
    monkeypatch.setattr(agent_tools, "default_tools", lambda *a, **k: [])
    monkeypatch.setattr(engine, "build_contents", lambda m, a: [])
    monkeypatch.setattr(agent_tools, "run", lambda *a, **k: ("old loop answer", agent_tools.AgentRun({})))
    events = []
    engine.tool_loop([{"role": "user", "text": "x"}], [], "sys", lambda kind, ev=None: events.append((kind, ev)) or True, {})
    assert [ev.text for kind, ev in events if kind == "chunk"] == ["old loop answer"]


# ------------------------------------------------------------------ real ADK, scripted offline model
def test_real_adk_parallel_pipeline_offline():
    pytest.importorskip("google.adk")
    from google.adk.models import BaseLlm, LlmResponse
    from google.genai import types

    class Scripted(BaseLlm):
        async def generate_content_async(self, llm_request, stream=False):
            system = str(llm_request.config.system_instruction or "")
            responded = any(p.function_response for c in llm_request.contents for p in (c.parts or []))
            if "controller of the Dua Agent" in system:
                part = types.Part(text=json.dumps(PLAN))
            elif "You research" in system and not responded:
                part = types.Part(function_call=types.FunctionCall(name="google_search", args={"query": "gemini"}))
            elif "private notes" in system and "Your part" in system and not responded:
                part = types.Part(function_call=types.FunctionCall(name="search_notes", args={"query": "pref"}))
            elif "Helper results" in system:
                assert "Gemini 4 launched" in system or "found" in system
                part = types.Part(text="FINAL [1]")
            else:
                part = types.Part(text="found [1]")
            yield LlmResponse(content=types.Content(role="model", parts=[part]))

    events = []
    model = Scripted(model="scripted")
    text, result = agent_adk.run(make_env(events), "news and my preference", model, model,
                                 lambda m, t, json_out=False: types.GenerateContentConfig(max_output_tokens=t),
                                 log=lambda m: None)
    assert text == "FINAL [1]"
    assert result.sources and result.sources[0]["url"] == "https://blog.test/gemini"
    assert ("sub_a", "Research: Searching the web\u2026", "active") in events
    assert ("sub_b", "Notes: Checking notes\u2026", "active") in events


def test_parse_plan_markdown_wrapped_json():
    wrapped = "Here is the plan:\n```json\n" + json.dumps(PLAN) + "\n```\nLet me know if this works."
    subs = agent_adk.parse_plan(wrapped, ["research", "notes"])
    assert [s["kind"] for s in subs] == ["research", "notes"]


def test_final_text_extracts_thoughts_to_callback():
    thoughts = []
    events = [
        SimpleNamespace(
            author="controller",
            content=SimpleNamespace(
                parts=[
                    SimpleNamespace(thought=True, text="Analyzing user request structure..."),
                    SimpleNamespace(thought=False, text="Final text output")
                ]
            )
        )
    ]
    text = agent_adk.final_text(events, "controller", on_thought=thoughts.append)
    assert text == "Final text output"
    assert thoughts == ["Analyzing user request structure..."]


def test_merge_prompt_includes_artifact_guidance():
    assert "Artifacts Canvas" in agent_adk.MERGE_PROMPT
    assert "```html" in agent_adk.MERGE_PROMPT

