"""Per-invocation budgets shared with the internal reviewer, not model instructions."""
from contextlib import contextmanager
from contextvars import ContextVar
import time
from google.genai import types
from google.adk.models.llm_response import LlmResponse

ACTIVE = ContextVar("dua_shared_budget", default=None)
KEY = "dua:budget"
LIMITS = {"model_calls": 15, "tool_calls": 40, "tokens": 60_000, "seconds": 600}


def get_budget(context):
    active = ACTIVE.get()
    if active is not None:
        return active
    current = context.state.get(KEY)
    if not current or current["invocation_id"] != context.invocation_id:
        current = {"invocation_id": context.invocation_id, "started": time.time(),
                   "model_calls": 0, "tool_calls": 0, "tokens": 0, "stopped": ""}
        context.state[KEY] = current
    return current


def exhausted(budget):
    if budget["stopped"]:
        return budget["stopped"]
    if time.time() - budget["started"] >= LIMITS["seconds"]:
        budget["stopped"] = "Task time budget reached. No more work scheduled."
    elif budget["tokens"] >= LIMITS["tokens"]:
        budget["stopped"] = "Reported token budget reached. No more model/tool calls."
    return budget["stopped"]


def before_model(callback_context, llm_request):
    budget = get_budget(callback_context)
    reason = exhausted(budget)
    if not reason and budget["model_calls"] >= LIMITS["model_calls"]:
        reason = budget["stopped"] = "Model call budget reached."
    if reason:
        callback_context.state[KEY] = dict(budget)
        return LlmResponse(content=types.Content(role="model", parts=[
            types.Part.from_text(text=reason + " Pending work/tests are not complete; provide a smaller task.")]))
    budget["model_calls"] += 1
    callback_context.state[KEY] = dict(budget)
    return None


def after_model(callback_context, llm_response):
    budget = get_budget(callback_context)
    usage = llm_response.usage_metadata
    if usage and not llm_response.partial:
        budget["tokens"] += usage.total_token_count or 0
    exhausted(budget)
    callback_context.state[KEY] = dict(budget)
    return None


def before_tool(tool, args, tool_context):
    budget = get_budget(tool_context)
    reason = exhausted(budget)
    if not reason and budget["tool_calls"] >= LIMITS["tool_calls"]:
        reason = budget["stopped"] = "Tool call budget reached."
    if reason:
        tool_context.state[KEY] = dict(budget)
        return {"error": reason, "status": "stopped"}
    budget["tool_calls"] += 1
    tool_context.state[KEY] = dict(budget)
    return None


@contextmanager
def shared_budget(context):
    budget = get_budget(context)
    token = ACTIVE.set(budget)
    try:
        yield budget
    finally:
        context.state[KEY] = dict(budget)
        ACTIVE.reset(token)
