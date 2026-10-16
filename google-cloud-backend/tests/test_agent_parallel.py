import json
import os
import sys
import threading
import time
from types import SimpleNamespace

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import agent_parallel as ap  # noqa: E402
import agent_tools  # noqa: E402


def text_of(response):
    return response.text


class Merger:
    def __init__(self, plan, merge_text="MERGED", fail_plan=False):
        self.plan, self.merge_text, self.fail_plan, self.calls = plan, merge_text, fail_plan, []

    def generate_content(self, contents, generation_config=None, **kw):
        self.calls.append(contents)
        if contents.startswith("You are the planner"):
            if self.fail_plan:
                raise RuntimeError("boom")
            return SimpleNamespace(text=json.dumps(self.plan))
        return SimpleNamespace(text=self.merge_text)


def base_env(events):
    return {"progress": lambda step, label, state="active": events.append((step, label, state)),
            "thinking": lambda d: events.append(("thinking", d, "")), "chunk_text": text_of,
            "request_text": "do A and B", "github": "GH", "enqueue": lambda *a: True}


PLAN = {"split": True, "subtasks": [{"title": "Research", "kind": "research", "task": "find X"},
                                    {"title": "Code", "kind": "code", "task": "change Y"},
                                    {"title": "Extra", "kind": "code", "task": "change Z"},
                                    {"title": "Too many", "kind": "notes", "task": "ignored"}]}


def test_parse_plan_caps_and_single_code():
    subs = ap.parse_plan(json.dumps(PLAN))
    assert len(subs) == 3 and [s["kind"] for s in subs] == ["research", "code", "general"]
    assert ap.parse_plan('{"split": false}') == [] and ap.parse_plan("nope") == []
    assert ap.parse_plan(json.dumps({"split": True, "subtasks": [{"task": "only one"}]})) == []


def test_runs_subtasks_in_parallel_renumbers_sources_and_merges(monkeypatch):
    seen, barrier = [], threading.Barrier(3, timeout=5)

    def fake_run(env, history, tools, tool_response, max_steps=None):
        barrier.wait()  # proves all three run at the same time
        seen.append((env["request_text"], env["github"], tools, max_steps))
        env["progress"]("search", "Searching the web\u2026")
        agent = agent_tools.AgentRun(env)
        url = "https://a.test" if env["request_text"] == "find X" else "https://b.test/" + env["request_text"]
        agent.sources = [{"id": 1, "title": "t", "url": url}]
        agent.steps = ["- Step 1: google_search\n"]
        return f"result for {env['request_text']} [1]", agent
    monkeypatch.setattr(agent_tools, "run", fake_run)
    events, merger = [], Merger(PLAN)
    text, merged = ap.run(base_env(events), lambda sub: "FLASH", merger, lambda sub: [sub["task"]],
                          lambda gh: ["TOOLS", gh], None, log=lambda m: None)
    assert text == "MERGED"
    assert sorted(s[0] for s in seen) == ["change Y", "change Z", "find X"]
    github = {s[0]: s[1] for s in seen}
    assert github == {"find X": None, "change Y": "GH", "change Z": None}
    assert all(s[3] == ap.SUB_STEPS for s in seen)
    assert [s["id"] for s in merged.sources] == [1, 2, 3]
    merge_prompt = merger.calls[-1]
    for i, task in enumerate(["find X", "change Y", "change Z"], 1):
        line = f"result for {task} [{[s['id'] for s in merged.sources if (s['url'] == 'https://a.test') == (task == 'find X') and (task == 'find X' or s['url'].endswith(task))][0]}]"
        assert line in merge_prompt
    chips = {e[0] for e in events if e[2] in ("active", "done")}
    assert {"sub_a", "sub_b", "sub_c", "merge"} <= chips
    assert ("sub_a", "Research: Searching the web\u2026", "active") in events
    assert len(merged.steps) == 3


def test_no_split_or_plan_failure_falls_back():
    assert ap.run(base_env([]), None, Merger({"split": False}), None, None, None, log=lambda m: None) is None
    assert ap.run(base_env([]), None, Merger(PLAN, fail_plan=True), None, None, None, log=lambda m: None) is None


def test_failed_and_slow_subtasks_are_reported_not_hanging(monkeypatch):
    monkeypatch.setattr(ap, "PARALLEL_SECONDS", 0.5)
    release = threading.Event()

    def fake_run(env, history, tools, tool_response, max_steps=None):
        if env["request_text"] == "find X":
            raise RuntimeError("bad")
        if env["request_text"] == "change Y":
            release.wait(5)
        return "ok", agent_tools.AgentRun(env)
    monkeypatch.setattr(agent_tools, "run", fake_run)
    merger = Merger(PLAN)
    started = time.monotonic()
    text, merged = ap.run(base_env([]), lambda s: None, merger, lambda s: [], lambda g: [], None, log=lambda m: None)
    release.set()
    assert time.monotonic() - started < 3 and text == "MERGED"
    assert "This part failed (RuntimeError)" in merger.calls[-1] and "did not finish" in merger.calls[-1]


def test_merge_failure_returns_helper_results(monkeypatch):
    monkeypatch.setattr(agent_tools, "run", lambda env, *a, **k: ("part done", agent_tools.AgentRun(env)))

    class BadMerge(Merger):
        def generate_content(self, contents, **kw):
            if contents.startswith("You are the planner"):
                return super().generate_content(contents, **kw)
            raise RuntimeError("x")
    text, _ = ap.run(base_env([]), lambda s: None, BadMerge(PLAN), lambda s: [], lambda g: [], None, log=lambda m: None)
    assert "part done" in text and "## Research" in text


def test_flag_default_off(monkeypatch):
    monkeypatch.delenv("AGENT_PARALLEL", raising=False)
    assert not ap.enabled()
    monkeypatch.setenv("AGENT_PARALLEL", "1")
    assert ap.enabled()
