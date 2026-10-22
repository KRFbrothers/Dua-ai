"""Device boundary tests: fake displays/PTYs, no device control, Docker or provider calls."""
import asyncio
import base64
import os
from pathlib import Path
import sys
from types import SimpleNamespace
from unittest.mock import Mock

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import agent_automation as automation
import agent_safety
import agent_tools
from automation_companion import Companion, create_app
from automation_drivers import Android, point, public_url
from automation_terminal import Session, Terminals

OWNER = "11111111-1111-4111-8111-111111111111"
OTHER = "22222222-2222-4222-8222-222222222222"
TOKEN = "x" * 40
JPEG = base64.b64encode(b"\xff\xd8pixels\xff\xd9").decode()


@pytest.fixture
def configured(monkeypatch):
    monkeypatch.setenv("DUA_AUTOMATION_URL", "https://companion.example.com")
    monkeypatch.setenv("DUA_AUTOMATION_TOKEN", TOKEN)


class Driver:
    def __init__(self):
        self.version, self.actions = 1, []

    def observe(self):
        return {"screenshot": JPEG, "width": 1280, "height": 720, "text": "page " + str(self.version)}

    def act(self, args, view):
        point(args, view["width"], view["height"])
        self.actions.append(dict(args))

    def close(self):
        pass


@pytest.fixture
def companion():
    now, driver = [0], Driver()
    control = Companion({"owner": OWNER, "targets": {"desktop": {"kind": "desktop"}}},
                        driver_factory=lambda _: driver, clock=lambda: now[0])
    yield control, driver, now
    control.close()


def invoke(control, tool, args=None, owner=OWNER, request_id=None):
    body = {"owner": owner, "tool": tool, "args": args or {}}
    if request_id:
        body["request_id"] = request_id
    return control.handle(body)


def test_tools_hidden_without_config(monkeypatch):
    monkeypatch.delenv("DUA_AUTOMATION_URL", raising=False)
    monkeypatch.delenv("DUA_AUTOMATION_TOKEN", raising=False)
    assert not automation.status()["configured"]
    assert not set(automation.NAMES) & {t["name"] for t in agent_tools.declarations(False)}


def test_configured_tools_and_adk_wrappers(configured):
    assert set(automation.NAMES) <= {t["name"] for t in agent_tools.declarations(False)}
    import agent_adk
    async def call(name, args):
        return {"name": name, "args": args}
    tools = {f.__name__: f for f in agent_adk.make_tools(("current_time",), call)}
    assert set(automation.NAMES) <= tools.keys()
    assert asyncio.run(tools["observe_device"]("desktop"))["args"] == {"target": "desktop"}


@pytest.mark.parametrize("url", ["http://example.com", "https://u:p@example.com", "https://example.com/path", "https://example.com:444", "https://example.com?token=a"])
def test_invalid_origin_hides_tools(configured, monkeypatch, url):
    monkeypatch.setenv("DUA_AUTOMATION_URL", url)
    assert not automation.status()["configured"]


def test_owner_cannot_be_selected_in_tool_args():
    with pytest.raises(ValueError):
        automation.validate("observe_device", {"target": "desktop", "owner": OTHER})
    with pytest.raises(ValueError):
        automation.owner(None)


def test_reads_owner_isolated(companion):
    control, driver, _ = companion
    with pytest.raises(ValueError):
        invoke(control, "observe_device", {"target": "desktop"}, owner=OTHER)
    assert driver.actions == []


def test_action_requires_approval_and_single_use_view(companion):
    control, driver, _ = companion
    view = invoke(control, "observe_device", {"target": "desktop"})
    args = {"target": "desktop", "view_id": view["view_id"], "action": "click", "x": 10, "y": 20}
    with pytest.raises(ValueError):
        invoke(control, "control_device", args)
    assert not driver.actions
    assert invoke(control, "control_device", args, request_id="approval_123456789")["completed"]
    for rid in ("approval_123456789", "new_approval_123456789"):
        with pytest.raises(ValueError):
            invoke(control, "control_device", args, request_id=rid)
    assert len(driver.actions) == 1


@pytest.mark.parametrize("change", ["time", "screen"])
def test_stale_approval_never_clicks(companion, change):
    control, driver, now = companion
    view = invoke(control, "observe_device", {"target": "desktop"})
    if change == "time":
        now[0] += 121
    else:
        driver.version += 1
    with pytest.raises(ValueError):
        invoke(control, "control_device", {"target": "desktop", "view_id": view["view_id"],
                                          "action": "click", "x": 1, "y": 1}, request_id="approval_123456789")
    assert driver.actions == []


def test_companion_http_auth_no_private_errors(companion):
    control, _, _ = companion
    app = create_app({}, TOKEN, companion=control)
    client = app.test_client()
    body = {"owner": OWNER, "tool": "automation_targets", "args": {}}
    assert client.post("/v1/tool", json=body).status_code == 401
    response = client.post("/v1/tool", json=body, headers={"Authorization": "Bearer " + TOKEN})
    assert response.status_code == 200 and response.json["targets"][0]["id"] == "desktop"
    assert response.headers["Cache-Control"] == "no-store"
    body["owner"] = OTHER
    response = client.post("/v1/tool", json=body, headers={"Authorization": "Bearer " + TOKEN})
    assert "error" in response.json and OTHER not in response.get_data(as_text=True)


def test_pending_action_does_not_control_device(configured, monkeypatch):
    call = Mock(return_value={"completed": True})
    monkeypatch.setattr(automation, "call", call)
    store = agent_safety.PendingStore()
    run = agent_tools.AgentRun({"owner_id": OWNER, "approvals": store, "enqueue": lambda *a: True})
    result = run.request_approval("control_device", {"target": "desktop", "view_id": "fresh_view_123", "action": "click", "x": 1, "y": 2})
    assert result["status"] == "waiting_for_user_approval" and not call.called
    assert "error" in run.run_tool("control_device", {}) and not call.called
    with pytest.raises(agent_safety.ApprovalError):
        store.take(run.pending[0]["id"], OTHER)
    action = store.take(run.pending[0]["id"], OWNER)
    assert agent_safety.execute(action, {})[0]
    assert call.call_args.args[2] == OWNER
    with pytest.raises(agent_safety.ApprovalError):
        store.take(action["id"], OWNER)


def test_configuration_change_invalidates_approval(configured, monkeypatch):
    args = agent_safety.prepare("open_terminal", {"profile": "python"})
    monkeypatch.setenv("DUA_AUTOMATION_URL", "https://other.example.com")
    call = Mock()
    monkeypatch.setattr(automation, "call", call)
    ok, message = agent_safety.execute({"tool": "open_terminal", "args": args, "owner": OWNER, "id": "approval_123456789"}, {})
    assert not ok and "changed" in message and not call.called


def test_approval_preview_preserves_newlines_and_complete_input(configured):
    text = "print('hello')\n" + "a" * 2000
    args = agent_safety.prepare("write_terminal", {"session_id": "session123", "text": text})
    title, details = agent_safety.describe("write_terminal", args)
    assert len(details) <= 12
    shown = "".join(line.split(": ", 1)[1] for line in details if line.startswith("text"))
    import json
    assert json.loads(shown) == text
    assert "\\n" in shown


def test_screenshot_not_in_tool_text_or_evidence(monkeypatch):
    monkeypatch.setattr(automation, "call", lambda *a, **k: {"screenshot": JPEG, "view_id": "v123", "width": 1280, "height": 720})
    run = agent_tools.AgentRun({"owner_id": OWNER})
    result = run.run_tool("observe_device", {"target": "desktop"})
    assert "screenshot" not in result and run.device_images
    assert JPEG not in str(result)


def test_observe_budget(monkeypatch):
    calls = Mock(return_value={"text": "screen unavailable"})
    monkeypatch.setattr(automation, "call", calls)
    run = agent_tools.AgentRun({"owner_id": OWNER})
    for _ in range(7):
        result = run.run_tool("observe_device", {"target": "desktop"})
    assert calls.call_count == 6 and "error" in result


def test_browser_rejects_local_and_nonallowed_destinations(monkeypatch):
    import socket
    monkeypatch.setattr(socket, "getaddrinfo", lambda *a, **k: [(2, 1, 6, "", ("127.0.0.1", 443))])
    for url in ("file:///etc/passwd", "http://example.com", "https://other.com", "https://example.com"):
        with pytest.raises(ValueError):
            public_url(url, {"example.com"})


def test_android_does_not_accept_shell_injection():
    android = Android.__new__(Android)
    android.device = Mock()
    for value in ("hello; id", "$(id)", "hello\nwhoami", "a' b"):
        with pytest.raises(ValueError):
            android.act({"action": "type", "value": value}, {"width": 100, "height": 100})
    assert not android.device.shell.called


def test_terminal_owner_and_expiry():
    session = SimpleNamespace(id="s1", owner=OWNER, expired=lambda: False, closed=False)
    terminals = Terminals({"python": "unused"}, factory=lambda *a: session)
    opened = terminals.open(OWNER, "python")
    assert terminals.get(OWNER, opened["session_id"]) is session
    with pytest.raises(ValueError):
        terminals.get(OTHER, "s1")
    session.expired = lambda: True
    session.close = Mock()
    with pytest.raises(ValueError):
        terminals.get(OWNER, "s1")
    assert session.close.called


def test_terminal_profile_requires_digest():
    with pytest.raises(ValueError):
        Session(OWNER, "python", "python:latest", spawn=Mock())


def test_terminal_container_isolated(monkeypatch):
    monkeypatch.setattr(Session, "pump", lambda s: None)
    spawn = Mock(return_value=Mock())
    session = Session(OWNER, "python", "python@sha256:" + "a" * 64, spawn=spawn)
    args = spawn.call_args.args[1]
    assert args[args.index("--network") + 1] == "none"
    assert args[args.index("--user") + 1] == "10001:10001"
    assert "--read-only" in args and "--pull=never" in args
    assert "-v" not in args and "--mount" not in args
    assert session.name.startswith("dua-auto-")
