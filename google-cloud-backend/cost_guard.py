"""Daily cost guard for the Dua AI engine (Agent runs, chat requests, model tokens).

Caps (UTC day, reset at 00:00 UTC = 05:30 India time). Each is an env var; the owner can change it with
`gcloud run services update ... --update-env-vars NAME=value`. 0 turns that cap off; a bad value uses the default.
  DUA_DAILY_AGENT_RUNS      Agent-mode requests per day          (default 50)
  DUA_DAILY_CHAT_REQUESTS   normal chat requests per day         (default 500)
  DUA_DAILY_TOKENS          Vertex tokens per day, input+output  (default 5,000,000)

Counters live in memory on each Cloud Run instance (no new library, no database write per request). They reset
when an instance restarts, and with --max-instances 2 the real worst case is about twice each cap. That is a
deliberate trade-off: this is a cost brake against runaway loops and abuse, not exact billing. Token use is read
from Vertex usage metadata of calls that go through app.GenaiModel (chat, router, Agent loop steps and sub-calls,
self-check, notes, titles); the optional ADK engine's own model calls are not counted (its runs still count as
Agent runs). A request already running is never cut off; the caps are checked when a request starts.
"""
import datetime
import os
import threading

DEFAULTS = {"agent": ("DUA_DAILY_AGENT_RUNS", 50), "chat": ("DUA_DAILY_CHAT_REQUESTS", 500),
            "tokens": ("DUA_DAILY_TOKENS", 5_000_000)}
MAX_VALUE = 10**12
LABELS = {"agent": "Agent runs", "chat": "chat messages", "tokens": "AI usage (tokens)"}
RESET_HINT = "It resets at midnight UTC (5:30 AM India time)."


def limit(kind, env=None):
    """Configured cap for one counter: a non-negative integer, 0 = no cap. Invalid values fall back to the default."""
    env = os.environ if env is None else env
    name, default = DEFAULTS[kind]
    raw = str(env.get(name, "") or "").strip().replace("_", "")
    if not raw:
        return default
    if not raw.isdigit():
        return default
    value = int(raw)
    return value if value <= MAX_VALUE else default


def _utc_day(now):
    return now.astimezone(datetime.timezone.utc).date().isoformat()


def seconds_to_reset(now):
    now = now.astimezone(datetime.timezone.utc)
    tomorrow = (now + datetime.timedelta(days=1)).replace(hour=0, minute=0, second=0, microsecond=0)
    return max(1, int((tomorrow - now).total_seconds()))


class Budget:
    """Thread-safe per-instance daily counters."""

    def __init__(self, clock=None):
        self._clock = clock or (lambda: datetime.datetime.now(datetime.timezone.utc))
        self._lock = threading.Lock()
        self._day = None
        self._used = {}

    def _roll(self):
        day = _utc_day(self._clock())
        if day != self._day:
            self._day, self._used = day, {"agent": 0, "chat": 0, "tokens": 0}

    def reset(self):
        with self._lock:
            self._day, self._used = None, {}

    def used(self):
        with self._lock:
            self._roll()
            return dict(self._used)

    def admit(self, kind, env=None):
        """Counts one request of `kind` ("agent" or "chat") if it is allowed.

        Returns None when admitted, or (message, retry_after_seconds) when a daily cap (that kind or tokens) is full."""
        with self._lock:
            self._roll()
            for name in (kind, "tokens"):
                cap = limit(name, env)
                if cap and self._used[name] >= cap:
                    return blocked_message(name, cap), seconds_to_reset(self._clock())
            self._used[kind] += 1
            return None

    def add_tokens(self, count):
        try:
            count = int(count or 0)
        except (TypeError, ValueError):
            return
        if count <= 0:
            return
        with self._lock:
            self._roll()
            self._used["tokens"] += min(count, MAX_VALUE)


def blocked_message(kind, cap):
    name, _ = DEFAULTS[kind]
    amount = f"{cap:,}"
    return (f"Today's limit for {LABELS[kind]} is used up ({amount} per day), so I paused to protect your Google Cloud "
            f"credit. {RESET_HINT} To change it, the owner can set {name} on the dua-ai-engine service.")


def usage_tokens(response):
    """total_token_count from Vertex usage metadata, or 0 when the response carries none."""
    meta = getattr(response, "usage_metadata", None)
    total = getattr(meta, "total_token_count", None) if meta is not None else None
    if total is None and meta is not None:
        total = (getattr(meta, "prompt_token_count", 0) or 0) + (getattr(meta, "candidates_token_count", 0) or 0) \
            + (getattr(meta, "thoughts_token_count", 0) or 0)
    try:
        return max(0, int(total or 0))
    except (TypeError, ValueError):
        return 0


def counted_stream(iterator, budget):
    """Yields a streamed reply unchanged and records its tokens once (the last chunk carries the totals).

    close() is forwarded so the caller can still stop the Vertex stream early."""
    last = 0
    try:
        for chunk in iterator:
            last = usage_tokens(chunk) or last
            yield chunk
    finally:
        budget.add_tokens(last)
        close = getattr(iterator, "close", None)
        if callable(close):
            try:
                close()
            except Exception:
                pass


BUDGET = Budget()
