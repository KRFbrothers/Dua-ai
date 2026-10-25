"""Engine implementation of packages/contracts/KILL_SWITCHES.md (authoritative).

DUA_DISABLE_AGENT and DUA_DISABLE_IMAGINE pause the corresponding engine work.
DUA_MAINTENANCE pauses selected AI work; DUA_MAINTENANCE_MESSAGE is optional.
DUA_DISABLE_VOICE belongs to the API only; this engine does not implement it.
All switches default off. Each service reads its own Cloud Run environment;
shared names/messages do not synchronize service configuration automatically.
"""
import os
import re

DEFAULT_MAINTENANCE_MESSAGE = "Dua is taking a short break for maintenance. Please check back soon."
AGENT_PAUSED = "Agent mode is paused right now. Normal chat still works; try Agent again later."
IMAGINE_PAUSED = "Imagine is paused right now. Please try again later."


def on(name):
    return os.environ.get(name, "").strip().lower() in ("1", "true", "yes", "on")


def agent_disabled():
    return on("DUA_DISABLE_AGENT")


def imagine_disabled():
    return on("DUA_DISABLE_IMAGINE")


def maintenance_message():
    """None when maintenance is off; otherwise the cleaned message (max 300 characters)."""
    if not on("DUA_MAINTENANCE"):
        return None
    text = re.sub(r"[\x00-\x1f\x7f]+", " ", os.environ.get("DUA_MAINTENANCE_MESSAGE", "")).strip()[:300]
    return text or DEFAULT_MAINTENANCE_MESSAGE
