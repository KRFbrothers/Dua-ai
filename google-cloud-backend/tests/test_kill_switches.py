import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import app as engine  # noqa: E402
import kill_switches  # noqa: E402

SWITCHES = ("DUA_DISABLE_AGENT", "DUA_DISABLE_IMAGINE", "DUA_MAINTENANCE", "DUA_MAINTENANCE_MESSAGE")


@pytest.fixture(autouse=True)
def owner(monkeypatch):
    for name in SWITCHES:
        monkeypatch.delenv(name, raising=False)
    monkeypatch.setattr(engine, "verify_owner", lambda authorization: "verified-owner")
    monkeypatch.setattr(engine, "make_part", lambda text=None, data=None, mime_type=None: ("text", text))
    monkeypatch.setattr(engine, "make_content", lambda role, parts: {"role": role, "parts": parts})
    monkeypatch.delenv("DUA_APP_TOKEN", raising=False)


def client():
    return engine.app.test_client()


def agent_chat():
    return client().post("/api/chat", json={"model": "Dua", "stream": True, "mode": "agent", "messages": [{"role": "user", "text": "Hi"}]})


def test_defaults_are_off(monkeypatch):
    assert not kill_switches.agent_disabled() and not kill_switches.imagine_disabled()
    assert kill_switches.maintenance_message() is None
    monkeypatch.setenv("DUA_DISABLE_AGENT", "0")
    assert not kill_switches.agent_disabled()


def test_agent_switch_hides_capability_and_refuses_agent_but_not_chat(monkeypatch):
    monkeypatch.setattr(engine, "get_model", lambda system: (_ for _ in ()).throw(AssertionError("model must not run")))
    monkeypatch.setenv("DUA_DISABLE_AGENT", "1")
    assert client().get("/").get_json()["agent"] is False
    response = agent_chat()
    assert response.status_code == 503
    assert response.get_json() == {"error": kill_switches.AGENT_PAUSED, "paused": "agent"}
    approval = client().post("/api/agent/action", json={"id": "x", "decision": "approve"})
    assert approval.status_code == 503


def test_agent_capability_is_on_by_default():
    assert client().get("/").get_json()["agent"] is True


def test_maintenance_refuses_chat_with_custom_message(monkeypatch):
    monkeypatch.setenv("DUA_MAINTENANCE", "true")
    monkeypatch.setenv("DUA_MAINTENANCE_MESSAGE", "Back\nat 6 pm")
    response = client().post("/api/chat", json={"model": "Dua", "stream": True, "messages": [{"role": "user", "text": "Hi"}]})
    assert response.status_code == 503
    assert response.get_json() == {"error": "Back at 6 pm", "maintenance": True}
    assert client().get("/").get_json()["agent"] is False


def test_maintenance_default_message(monkeypatch):
    monkeypatch.setenv("DUA_MAINTENANCE", "1")
    assert kill_switches.maintenance_message() == kill_switches.DEFAULT_MAINTENANCE_MESSAGE


def test_imagine_switch_refuses_engine_imagine(monkeypatch):
    monkeypatch.setenv("DUA_DISABLE_IMAGINE", "1")
    response = client().post("/api/imagine/image", json={"prompt": "cat"}, headers={"Authorization": "Bearer t"})
    assert response.status_code == 503
    assert response.get_json()["error"] == kill_switches.IMAGINE_PAUSED
    imagine = client().get("/").get_json().get("imagine")
    if isinstance(imagine, dict):
        assert imagine["enabled"] is False
