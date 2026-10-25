import sys
import time
from pathlib import Path

import pytest
from flask import Flask, Response, request

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from web_cors import CorsMiddleware, allowed_origins

SITE = "https://dua-api-228305912525.us-central1.run.app"


def make_app(env):
    flask_app = Flask(__name__)

    @flask_app.post("/api/chat")
    def chat():
        if env.get("DUA_APP_TOKEN") and request.headers.get("X-Dua-App-Token") != env["DUA_APP_TOKEN"]:
            return {"error": "token"}, 401

        def gen():
            for i in range(3):
                yield f"event: text\ndata: {i}\n\n"
                time.sleep(0.01)
        return Response(gen(), mimetype="text/event-stream")

    flask_app.wsgi_app = CorsMiddleware(flask_app.wsgi_app, env=env)
    return flask_app.test_client()


def test_default_origins_and_off():
    assert SITE in allowed_origins({})
    assert "http://localhost:5173" in allowed_origins({})
    assert allowed_origins({"WEB_ORIGINS": "off"}) == frozenset()
    assert allowed_origins({"WEB_ORIGINS": "*, https://a.example/"}) == {"https://a.example"}


def test_preflight_allowed_and_denied():
    client = make_app({})
    ok = client.options("/api/chat", headers={"Origin": SITE, "Access-Control-Request-Method": "POST",
                                              "Access-Control-Request-Headers": "authorization,content-type,x-dua-native"})
    assert ok.status_code == 204
    assert ok.headers["Access-Control-Allow-Origin"] == SITE
    allow = ok.headers["Access-Control-Allow-Headers"].lower()
    for header in ("authorization", "content-type", "x-dua-native", "x-dua-app-token"):
        assert header in allow
    bad = client.options("/api/chat", headers={"Origin": "https://evil.example", "Access-Control-Request-Method": "POST"})
    assert bad.status_code == 403
    assert "Access-Control-Allow-Origin" not in bad.headers


def test_streaming_response_gets_cors_headers_and_full_body():
    client = make_app({})
    res = client.post("/api/chat", headers={"Origin": SITE}, json={})
    assert res.headers["Access-Control-Allow-Origin"] == SITE
    assert res.headers["Content-Type"].startswith("text/event-stream")
    assert res.get_data(as_text=True).count("event: text") == 3


def test_unknown_origin_and_native_requests_unchanged():
    client = make_app({})
    assert "Access-Control-Allow-Origin" not in client.post("/api/chat", headers={"Origin": "https://evil.example"}, json={}).headers
    assert "Access-Control-Allow-Origin" not in client.post("/api/chat", json={}).headers


@pytest.mark.parametrize("skip,origin,status", [
    ("1", SITE, 200), ("", SITE, 401), ("1", "https://evil.example", 401), ("1", None, 401)])
def test_app_token_skip_only_for_allowed_origin_when_enabled(skip, origin, status):
    client = make_app({"DUA_APP_TOKEN": "secret-x", "WEB_SKIP_APP_TOKEN": skip})
    headers = {"Origin": origin} if origin else {}
    assert client.post("/api/chat", headers=headers, json={}).status_code == status


def test_wsgi_entry_wraps_engine():
    pytest.importorskip("flask")
    try:
        import wsgi
    except Exception as error:  # engine import needs its own optional deps
        pytest.skip(f"engine import unavailable: {error}")
    assert isinstance(wsgi.app.wsgi_app, CorsMiddleware)
