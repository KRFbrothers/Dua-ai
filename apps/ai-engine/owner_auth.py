"""Verify the existing Supabase session before allowing private Vertex chat."""
import json
import os
import re
import uuid
from urllib.error import HTTPError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener

AUTH_TIMEOUT_SECONDS = 10
MAX_USER_RESPONSE_BYTES = 64 * 1024


class OwnerAuthError(Exception):
    def __init__(self, message, status):
        super().__init__(message)
        self.message = message
        self.status = status


class NoAuthRedirects(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Never forward the owner's bearer token to a redirected destination.
        return None


auth_opener = build_opener(NoAuthRedirects())


def verify_owner(authorization):
    """Validate with Auth's /user endpoint, not unverified JWT/user_metadata claims.

    Verification is per request so expired/deleted identities are not accepted from
    an application cache. Supabase's own access-token/session lifetime still applies.
    Owner login is always required, even if DUA_REQUIRE_LOGIN is absent or disabled.
    """
    match = re.fullmatch(r"Bearer ([A-Za-z0-9._-]{1,10000})", authorization or "", re.IGNORECASE)
    if not match:
        raise OwnerAuthError("Authentication required. Sign in from Profile before using online AI.", 401)

    url = os.environ.get("SUPABASE_URL", "").strip().rstrip("/")
    key = (os.environ.get("SUPABASE_PUBLISHABLE_KEY") or os.environ.get("SUPABASE_ANON_KEY", "")).strip()
    owner = os.environ.get("DUA_ALLOWED_EMAIL", "").strip().lower()
    try:
        parsed = urlsplit(url)
    except ValueError:
        raise OwnerAuthError("Owner authentication setup is required on the Dua AI engine.", 503) from None
    if (parsed.scheme != "https" or not parsed.hostname or parsed.username is not None or
            parsed.password is not None or parsed.path or parsed.query or parsed.fragment or
            not key or len(key) > 10000 or re.search(r"\s", key) or
            not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", owner)):
        raise OwnerAuthError("Owner authentication setup is required on the Dua AI engine.", 503)

    unavailable = OwnerAuthError("Account verification is unavailable. Retry without changing your login.", 503)
    auth_request = Request(url + "/auth/v1/user", headers={
        "Authorization": "Bearer " + match.group(1), "apikey": key, "Accept": "application/json",
    })
    try:
        with auth_opener.open(auth_request, timeout=AUTH_TIMEOUT_SECONDS) as response:
            if response.status != 200 or "application/json" not in response.headers.get("Content-Type", "").lower():
                raise unavailable
            raw = response.read(MAX_USER_RESPONSE_BYTES + 1)
            if len(raw) > MAX_USER_RESPONSE_BYTES:
                raise unavailable
            user = json.loads(raw)
    except HTTPError as error:
        status = error.code
        error.close()
        if status in (401, 403):
            raise OwnerAuthError("Account verification failed. Sign in and retry.", 401) from None
        raise unavailable from None
    except OwnerAuthError:
        raise
    except Exception:
        # Never return/log the token, Auth response or provider exception details.
        raise unavailable from None

    try:
        if not isinstance(user, dict) or not isinstance(user.get("id"), str):
            raise ValueError("Invalid account response")
        uuid.UUID(user["id"])
        email = user.get("email")
        if not isinstance(email, str):
            raise ValueError("Invalid account response")
    except (ValueError, TypeError, AttributeError):
        raise unavailable from None
    if (user.get("is_anonymous") is True or not isinstance(user.get("email_confirmed_at"), str) or
            not user["email_confirmed_at"].strip()):
        raise OwnerAuthError("A verified owner account is required. Sign in and retry.", 403)
    if email.strip().lower() != owner:
        raise OwnerAuthError("Only the configured personal account can use online AI.", 403)
    return user["id"]
