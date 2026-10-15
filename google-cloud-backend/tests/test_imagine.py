import io
import json
import os
import sys
from types import SimpleNamespace
from urllib.error import HTTPError

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import app as engine  # noqa: E402
import imagine  # noqa: E402

OWNER = "8f14e45f-ceea-467f-a0e6-3c4b1d2f9a10"


class Resp(io.BytesIO):
    def __enter__(self):
        return self

    def __exit__(self, *a):
        self.close()


class Opener:
    def __init__(self, allowed=True, rpc_status=None, upload_status=None):
        self.calls, self.allowed, self.rpc_status, self.upload_status = [], allowed, rpc_status, upload_status

    def open(self, request, timeout=None):
        self.calls.append((request.get_method(), request.full_url, request.headers.get("Authorization"), request.data))
        url = request.full_url
        if "/rpc/dua_consume_imagine" in url:
            if self.rpc_status:
                raise HTTPError(url, self.rpc_status, "x", {}, io.BytesIO(b"{}"))
            return Resp(json.dumps({"allowed": self.allowed, "used": 3, "resetAt": 1760000000}).encode())
        if "/object/sign/" in url:
            return Resp(json.dumps({"signedURL": "/object/sign/dua-files/x.png?token=abc"}).encode())
        if "/object/" in url:
            if self.upload_status:
                raise HTTPError(url, self.upload_status, "secret detail", {}, io.BytesIO(b"{}"))
            return Resp(b'{"Key":"dua-files/x"}')
        raise AssertionError(url)


@pytest.fixture
def setup(monkeypatch):
    monkeypatch.setattr(engine, "verify_owner", lambda authorization: OWNER)
    monkeypatch.setenv("SUPABASE_URL", "https://proj.supabase.co")
    monkeypatch.setenv("SUPABASE_ANON_KEY", "anon")
    monkeypatch.delenv("DUA_APP_TOKEN", raising=False)
    monkeypatch.delenv("DUA_IMAGINE", raising=False)
    opener = Opener()
    monkeypatch.setattr(imagine, "auth_opener", opener)
    monkeypatch.setattr(engine, "get_client", lambda: "CLIENT")
    calls = []

    def fake_generate(client, prompt, aspect):
        calls.append((client, prompt, aspect))
        return b"\x89PNG-bytes", "image/png"
    monkeypatch.setattr(imagine, "generate", fake_generate)
    return SimpleNamespace(opener=opener, calls=calls, monkeypatch=monkeypatch)


def post(body, token="Bearer user.jwt"):
    return engine.app.test_client().post("/api/imagine/image", json=body, headers={"Authorization": token})


def test_one_image_stored_in_owner_folder_in_app_format(setup):
    r = post({"prompt": "a red kite over Lahore", "n": 4, "aspectRatio": "16:9"})
    assert r.status_code == 200
    body = r.get_json()
    assert setup.calls == [("CLIENT", "a red kite over Lahore", "16:9")]  # n capped to exactly one image
    [image] = body["images"]
    assert image["path"].startswith(OWNER + "/") and image["path"].endswith(".png")
    assert image["mimeType"] == "image/png" and image["size"] == len(b"\x89PNG-bytes")
    assert image["url"].startswith("https://proj.supabase.co/storage/v1/object/sign/") and "token=" in image["url"]
    assert body["provider"] == "gemini" and body["model"] == imagine.MODEL and body["edit"] is False
    assert body["usage"]["images"] == {"used": 3, "limit": 20}
    # Every Supabase call uses the owner's own session, never a service key.
    assert all(auth == "Bearer user.jwt" for _, _, auth, _ in setup.opener.calls)
    rpc = json.loads(setup.opener.calls[0][3])
    assert rpc == {"feature": "image", "amount": 1, "daily_limit": 20}


def test_daily_cap_blocks_before_generating(setup):
    setup.opener.allowed = False
    r = post({"prompt": "x"})
    assert r.status_code == 429 and "Daily Imagine limit" in r.get_json()["error"] and setup.calls == []


def test_validation(setup):
    assert post({"prompt": ""}).status_code == 400
    assert post({"prompt": "x" * 4001}).status_code == 400
    assert post({"prompt": "x", "aspectRatio": "21:9"}).status_code == 400
    assert post({"prompt": "edit", "sourcePaths": [OWNER + "/a.png"]}).status_code == 400
    assert setup.calls == []


def test_requires_owner(setup):
    def deny(authorization):
        raise engine.OwnerAuthError("Only the configured personal account can use online AI.", 403)
    setup.monkeypatch.setattr(engine, "verify_owner", deny)
    assert post({"prompt": "x"}).status_code == 403 and setup.calls == []


def test_storage_failure_is_generic(setup):
    setup.opener.upload_status = 403
    r = post({"prompt": "x"})
    assert r.status_code == 502 and "secret" not in r.get_data(as_text=True)


def test_model_failure_does_not_leak(setup):
    def boom(client, prompt, aspect):
        raise RuntimeError("quota secret")
    setup.monkeypatch.setattr(imagine, "generate", boom)
    r = post({"prompt": "x"})
    assert r.status_code == 502 and "secret" not in r.get_data(as_text=True)


def test_missing_migration_is_explained(setup):
    setup.opener.rpc_status = 404
    r = post({"prompt": "x"})
    assert r.status_code == 503 and "migration" in r.get_json()["error"]


def test_capability_flag_and_switch(setup):
    info = engine.app.test_client().get("/").get_json()
    assert info["imagine"]["image"] is True and info["imagine"]["maxImages"] == 1
    setup.monkeypatch.setenv("DUA_IMAGINE", "0")
    assert engine.app.test_client().get("/").get_json()["imagine"] == {"image": False}
    assert post({"prompt": "x"}).status_code == 503


def test_first_image_picks_inline_image_and_explains_blocks():
    part = SimpleNamespace(inline_data=SimpleNamespace(data=b"img", mime_type="image/png"))
    text = SimpleNamespace(inline_data=None)
    response = SimpleNamespace(candidates=[SimpleNamespace(content=SimpleNamespace(parts=[text, part]))])
    assert imagine.first_image(response) == (b"img", "image/png")
    with pytest.raises(imagine.ImagineError) as blocked:
        imagine.first_image(SimpleNamespace(candidates=[]))
    assert blocked.value.status == 422
