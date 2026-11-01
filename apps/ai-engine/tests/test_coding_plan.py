"""Request-local planning validation and both Agent tool paths, without providers."""
import asyncio
import os
import sys
from types import SimpleNamespace

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import dua_engine.agent.agent_adk as agent_adk
import dua_engine.agent.agent_tools as agent_tools
import dua_engine.coding.coding_agent as coding_agent
import dua_engine.coding.coding_plan as coding_plan


def args(status="in_progress"):
    return {"steps": [{"step": "Read existing code", "status": status},
                      {"step": "Prepare changes", "status": "pending"}]}


@pytest.mark.parametrize("bad", [
    None, {}, {"steps": []}, {"steps": args()["steps"] * 5},
    {"steps": [{"step": "", "status": "pending"}]},
    {"steps": [{"step": "x" * 141, "status": "pending"}]},
    {"steps": [{"step": "bad\nstep", "status": "pending"}]},
    {"steps": [{"step": "Read", "status": "verified"}]},
    {"steps": [{"step": "Read", "status": "pending", "approved": True}]},
    {"steps": [{"step": " Read ", "status": "completed"}, {"step": "read", "status": "pending"}]},
    {"steps": [{"step": "A", "status": "in_progress"}, {"step": "B", "status": "in_progress"}]},
    dict(args(), explanation="x" * 501),
])
def test_invalid_plan_does_not_consume_revision(bad):
    plan = coding_plan.Plan()
    with pytest.raises(coding_agent.CodingError):
        plan.update(bad)
    assert plan.revision == 0


def test_revisions_are_server_selected_and_bounded():
    plan = coding_plan.Plan()
    for revision in range(1, coding_plan.MAX_UPDATES + 1):
        result = plan.update(dict(args(), revision=999))
        assert result["plan"]["revision"] == revision
    with pytest.raises(coding_agent.CodingError):
        plan.update(args())
    assert plan.revision == coding_plan.MAX_UPDATES


def test_plan_tool_needs_no_github_read_and_emits_redacted_coding_event(monkeypatch):
    events = []
    monkeypatch.setenv("GITHUB_TOKEN", "private-credential-value")
    github = SimpleNamespace(repo="KRFbrothers/Dua-ai")  # No request/read methods.
    agent = agent_tools.AgentRun({"enqueue": lambda _, event: events.append(event) or True})
    agent.coding = coding_agent.CodingSession(github, lambda _: True)
    result = agent.run_tool_timed("update_plan", dict(args(), explanation="private-credential-value"))
    assert agent.coding.workspace is None and agent.pending == []
    assert result["plan"]["revision"] == 1
    assert events == [{"type": "coding", "plan": dict(result["plan"], explanation="[redacted secret]")}]
    assert agent.run_tool_timed("update_plan", {"steps": []}).get("error")
    assert len(events) == 1 and agent.coding.plan.revision == 1


def test_adk_parallel_arrays_keep_every_step_or_refuse_mismatch():
    calls = []

    async def call(name, values):
        calls.append((name, values))
        return {"ok": True}

    update = agent_adk.make_tools(["update_plan"], call)[0]
    assert asyncio.run(update(["Read", "Edit"], ["in_progress"])).get("error")
    assert calls == []
    assert asyncio.run(update(["Read", "Edit"], ["completed", "in_progress"], "Next")) == {"ok": True}
    assert calls == [("update_plan", {"steps": [{"step": "Read", "status": "completed"},
                                               {"step": "Edit", "status": "in_progress"}], "explanation": "Next"})]
