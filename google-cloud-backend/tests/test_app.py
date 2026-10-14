import base64
import json
import os
import sys
import queue
import threading
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
def client(monkeypatch):
    # These tests cover the existing chat contract; test_owner_auth exercises real verification.
    monkeypatch.setattr(engine, "verify_owner", lambda authorization: "verified-owner")
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


def test_file_metadata_text_is_included_on_its_original_user_turn(client, fake):
    history = [dict(HISTORY[0], files=[{"name": "voice.m4a", "transcript": "My name is Fareed"},
                                     {"name": "sales.csv", "extractedText": "Revenue,42"}]), *HISTORY[1:]]
    response = client.post("/api/chat", json={"messages": history})
    assert response.status_code == 200
    contents, _ = fake.calls[0]
    assert "My name is Fareed" in contents[0]["parts"][0][1]
    assert "Revenue,42" in contents[0]["parts"][0][1]
    assert contents[-1]["parts"] == [("text", "My name?")]


def test_follow_up_and_retry_keep_inline_parts_on_original_turns(client, fake):
    attachment = {"name": "a.pdf", "mimeType": "application/pdf", "data": base64.b64encode(b"pdf bytes").decode()}
    first = dict(HISTORY[0], attachments=[attachment], attachmentText="Original file text")
    for history in ([first, *HISTORY[1:]], [first]):
        response = client.post("/api/chat", json={"messages": history})
        assert response.status_code == 200
        contents, _ = fake.calls[-1]
        assert contents[0]["parts"][1] == ("data", "application/pdf", b"pdf bytes")
        assert "Original file text" in contents[0]["parts"][0][1]


def test_invalid_inline_history_attachments_and_large_file_text_are_rejected(client, fake):
    assert client.post("/api/chat", json={"messages": [dict(HISTORY[0], attachments=[{"mimeType": "application/pdf", "data": "bad!"}])]}).status_code == 400
    assert client.post("/api/chat", json={"messages": [dict(HISTORY[0], files=[{"transcript": "x" * engine.MAX_TURN_CHARS}])]}).status_code == 413
    assert fake.calls == []


@pytest.mark.parametrize("between_chunks", [False, True])
def test_heartbeats_while_vertex_waits_for_iterator_or_next_chunk(client, fake, monkeypatch, between_chunks):
    release = threading.Event()
    polls = []
    real_queue = queue.Queue

    def generate(contents, stream=False):
        if not between_chunks:
            assert release.wait(3)
            return iter([SimpleNamespace(text="Late", candidates=[])])
        def chunks():
            yield SimpleNamespace(text="First", candidates=[])
            assert release.wait(3)
            yield SimpleNamespace(text="Late", candidates=[])
        return chunks()

    class PollQueue(real_queue):
        def get(self, block=True, timeout=None):
            polls.append(timeout)
            first_timeout = 2 if between_chunks else 1
            if first_timeout <= len(polls) < first_timeout + 2:
                raise queue.Empty
            if len(polls) >= first_timeout + 2:
                release.set()
            return super().get(block=block, timeout=2)

    monkeypatch.setattr(fake, "generate_content", generate)
    monkeypatch.setattr(engine.queue, "Queue", PollQueue)
    response = client.post("/api/chat", json={"messages": HISTORY, "stream": True})
    body = response.get_data(as_text=True)
    assert body.count(": heartbeat\n\n") == 3  # Immediate header flush + two 15-second polling intervals.
    assert polls and all(timeout == 15 for timeout in polls)
    text = [event["delta"] for event in events(response) if event["type"] == "text"]
    assert text == (["First", "Late"] if between_chunks else ["Late"])
    assert events(response)[-1] == {"type": "done", "truncated": False}


def test_client_disconnect_stops_a_worker_blocked_on_a_full_queue(client, fake, monkeypatch):
    closed = threading.Event()
    def generate(contents, stream=False):
        def chunks():
            try:
                for _ in range(10000):
                    yield SimpleNamespace(text="chunk", candidates=[])
            finally:
                closed.set()
        return chunks()
    monkeypatch.setattr(fake, "generate_content", generate)
    response = client.post("/api/chat", json={"messages": HISTORY, "stream": True}, buffered=False)
    response.close()
    assert closed.wait(2)


def test_vertex_error_after_partial_text_keeps_text_and_does_not_emit_done(client, fake, monkeypatch):
    def generate(contents, stream=False):
        def chunks():
            yield SimpleNamespace(text="Partial", candidates=[])
            raise RuntimeError("secret Vertex detail")
        return chunks()
    monkeypatch.setattr(fake, "generate_content", generate)
    response = client.post("/api/chat", json={"messages": HISTORY, "stream": True})
    assert events(response) == [{"type": "text", "delta": "Partial"}, {"type": "error", "message": engine.GENERIC_ERROR, "status": 502}]
    assert "secret Vertex detail" not in response.get_data(as_text=True)


def test_worker_preserves_truncation_state(client, fake, monkeypatch):
    monkeypatch.setattr(fake, "generate_content", lambda *args, **kwargs: iter([
        SimpleNamespace(text="Short", candidates=[SimpleNamespace(finish_reason=SimpleNamespace(name="MAX_TOKENS"))])
    ]))
    response = client.post("/api/chat", json={"messages": HISTORY, "stream": True})
    assert events(response)[-1] == {"type": "done", "truncated": True}


def test_metadata_only_chunks_also_keep_the_connection_alive(client, fake):
    fake.chunks = ("", "", "Late text")
    response = client.post("/api/chat", json={"messages": HISTORY, "stream": True})
    assert response.get_data(as_text=True).count(": heartbeat\n\n") == 3
    assert events(response) == [{"type": "text", "delta": "Late text"}, {"type": "done", "truncated": False}]
