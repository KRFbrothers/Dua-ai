"""Bounded request-local coding checklist; never proof of tool execution."""
from dua_engine.agent.agent_policy import filter_text
from dua_engine.coding.coding_agent import CodingError

MAX_STEPS = 8
MAX_UPDATES = 12
STATUSES = frozenset(("pending", "in_progress", "completed"))


def validate(args):
    if not isinstance(args, dict):
        raise CodingError("Plan arguments must be an object.")
    steps = args.get("steps")
    explanation = args.get("explanation", "")
    if not isinstance(steps, list) or not 1 <= len(steps) <= MAX_STEPS:
        raise CodingError("Provide 1–8 plan steps.")
    if not isinstance(explanation, str) or len(explanation) > 500:
        raise CodingError("Plan explanation must be text up to 500 characters.")
    clean, seen = [], set()
    for item in steps:
        if not isinstance(item, dict) or set(item) != {"step", "status"}:
            raise CodingError("Each plan step needs only step and status.")
        text, status = item["step"], item["status"]
        if (not isinstance(text, str) or not text.strip() or len(text) > 140 or
                any(ord(c) < 32 for c in text) or not isinstance(status, str) or status not in STATUSES):
            raise CodingError("Use non-empty single-line steps up to 140 characters and pending/in_progress/completed status.")
        text = text.strip()
        if text.casefold() in seen:
            raise CodingError("Plan steps must be distinct.")
        seen.add(text.casefold())
        clean.append({"step": text, "status": status})
    if sum(item["status"] == "in_progress" for item in clean) > 1:
        raise CodingError("Only one plan step may be in progress.")
    return clean, explanation.strip()


class Plan:
    # CodingSession serializes updates with its existing request-local lock.
    def __init__(self):
        self.revision = 0

    def update(self, args):
        steps, explanation = validate(args)
        if self.revision >= MAX_UPDATES:
            raise CodingError("Plan update limit reached for this task.")
        self.revision += 1
        return {"plan": {"revision": self.revision, "steps": steps, "explanation": explanation},
                "note": "Agent-reported checklist only. Completion is not proof of tests, approvals or external actions. Plan lasts only this task."}


def preview(value):
    """Validate again at the UI boundary and redact without changing revisions."""
    if not isinstance(value, dict) or type(value.get("revision")) is not int or not 1 <= value["revision"] <= MAX_UPDATES:
        raise CodingError("Invalid plan revision.")
    steps, explanation = validate(value)
    return {"revision": value["revision"],
            "steps": [{"step": filter_text(item["step"])[:140], "status": item["status"]} for item in steps],
            "explanation": filter_text(explanation)[:500]}
