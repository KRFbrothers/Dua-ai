"""The Cloud Shell `smoke` check must send what the real /api/chat handler accepts.

Loads the Python embedded in dua-adk-cloudshell.sh and routes its HTTP calls
into the real Flask app (test client). Only the model call and the ADK agent
producer are stubbed; validation, auth wiring and SSE framing are real.
"""
import importlib.util
import io
import json
import os
import re
import sys
from types import SimpleNamespace
from urllib.error import HTTPError

import pytest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)
import app as engine  # noqa: E402

TOKEN = "owner-session-token"


def load_smoke():
    script = open(os.path.join(ROOT, "dua-adk-cloudshell.sh"), encoding="utf-8").read()
    source = re.search(r"<<'DUA_ADK_PYTHON'\n(.*?)\nDUA_ADK_PYTHON\n", script, re.S).group(1)
    spec = importlib.util.spec_from_loader("cloudshell_setup", loader=None)
    module = importlib.util.module_from_spec(spec)
    exec(compile(source, "cloudshell_setup.py", "exec"), module.__dict__)
    return module


class Response:
    def __init__(self, result):
        self.status = result.status_code
        self.raw = result.get_data()

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False

    def __iter__(self):
        return iter(io.BytesIO(self.raw))

    def read(self, limit=-1):
        return self.raw if limit < 0 else self.raw[:limit]


@pytest.fixture
def smoke(monkeypatch):
    module = load_smoke()
    client = engine.app.test_client()
    seen = {"headers": [], "contents": [], "agent_messages": []}

    def verify_owner(authorization):
        assert authorization == "Bearer " + TOKEN
        return "verified-owner"

    class Model:
        def generate_content(self, contents, stream=False, generation_config=None):
            seen["contents"].append(contents)
            return iter([SimpleNamespace(text=t, candidates=[]) for t in ("I am ", "working.")])

    def agent_producer(messages, attachments, system, owner, cancelled=None):
        seen["agent_messages"].append(messages)
        def run(enqueue):
            enqueue("event", {"type": "agent_session", "runId": "11111111-2222-4333-8444-555555555555", "resumable": True})
            enqueue("event", {"type": "text", "delta": "17 times 23 is 391."})
            enqueue("done")  # the real producer ends the stream the same way
        return run

    monkeypatch.setattr(engine, "verify_owner", verify_owner)
    monkeypatch.setattr(engine, "get_model", lambda system=None: Model())
    monkeypatch.setattr(engine, "make_part", lambda text=None, data=None, mime_type=None: ("text", text))
    monkeypatch.setattr(engine, "make_content", lambda role, parts: {"role": role, "parts": parts})
    monkeypatch.setattr(engine, "agent_producer", agent_producer)
    monkeypatch.delenv("DUA_APP_TOKEN", raising=False)

    def urlopen(request, timeout=None):
        path = re.sub(r"^https://[^/]+", "", request.full_url)
        headers = dict(request.header_items())
        seen["headers"].append(headers)
        result = client.open(path, method=request.get_method(), data=request.data, headers=headers)
        if result.status_code >= 400:
            raise HTTPError(request.full_url, result.status_code, "error", {}, io.BytesIO(result.get_data()))
        return Response(result)

    monkeypatch.setattr(module, "urlopen", urlopen)
    module.seen = seen
    return module


def test_smoke_chat_request_is_accepted_by_real_handler(smoke):
    result = smoke.chat_stream("https://candidate.run.app", TOKEN, smoke.smoke_request(smoke.SMOKE_CHAT_PROMPT))
    assert result["errors"] == []
    assert result["reply"] == "I am working."
    last = smoke.seen["contents"][0][-1]
    assert last["role"] == "user" and ("text", smoke.SMOKE_CHAT_PROMPT) in last["parts"]
    headers = {k.lower(): v for k, v in smoke.seen["headers"][0].items()}
    assert headers["x-dua-native"] == "1" and headers["accept"].startswith("text/event-stream")


def test_smoke_agent_request_reaches_agent_and_reports_run_id(smoke):
    result = smoke.chat_stream("https://candidate.run.app", TOKEN, smoke.smoke_request(smoke.SMOKE_AGENT_PROMPT, agent=True))
    assert result["errors"] == [] and result["reply"] == "17 times 23 is 391."
    assert "runId:11111111-2222-4333-8444-555555555555" in result["events"]
    assert smoke.seen["agent_messages"] == [[{"role": "user", "text": smoke.SMOKE_AGENT_PROMPT}]]


def test_smoke_request_matches_android_wire_fields(smoke):
    body = smoke.smoke_request("hi", agent=True)
    assert set(body) == {"model", "messages", "attachments", "memory", "stream", "mode"}
    assert body["model"] in engine.APP_MODELS and body["messages"] == [{"role": "user", "text": "hi"}]
    assert smoke.smoke_request("hi")["mode"] == "auto"


def test_previous_content_field_shape_is_what_failed(smoke):
    # Regression guard: the #285 body used 'content', which the handler reads as empty.
    result = smoke.chat_stream("https://candidate.run.app", TOKEN, {"stream": True, "messages": [{"role": "user", "content": "hi"}]})
    assert result["reply"] == "" and result["errors"] == ["HTTP 400: Message is empty"]


def test_smoke_resume_probe_shape_is_accepted(smoke, monkeypatch):
    monkeypatch.delenv("ADK_SESSION_DB_URL", raising=False)
    status, body = smoke.http_json("https://candidate.run.app/api/chat", {"resumeRun": "11111111-2222-4333-8444-555555555555"},
                                   {"Authorization": "Bearer " + TOKEN, "x-dua-native": "1"})
    # Not a shape error: without durable storage configured the handler says so explicitly.
    assert status == 503 and "Durable ADK sessions" in body["error"]


# ---------------------------------------------------------------- why the first real smoke had no runId
def _adk_env(events):
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from test_agent_adk import make_env
    env = make_env([])
    env.update({"owner_id": "verified-owner", "adk_run_id": "11111111-2222-4333-8444-555555555555",
                "adk_request": {"messages": []}, "enqueue": lambda kind, chunk=None: events.append((kind, chunk)) or True})
    return env


@pytest.mark.parametrize("split,expect_run_id", [(True, True), (False, False)])
def test_durable_run_id_only_for_split_agent_plans(monkeypatch, split, expect_run_id):
    import dua_engine.agent.agent_adk as agent_adk
    from test_agent_adk import PLAN, cfg, fake_adk
    monkeypatch.setenv("ADK_SESSION_DB_URL", "postgresql://runtime@db.invalid/postgres")
    original = agent_adk.run_agent
    # The fake runner is in-memory; drop session_options so no database is touched.
    monkeypatch.setattr(agent_adk, "run_agent", lambda *a, session_options=None, **k: original(*a, **k))
    adk, _ = fake_adk(json.dumps(PLAN) if split else '{"split": false}', {"research": ["r"], "notes": ["n"]})
    events = []
    result = agent_adk.run(_adk_env(events), "request", "flash", "argon", cfg, adk=adk, log=lambda m: None)
    sessions = [chunk for kind, chunk in events if kind == "event" and chunk.get("type") == "agent_session"]
    if expect_run_id:
        assert result is not None and sessions == [{"type": "agent_session", "runId": "11111111-2222-4333-8444-555555555555", "resumable": True}]
    else:
        # One-part questions (like 17 x 23) use the plain loop: no durable run, no runId, by design.
        assert result is None and sessions == []


@pytest.mark.parametrize("emit_run_id", [False, True])
def test_smoke_end_to_end_reports_durable_state_honestly(smoke, monkeypatch, capsys, emit_run_id):
    import dua_engine.agent.agent_store as agent_store
    produced = []

    def agent_producer(messages, attachments, system, owner, cancelled=None):
        produced.append(messages[-1]["text"])
        def run(enqueue):
            if emit_run_id and messages[-1]["text"] == smoke.SMOKE_DURABLE_PROMPT:
                enqueue("event", {"type": "agent_session", "runId": "11111111-2222-4333-8444-555555555555", "resumable": True})
            enqueue("event", {"type": "text", "delta": "Agent answer."})
            enqueue("done")
        return run

    monkeypatch.setattr(engine, "agent_producer", agent_producer)
    monkeypatch.setenv("ADK_SESSION_DB_URL", "postgresql://runtime@db.invalid/postgres")
    monkeypatch.setenv("AGENT_ENGINE", "adk")
    async def get_task(owner, run_id):
        return SimpleNamespace(state={"dua_schema": 1, "dua_request": {"messages": []}, "dua_answer": "done"})
    monkeypatch.setattr(agent_store, "get_task", get_task)
    monkeypatch.setattr(smoke, "supabase_sign_in", lambda base, key, owner: TOKEN)
    monkeypatch.setattr(smoke, "http_json", _wrap_logout(smoke.http_json))
    service = {"spec": {"template": {"spec": {"containers": [{"env": [
        {"name": "SUPABASE_URL", "value": "https://project.supabase.co"},
        {"name": "SUPABASE_PUBLISHABLE_KEY", "value": "publishable"},
        {"name": "DUA_ALLOWED_EMAIL", "value": "owner@example.com"}]}]}}},
        "status": {"traffic": [{"revisionName": "old", "percent": 90},
                               {"revisionName": "cand", "percent": 10, "tag": "adk-candidate", "url": "https://adk-candidate---svc.run.app"}]}}
    smoke.smoke(service)
    out = capsys.readouterr().out
    report = json.loads(out[:out.rindex("}") + 1])
    assert produced == [smoke.SMOKE_AGENT_PROMPT, smoke.SMOKE_DURABLE_PROMPT]
    assert report["candidateTrafficPercent"] == 10 and report["chat"]["ok"] and report["agent"]["ok"]
    if emit_run_id:
        assert report["durableSession"] == "loaded_from_database" and "durable Supabase session verified" in out
    else:
        assert report["durableSession"] == "not_exercised" and "PASSED for chat and Agent" in out
    assert TOKEN not in out


def _wrap_logout(http_json):
    def call(url, payload=None, headers=None, timeout=30):
        if "/auth/v1/logout" in url:
            return 200, {}
        return http_json(url, payload, headers, timeout)
    return call


# ---------------------------------------------------------------- empty / non-JSON replies (204 logout, SSE resume)
class _Raw:
    def __init__(self, status, raw):
        self.status, self.raw = status, raw

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False

    def read(self, limit=-1):
        return self.raw


@pytest.mark.parametrize("status,raw", [(204, b""), (200, b'event: text\ndata: {"type":"text","delta":"hi"}\n\n'), (200, b"<html>ok</html>")])
def test_http_json_tolerates_empty_and_non_json_bodies(smoke, monkeypatch, status, raw):
    monkeypatch.setattr(smoke, "urlopen", lambda req, timeout=30: _Raw(status, raw))
    assert smoke.http_json("https://project.supabase.co/auth/v1/logout?scope=local", {}, {"Authorization": "Bearer " + TOKEN}) == (status, {})


def test_smoke_resumed_unfinished_run_is_not_a_crash(smoke, monkeypatch, capsys):
    """An unfinished saved run makes /api/chat resume and stream SSE (HTTP 200); this used to raise JSONDecodeError."""
    import dua_engine.agent.agent_store as agent_store

    def agent_producer(messages, attachments, system, owner, cancelled=None):
        def run(enqueue):
            if messages[-1]["text"] == smoke.SMOKE_DURABLE_PROMPT:
                enqueue("event", {"type": "agent_session", "runId": "11111111-2222-4333-8444-555555555555", "resumable": True})
            enqueue("event", {"type": "text", "delta": "Agent answer."})
            enqueue("done")
        return run

    monkeypatch.setattr(engine, "agent_producer", agent_producer)
    monkeypatch.setenv("ADK_SESSION_DB_URL", "postgresql://runtime@db.invalid/postgres")
    monkeypatch.setenv("AGENT_ENGINE", "adk")
    async def get_task(owner, run_id):
        return SimpleNamespace(state={"dua_schema": 1, "dua_request": {"messages": [{"role": "user", "text": "resume me"}]}})
    monkeypatch.setattr(agent_store, "get_task", get_task)
    monkeypatch.setattr(smoke, "supabase_sign_in", lambda base, key, owner: TOKEN)
    monkeypatch.setattr(smoke, "http_json", _wrap_logout(smoke.http_json))
    service = {"spec": {"template": {"spec": {"containers": [{"env": [
        {"name": "SUPABASE_URL", "value": "https://project.supabase.co"},
        {"name": "SUPABASE_PUBLISHABLE_KEY", "value": "publishable"},
        {"name": "DUA_ALLOWED_EMAIL", "value": "owner@example.com"}]}]}}},
        "status": {"traffic": [{"revisionName": "cand", "percent": 0, "tag": "adk-candidate", "url": "https://adk-candidate---svc.run.app"}]}}
    smoke.smoke(service)
    out = capsys.readouterr().out
    assert '"durableSession": "resumed_from_database"' in out and "durable Supabase session verified" in out
    assert TOKEN not in out
