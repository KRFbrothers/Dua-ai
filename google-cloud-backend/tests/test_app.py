import base64
import json
import os
import sys
from types import SimpleNamespace

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import app as engine  # noqa: E402


class FakeModel:
    def __init__(self, chunks=("Hello", " there"), fail=False):
        self.chunks, self.fail, self.calls = chunks, fail, []

    def generate_content(self, contents, stream=False):
        self.calls.append((contents, stream))
        if self.fail:
            raise RuntimeError("secret internal detail")
        if stream:
            return iter([SimpleNamespace(text=c, candidates=[]) for c in self.chunks])
        return SimpleNamespace(text="".join(self.chunks))


@pytest.fixture
def fake(monkeypatch):
    model = FakeModel()
    systems = []
    monkeypatch.setattr(engine, "get_model", lambda system=engine.SYSTEM_PROMPT: systems.append(system) or model)
    monkeypatch.setattr(engine, "make_part", lambda text=None, data=None, mime_type=None: ("text", text) if data is None else ("data", mime_type, data))
    monkeypatch.setattr(engine, "make_content", lambda role, parts: {"role": role, "parts": parts})
    monkeypatch.delenv("DUA_APP_TOKEN", raising=False)
    model.systems = systems
    return model


@pytest.fixture
def client():
    return engine.app.test_client()


def events(response):
    body = response.get_data(as_text=True)
    return [json.loads(line[6:]) for line in body.splitlines() if line.startswith("data: ")]


HISTORY = [{"role": "user", "text": "Hi"}, {"role": "model", "text": "Hello!"}, {"role": "user", "text": "My name?"}]


def test_stream_matches_app_wire_format(client, fake):
    r = client.post("/api/chat", json={"model": "Dua", "messages": HISTORY, "attachments": [], "memory": "", "stream": True, "mode": "auto"})
    assert r.status_code == 200 and r.headers["Content-Type"].startswith("text/event-stream")
    assert "event: text\ndata: " in r.get_data(as_text=True)
    assert events(r) == [{"type": "text", "delta": "Hello"}, {"type": "text", "delta": " there"}, {"type": "done", "truncated": False}]
    contents, stream = fake.calls[0]
    assert stream is True
    assert [c["role"] for c in contents] == ["user", "model", "user"]
    assert contents[2]["parts"] == [("text", "My name?")]


def test_non_stream_returns_text_json(client, fake):
    r = client.post("/api/chat", json={"model": "Gemini", "messages": HISTORY, "memory": "likes tea"})
    assert r.status_code == 200 and r.get_json() == {"text": "Hello there"}
    assert "likes tea" in fake.systems[-1]


def test_attachments_go_on_last_turn(client, fake):
    data = base64.b64encode(b"\x89PNG").decode()
    client.post("/api/chat", json={"messages": HISTORY, "attachments": [{"name": "a.png", "mimeType": "image/png", "data": data}], "stream": True})
    contents, _ = fake.calls[0]
    assert contents[-1]["parts"][1] == ("data", "image/png", b"\x89PNG")


def test_bad_inputs(client, fake):
    assert client.post("/api/chat", json={"messages": []}).status_code == 400
    assert client.post("/api/chat", json={"messages": [{"role": "model", "text": "x"}]}).status_code == 400
    assert client.post("/api/chat", json={"messages": HISTORY, "persistence": {}}).status_code == 400
    assert client.post("/api/chat", json={"messages": HISTORY, "attachments": [{"mimeType": "application/zip", "data": "AA=="}]}).status_code == 415
    assert fake.calls == []


def test_unsupported_model_gets_notice(client, fake):
    r = client.post("/api/chat", json={"model": "Claude", "messages": HISTORY, "stream": True, "web": "search"})
    assert events(r)[0]["type"] == "notice"


def test_errors_do_not_leak(client, fake):
    fake.fail = True
    r = client.post("/api/chat", json={"messages": HISTORY, "stream": True})
    assert events(r) == [{"type": "error", "message": engine.GENERIC_ERROR, "status": 502}]
    assert "secret" not in r.get_data(as_text=True)
    r = client.post("/chat", json={"message": "hi"})
    assert r.status_code == 500 and "secret" not in r.get_data(as_text=True)


def test_legacy_chat_still_works(client, fake):
    r = client.post("/chat", json={"message": "hi"})
    assert r.get_json() == {"reply": "Hello there"}


def test_shared_token(client, fake, monkeypatch):
    monkeypatch.setenv("DUA_APP_TOKEN", "abc")
    assert client.post("/api/chat", json={"messages": HISTORY}).status_code == 401
    assert client.post("/chat", json={"message": "hi"}).status_code == 401
    assert client.post("/api/chat", json={"messages": HISTORY}, headers={"X-Dua-App-Token": "abc"}).status_code == 200
    assert client.get("/").status_code == 200
