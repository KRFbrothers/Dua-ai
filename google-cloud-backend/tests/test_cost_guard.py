"""Cost + access guardrails: daily caps, token counting, Agent step defaults, Vertex timeouts/retries, gunicorn."""
import datetime
import json
import os
import re
import sys
from types import SimpleNamespace

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import app as engine  # noqa: E402
import agent_tools  # noqa: E402
import cost_guard  # noqa: E402

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UTC = datetime.timezone.utc


class Clock:
    def __init__(self, when):
        self.now = when

    def __call__(self):
        return self.now


def events(response):
    return [json.loads(line[6:]) for line in response.get_data(as_text=True).splitlines() if line.startswith("data: ")]


class CountingModel:
    """Fake Vertex adapter: counts calls so a refused request can prove it never reached the model."""

    def __init__(self):
        self.calls = 0

    def generate_content(self, contents, stream=False, generation_config=None, tools=None):
        self.calls += 1
        if stream:
            return iter([SimpleNamespace(text="Hi", candidates=[])])
        return SimpleNamespace(text="Hi", candidates=[], function_calls=[])


@pytest.fixture
def client(monkeypatch):
    model = CountingModel()
    monkeypatch.setattr(engine, "verify_owner", lambda authorization: "verified-owner")
    monkeypatch.setattr(engine, "get_model", lambda system=engine.SYSTEM_PROMPT: model)
    monkeypatch.setattr(engine, "make_part", lambda text=None, data=None, mime_type=None: ("text", text))
    monkeypatch.setattr(engine, "make_content", lambda role, parts: {"role": role, "parts": parts})
    monkeypatch.delenv("DUA_APP_TOKEN", raising=False)
    for name in ("DUA_DAILY_AGENT_RUNS", "DUA_DAILY_CHAT_REQUESTS", "DUA_DAILY_TOKENS"):
        monkeypatch.delenv(name, raising=False)
    c = engine.app.test_client()
    c.model = model
    return c


CHAT = {"model": "Dua", "messages": [{"role": "user", "text": "Hello"}], "stream": True}


# ---- limits -------------------------------------------------------------------------------------------------

def test_safe_defaults_and_owner_overrides():
    assert cost_guard.limit("agent", {}) == 50
    assert cost_guard.limit("chat", {}) == 500
    assert cost_guard.limit("tokens", {}) == 5_000_000
    assert cost_guard.limit("agent", {"DUA_DAILY_AGENT_RUNS": "120"}) == 120
    assert cost_guard.limit("tokens", {"DUA_DAILY_TOKENS": "2_000_000"}) == 2_000_000
    assert cost_guard.limit("chat", {"DUA_DAILY_CHAT_REQUESTS": "0"}) == 0  # 0 = cap off (explicit owner choice)
    for bad in ("-5", "lots", "1.5", "9" * 20):
        assert cost_guard.limit("agent", {"DUA_DAILY_AGENT_RUNS": bad}) == 50, bad


def test_budget_admits_until_cap_then_refuses_with_reset_time():
    clock = Clock(datetime.datetime(2026, 10, 20, 18, 0, tzinfo=UTC))
    budget = cost_guard.Budget(clock)
    env = {"DUA_DAILY_AGENT_RUNS": "2"}
    assert budget.admit("agent", env) is None
    assert budget.admit("agent", env) is None
    message, retry_after = budget.admit("agent", env)
    assert "Agent runs" in message and "2 per day" in message and "DUA_DAILY_AGENT_RUNS" in message
    assert "5:30 AM India time" in message
    assert retry_after == 6 * 3600
    assert budget.used()["agent"] == 2, "refused requests are not counted"
    assert budget.admit("chat", env) is None, "chat has its own cap"


def test_token_cap_blocks_every_kind_and_day_rolls_over_at_utc_midnight():
    clock = Clock(datetime.datetime(2026, 10, 20, 23, 59, 30, tzinfo=UTC))
    budget = cost_guard.Budget(clock)
    env = {"DUA_DAILY_TOKENS": "1000"}
    budget.add_tokens(600)
    assert budget.admit("chat", env) is None
    budget.add_tokens(400)
    for kind in ("chat", "agent"):
        message, retry_after = budget.admit(kind, env)
        assert "tokens" in message and retry_after == 30
    clock.now = datetime.datetime(2026, 10, 21, 0, 0, 1, tzinfo=UTC)
    assert budget.admit("agent", env) is None
    assert budget.used() == {"agent": 1, "chat": 0, "tokens": 0}


def test_zero_disables_a_cap():
    budget = cost_guard.Budget()
    for _ in range(5):
        assert budget.admit("agent", {"DUA_DAILY_AGENT_RUNS": "0"}) is None


def test_bad_token_counts_are_ignored():
    budget = cost_guard.Budget()
    for value in (None, -3, "x", 0):
        budget.add_tokens(value)
    assert budget.used()["tokens"] == 0


# ---- token counting -------------------------------------------------------------------------------------------

def test_usage_tokens_reads_vertex_metadata():
    assert cost_guard.usage_tokens(SimpleNamespace(usage_metadata=SimpleNamespace(total_token_count=1234))) == 1234
    meta = SimpleNamespace(total_token_count=None, prompt_token_count=10, candidates_token_count=5, thoughts_token_count=7)
    assert cost_guard.usage_tokens(SimpleNamespace(usage_metadata=meta)) == 22
    assert cost_guard.usage_tokens(SimpleNamespace()) == 0


def test_counted_stream_records_last_total_once_and_forwards_close():
    closed = []

    class Stream:
        def __init__(self):
            self.items = iter([SimpleNamespace(usage_metadata=SimpleNamespace(total_token_count=n)) for n in (5, 50, 120)])

        def __iter__(self):
            return self

        def __next__(self):
            return next(self.items)

        def close(self):
            closed.append(True)

    budget = cost_guard.Budget()
    assert len(list(cost_guard.counted_stream(Stream(), budget))) == 3
    assert budget.used()["tokens"] == 120
    assert closed == [True]
    # Stopped early (client disconnected): the tokens seen so far are still counted and the stream is closed.
    early = cost_guard.counted_stream(Stream(), budget)
    next(early)
    early.close()
    assert budget.used()["tokens"] == 125 and closed == [True, True]


def test_genai_model_counts_tokens_for_plain_and_streamed_calls(monkeypatch):
    model = engine.GenaiModel("system")
    replies = {False: SimpleNamespace(text="a", usage_metadata=SimpleNamespace(total_token_count=300)),
               True: iter([SimpleNamespace(text="b", usage_metadata=SimpleNamespace(total_token_count=40))])}
    monkeypatch.setattr(model, "_generate", lambda contents, stream, config, tools: replies[stream])
    assert model.generate_content("q").text == "a"
    assert [c.text for c in model.generate_content("q", stream=True)] == ["b"]
    assert cost_guard.BUDGET.used()["tokens"] == 340


# ---- endpoints ------------------------------------------------------------------------------------------------

def test_agent_cap_returns_friendly_stream_message_without_calling_the_model(client, monkeypatch):
    monkeypatch.setenv("DUA_DAILY_AGENT_RUNS", "1")
    monkeypatch.setattr(engine, "agent_producer", lambda *a, **k: (lambda enqueue: (enqueue("chunk", SimpleNamespace(text="ok", candidates=[])), enqueue("done"))))
    body = {**CHAT, "mode": "agent"}
    first = client.post("/api/chat", json=body)
    assert [e["type"] for e in events(first)] == ["text", "done"]
    second = client.post("/api/chat", json=body)
    assert second.status_code == 200 and second.headers["Content-Type"].startswith("text/event-stream")
    assert second.headers["Retry-After"].isdigit() and second.headers["Cache-Control"] == "no-store"
    (event,) = events(second)
    assert event["type"] == "error" and event["status"] == 429
    assert "Agent runs" in event["message"] and "1 per day" in event["message"]
    # Normal chat is still allowed: separate counter.
    assert [e["type"] for e in events(client.post("/api/chat", json=CHAT))] == ["text", "done"]


def test_chat_cap_streamed_and_plain_json(client, monkeypatch):
    monkeypatch.setenv("DUA_DAILY_CHAT_REQUESTS", "1")
    assert events(client.post("/api/chat", json=CHAT))[-1]["type"] == "done"
    calls = client.model.calls
    (event,) = events(client.post("/api/chat", json=CHAT))
    assert event["status"] == 429 and "chat messages" in event["message"]
    plain = client.post("/api/chat", json={**CHAT, "stream": False})
    assert plain.status_code == 429 and plain.headers["Retry-After"].isdigit()
    assert "chat messages" in plain.get_json()["error"]
    old = client.post("/chat", json={"message": "hi"})
    assert old.status_code == 429
    assert client.model.calls == calls, "refused requests never reach Vertex"


def test_token_cap_refuses_new_requests(client, monkeypatch):
    monkeypatch.setenv("DUA_DAILY_TOKENS", "100")
    cost_guard.BUDGET.add_tokens(100)
    (event,) = events(client.post("/api/chat", json=CHAT))
    assert event["status"] == 429 and "tokens" in event["message"]
    assert client.model.calls == 0


def test_unauthenticated_requests_do_not_use_the_budget(monkeypatch):
    monkeypatch.setenv("DUA_DAILY_CHAT_REQUESTS", "1")
    monkeypatch.delenv("DUA_APP_TOKEN", raising=False)
    from owner_auth import OwnerAuthError

    def deny(_):
        raise OwnerAuthError("Sign in", 401)
    monkeypatch.setattr(engine, "verify_owner", deny)
    c = engine.app.test_client()
    for _ in range(3):
        assert c.post("/api/chat", json=CHAT).status_code == 401
    assert cost_guard.BUDGET.used()["chat"] == 0


# ---- Agent defaults --------------------------------------------------------------------------------------------

def test_agent_step_and_token_defaults_are_lower_and_configurable(monkeypatch):
    if "AGENT_MAX_STEPS" not in os.environ:
        assert agent_tools.MAX_STEPS == 8
    if "AGENT_STEP_TOKENS" not in os.environ:
        assert agent_tools.STEP_TOKENS == 8192, "Argon no longer gets 32768 per step by default"
    monkeypatch.setenv("AGENT_MAX_STEPS", "14")
    monkeypatch.setenv("AGENT_STEP_TOKENS", "32768")
    assert agent_tools._env_int("AGENT_MAX_STEPS", 8, 1, 30) == 14
    assert agent_tools._env_int("AGENT_STEP_TOKENS", 8192, 1024, 65536) == 32768
    for bad in ("0", "500", "abc", ""):
        monkeypatch.setenv("AGENT_MAX_STEPS", bad)
        assert agent_tools._env_int("AGENT_MAX_STEPS", 8, 1, 30) == 8


# ---- timeouts ---------------------------------------------------------------------------------------------------

def test_gunicorn_has_a_real_timeout_above_the_agent_and_cloud_run_limits():
    line = open(os.path.join(HERE, "Procfile")).read()
    match = re.search(r"--timeout (\d+)", line)
    assert match, line
    timeout = int(match.group(1))
    assert timeout != 0
    assert timeout > agent_tools.MAX_SECONDS and timeout > 900, "above the Agent limit and Cloud Run's 900 s"
    assert "--workers 1" in line, "approval/budget state is per process"


def test_vertex_http_settings_are_bounded(monkeypatch):
    assert engine.VERTEX_HTTP_TIMEOUT_SECONDS == 180 or "VERTEX_HTTP_TIMEOUT_SECONDS" in os.environ
    assert 1 <= engine.VERTEX_HTTP_ATTEMPTS <= 5
    monkeypatch.setenv("X_TEST", "9999")
    assert engine._bounded_env("X_TEST", 180, 10, 600) == 180
    monkeypatch.setenv("X_TEST", "45")
    assert engine._bounded_env("X_TEST", 180, 10, 600) == 45


def test_vertex_calls_get_timeout_and_limited_retries():
    pytest.importorskip("google.genai")
    options = engine.vertex_http_options()
    assert options.timeout == engine.VERTEX_HTTP_TIMEOUT_SECONDS * 1000
    retry = options.retry_options
    assert retry.attempts == engine.VERTEX_HTTP_ATTEMPTS == 3
    assert set(retry.http_status_codes) == {408, 429, 500, 502, 503, 504}
    assert retry.max_delay == 8.0
    # Agent steps keep their own shorter deadline, plus the same retries.
    step = engine.GenaiModel("s").config({"timeout_seconds": 150})
    assert step.http_options.timeout == 150_000 and step.http_options.retry_options.attempts == 3
    adk = engine.adk_config(engine.MODEL_NAME, 1024)
    assert adk.http_options.timeout == agent_tools.STEP_SECONDS * 1000 and adk.http_options.retry_options is not None


def test_client_is_created_with_default_timeout_and_retries(monkeypatch):
    genai = pytest.importorskip("google.genai")
    seen = {}

    class FakeClient:
        def __init__(self, **kwargs):
            seen.update(kwargs)
    monkeypatch.setattr(genai, "Client", FakeClient)
    monkeypatch.setattr(engine, "_client", None)
    monkeypatch.setattr(engine, "PROJECT_ID", "test-project")
    engine.get_client()
    assert seen["http_options"].timeout == engine.VERTEX_HTTP_TIMEOUT_SECONDS * 1000
    assert seen["http_options"].retry_options.attempts == engine.VERTEX_HTTP_ATTEMPTS
