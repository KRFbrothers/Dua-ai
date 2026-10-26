import json
import os
import sys
from types import SimpleNamespace

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import app as engine  # noqa: E402

HISTORY = [{"role": "user", "text": "Hi"}]


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setattr(engine, "verify_owner", lambda authorization: "verified-owner")
    return engine.app.test_client()


def events(response):
    body = response.get_data(as_text=True)
    return [json.loads(line[6:]) for line in body.splitlines() if line.startswith("data: ")]


class RateLimited(Exception):
    code = 429

    def __str__(self):
        return "429 RESOURCE_EXHAUSTED secret quota detail"


class Model:
    """Each call pops the next outcome: an exception, or a list of chunk texts (a stream may raise mid-way)."""

    def __init__(self, outcomes):
        self.outcomes, self.calls = list(outcomes), 0

    def generate_content(self, contents, stream=False, generation_config=None):
        self.calls += 1
        outcome = self.outcomes.pop(0)
        if isinstance(outcome, Exception):
            raise outcome
        if not stream:
            return SimpleNamespace(text="".join(outcome))

        def chunks():
            for item in outcome:
                if isinstance(item, Exception):
                    raise item
                yield SimpleNamespace(text=item, candidates=[])
        return chunks()


@pytest.fixture
def setup(monkeypatch):
    sleeps = []
    monkeypatch.setattr(engine.time, "sleep", sleeps.append)
    monkeypatch.setattr(engine, "make_part", lambda text=None, data=None, mime_type=None: ("text", text))
    monkeypatch.setattr(engine, "make_content", lambda role, parts: {"role": role, "parts": parts})
    monkeypatch.delenv("DUA_APP_TOKEN", raising=False)
    monkeypatch.delenv("DUA_CHAT_429_RETRY_SECONDS", raising=False)

    def use(*outcomes):
        model = Model(outcomes)
        monkeypatch.setattr(engine, "get_model", lambda system=engine.SYSTEM_PROMPT: model)
        return model
    use.sleeps = sleeps
    return use


def test_429_then_success_retries_once(client, setup):
    model = setup(RateLimited(), ["Hello"])
    r = client.post("/api/chat", json={"messages": HISTORY})
    assert r.status_code == 200 and r.get_json() == {"text": "Hello"}
    assert model.calls == 2 and len(setup.sleeps) == 1 and 3 <= setup.sleeps[0] <= 4


def test_429_twice_gives_generic_error_after_one_retry(client, setup):
    model = setup(RateLimited(), RateLimited(), ["never"])
    r = client.post("/api/chat", json={"messages": HISTORY})
    assert r.status_code == 502 and r.get_json() == {"error": engine.GENERIC_ERROR}
    assert model.calls == 2 and len(setup.sleeps) == 1
    assert "secret" not in r.get_data(as_text=True)


def test_other_errors_are_not_retried(client, setup):
    model = setup(RuntimeError("boom"), ["never"])
    r = client.post("/api/chat", json={"messages": HISTORY})
    assert r.status_code == 502 and model.calls == 1 and setup.sleeps == []


def test_retry_disabled_with_zero(client, setup, monkeypatch):
    monkeypatch.setenv("DUA_CHAT_429_RETRY_SECONDS", "0")
    model = setup(RateLimited(), ["never"])
    assert client.post("/api/chat", json={"messages": HISTORY}).status_code == 502
    assert model.calls == 1 and setup.sleeps == []


@pytest.mark.parametrize("on_call", [True, False])
def test_stream_429_before_first_chunk_is_retried(client, setup, on_call):
    first = RateLimited() if on_call else [RateLimited()]
    model = setup(first, ["Hi", " there"])
    r = client.post("/api/chat", json={"messages": HISTORY, "stream": True})
    assert events(r) == [{"type": "text", "delta": "Hi"}, {"type": "text", "delta": " there"},
                         {"type": "done"}]
    assert model.calls == 2 and len(setup.sleeps) == 1


def test_stream_429_twice_gives_generic_error(client, setup):
    model = setup(RateLimited(), RateLimited(), ["never"])
    r = client.post("/api/chat", json={"messages": HISTORY, "stream": True})
    assert events(r) == [{"type": "error", "message": engine.GENERIC_ERROR, "status": 502}]
    assert model.calls == 2 and "secret" not in r.get_data(as_text=True)


def test_stream_429_after_first_chunk_is_not_retried(client, setup):
    model = setup(["Partial", RateLimited()], ["never"])
    r = client.post("/api/chat", json={"messages": HISTORY, "stream": True})
    got = events(r)
    assert got[0] == {"type": "text", "delta": "Partial"} and {"type": "done"} not in got
    assert model.calls == 1 and setup.sleeps == []
