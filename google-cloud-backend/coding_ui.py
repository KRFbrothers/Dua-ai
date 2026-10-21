"""Bounded, redacted owner UI events. Never calls tools or reads repository files."""
import secrets
import shlex

from agent_policy import filter_text

TOOLS = frozenset(("workspace_status", "edit_file", "workspace_diff", "run_command",
                   "import_command_changes", "checkpoint_workspace", "restore_workspace", "open_pr_workspace"))


def started(agent, name, args):
    if name != "run_command":
        return None
    activity = secrets.token_hex(8)
    argv = args.get("argv") if isinstance(args, dict) else None
    label = shlex.join(argv) if isinstance(argv, list) and len(argv) <= 64 and all(isinstance(a, str) and len(a) <= 4096 for a in argv) else "Command"
    agent.emit({"type": "coding", "terminal": {"id": activity, "command": filter_text(label)[:600],
                                                 "state": "preparing"}})
    return activity


def completed(agent, name, result, activity):
    if name not in TOOLS or not isinstance(result, dict):
        return
    event = {"type": "coding"}
    if activity:
        event["terminal"] = {"id": activity, "state": result.get("state", "error"),
                             "output": filter_text(str(result.get("output", result.get("error", ""))))[:32_000],
                             "exitCode": result.get("exit_code"), "truncated": bool(result.get("truncated"))}
        if result.get("artifact_error"):
            combined = event["terminal"]["output"] + "\nText export unavailable: " + filter_text(str(result["artifact_error"]))[:300]
            event["terminal"]["output"] = combined[:32_000]
            event["terminal"]["truncated"] = event["terminal"]["truncated"] or len(combined) > 32_000
    # A deadline must not wait on a tool that is still unwinding. A later
    # successful tool event can refresh the workspace metadata.
    current_fingerprint = None
    session = agent.coding
    if session is not None and session.lock.acquire(blocking=False):
        try:
            workspace = session.workspace
            if workspace is not None and workspace.lock.acquire(blocking=False):
                try:
                    if workspace.base_sha:
                        current_fingerprint = workspace.fingerprint()
                        event["workspace"] = {"repository": workspace.github.repo, "base": workspace.base_sha,
                            "paths": sorted(workspace.staged)[:10], "checkpointId": workspace.checkpoint_id,
                            "revision": workspace.checkpoint_revision, "prTarget": workspace.pr_target,
                            "saved": workspace.saved_fingerprint == current_fingerprint and
                                result.get("checkpoint", {}).get("saved") is not False}
                        _finish(agent, name, result, event, current_fingerprint)
                        return
                finally:
                    workspace.lock.release()
        finally:
            session.lock.release()
    # If the workspace is locked by a timed-out tool, still finish the
    # terminal card without blocking or showing inconsistent draft metadata.
    if activity:
        agent.emit(event)


def _finish(agent, name, result, event, current_fingerprint):
    # Emit while holding the draft locks, so a later edit/reset cannot be
    # followed by an older diff event from a concurrently finishing call.
    if "error" not in result:
        if name in ("edit_file", "import_command_changes", "restore_workspace", "open_pr_workspace"):
            event["resetDiff"] = True
        if name == "workspace_diff" and current_fingerprint == result.get("draft_fingerprint"):
            text = filter_text(str(result.get("diff", "")))
            event.update(diff=text[:24_000], diffTruncated=bool(result.get("truncated")) or len(text) > 24_000)
    if len(event) > 1:
        agent.emit(event)
