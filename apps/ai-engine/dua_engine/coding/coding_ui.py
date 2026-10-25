"""Bounded, redacted owner UI events. Never calls tools or reads repository files."""
import secrets
import shlex
import threading
import time

from agent_policy import Redactor, filter_text

TOOLS = frozenset(("update_plan", "workspace_status", "edit_file", "workspace_diff", "run_command",
                   "import_command_changes", "checkpoint_workspace", "restore_workspace", "open_pr_workspace"))


class CommandProgress:
    """Per-invocation cumulative UI snapshots; closing never waits on enqueue."""
    def __init__(self, agent, activity):
        self.agent, self.activity = agent, activity
        self.lock = threading.RLock()
        self.closed = threading.Event()
        self.previous, self.last_emit = None, None
        self.secrets = Redactor().secrets

    def update(self, snapshot):
        with self.lock:
            if self.closed.is_set() or self.agent.stop_requested():
                return
            # Reprocess the bounded cumulative snapshot, holding unfinished keys
            # and private-key blocks. A buffered partial secret never becomes a
            # UI delta merely because the next runner poll has not arrived yet.
            text = Redactor(self.secrets).feed(snapshot["output"])
            # A partially received END marker must not release a PEM block
            # before the redactor can match and replace the complete block.
            text = text.split("-----BEGIN ", 1)[0]
            terminal = {"id": self.activity, "state": snapshot["state"],
                        "profile": snapshot["profile"], "output": text[:32_000],
                        "truncated": snapshot["truncated"] or len(text) > 32_000}
            now = time.monotonic()
            changed_state = self.previous is None or terminal["state"] != self.previous["state"]
            if terminal == self.previous or (not changed_state and self.last_emit is not None and now - self.last_emit < 1):
                return
            self.previous, self.last_emit = terminal, now
            if self.agent.emit({"type": "coding", "terminal": terminal}) is False:
                self.closed.set()

    def close(self):
        # The existing bounded SSE enqueue may be waiting on a slow consumer.
        # Suppress new updates without delaying the tool deadline. Android also
        # ignores an already-in-flight live event after a final/interrupted card.
        self.closed.set()


def started(agent, name, args):
    if name != "run_command":
        return None
    activity = secrets.token_hex(8)
    argv = args.get("argv") if isinstance(args, dict) else None
    label = shlex.join(argv) if isinstance(argv, list) and len(argv) <= 64 and all(isinstance(a, str) and len(a) <= 4096 for a in argv) else "Command"
    terminal = {"id": activity, "command": filter_text(label)[:600], "state": "preparing"}
    profile = args.get("profile", "python") if isinstance(args, dict) else None
    if isinstance(profile, str) and profile in ("python", "jvm", "android"):
        terminal["profile"] = profile
    agent.emit({"type": "coding", "terminal": terminal})
    return activity


def completed(agent, name, result, activity):
    if name not in TOOLS or not isinstance(result, dict):
        return
    event = {"type": "coding"}
    if name == "update_plan" and "error" not in result:
        import coding_plan
        event["plan"] = coding_plan.preview(result.get("plan"))
    if activity:
        event["terminal"] = {"id": activity, "state": result.get("state", "error"),
                             "output": filter_text(str(result.get("output", result.get("error", ""))))[:32_000],
                             "exitCode": result.get("exit_code"), "truncated": bool(result.get("truncated"))}
        if result.get("profile") in ("python", "jvm", "android"):
            event["terminal"]["profile"] = result["profile"]
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
    if len(event) > 1:
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
