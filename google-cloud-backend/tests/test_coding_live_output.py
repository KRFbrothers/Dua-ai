"""Offline regression cases for cumulative command progress; no real runner."""
import os
import sys
import threading
from types import SimpleNamespace

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import agent_tools
import coding_agent
import coding_terminal
import coding_ui


def relay(monkeypatch):
    events = []
    clock = [0.0]
    monkeypatch.setattr(coding_ui.time, "monotonic", lambda: clock[0])
    agent = SimpleNamespace(stop_requested=lambda: False, emit=lambda event: events.append(event) or True)
    return coding_ui.CommandProgress(agent, "a" * 16), events, clock


def snapshot(output="", state="running"):
    return dict(output=output, state=state, profile="python", truncated=False)


def test_live_snapshots_hold_split_secrets_and_private_key_blocks(monkeypatch):
    monkeypatch.setenv("DUA_CODING_RUNNER_TOKEN", "dedicated-secret-value")
    progress, events, clock = relay(monkeypatch)
    for number, text in enumerate([
        "safe\ndedicated-sec", "safe\ndedicated-secret-value\n",
        "safe\n-----BEGIN PRIVATE KEY-----\nprivate-body",
        "safe\n-----BEGIN PRIVATE KEY-----\nprivate-body\n-----END PRIVATE KEY--",
        "safe\n-----BEGIN PRIVATE KEY-----\nprivate-body\n-----END PRIVATE KEY-----\n",
    ]):
        clock[0] = number * 2.0
        progress.update(snapshot(text))
    assert events[0]["terminal"]["output"] == "safe\n"
    assert all("dedicated-sec" not in str(e) and "private-body" not in str(e) for e in events)
    assert "[redacted secret]" in events[-1]["terminal"]["output"]


def test_live_updates_are_coalesced_and_close_stops_late_worker(monkeypatch):
    progress, events, clock = relay(monkeypatch)
    progress.update(snapshot("one\n", "queued"))
    progress.update(snapshot("one\n", "queued"))
    progress.update(snapshot("one\n", "running"))  # state change is immediate
    clock[0] = 0.2
    progress.update(snapshot("one\ntwo\n"))
    clock[0] = 1.1
    progress.update(snapshot("one\ntwo\n"))
    progress.close()
    clock[0] = 3.0
    progress.update(snapshot("late\n"))
    assert [e["terminal"]["state"] for e in events] == ["queued", "running", "running"]
    assert events[-1]["terminal"]["output"] == "one\ntwo\n"


def test_closing_progress_does_not_wait_for_worker_lock(monkeypatch):
    progress, _, _ = relay(monkeypatch)
    held, release = threading.Event(), threading.Event()

    def worker():
        with progress.lock:
            held.set()
            release.wait(2)

    thread = threading.Thread(target=worker)
    closer = threading.Thread(target=progress.close)
    thread.start()
    try:
        assert held.wait(1)
        closer.start()
        assert progress.closed.wait(0.5)
    finally:
        release.set()
        thread.join(2)
        if closer.ident is not None:
            closer.join(2)


@pytest.mark.parametrize("tamper", [None, "id", "base_sha", "profile", "image_digest", "output"])
def test_runner_progress_is_emitted_only_after_identity_checks(monkeypatch, tamper):
    calls, updates = [], []
    workspace = SimpleNamespace(lock=threading.RLock(), start=lambda: None, base_sha="b" * 40,
        instructions_seen=set(), instruction_paths=lambda path: [])
    monkeypatch.setattr(coding_terminal, "configuration", lambda: ("https://runner.example", "unused"))
    monkeypatch.setattr(coding_terminal.coding_snapshot, "from_workspace", lambda *_: (b"snapshot", "fingerprint"))
    monkeypatch.setattr(coding_terminal.time, "sleep", lambda _: None)

    class Client:
        def __init__(self, *args):
            self.payload = None

        def request(self, method, path, payload=None):
            calls.append(method)
            if method == "DELETE":
                return {}
            if method == "POST":
                self.payload = payload
            result = {key: self.payload[key] for key in ("id", "base_sha", "snapshot_sha256")}
            result.update(state="running" if method == "POST" else "succeeded", output="first\n", exit_code=0)
            if method == "POST" and tamper:
                result[tamper] = {"id": "c" * 32, "base_sha": "c" * 40, "profile": "android",
                                  "image_digest": "invalid", "output": []}[tamper]
            return result

    monkeypatch.setattr(coding_terminal, "Client", Client)
    if tamper:
        with pytest.raises(coding_agent.CodingError):
            coding_terminal.run(workspace, {"argv": ["python", "main.py"]}, progress=updates.append)
        assert updates == [] and calls[-1] == "DELETE"
    else:
        result = coding_terminal.run(workspace, {"argv": ["python", "main.py"]}, progress=updates.append)
        assert updates == [snapshot("first\n")]
        assert result["state"] == "succeeded" and result["exit_code"] == 0


def test_timed_command_uses_one_activity_for_live_and_final_events(monkeypatch):
    events, callbacks = [], []
    agent = agent_tools.AgentRun({"enqueue": lambda _, event: events.append(event) or True})

    def command(name, args, command_progress=None):
        callbacks.append(command_progress)
        command_progress(snapshot("working\n"))
        return dict(state="succeeded", output="done\n", exit_code=0)

    agent.coding = SimpleNamespace(lock=threading.RLock(), workspace=None, run_tool=command)
    monkeypatch.setattr(agent_tools, "call_with_deadline", lambda fn, *a, **kw: fn())
    result = agent.run_tool_timed("run_command", {"argv": ["python", "main.py"]})
    callbacks[0](snapshot("late\n"))
    assert result["exit_code"] == 0
    assert [e["terminal"]["state"] for e in events] == ["preparing", "running", "succeeded"]
    assert len({e["terminal"]["id"] for e in events}) == 1
