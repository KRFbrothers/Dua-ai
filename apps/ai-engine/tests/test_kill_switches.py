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


def test_maintenance_refuses_title_and_legacy_chat_without_model_call(monkeypatch):
    monkeypatch.setattr(engine, "get_model", lambda *a, **k: (_ for _ in ()).throw(AssertionError("model must not run")))
    monkeypatch.setenv("DUA_MAINTENANCE", "1")
    for path, body in (("/api/title", {"user": "Hi", "reply": "Hello"}), ("/chat", {"message": "Hi"})):
        response = client().post(path, json=body)
        assert response.status_code == 503
        assert response.get_json() == {"error": kill_switches.DEFAULT_MAINTENANCE_MESSAGE, "maintenance": True}
        assert "no-store" in response.headers.get("Cache-Control", "")


def test_title_and_legacy_chat_still_answer_without_maintenance(monkeypatch):
    class Reply:
        text = '{"title": "Greeting chat"}'

    class Model:
        def generate_content(self, *args, **kwargs):
            return Reply()

    monkeypatch.setattr(engine, "get_model", lambda *a, **k: Model())
    monkeypatch.setattr(engine, "chunk_text", lambda response: response.text)
    title = client().post("/api/title", json={"user": "Hi", "reply": "Hello"})
    assert title.status_code == 200 and title.get_json() == {"title": "Greeting chat"}
    chat = client().post("/chat", json={"message": "Hi"})
    assert chat.status_code == 200 and chat.get_json() == {"reply": Reply.text}


@pytest.mark.parametrize("switch", ["DUA_MAINTENANCE", "DUA_DISABLE_AGENT"])
@pytest.mark.parametrize("method,path,body", [
    ("POST", "/api/agent/documents", {"name": "Example", "text": "Document"}),
    ("POST", "/api/agent/documents", {"query": "Find a document"}),
    ("DELETE", "/api/agent/documents", {"id": "invalid"}),
    ("POST", "/api/agent/notes/reindex", {"offset": 0, "limit": 20}),
])
def test_paused_document_operations_do_not_initialize_providers(monkeypatch, switch, method, path, body):
    import agent_documents
    def forbidden(*args, **kwargs):
        pytest.fail("Paused operation must not initialize storage or providers")
    monkeypatch.setenv(switch, "1")
    monkeypatch.setenv("DUA_MAINTENANCE_MESSAGE", "Back soon")
    monkeypatch.setattr(agent_documents, "enabled", forbidden)
    monkeypatch.setattr(engine, "get_adc_client", forbidden)
    response = client().open(path, method=method, json=body)
    assert response.status_code == 503
    expected = {"error": "Back soon", "maintenance": True} if switch == "DUA_MAINTENANCE" else {
        "error": kill_switches.AGENT_PAUSED, "paused": "agent"}
    assert response.get_json() == expected
    assert response.headers["Cache-Control"] == "no-store"


def test_document_get_remains_available_during_maintenance(monkeypatch):
    import agent_documents
    from types import SimpleNamespace
    monkeypatch.setenv("DUA_MAINTENANCE", "1")
    monkeypatch.setattr(agent_documents, "enabled", lambda: True)
    monkeypatch.setattr(engine, "get_adc_client", lambda: None)
    monkeypatch.setattr(agent_documents, "VertexEmbeddings", lambda client: None)
    monkeypatch.setattr(agent_documents, "Documents", lambda *args: SimpleNamespace(list=lambda: []))
    response = client().get("/api/agent/documents")
    assert response.status_code == 200
    assert response.get_json() == {"documents": []}


def test_document_provider_failure_is_already_a_safe_503(monkeypatch):
    import agent_documents
    monkeypatch.setattr(agent_documents, "enabled", lambda: True)
    def unavailable():
        raise RuntimeError("private provider diagnostic")
    monkeypatch.setattr(engine, "get_adc_client", unavailable)
    response = client().post("/api/agent/documents", json={"name": "Example", "text": "Document"})
    assert response.status_code == 503
    assert response.get_json() == {"error": "Document storage or embeddings are unavailable."}
