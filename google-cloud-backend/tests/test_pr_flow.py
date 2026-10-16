import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import agent_tools  # noqa: E402
import coding_agent as ca  # noqa: E402
import pr_flow  # noqa: E402

SHA = "a" * 40
REPO = "KRFbrothers/Dua-ai"


class FakeGitHub:
    def __init__(self, ci=(), refuse_checks=False, refuse_actions=False):
        self.ci = list(ci)  # sequence of check-run lists returned per poll
        self.refuse_checks, self.refuse_actions = refuse_checks, refuse_actions
        self.opened, self.fixes, self.last_commit = [], [], SHA

    def open_pull_request(self, files, title, body, branch):
        self.opened.append((files, branch))
        return 7, "https://github.com/KRFbrothers/Dua-ai/pull/7"

    def push_fix(self, files, message, branch):
        self.fixes.append((files, branch))
        return "b" * 40

    def check_runs(self, sha):
        if self.refuse_checks:
            raise ca.CodingError("GitHub refused access. Check that the GitHub token ...")
        return self.ci.pop(0) if len(self.ci) > 1 else self.ci[0]

    def action_runs(self, sha):
        if self.refuse_actions:
            raise ca.CodingError("GitHub refused access. Check that the GitHub token ...")
        return [{"name": "Project checks", "status": "completed", "conclusion": "success", "output": {}}]


OK = [{"name": "Android build and tests", "status": "completed", "conclusion": "success"},
      {"name": "Vercel", "status": "completed", "conclusion": "failure"}]
FAIL = [{"name": "Backend tests", "status": "completed", "conclusion": "failure", "output": {"title": "1 failed"}}]
PENDING = [{"name": "Backend tests", "status": "in_progress", "conclusion": None}]
GOOD = {"title": "T", "description": "D", "files": [{"path": "google-cloud-backend/x.py", "content": "x = 1\n"}]}
BAD = {"title": "T", "description": "D", "files": [{"path": "google-cloud-backend/x.py", "content": "def broken(:\n"}]}


def flow(gh, test=True):
    events = []
    clock = [0.0]

    def sleep(s):
        clock[0] += s
    return pr_flow.PrFlow(gh, lambda d: None, lambda *a: events.append(a), "req", clock=lambda: clock[0],
                          sleep=sleep, test=test), events


def test_precheck_compiles_python_and_parses_json():
    assert pr_flow.precheck(GOOD["files"]) == []
    problems = pr_flow.precheck(BAD["files"] + [{"path": "a/b.json", "content": "{bad"}])
    assert any("x.py" in p for p in problems) and any("invalid JSON" in p for p in problems)
    assert "dua-precheck" not in " ".join(problems)  # temp paths are hidden


def test_flag_off_keeps_old_behaviour():
    gh = FakeGitHub(ci=[FAIL])
    f, _ = flow(gh, test=False)
    done, result, text = f.propose(BAD)  # no pre-check, no CI wait
    assert done and "pull/7" in text and gh.opened


def test_precheck_failure_goes_back_to_model_then_stops():
    gh = FakeGitHub(ci=[OK])
    f, _ = flow(gh)
    done, result, _ = f.propose(BAD)
    assert not done and "Pre-check failed" in result["error"] and not gh.opened
    done, _, text = f.propose(BAD)
    assert done and "no pull request" in text and not gh.opened


def test_ci_success_ignores_vercel_and_reports():
    gh = FakeGitHub(ci=[PENDING, OK])
    f, events = flow(gh)
    done, _, text = f.propose(GOOD)
    assert done and "CI: all checks passed" in text
    assert any(e[0] == "ci" and "Waiting for CI" in e[1] for e in events)


def test_ci_failure_allows_one_fix_on_same_branch():
    gh = FakeGitHub(ci=[FAIL, FAIL, OK])
    f, _ = flow(gh)
    done, result, _ = f.propose(GOOD)
    assert not done and result["ci"] == "failure" and result["failed_checks"][0]["name"] == "Backend tests"
    gh.ci = [OK]
    done, _, text = f.propose(GOOD)
    assert done and gh.fixes and gh.fixes[0][1] == gh.opened[0][1] and "one fix pushed" in text and "passed" in text
    done, _, text = f.propose(GOOD)  # no third attempt
    assert done and len(gh.fixes) == 1


def test_ci_wait_is_bounded(monkeypatch):
    gh = FakeGitHub(ci=[PENDING])
    f, _ = flow(gh)
    done, _, text = f.propose(GOOD)
    assert done and "still running" in text


def test_falls_back_to_actions_then_reports_missing_permission():
    f, _ = flow(FakeGitHub(ci=[OK], refuse_checks=True))
    f.pr = {"sha": SHA, "url": "u", "branch": "dua-agent/x-1", "title": "t"}
    assert f.ci_status()["state"] == "success"
    f, _ = flow(FakeGitHub(ci=[OK], refuse_checks=True, refuse_actions=True))
    f.pr = {"sha": SHA, "url": "u", "branch": "dua-agent/x-1", "title": "t"}
    status = f.ci_status()
    assert status["state"] == "no_permission" and "Actions: Read-only" in status["error"]


def test_allowlist_permits_only_safe_ci_and_branch_updates():
    base = f"/repos/{REPO}"
    assert ca.allowed("GET", f"{base}/commits/{SHA}/check-runs", None, REPO) is None
    assert ca.allowed("GET", f"{base}/actions/runs", None, REPO) is None
    assert ca.allowed("PATCH", f"{base}/git/refs/heads/dua-agent/fix-1", {"sha": SHA, "force": False}, REPO) is None
    assert ca.allowed("PATCH", f"{base}/git/refs/heads/dua-agent/fix-1", {"sha": SHA, "force": True}, REPO)
    assert ca.allowed("PATCH", f"{base}/git/refs/heads/main", {"sha": SHA, "force": False}, REPO)
    assert ca.allowed("DELETE", f"{base}/git/refs/heads/dua-agent/fix-1", None, REPO)
    assert ca.allowed("PUT", f"{base}/pulls/7/merge", {}, REPO)


def test_old_loop_hands_ci_failure_back_and_finishes_after_fix(monkeypatch):
    monkeypatch.setenv("AGENT_TEST_CODE", "1")
    monkeypatch.setattr(pr_flow, "CI_POLL_SECONDS", 0)
    gh = FakeGitHub(ci=[FAIL])

    class Script:
        def __init__(self):
            self.n = 0

        def generate_content(self, history, **kw):
            self.n += 1
            from types import SimpleNamespace
            call = SimpleNamespace(name="propose_changes", args=GOOD)
            if self.n == 2:
                gh.ci = [OK]
            return SimpleNamespace(function_calls=[call], candidates=[SimpleNamespace(content="c")])
    env = {"model": Script(), "progress": lambda *a: None, "thinking": lambda d: None, "github": gh,
           "request_text": "req", "enqueue": lambda *a: True, "chunk_text": lambda r: ""}
    monkeypatch.setattr(ca, "CodingSession", lambda *a: None)
    text, agent = agent_tools.run(env, [], ["TOOLS"], lambda results: ("tool", results))
    assert "one fix pushed" in text and len(gh.opened) == 1 and len(gh.fixes) == 1
    assert agent.run_tool("check_pr_ci", {})["state"] == "success"


def test_ci_budget_is_shared_across_both_waits(monkeypatch):
    monkeypatch.setattr(pr_flow, "CI_SECONDS", 100)
    gh = FakeGitHub(ci=[PENDING])
    f, _ = flow(gh)
    f.pr = {"sha": SHA, "url": "u", "branch": "dua-agent/x-1", "title": "t"}
    f.ci_left = 100
    f.wait_ci()
    assert f.ci_left <= 0
    assert f.wait_ci()["state"] == "still_running"
