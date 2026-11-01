import json
import os
import sys
import time
from types import SimpleNamespace

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import dua_engine.agent.agent_verify as agent_verify  # noqa: E402


class Model:
    def __init__(self, text=None, hang=False, fail=False):
        self.text, self.hang, self.fail, self.calls = text, hang, fail, []

    def generate_content(self, prompt, generation_config=None, **kw):
        self.calls.append((prompt, generation_config))
        if self.fail:
            raise RuntimeError("x")
        if self.hang:
            time.sleep(3)
        return SimpleNamespace(text=self.text)


def run(critic, reviser, draft="Delhi is 30C [1].", log=None):
    events, logs = [], []
    text = agent_verify.check("weather in Delhi and Mumbai?", draft, ["google_search: Delhi 31C"],
                              [{"id": 1, "title": "IMD", "url": "https://imd.test"}], critic, reviser, lambda r: r.text,
                              lambda *a: events.append(a), log=(log or logs.append))
    return text, events, logs


def test_ok_keeps_draft_and_uses_one_cheap_call():
    critic, reviser = Model(json.dumps({"ok": True, "issues": []})), Model("never")
    text, events, logs = run(critic, reviser)
    assert text == "Delhi is 30C [1]." and not reviser.calls and len(critic.calls) == 1
    assert critic.calls[0][1]["thinking_level"] == "low" and "Delhi 31C" in critic.calls[0][0]
    assert ("verify", "Checking answer\u2026") in events and ("verify", "Answer checked", "done") in events
    assert "verify: ok" in logs[-1]


def test_issues_trigger_exactly_one_revision():
    critic = Model(json.dumps({"ok": False, "issues": ["Mumbai not answered", "30C vs 31C"]}))
    reviser = Model("Delhi is 31C [1]. Mumbai: no data found.")
    text, events, logs = run(critic, reviser)
    assert text.startswith("Delhi is 31C") and len(reviser.calls) == 1 and "Mumbai not answered" in reviser.calls[0][0]
    assert any("2 issue(s)" in line for line in logs)


def test_failures_and_timeouts_keep_draft(monkeypatch):
    assert run(Model(fail=True), Model("x"))[0] == "Delhi is 30C [1]."
    assert run(Model("not json"), Model("x"))[0] == "Delhi is 30C [1]."
    monkeypatch.setattr(agent_verify, "REVISE_SECONDS", 0.3)
    critic = Model(json.dumps({"ok": False, "issues": ["x"]}))
    started = time.monotonic()
    assert run(critic, Model("late", hang=True))[0] == "Delhi is 30C [1]."
    assert time.monotonic() - started < 2


def test_revision_that_drops_pr_link_is_rejected():
    draft = "Done: https://github.com/KRFbrothers/Dua-ai/pull/9"
    text, *_ = run(Model(json.dumps({"ok": False, "issues": ["x"]})), Model("Done, PR opened."), draft=draft)
    assert text == draft


def test_tool_loop_runs_verify_only_when_enabled(monkeypatch):
    import dua_engine.agent.agent_notes as agent_notes
    import dua_engine.agent.agent_tools as agent_tools
    import app as engine
    monkeypatch.delenv("GITHUB_TOKEN", raising=False)
    monkeypatch.setenv("AGENT_ENGINE", "loop")
    monkeypatch.setattr(agent_notes, "for_owner", lambda owner, opener=None: None)
    monkeypatch.setattr(engine, "get_agent_model", lambda system: "ARGON")
    monkeypatch.setattr(engine, "get_model", lambda system=None: "FLASH")
    monkeypatch.setattr(engine, "build_contents", lambda m, a: [])
    monkeypatch.setattr(agent_tools, "default_tools", lambda *a, **k: [])
    monkeypatch.setattr(agent_tools, "run", lambda *a, **k: ("draft", agent_tools.AgentRun({})))
    seen = []
    monkeypatch.setattr(agent_verify, "check", lambda req, draft, ev, src, critic, reviser, *a: seen.append((critic, reviser)) or "fixed")

    def chunks(flag):
        monkeypatch.setenv("AGENT_VERIFY", flag)
        events = []
        engine.tool_loop([{"role": "user", "text": "q"}], [], "sys", lambda kind, ev=None: events.append((kind, ev)) or True, {})
        return [ev.text for kind, ev in events if kind == "chunk"]
    assert chunks("0") == ["draft"] and not seen
    assert chunks("1") == ["fixed"] and seen == [("FLASH", "ARGON")]
