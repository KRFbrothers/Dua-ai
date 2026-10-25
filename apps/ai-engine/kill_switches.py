"""Remote kill switches from Cloud Run env vars (all default off). Same names and messages as dua-api (apps/api/src/core/kill-switches.mjs).

DUA_DISABLE_AGENT=1   Agent mode is refused here, so it stops even for old APKs.
DUA_DISABLE_IMAGINE=1 The engine's Imagine endpoint is refused.
DUA_MAINTENANCE=1     Chat, Agent and Imagine are refused with DUA_MAINTENANCE_MESSAGE (optional).
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
