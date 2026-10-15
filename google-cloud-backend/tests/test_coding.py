import base64
import json
import os
import sys
from types import SimpleNamespace

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import app as engine  # noqa: E402
import coding_agent as ca  # noqa: E402

REPO = "KRFbrothers/Dua-ai"


class FakeOpener:
    """Records GitHub HTTP calls that passed the allowlist and answers like the REST API."""

    def __init__(self):
        self.calls = []

    def open(self, req, timeout=None):
        path = req.full_url.replace(ca.API, "").split("?")[0]
        body = json.loads(req.data) if req.data else None
        self.calls.append((req.get_method(), path, body))
        if path.endswith("/git/ref/heads/main"):
            out = {"object": {"sha": "basesha"}}
        elif "/git/trees/" in path:
            out = {"sha": "basetree", "tree": [{"path": "app/Main.kt", "type": "blob"}, {"path": "README.md", "type": "blob"}]}
        elif "/contents/" in path:
            out = {"type": "file", "content": base64.b64encode(b"fun main() {}").decode()}
        elif path.endswith("/git/blobs"):
            out = {"sha": "blob1"}
        elif path.endswith("/git/trees"):
            out = {"sha": "tree1"}
        elif path.endswith("/git/commits"):
            out = {"sha": "commit1"}
        elif path.endswith("/git/refs"):
            out = {"ref": body["ref"]}
        elif path.endswith("/pulls"):
            out = {"number": 201, "html_url": f"https://github.com/{REPO}/pull/201"}
        else:
            out = {"items": [{"path": "app/Main.kt", "repository": {"full_name": REPO}}]}
        return SimpleNamespace(read=lambda n=None: json.dumps(out).encode(), __enter__=None)


class Resp:
    def __init__(self, data):
        self.data = data

    def read(self, n=None):
        return self.data

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False


class Opener(FakeOpener):
    def open(self, req, timeout=None):
        return Resp(super().open(req, timeout).read())


def client():
    return ca.GitHubClient("tok", REPO, opener=Opener())


@pytest.mark.parametrize("method,path,body", [
    ("DELETE", f"/repos/{REPO}/git/refs/heads/dua-agent/x", None),
    ("PUT", f"/repos/{REPO}/pulls/5/merge", {}),
    ("PATCH", f"/repos/{REPO}/git/refs/heads/main", {"sha": "x", "force": True}),
    ("PUT", f"/repos/{REPO}/contents/app/Main.kt", {"branch": "main"}),
    ("POST", f"/repos/{REPO}/git/refs", {"ref": "refs/heads/main", "sha": "x"}),
    ("POST", f"/repos/{REPO}/pulls", {"head": "main", "base": "main"}),
    ("POST", f"/repos/{REPO}/pulls", {"head": "dua-agent/x", "base": "release"}),
    ("GET", "/repos/someone/else/contents/a.kt", None),
    ("POST", "/repos/someone/else/pulls", {"head": "dua-agent/x", "base": "main"}),
    ("POST", f"/repos/{REPO}/merges", {}),
])
def test_forbidden_requests_are_blocked_before_any_network(method, path, body):
    gh = client()
    with pytest.raises(ca.BlockedRequest):
        gh.request(method, path, body)
    assert gh.opener.calls == []


def test_code_search_must_stay_in_repo():
    gh = client()
    with pytest.raises(ca.BlockedRequest):
        gh.request("GET", "/search/code", query={"q": "token"})
    assert gh.search("Theme") == ["app/Main.kt"]


def test_main_push_is_impossible_via_open_pull_request():
    gh = client()
    with pytest.raises(ca.BlockedRequest):
        gh.open_pull_request([{"path": "a.kt", "content": "x"}], "t", "d", "main")
    assert all(method == "GET" for method, _, _ in gh.opener.calls)


def test_change_validation_caps():
    with pytest.raises(ca.CodingError):
        ca.validate_changes([{"path": f"f{i}.kt", "content": "x"} for i in range(ca.MAX_FILES_CHANGED + 1)])
    for bad in ["../x", "/abs", ".github/workflows/ci.yml", ".git/config"]:
        with pytest.raises(ca.CodingError):
            ca.validate_changes([{"path": bad, "content": "x"}])
    with pytest.raises(ca.CodingError):
        ca.validate_changes([{"path": "big.kt", "content": "x" * (ca.MAX_FILE_BYTES + 1)}])


@pytest.mark.parametrize("repo", ["owner/.", "owner/..", "owner/repo\n", "owner/repo\t", "owner/repo/extra", None, [REPO]])
def test_invalid_repository_rejected_at_config_and_client_boundary(monkeypatch, repo):
    opener = Opener()
    with pytest.raises(ca.CodingError):
        ca.GitHubClient("tok", repo, opener=opener)
    assert opener.calls == []
    if isinstance(repo, str):
        monkeypatch.setenv("DUA_REPO", repo)
        with pytest.raises(ca.CodingError):
            ca.repo_name()


@pytest.mark.parametrize("path", ["../x", "a/../b", "a//b", "a/./b", "a\n", "\ta", "a\r", "a\\b", ".GiT/config", "src/.git/config", ".", "a" * 301, None, ["a"]])
def test_unsafe_reads_and_proposals_never_reach_github(path):
    gh = client()
    with pytest.raises(ca.CodingError):
        gh.read(path)
    with pytest.raises(ca.CodingError):
        ca.validate_changes([{"path": path, "content": "x"}])
    assert gh.opener.calls == []


@pytest.mark.parametrize("path", [".github/workflows/ci.yml", ".GitHub/Workflows/ci.yml", ".GITHUB/CODEOWNERS", ".gitmodules", ".GitModules", "src/.GiT/config"])
def test_protected_writes_cannot_bypass_proposals_via_client(path):
    gh = client()
    with pytest.raises(ca.CodingError):
        gh.open_pull_request([{"path": path, "content": "x"}], "t", "d", "dua-agent/test")
    with pytest.raises(ca.BlockedRequest):
        gh.request("POST", f"/repos/{REPO}/git/trees", {"tree": [{"path": path, "type": "blob", "mode": "100644", "sha": "x"}]})
    assert gh.opener.calls == []


@pytest.mark.parametrize("type_,mode", [("blob", "120000"), ("commit", "160000"), ("tree", "040000")])
def test_tree_writes_only_accept_regular_files(type_, mode):
    gh = client()
    with pytest.raises(ca.BlockedRequest):
        gh.request("POST", f"/repos/{REPO}/git/trees", {"tree": [{"path": "safe", "type": type_, "mode": mode, "sha": "x"}]})
    assert gh.opener.calls == []


@pytest.mark.parametrize("suffix", ["", "../private", "%2e%2e/private", "a//b", "a/./b", "a\n", "a?ref=other", "a\\b", "src/.GiT/config"])
def test_direct_read_requests_cannot_bypass_path_validation(suffix):
    gh = client()
    with pytest.raises(ca.BlockedRequest):
        gh.request("GET", f"/repos/{REPO}/contents/{suffix}")
    assert gh.opener.calls == []


@pytest.mark.parametrize("query", ["term", f"term repo:{REPO} repo:other/repo", f"term repo:{REPO} org:other", f"term OR repo:{REPO}", f'"term repo:{REPO}', f"term -repo:{REPO}", f"(term) repo:{REPO}", f"term repo:{REPO}\n"])
def test_direct_search_requires_exactly_one_unambiguous_repo_scope(query):
    gh = client()
    with pytest.raises(ca.BlockedRequest):
        gh.request("GET", "/search/code", query={"q": query})
    assert gh.opener.calls == []


def test_search_sanitizes_scopes_and_checks_actual_result_repository():
    gh = client()
    assert gh.search("Theme repo:other/repo org:other user:other") == ["app/Main.kt"]
    for query in ['"unterminated', "term OR other", "term NOT other", "(term)", "term\\other"]:
        with pytest.raises(ca.CodingError):
            gh.search(query)
    for item in [None, {"path": "x"}, {"path": "x", "repository": {"full_name": "other/repo"}},
                 {"path": "../x", "repository": {"full_name": REPO}}]:
        gh.request = lambda *args, **kwargs: {"items": [item]}
        with pytest.raises(ca.CodingError):
            gh.search("term")
    gh.request = lambda *args, **kwargs: {"items": [{"path": f"f{i}.kt", "repository": {"full_name": REPO.lower()}} for i in range(25)]}
    assert len(gh.search("term")) == 20


def test_normal_file_reads_and_proposals_still_work():
    gh = client()
    assert gh.read("docs/a b.md") == "fun main() {}"
    assert gh.read(".github/workflows/checks.yml") == "fun main() {}"
    assert ca.validate_changes([{"path": " app/Main.kt ", "content": "x"}]) == [{"path": "app/Main.kt", "content": "x"}]
    for item in [None, "README.md", 12]:
        with pytest.raises(ca.CodingError):
            ca.validate_changes([item])


class CodingModel:
    """Reads a file, then proposes one change."""

    def __init__(self, propose=True, steps=None):
        self.calls, self.propose, self.steps = [], propose, steps

    def generate_content(self, contents, stream=False, generation_config=None, tools=None):
        self.calls.append({"contents": list(contents), "tools": tools, "config": generation_config})
        n = len(self.calls)
        content = SimpleNamespace(role="model", parts=[f"call{n}"])
        if self.steps is not None or n == 1:
            return SimpleNamespace(function_calls=[SimpleNamespace(name="read_file", args={"path": "app/Main.kt"})], candidates=[SimpleNamespace(content=content)], text=None)
        if not self.propose:
            return SimpleNamespace(function_calls=[], candidates=[SimpleNamespace(content=content)], text="Need more details.")
        args = {"title": "Add hello screen", "description": "Simple hello screen add kiya.",
                "files": [{"path": "app/Main.kt", "content": "fun main() { println(\"hi\") }"}]}
        return SimpleNamespace(function_calls=[SimpleNamespace(name="propose_changes", args=args)], candidates=[SimpleNamespace(content=content)], text=None)


def test_happy_path_opens_pr_from_new_branch_into_main():
    gh, model, progress = client(), CodingModel(), []
    text = ca.run_coding(model, ["user turn"], gh, progress.append, "add hello screen",
                         tools=["TOOLS"], tool_response=lambda results: ("tool", results))
    assert "#201" in text and f"https://github.com/{REPO}/pull/201" in text
    writes = [(m, p.rsplit("/", 2)[-2:], b) for m, p, b in gh.opener.calls if m != "GET"]
    assert [w[1][-1] for w in writes] == ["blobs", "trees", "commits", "refs", "pulls"]
    ref = writes[3][2]["ref"]
    assert ref.startswith("refs/heads/dua-agent/add-hello-screen")
    pr = writes[4][2]
    assert pr["base"] == "main" and pr["head"].startswith("dua-agent/") and pr["title"] == "Dua Agent: Add hello screen"
    assert writes[2][2]["parents"] == ["basesha"]
    assert not any(m in ("PUT", "PATCH", "DELETE") for m, _, _ in gh.opener.calls)
    assert model.calls[1]["contents"][-1][0] == "tool"  # tool result fed back
    assert any("read_file" in line for line in progress)


def test_step_cap_stops_without_pr():
    gh = client()
    text = ca.run_coding(CodingModel(steps=True), [], gh, lambda d: None, "x", tools=[], tool_response=lambda r: r)
    assert "steps" in text and not any(m == "POST" for m, _, _ in gh.opener.calls)


def test_model_without_proposal_opens_nothing():
    gh = client()
    text = ca.run_coding(CodingModel(propose=False), [], gh, lambda d: None, "x", tools=[], tool_response=lambda r: r)
    assert "No pull request" in text and not any(m == "POST" for m, _, _ in gh.opener.calls)


# --- through /api/chat ---

@pytest.fixture
def owner(monkeypatch):
    monkeypatch.setattr(engine, "verify_owner", lambda authorization: "verified-owner")
    monkeypatch.setattr(engine, "make_part", lambda text=None, data=None, mime_type=None: ("text", text))
    monkeypatch.setattr(engine, "make_content", lambda role, parts: {"role": role, "parts": parts})
    monkeypatch.delenv("DUA_APP_TOKEN", raising=False)


def events(response):
    return [json.loads(l[6:]) for l in response.get_data(as_text=True).splitlines() if l.startswith("data: ")]


def post(text):
    return events(engine.app.test_client().post("/api/chat", json={"model": "Dua", "stream": True, "mode": "agent",
                                                                    "messages": [{"role": "user", "text": text}]}))


def test_missing_token_replies_politely_without_model_or_github(monkeypatch, owner):
    monkeypatch.delenv("GITHUB_TOKEN", raising=False)
    calls = []
    monkeypatch.setattr(engine, "get_model", lambda system=engine.SYSTEM_PROMPT: calls.append(1) or None)
    evs = post("Fix the crash on the settings screen")
    text = "".join(e["delta"] for e in evs if e["type"] == "text")
    assert "set up nahi" in text and evs[-1]["type"] == "done" and calls == [1]  # model object fetched, never called


def test_coding_route_runs_pipeline_and_streams_pr_link(monkeypatch, owner):
    monkeypatch.setenv("GITHUB_TOKEN", "tok")
    model = CodingModel()
    monkeypatch.setattr(engine, "get_model", lambda system=engine.SYSTEM_PROMPT: model)
    opener = Opener()
    real = ca.GitHubClient
    monkeypatch.setattr(ca, "GitHubClient", lambda token, repo: real(token, repo, opener=opener))
    monkeypatch.setattr(ca, "default_tools", lambda: ["TOOLS"])
    monkeypatch.setattr(ca, "default_tool_response", lambda results: ("tool", results))
    evs = post("code likho: add a hello screen to the app")
    kinds = [e["type"] for e in evs]
    assert "thinking" in kinds and kinds[-1] == "done"
    text = "".join(e["delta"] for e in evs if e["type"] == "text")
    assert "pull/201" in text
    assert "48dp" in model.calls[0]["contents"][-1]["parts"][0][1]  # repo rules in the prompt
    assert any(p.endswith("/pulls") for _, p, _ in opener.calls)


def test_router_sends_coding_words_to_code_and_ideas_to_agent():
    assert engine.route_agent([{"role": "user", "text": "fix the login bug"}], [], "s") == "code"
    assert engine.route_agent([{"role": "user", "text": "add dark mode toggle to the app"}], [], "s") == "code"
    assert engine.route_agent([{"role": "user", "text": "latest AI tools and feature ideas"}], [], "s") == "agent"
    assert engine.route_agent([{"role": "user", "text": "hi"}], [], "s") == "chat"


def test_dua_repo_env_is_validated(monkeypatch):
    monkeypatch.setenv("DUA_REPO", "bad repo")
    with pytest.raises(ca.CodingError):
        ca.repo_name()
    monkeypatch.setenv("DUA_REPO", "KRFbrothers/Dua-ai")
    assert ca.repo_name() == "KRFbrothers/Dua-ai"


def test_real_genai_tool_declarations():
    pytest.importorskip("google.genai")
    tools = ca.default_tools()
    assert [f.name for f in tools[0].function_declarations] == ["list_files", "read_file", "search_code", "propose_changes"]
    content = ca.default_tool_response([("read_file", {"content": "x"})])
    assert content.role == "user" and content.parts[0].function_response.name == "read_file"


def test_coding_reports_read_and_pr_progress(monkeypatch, owner):
    monkeypatch.setenv("GITHUB_TOKEN", "tok")
    monkeypatch.setattr(engine, "get_model", lambda system=engine.SYSTEM_PROMPT: CodingModel())
    opener = Opener()
    real = ca.GitHubClient
    monkeypatch.setattr(ca, "GitHubClient", lambda token, repo: real(token, repo, opener=opener))
    monkeypatch.setattr(ca, "default_tools", lambda: ["TOOLS"])
    monkeypatch.setattr(ca, "default_tool_response", lambda results: ("tool", results))
    evs = post("code likho: add a hello screen to the app")
    steps = [e["step"] for e in evs if e["type"] == "progress"]
    assert steps[0] == "plan" and steps[-1] == "pr" and "Opening PR\u2026" in [e["label"] for e in evs if e["type"] == "progress"]


def test_run_coding_progress_is_optional():
    gh = client()
    calls = []
    ca.run_coding(CodingModel(), [], gh, lambda d: None, "x", tools=[], tool_response=lambda r: r,
                  progress=lambda step, label, state="active": calls.append(step))
    assert calls[-1] == "pr"
