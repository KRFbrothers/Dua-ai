import json
import os
import sys
from types import SimpleNamespace

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import agent_tools as at  # noqa: E402
import coding_agent as ca  # noqa: E402

REPO = "KRFbrothers/Dua-ai"


class Resp:
    def __init__(self, data):
        self.data = data

    def read(self, n=None):
        return self.data

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False


class IssueOpener:
    """Answers like the GitHub Issues REST API and records every request that passed the allowlist."""

    def __init__(self):
        self.calls = []

    def open(self, req, timeout=None):
        path, _, query = req.full_url.replace(ca.API, "").partition("?")
        body = json.loads(req.data) if req.data else None
        self.calls.append((req.get_method(), path, body, query))
        base = f"/repos/{REPO}"
        if path == f"{base}/issues" and req.get_method() == "GET":
            out = [{"number": 1, "title": "Crash", "state": "open", "html_url": "u1", "labels": [{"name": "bug"}], "comments": 1},
                   {"number": 2, "title": "A PR", "pull_request": {}, "state": "open"}]
        elif path == f"{base}/issues/1":
            out = {"number": 1, "title": "Crash", "state": "open", "body": "x" * 9000, "labels": [], "html_url": "u1"}
        elif path == f"{base}/issues/2":
            out = {"number": 2, "title": "A PR", "pull_request": {}}
        elif path == f"{base}/issues/1/comments" and req.get_method() == "GET":
            out = [{"user": {"login": "fareed"}, "body": "same here", "created_at": "t"}]
        elif path == "/search/issues":
            out = {"items": [{"number": 1, "title": "Crash", "state": "open"}]}
        elif path == f"{base}/labels":
            out = [{"name": "bug"}, {"name": "enhancement"}]
        elif path == f"{base}/issues" and req.get_method() == "POST":
            out = {"number": 9, "html_url": f"https://github.com/{REPO}/issues/9"}
        elif path.endswith("/comments"):
            out = {"html_url": f"https://github.com/{REPO}/issues/1#issuecomment-5"}
        else:
            out = {}
        return Resp(json.dumps(out).encode())


def gh(repo=REPO):
    return ca.GitHubClient("tok", repo, opener=IssueOpener())


def call(name, args=None):
    return SimpleNamespace(function_calls=[SimpleNamespace(name=name, args=args or {})], candidates=[SimpleNamespace(content="t")], text=None)


class Script:
    def __init__(self, steps):
        self.steps, self.calls = list(steps), []

    def generate_content(self, contents, stream=False, generation_config=None, tools=None):
        self.calls.append(list(contents))
        return self.steps.pop(0) if self.steps else SimpleNamespace(function_calls=[], candidates=[], text="done")


def run(steps, github):
    model, progress = Script(steps), []
    env = {"model": model, "enqueue": lambda *a: True, "chunk_text": lambda r: r.text or "", "github": github,
           "progress": lambda step, label, state="active": progress.append(label), "thinking": lambda d: None,
           "request_text": "x", "memory": "", "imagine": None}
    text, _ = at.run(env, ["u"], ["TOOLS"], lambda results: ("tool", results), clock=lambda: 0)
    results = [c[-1][1][0][1] for c in model.calls if c and isinstance(c[-1], tuple)]
    return text, results, progress


def test_list_read_search_skip_prs_cap_text_and_show_chips():
    g = gh()
    _, results, progress = run([call("list_issues"), call("read_issue", {"number": 1}), call("search_issues", {"query": "crash repo:evil/x"})], g)
    assert [i["number"] for i in results[0]["issues"]] == [1]
    assert len(results[1]["body"]) == ca.MAX_ISSUE_TEXT and results[1]["truncated"] and results[1]["comment_list"][0]["author"] == "fareed"
    assert [i["number"] for i in results[2]["issues"]] == [1]
    search_query = next(q for m, p, b, q in g.opener.calls if p == "/search/issues")
    assert "evil" not in search_query and "repo%3AKRFbrothers%2FDua-ai" in search_query
    assert progress == ["Reading issues\u2026", "Reading issues\u2026", "Searching issues\u2026"]


def test_read_issue_rejects_pull_requests():
    _, results, _ = run([call("read_issue", {"number": 2})], gh())
    assert "pull request" in results[0]["error"]


def test_issue_reads_share_a_cap_of_five():
    _, results, _ = run([call("list_issues")] * 3 + [call("read_issue", {"number": 1})] * 3, gh())
    assert sum("error" in r for r in results) == 1 and "limit reached" in results[-1]["error"]


def test_create_issue_adds_footer_validates_labels_and_caps_one():
    g = gh()
    _, results, progress = run([call("create_issue", {"title": "  Dark  mode bug ", "body": "Steps...", "labels": ["bug"]}),
                                call("create_issue", {"title": "again", "body": "b"})], g)
    assert results[0] == {"number": 9, "url": f"https://github.com/{REPO}/issues/9", "created": True}
    posted = [b for m, p, b, q in g.opener.calls if m == "POST"]
    assert posted == [{"title": "Dark mode bug", "body": "Steps..." + ca.ISSUE_FOOTER, "labels": ["bug"]}]
    assert "Created by Dua Agent" in ca.ISSUE_FOOTER
    assert "limit reached" in results[1]["error"] and "Creating issue\u2026" in progress


def test_create_issue_refuses_unknown_labels():
    g = gh()
    _, results, _ = run([call("create_issue", {"title": "t", "body": "b", "labels": ["urgent"]})], g)
    assert "Unknown label" in results[0]["error"] and not any(m == "POST" for m, *_ in g.opener.calls)


def test_comment_on_issue_once_with_footer():
    g = gh()
    _, results, progress = run([call("comment_on_issue", {"number": 1, "body": "Fixed in #200"}),
                                call("comment_on_issue", {"number": 1, "body": "again"})], g)
    assert results[0]["commented"] and "limit reached" in results[1]["error"]
    posted = [b for m, p, b, q in g.opener.calls if m == "POST"]
    assert posted == [{"body": "Fixed in #200" + ca.COMMENT_FOOTER}] and "Commenting on issue\u2026" in progress


def test_comment_refuses_pull_request_numbers():
    g = gh()
    _, results, _ = run([call("comment_on_issue", {"number": 2, "body": "x"})], g)
    assert "error" in results[0] and not any(m == "POST" for m, *_ in g.opener.calls)


@pytest.mark.parametrize("method,path,body", [
    ("PATCH", f"/repos/{REPO}/issues/1", {"state": "closed"}),
    ("DELETE", f"/repos/{REPO}/issues/1", None),
    ("PUT", f"/repos/{REPO}/issues/1/lock", {}),
    ("POST", f"/repos/{REPO}/issues/1/assignees", {"assignees": ["x"]}),
    ("POST", f"/repos/{REPO}/issues/1/labels", {"labels": ["x"]}),
    ("POST", f"/repos/{REPO}/issues", {"title": "t", "assignees": ["x"]}),
    ("POST", f"/repos/{REPO}/issues", {"title": "t", "state": "closed"}),
    ("POST", f"/repos/{REPO}/issues/1/comments", {"body": "x", "extra": 1}),
    ("PATCH", f"/repos/{REPO}/issues/comments/5", {"body": "x"}),
    ("DELETE", f"/repos/{REPO}/issues/comments/5", None),
    ("GET", "/repos/someone/else/issues", None),
    ("POST", "/repos/someone/else/issues", {"title": "t"}),
])
def test_forbidden_issue_requests_never_reach_github(method, path, body):
    g = gh()
    with pytest.raises(ca.BlockedRequest):
        g.request(method, path, body)
    assert g.opener.calls == []


def test_issue_search_must_stay_in_repo():
    g = gh()
    with pytest.raises(ca.BlockedRequest):
        g.request("GET", "/search/issues", query={"q": "crash repo:other/repo"})
    assert g.opener.calls == []


def test_issue_tools_locked_to_dua_ai_repo():
    other = gh("someone/other")
    assert not other.issues_enabled()
    with pytest.raises(ca.BlockedRequest):
        other.request("GET", "/repos/someone/other/issues")
    _, results, _ = run([call("list_issues")], other)
    assert "not set up" in results[0]["error"]
    _, results, _ = run([call("list_issues")], None)
    assert "not set up" in results[0]["error"]


def test_declarations_only_offer_issue_tools_when_enabled():
    names = lambda *a: [t["name"] for t in at.declarations(*a)]
    assert "create_issue" not in names(True) and "create_issue" not in names(False, True)
    assert {"list_issues", "read_issue", "search_issues", "create_issue", "comment_on_issue"} <= set(names(True, True))
    assert not any(n in names(True, True) for n in ("close_issue", "edit_issue", "delete_issue"))


def test_real_genai_issue_declarations():
    pytest.importorskip("google.genai")
    tools = at.default_tools(True, True)[0].function_declarations
    assert "comment_on_issue" in [t.name for t in tools]
