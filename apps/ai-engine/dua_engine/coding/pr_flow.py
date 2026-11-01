"""Tested pull requests for the Dua Agent (AGENT_TEST_CODE=1). Used by both the old loop and the ADK path.

1) Pre-check before any PR: Python files are byte-compiled (python -m py_compile) in a temp dir with a timeout;
   JSON files are parsed. Nothing from the change is executed. Failures go back to the model (max 2 attempts).
2) After the PR opens: poll GitHub CI (check runs; falls back to Actions runs) for up to CI_SECONDS.
3) If CI fails: the model gets ONE fix attempt; the fix is a new commit on the SAME dua-agent/ branch (never main,
   never force, never merge), then CI is polled once more.
Without the flag, propose_changes behaves exactly as before (open the PR and finish).
"""
import json
import os
import subprocess
import sys
import tempfile
import time

import dua_engine.coding.coding_agent as coding_agent

PRECHECK_SECONDS = 20
# Total CI waiting per request (both waits together), so a request stays inside Cloud Run's 900 s limit.
CI_SECONDS = int(os.environ.get("AGENT_CI_SECONDS", "420"))
CI_POLL_SECONDS = 20
MAX_PRECHECKS = 2
FAILED = ("failure", "timed_out", "cancelled", "action_required", "startup_failure")
PERMISSION_HINT = ("The GitHub token cannot read CI results. Add 'Actions: Read-only' (and 'Checks: Read-only' if "
                   "offered) to the fine-grained token for this repository.")


def enabled():
    return os.environ.get("AGENT_TEST_CODE", "0").strip() == "1"


def precheck(files):
    """Returns a list of problems (empty = fine)."""
    problems = []
    for item in files:
        if item["path"].endswith(".json"):
            try:
                json.loads(item["content"])
            except ValueError as error:
                problems.append(f"{item['path']}: invalid JSON ({error})"[:300])
    python = [item for item in files if item["path"].endswith(".py")]
    if python:
        with tempfile.TemporaryDirectory(prefix="dua-precheck-") as root:
            paths = []
            for i, item in enumerate(python):
                path = os.path.join(root, f"f{i}_" + os.path.basename(item["path"]))
                with open(path, "w", encoding="utf-8") as handle:
                    handle.write(item["content"])
                paths.append((path, item["path"]))
            for path, original in paths:
                try:
                    done = subprocess.run([sys.executable, "-m", "py_compile", path], capture_output=True, text=True,
                                          timeout=PRECHECK_SECONDS, cwd=root, env={"PATH": os.environ.get("PATH", "")})
                except subprocess.TimeoutExpired:
                    problems.append(f"{original}: compile check timed out")
                    continue
                if done.returncode != 0:
                    detail = (done.stderr or done.stdout).replace(path, original).strip().splitlines()[-3:]
                    problems.append(f"{original}: " + " | ".join(detail)[:400])
    return problems


def summarize(runs):
    """check-run like dicts -> (state, failed list). state: success|failure|pending|none."""
    if not runs:
        return "none", []
    failed = [r for r in runs if r.get("conclusion") in FAILED]
    if failed:
        return "failure", [{"name": r.get("name"), "summary": str((r.get("output") or {}).get("title") or
                                                                    (r.get("output") or {}).get("summary") or "")[:300]}
                           for r in failed]
    if any(r.get("status") != "completed" for r in runs):
        return "pending", []
    return "success", []


class PrFlow:
    def __init__(self, github, emit, progress, request_text, clock=time.monotonic, sleep=time.sleep, test=None):
        self.github, self.emit, self.progress, self.request_text = github, emit, progress, request_text
        self.clock, self.sleep = clock, sleep
        self.test = enabled() if test is None else test
        self.pr = None  # {"number", "url", "branch", "sha", "title"}
        self.prechecks = 0
        self.fixed = False
        self.ci = None
        self.ci_left = CI_SECONDS

    # ------------------------------------------------------------ CI
    def ci_status(self):
        if self.pr is None:
            return {"error": "No pull request was opened in this request yet."}
        try:
            runs = self.github.check_runs(self.pr["sha"])
        except coding_agent.CodingError as error:
            if "refused" not in str(error):
                return {"state": "unknown", "error": str(error)[:200]}
            try:
                runs = self.github.action_runs(self.pr["sha"])
            except coding_agent.CodingError as again:
                return {"state": "no_permission", "error": PERMISSION_HINT if "refused" in str(again) else str(again)[:200]}
        state, failed = summarize(runs)
        return {"state": state, "failed": failed, "pr": self.pr["url"]}

    def wait_ci(self):
        started = self.clock()
        seconds = max(self.ci_left, 0)
        status = {"state": "pending"}
        while self.clock() - started < seconds:
            status = self.ci_status()
            if status.get("state") in ("success", "failure", "no_permission"):
                break
            waited = int(self.clock() - started)
            self.progress("ci", f"Waiting for CI ({waited // 60}m {waited % 60}s)\u2026")
            self.sleep(CI_POLL_SECONDS)
        else:
            status = dict(status, state="still_running" if status.get("state") in ("pending", "none") else status["state"])
        self.ci_left -= self.clock() - started
        self.progress("ci", f"CI: {status['state'].replace('_', ' ')}", "done")
        coding_agent_log(f"ci {self.pr['url']}: {status['state']}")
        self.ci = status
        return status

    # ------------------------------------------------------------ propose
    def propose(self, args):
        """Returns (done, result_for_model, final_text)."""
        files = coding_agent.validate_changes(args.get("files") or [])
        if self.test and files:
            problems = precheck(files)
            if problems:
                self.prechecks += 1
                coding_agent_log(f"precheck failed ({self.prechecks}): {problems[0][:120]}")
                if self.prechecks >= MAX_PRECHECKS:
                    return True, None, ("Stopped: the change did not pass the pre-check twice, so no pull request "
                                        "was opened.\n\n" + "\n".join("- " + p for p in problems))
                return False, {"error": "Pre-check failed. Fix these and call propose_changes again.",
                               "problems": problems}, None
        if self.pr is None:
            text = coding_agent.finish(self.github, self.emit, dict(args, files=files), self.request_text, flow=self)
            if not self.test or self.pr is None:
                return True, None, text
            status = self.wait_ci()
            if status["state"] == "failure":
                return False, {"pr_opened": self.pr["url"], "ci": "failure", "failed_checks": status["failed"],
                               "instruction": "CI failed. You have ONE fix attempt: call propose_changes again with the "
                                              "corrected full file contents; it pushes to the same pull request."}, None
            return True, None, text + ci_line(status)
        if self.fixed or not self.test:
            return True, None, f"Pull request already opened: {self.pr['url']}" + ci_line(self.ci or {})
        self.fixed = True
        self.emit(f"\n**Pushing a fix** to `{self.pr['branch']}`\n")
        self.pr["sha"] = self.github.push_fix(files, f"Dua Agent fix: {self.pr['title']}"[:120], self.pr["branch"])
        status = self.wait_ci()
        return True, None, (f"Pull request: {self.pr['url']} (one fix pushed after CI failed)." + ci_line(status))


def ci_line(status):
    state = status.get("state")
    if state == "success":
        return "\n\nCI: all checks passed."
    if state == "failure":
        names = ", ".join(str(f.get("name")) for f in status.get("failed", [])) or "some checks"
        return f"\n\nCI: failed ({names}). Please review the PR; I did not merge anything."
    if state == "no_permission":
        return "\n\nCI: " + PERMISSION_HINT
    if state in ("still_running", "pending", "none"):
        return "\n\nCI: still running; check the PR page in a few minutes."
    return ""


def coding_agent_log(message):
    print(f"[dua-agent] {message}", flush=True)
