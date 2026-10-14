import io
import json
import os
import sys
from types import SimpleNamespace
from urllib.error import HTTPError, URLError
from urllib.request import Request

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import app as engine
import owner_auth

OWNER = {"id": "d4b75d74-dc27-44d9-834b-2385731b8f47", "email": "owner@example.com",
         "email_confirmed_at": "2026-10-14T00:00:00Z", "is_anonymous": False}
ROUTES = ["/api/chat", "/chat"]


class AuthResponse(io.BytesIO):
    status = 200
    headers = {"Content-Type": "application/json; charset=utf-8"}

    def __init__(self, user=OWNER, raw=None):
        super().__init__(raw if raw is not None else json.dumps(user).encode())


@pytest.fixture
def auth(monkeypatch):
    monkeypatch.setenv("SUPABASE_URL", "https://project.supabase.co")
    monkeypatch.setenv("SUPABASE_ANON_KEY", "public-anon-key")
    monkeypatch.setenv("DUA_ALLOWED_EMAIL", " OWNER@example.com ")
    monkeypatch.delenv("SUPABASE_PUBLISHABLE_KEY", raising=False)
    monkeypatch.delenv("DUA_APP_TOKEN", raising=False)
    calls = []

    def verify(request, timeout):
        calls.append(request)
        assert request.full_url == "https://project.supabase.co/auth/v1/user"
        assert request.get_header("Authorization") == "Bearer owner.token"
        assert request.get_header("Apikey") == "public-anon-key"
        assert timeout == owner_auth.AUTH_TIMEOUT_SECONDS
        return AuthResponse()

    monkeypatch.setattr(owner_auth.auth_opener, "open", verify)
    provider_calls = []

    def generate(contents, stream=False):
        provider_calls.append(contents)
        chunk = SimpleNamespace(text="Hello", candidates=[])
        return iter([chunk]) if stream else chunk

    monkeypatch.setattr(engine, "get_model", lambda system=engine.SYSTEM_PROMPT: SimpleNamespace(generate_content=generate))
    monkeypatch.setattr(engine, "make_part", lambda text=None, **kwargs: text)
    monkeypatch.setattr(engine, "make_content", lambda role, parts: (role, parts))
    return calls, provider_calls


def post(route, authorization="Bearer owner.token", **fields):
    body = {"messages": [{"role": "user", "text": "Hi"}], "message": "Hi", **fields}
    return engine.app.test_client().post(route, json=body, headers={"Authorization": authorization})


@pytest.mark.parametrize("route", ROUTES)
@pytest.mark.parametrize("stream", [False, True])
def test_verified_owner_can_chat(route, stream, auth):
    response = post(route, stream=stream)
    assert response.status_code == 200
    assert "Hello" in response.get_data(as_text=True)
    assert len(auth[0]) == 1 and len(auth[1]) == 1


@pytest.mark.parametrize("route", ROUTES)
@pytest.mark.parametrize("header", ["", "Basic owner.token", "Bearer", "Bearer x y", "Bearer " + "x" * 10001])
def test_anonymous_or_malformed_token_never_calls_auth_or_vertex(route, header, auth):
    response = post(route, header, stream=True)
    assert response.status_code == 401
    assert response.headers["Cache-Control"] == "no-store"
    assert auth == ([], [])


@pytest.mark.parametrize("route", ROUTES)
@pytest.mark.parametrize("status", [401, 403])
def test_invalid_expired_or_deleted_user_is_denied_before_vertex(route, status, auth, monkeypatch):
    def reject(*args, **kwargs):
        raise HTTPError("https://project.supabase.co/auth/v1/user", status, "private details", {}, io.BytesIO(b"secret"))
    monkeypatch.setattr(owner_auth.auth_opener, "open", reject)
    response = post(route)
    assert response.status_code == 401 and auth[1] == []
    assert "secret" not in response.get_data(as_text=True)


@pytest.mark.parametrize("route", ROUTES)
@pytest.mark.parametrize("user", [
    {**OWNER, "email": "another@example.com", "user_metadata": {"email": "owner@example.com"}},
    {**OWNER, "email_confirmed_at": None},
    {**OWNER, "is_anonymous": True},
])
def test_non_owner_unconfirmed_and_anonymous_accounts_are_denied(route, user, auth, monkeypatch):
    monkeypatch.setattr(owner_auth.auth_opener, "open", lambda *args, **kwargs: AuthResponse(user))
    response = post(route)
    assert response.status_code == 403 and auth[1] == []


@pytest.mark.parametrize("name,value", [
    ("SUPABASE_URL", ""), ("SUPABASE_ANON_KEY", ""), ("DUA_ALLOWED_EMAIL", ""),
    ("SUPABASE_URL", "http://project.supabase.co"), ("SUPABASE_URL", "https://[invalid"),
    ("SUPABASE_URL", "https://user:password@project.supabase.co"),
    ("SUPABASE_URL", "https://project.supabase.co/path"),
    ("SUPABASE_ANON_KEY", "invalid\nkey"), ("DUA_ALLOWED_EMAIL", "invalid"),
])
def test_missing_or_invalid_configuration_fails_closed(name, value, auth, monkeypatch):
    monkeypatch.setenv(name, value)
    monkeypatch.setenv("DUA_REQUIRE_LOGIN", "0")  # Cannot disable this engine's owner lock.
    response = post("/api/chat")
    assert response.status_code == 503 and auth == ([], [])


@pytest.mark.parametrize("status", [302, 429, 500, 503])
def test_upstream_errors_fail_closed_without_exposing_details(status, auth, monkeypatch):
    def fail(*args, **kwargs):
        raise HTTPError("https://project.supabase.co/auth/v1/user", status, "secret owner.token", {}, io.BytesIO(b"secret"))
    monkeypatch.setattr(owner_auth.auth_opener, "open", fail)
    response = post("/api/chat")
    assert response.status_code == 503 and auth[1] == []
    assert "secret" not in response.get_data(as_text=True)


@pytest.mark.parametrize("failure", [TimeoutError("owner.token"), URLError("owner.token")])
def test_network_verification_failure_never_calls_vertex(failure, auth, monkeypatch):
    def fail(*args, **kwargs):
        raise failure
    monkeypatch.setattr(owner_auth.auth_opener, "open", fail)
    response = post("/chat")
    assert response.status_code == 503 and auth[1] == []
    assert "owner.token" not in response.get_data(as_text=True)


@pytest.mark.parametrize("raw", [b"not json", b"[]", b"{}", b"x" * (owner_auth.MAX_USER_RESPONSE_BYTES + 1),
                                  json.dumps({**OWNER, "id": "not-a-uuid"}).encode()])
def test_malformed_or_oversized_auth_responses_fail_closed(raw, auth, monkeypatch):
    response_body = AuthResponse(raw=raw)
    monkeypatch.setattr(owner_auth.auth_opener, "open", lambda *args, **kwargs: response_body)
    response = post("/api/chat")
    assert response.status_code == 503 and auth[1] == []
    assert response_body.closed


def test_auth_redirects_are_not_followed():
    handler = owner_auth.NoAuthRedirects()
    request = Request("https://project.supabase.co/auth/v1/user", headers={"Authorization": "Bearer owner.token"})
    for status in (301, 302, 303, 307, 308):
        assert handler.redirect_request(request, None, status, "redirect", {}, "https://other.example/") is None


def test_shared_apk_token_alone_cannot_authenticate_owner(auth, monkeypatch):
    monkeypatch.setenv("DUA_APP_TOKEN", "apk-token")
    client = engine.app.test_client()
    for route in ROUTES:
        response = client.post(route, json={"message": "Hi"}, headers={"X-Dua-App-Token": "apk-token"})
        assert response.status_code == 401
    assert auth == ([], [])
    response = client.post("/chat", json={"message": "Hi"},
                           headers={"Authorization": "Bearer owner.token", "X-Dua-App-Token": "apk-token"})
    assert response.status_code == 200 and len(auth[1]) == 1


def test_publishable_key_is_supported(auth, monkeypatch):
    monkeypatch.delenv("SUPABASE_ANON_KEY")
    monkeypatch.setenv("SUPABASE_PUBLISHABLE_KEY", "public-anon-key")
    assert post("/chat").status_code == 200


def test_health_does_not_require_owner_setup(auth, monkeypatch):
    monkeypatch.delenv("SUPABASE_URL")
    assert engine.app.test_client().get("/").status_code == 200
    assert auth == ([], [])
