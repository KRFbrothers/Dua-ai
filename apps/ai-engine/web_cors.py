"""CORS for the Dua AI website (browser calls to this engine).

WEB_ORIGINS: comma-separated exact origins allowed to call the engine from a browser.
Default: the dua-api website origin and the local Vite dev server. Set WEB_ORIGINS=off
to disable browser access entirely. Requests without an Origin header (the Android app,
curl, Cloud Scheduler) are untouched.

WEB_SKIP_APP_TOKEN=1 (default off): when DUA_APP_TOKEN is set, a browser cannot keep it
secret, so requests from an allowed web origin may skip that extra gate. Owner login
(Supabase Bearer token + DUA_ALLOWED_EMAIL) is still required for every request.

Implemented as WSGI middleware so streamed (SSE) responses pass through unbuffered.
"""
import os

DEFAULT_WEB_ORIGINS = (
    "https://dua-api-228305912525.us-central1.run.app",
    "http://localhost:5173",
)
ALLOW_METHODS = "GET, POST, OPTIONS"
ALLOW_HEADERS = "Authorization, Content-Type, Accept, X-Dua-Native, X-Dua-App-Token"
EXPOSE_HEADERS = "Content-Type"
MAX_AGE = "600"


def allowed_origins(env=None):
    env = os.environ if env is None else env
    raw = (env.get("WEB_ORIGINS") or "").strip()
    if raw.lower() in ("off", "none", "0"):
        return frozenset()
    items = raw.split(",") if raw else DEFAULT_WEB_ORIGINS
    return frozenset(o.strip().rstrip("/") for o in items if o.strip() and o.strip() != "*")


class CorsMiddleware:
    def __init__(self, wsgi_app, env=None):
        self.wsgi_app = wsgi_app
        self.env = env

    def __call__(self, environ, start_response):
        env = os.environ if self.env is None else self.env
        origin = environ.get("HTTP_ORIGIN", "")
        if not origin:
            return self.wsgi_app(environ, start_response)
        allowed = origin in allowed_origins(env)
        if environ.get("REQUEST_METHOD") == "OPTIONS" and environ.get("HTTP_ACCESS_CONTROL_REQUEST_METHOD"):
            if not allowed:
                start_response("403 Forbidden", [("Content-Length", "0"), ("Vary", "Origin")])
                return [b""]
            start_response("204 No Content", [
                ("Access-Control-Allow-Origin", origin),
                ("Access-Control-Allow-Methods", ALLOW_METHODS),
                ("Access-Control-Allow-Headers", ALLOW_HEADERS),
                ("Access-Control-Max-Age", MAX_AGE),
                ("Vary", "Origin"),
                ("Content-Length", "0"),
            ])
            return [b""]
        if not allowed:
            # Not an allowed site: serve normally but without CORS headers, so browsers block it.
            return self.wsgi_app(environ, start_response)
        expected = env.get("DUA_APP_TOKEN", "")
        if (expected and env.get("WEB_SKIP_APP_TOKEN") == "1"
                and not environ.get("HTTP_X_DUA_APP_TOKEN")):
            environ = dict(environ, HTTP_X_DUA_APP_TOKEN=expected)

        def cors_start_response(status, headers, exc_info=None):
            headers = [(k, v) for k, v in headers if k.lower() not in (
                "access-control-allow-origin", "access-control-expose-headers")]
            headers += [("Access-Control-Allow-Origin", origin),
                        ("Access-Control-Expose-Headers", EXPOSE_HEADERS)]
            if not any(k.lower() == "vary" for k, _ in headers):
                headers.append(("Vary", "Origin"))
            return start_response(status, headers, exc_info)

        return self.wsgi_app(environ, cors_start_response)

