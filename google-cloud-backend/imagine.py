"""Imagine (image generation) on Google Cloud: Gemini image model on Vertex AI, stored in the owner's Supabase bucket.

Same request/response shape as the Node API's POST /api/imagine/image, so the app's Imagine UI is unchanged:
  request  {"prompt": "...", "n": 1, "aspectRatio": "1:1"}
  response {"images": [{"path", "mimeType", "size", "url", "expiresIn"}], "provider": "gemini", "model", "edit": false,
            "usage": {"images": {"used", "limit"}, "resetAt"}}
Storage and the daily cap use the owner's own Supabase session (row-level security; no service-role key).
"""
import json
import os
import uuid
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import Request

from owner_auth import auth_opener

# Nano Banana 2 Lite: cheapest Gemini image model on Vertex (global), ~$0.034 per 1K image. Override with IMAGINE_MODEL.
MODEL = os.environ.get("IMAGINE_MODEL", "gemini-3.1-flash-lite-image").strip()
ASPECTS = ("auto", "1:1", "3:4", "4:3", "9:16", "16:9")
MAX_PROMPT = 4000
MAX_IMAGE_BYTES = 10 * 1024 * 1024
RESULT_URL_SECONDS = 3600
EXTENSIONS = {"image/png": "png", "image/jpeg": "jpg", "image/webp": "webp"}


class ImagineError(Exception):
    def __init__(self, message, status=502):
        super().__init__(message)
        self.message = message
        self.status = status


def daily_limit():
    try:
        return max(1, min(500, int(os.environ.get("IMAGINE_DAILY_IMAGES", "20"))))
    except ValueError:
        return 20


def enabled():
    return os.environ.get("DUA_IMAGINE", "1").strip() != "0" and bool(MODEL)


def capability():
    return {"image": True, "model": MODEL, "maxImages": 1, "aspectRatios": list(ASPECTS), "dailyLimit": daily_limit()} if enabled() else {"image": False}


def validate(body):
    if not isinstance(body, dict):
        raise ImagineError("Invalid request.", 400)
    prompt = body.get("prompt")
    if not isinstance(prompt, str) or not prompt.strip():
        raise ImagineError("Describe the image to create.", 400)
    if len(prompt) > MAX_PROMPT:
        raise ImagineError("Keep the description under 4,000 characters.", 400)
    if body.get("sourcePaths") or body.get("images"):
        raise ImagineError("Photo edits are not available on the Google Cloud engine yet.", 400)
    aspect = body.get("aspectRatio", "1:1")
    if aspect not in ASPECTS:
        raise ImagineError("Unsupported aspect ratio.", 400)
    # Cost cap: always exactly one image per request.
    return prompt.strip(), aspect


def _supabase():
    url = os.environ.get("SUPABASE_URL", "").strip().rstrip("/")
    key = (os.environ.get("SUPABASE_PUBLISHABLE_KEY") or os.environ.get("SUPABASE_ANON_KEY", "")).strip()
    bucket = os.environ.get("DUA_FILES_BUCKET", "dua-files").strip()
    if not url.startswith("https://") or not key:
        raise ImagineError("Imagine storage is not set up on the Google Cloud engine.", 503)
    return url, key, bucket


def _call(request, limit=65536):
    with auth_opener.open(request, timeout=30) as response:
        return json.loads(response.read(limit) or b"null")


def consume(token, amount=1):
    """Daily cap through the same Supabase RPC as the Node API (dua_consume_imagine)."""
    url, key, _ = _supabase()
    limit = daily_limit()
    request = Request(url + "/rest/v1/rpc/dua_consume_imagine", method="POST",
                      data=json.dumps({"feature": "image", "amount": amount, "daily_limit": limit}).encode(),
                      headers={"apikey": key, "Authorization": "Bearer " + token, "Content-Type": "application/json"})
    try:
        data = _call(request)
    except HTTPError as error:
        status = error.code
        error.close()
        if status == 404:
            raise ImagineError("Imagine usage caps need the Supabase Imagine migration (see SUPABASE.md).", 503) from None
        raise ImagineError("Imagine usage storage is unavailable. Retry.", 503) from None
    except Exception:
        raise ImagineError("Imagine usage storage is unavailable. Retry.", 503) from None
    if not isinstance(data, dict) or not isinstance(data.get("allowed"), bool) or not isinstance(data.get("used"), int):
        raise ImagineError("Imagine usage storage returned an invalid response. Retry.", 503)
    usage = {"images": {"used": data["used"], "limit": limit}, "resetAt": data.get("resetAt", 0)}
    if not data["allowed"]:
        raise ImagineError(f"Daily Imagine limit reached: {data['used']} of {limit} images used. It resets at midnight UTC.", 429)
    return usage


def store(token, user_id, data, mime):
    url, key, bucket = _supabase()
    path = f"{user_id.lower()}/{uuid.uuid4()}.{EXTENSIONS[mime]}"
    object_url = f"{url}/storage/v1/object/{quote(bucket)}/{quote(path)}"
    try:
        _call(Request(object_url, data=data, method="POST", headers={
            "apikey": key, "Authorization": "Bearer " + token, "Content-Type": mime, "x-upsert": "false"}))
        signed = _call(Request(f"{url}/storage/v1/object/sign/{quote(bucket)}/{quote(path)}", method="POST",
                               data=json.dumps({"expiresIn": RESULT_URL_SECONDS}).encode(),
                               headers={"apikey": key, "Authorization": "Bearer " + token, "Content-Type": "application/json"}))
    except Exception:
        raise ImagineError("The image was created but could not be saved to your storage. Retry.", 502) from None
    relative = (signed or {}).get("signedURL") or (signed or {}).get("signedUrl") or ""
    if not isinstance(relative, str) or "token=" not in relative:
        raise ImagineError("The image was created but could not be saved to your storage. Retry.", 502)
    link = url + "/storage/v1" + (relative if relative.startswith("/") else "/" + relative)
    return {"path": path, "mimeType": mime, "size": len(data), "url": link, "expiresIn": RESULT_URL_SECONDS}


def generate(client, prompt, aspect):
    """One image from the Gemini image model. Returns (bytes, mime)."""
    from google.genai import types
    image_config = types.ImageConfig(aspect_ratio=aspect) if aspect != "auto" else None
    response = client.models.generate_content(model=MODEL, contents=prompt, config=types.GenerateContentConfig(
        response_modalities=["IMAGE"], candidate_count=1, image_config=image_config))
    return first_image(response)


def first_image(response):
    for candidate in getattr(response, "candidates", None) or []:
        content = getattr(candidate, "content", None)
        for part in getattr(content, "parts", None) or []:
            inline = getattr(part, "inline_data", None)
            data = getattr(inline, "data", None)
            mime = getattr(inline, "mime_type", None)
            if data and mime in EXTENSIONS:
                if len(data) > MAX_IMAGE_BYTES:
                    raise ImagineError("The image was too large to save. Try again.", 502)
                return data, mime
    raise ImagineError("No image came back (the prompt may have been blocked). Try a different description.", 422)


def handle(body, token, user_id, client):
    prompt, aspect = validate(body)
    usage = consume(token)
    data, mime = generate(client, prompt, aspect)
    image = store(token, user_id, data, mime)
    return {"images": [image], "provider": "gemini", "model": MODEL, "edit": False, "usage": usage}
