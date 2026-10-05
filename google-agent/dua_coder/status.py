"""Small phone status derived from stored evidence, without source or credentials."""
from google.adk.tools import ToolContext
from .budget import KEY as BUDGET_KEY, LIMITS
from .proposals import KEY as PROPOSALS_KEY


def task_status(tool_context: ToolContext) -> dict:
    """Show stored patch/review/PR and validation state; this performs no remote checks."""
    proposals = tool_context.state.get(PROPOSALS_KEY, {})
    newest = max(proposals.values(), key=lambda p: p.get("created", 0), default=None)
    patch = None
    if newest:
        review = newest.get("review") or {}
        result = newest.get("result") or {}
        patch = {"status": newest["status"], "base_sha": newest["base_sha"],
                 "files": [c["path"] for c in newest["changes"]],
                 "review": review.get("verdict", "not_reviewed"),
                 "pr_url": result.get("url"), "compare_url": result.get("compare_url"),
                 "branch": result.get("branch"), "head_sha": result.get("commit_sha")}
    validation = tool_context.state.get("dua:validation_result", {}).get("result")
    # Never attach evidence for another patch/commit to the latest proposed change.
    matches = bool(patch and validation and patch["head_sha"]
                   and validation.get("head_sha") == patch["head_sha"]
                   and validation.get("branch") == patch["branch"])
    checks = {"status": validation["status"] if matches else "not_verified",
              "head_sha": patch["head_sha"] if patch else None,
              "url": ((validation.get("latest") or {}).get("url") if matches else None)}
    budget = tool_context.state.get(BUDGET_KEY, {})
    workers = tool_context.state.get("dua:delegation_result", {})
    worker_summary = workers.get("result") if workers.get("invocation_id") == tool_context.invocation_id else None
    return {"patch": patch, "checks": checks, "workers": worker_summary,
            "budget": {k: budget.get(k, 0) for k in ("model_calls", "tool_calls", "tokens")},
            "limits": dict(LIMITS), "stopped": budget.get("stopped", ""),
            "note": "Stored snapshot only; refresh exact-commit checks for current status. No local/device/live verification."}
