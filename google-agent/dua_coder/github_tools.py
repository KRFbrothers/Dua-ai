"""Bounded GitHub tools; no local shell, merge, deployment or secret endpoints."""
import base64
import json
import os
import re
import uuid
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen

REPO = "KRFbrothers/Dua-ai"
ROOT = f"https://api.github.com/repos/{REPO}"
SHA = re.compile(r"^[0-9a-f]{40}$")
BRANCH = re.compile(r"^dua-agent/[a-z0-9-]{1,70}$")
MAX_FILE = 100_000


class GitHubError(Exception):
    pass


def api(method, path, body=None):
    token = os.environ.get("DUA_GITHUB_TOKEN", "").strip()
    if not token and os.environ.get("DUA_GITHUB_TOKEN_SECRET"):
        token = secret_token()
    if not token:
        raise GitHubError("Configure DUA_GITHUB_TOKEN in the private runtime environment.")
    request = Request(
        ROOT + path,
        data=None if body is None else json.dumps(body).encode(),
        method=method,
        headers={"Authorization": "Bearer " + token,
                 "Accept": "application/vnd.github+json",
                 "X-GitHub-Api-Version": "2022-11-28",
                 "Content-Type": "application/json", "User-Agent": "dua-coder"},
    )
    try:
        with urlopen(request, timeout=30) as response:
            raw = response.read(2_000_001)
        if len(raw) > 2_000_000:
            raise GitHubError("Response exceeds the agent size limit.")
        return json.loads(raw) if raw else {}
    except HTTPError as error:
        # Do not echo remote bodies: they can contain submitted source or credentials.
        raise GitHubError(f"GitHub HTTP {error.code}. Check access, branch and service status.") from None
    except (URLError, TimeoutError):
        raise GitHubError("GitHub unavailable; check status before retrying a write.") from None


def secret_token():
    """Resolve a runtime-configured Secret Manager reference, never a model argument."""
    resource = os.environ.get("DUA_GITHUB_TOKEN_SECRET", "")
    if not re.fullmatch(r"projects/[a-zA-Z0-9-]+/secrets/[a-zA-Z0-9_-]+/versions/(latest|[0-9]+)", resource):
        raise GitHubError("Invalid Secret Manager resource configuration.")
    try:
        import google.auth
        from google.auth.transport.requests import AuthorizedSession
        credentials, _ = google.auth.default(scopes=["https://www.googleapis.com/auth/cloud-platform"])
        with AuthorizedSession(credentials) as session:
            response = session.get(f"https://secretmanager.googleapis.com/v1/{resource}:access", timeout=20)
            response.raise_for_status()
            return base64.b64decode(response.json()["payload"]["data"]).decode().strip()
    except Exception:
        raise GitHubError("Secret Manager access failed. Check runtime identity and secret-level access.") from None


def validate_path(path, write=False):
    if not isinstance(path, str) or not path or len(path) > 240:
        raise ValueError("Invalid path.")
    parts = path.split("/")
    if any(p in ("", ".", "..") or p.startswith(".") for p in parts) or "\\" in path:
        raise ValueError("Hidden, traversal and empty paths are not allowed.")
    if any(p.lower() in ("secrets", "credentials", "node_modules", "build") for p in parts):
        raise ValueError("Generated and credential paths are blocked.")
    if path.endswith((".pem", ".key", ".jks", ".keystore", ".db")) or "secret" in parts[-1].lower():
        raise ValueError("Credential paths are blocked.")
    if write:
        allowed = (
            path.startswith("app/src/") and path.endswith((".kt", ".java", ".xml"))
            or path.startswith("web/dist/") and path.endswith((".html", ".css", ".js"))
            or path.startswith("web/api/") and path.endswith(".js")
            or path.startswith("web/") and len(parts) == 2 and path.endswith((".mjs", ".test.mjs"))
            or len(parts) == 1 and path.endswith(".md")
        )
        if not allowed:
            raise ValueError("Only app source, web source/tests and root Markdown can be edited.")
    return path


def validate_sha(sha):
    if not isinstance(sha, str) or not SHA.fullmatch(sha):
        raise ValueError("Use the exact 40-character commit SHA returned by repository_info.")
    return sha


def repository_info() -> dict:
    """Get current main commit; pin subsequent reads and changes to this SHA."""
    data = api("GET", "/branches/main")
    return {"repository": REPO, "main_sha": data["commit"]["sha"],
            "web": "web/", "android": "app/", "backend": "https://dua-cyan.vercel.app"}


def list_files(commit_sha: str) -> dict:
    """List source paths at a pinned commit, excluding hidden/credential paths."""
    data = api("GET", f"/git/trees/{validate_sha(commit_sha)}?recursive=1")
    paths = []
    for entry in data.get("tree", []):
        if entry["type"] != "blob":
            continue
        try:
            validate_path(entry["path"])
            paths.append(entry["path"])
        except ValueError:
            pass
    return {"paths": paths[:3000], "truncated": data.get("truncated", False) or len(paths) > 3000}


def read_file(path: str, commit_sha: str) -> dict:
    """Read one UTF-8 source file at a pinned commit; never read hidden secrets."""
    validate_path(path)
    validate_sha(commit_sha)
    tree = api("GET", f"/git/trees/{commit_sha}?recursive=1")
    if tree.get("truncated"):
        raise ValueError("Cannot safely inspect a truncated tree.")
    entry = next((x for x in tree["tree"] if x["path"] == path), None)
    if not entry or entry["type"] != "blob" or entry["mode"] not in ("100644", "100755"):
        raise ValueError("Only regular files can be read; symlinks are blocked.")
    data = api("GET", f"/contents/{quote(path, safe='/')}?ref={validate_sha(commit_sha)}")
    if not isinstance(data, dict) or data.get("type") != "file" or data.get("size", MAX_FILE + 1) > MAX_FILE:
        raise ValueError("Only small regular source files can be read.")
    if data.get("encoding") != "base64":
        raise ValueError("File is not available as bounded text.")
    return {"path": path, "blob_sha": data["sha"],
            "content": base64.b64decode(data["content"]).decode("utf-8")}


def validate_changes(changes):
    if not isinstance(changes, list) or not 1 <= len(changes) <= 12:
        raise ValueError("Provide 1 to 12 files, with full text contents. Deletion is not supported.")
    seen, total = set(), 0
    for entry in changes:
        if not isinstance(entry, dict) or set(entry) != {"path", "content"}:
            raise ValueError("Each change needs exactly path and content.")
        path = validate_path(entry["path"], write=True)
        text = entry["content"]
        if path in seen or not isinstance(text, str) or "\x00" in text:
            raise ValueError("Duplicate path or invalid text.")
        size = len(text.encode())
        if size > MAX_FILE:
            raise ValueError("File exceeds 100 KB limit.")
        if re.search(r"-----BEGIN .*PRIVATE KEY-----|github_pat_[A-Za-z0-9_]+|gh[pousr]_[A-Za-z0-9]+", text):
            raise ValueError("Possible private credential in content. Remove it before proceeding.")
        total += size
        seen.add(path)
    if total > 300_000:
        raise ValueError("Combined patch exceeds 300 KB limit.")


def create_draft_pr(base_sha: str, title: str, description: str, changes: list[dict[str, str]]) -> dict:
    """AFTER USER CONFIRMATION create a new branch and draft PR containing reviewed file text.

    Never update main, existing branches, workflows, dependencies or credentials.
    Tests are not yet run; the user reviews the concrete changes in the confirmation UI.
    """
    validate_sha(base_sha)
    validate_changes(changes)
    if not title.strip() or len(title) > 160 or len(description) > 10_000:
        raise ValueError("Provide a short title and bounded description.")
    if repository_info()["main_sha"] != base_sha:
        raise ValueError("Main changed. Re-read current files and request approval for an updated patch.")
    commit = api("GET", f"/git/commits/{base_sha}")
    tree = api("GET", f"/git/trees/{commit['tree']['sha']}?recursive=1")
    if tree.get("truncated"):
        raise ValueError("Cannot safely inspect a truncated tree.")
    modes = {x["path"]: (x["mode"], x["type"]) for x in tree["tree"]}
    elements = []
    for change in changes:
        path = change["path"]
        mode, kind = modes.get(path, ("100644", "blob"))
        if kind != "blob" or mode not in ("100644", "100755"):
            raise ValueError("Cannot replace directories, symlinks or submodules.")
        if any(modes.get("/".join(path.split("/")[:i]), (None, None))[1] == "blob"
               for i in range(1, len(path.split("/")))):
            raise ValueError("A path parent is not a directory.")
        elements.append({"path": path, "mode": mode, "type": "blob", "content": change["content"]})
    branch = "dua-agent/" + uuid.uuid4().hex[:16]
    new_tree = api("POST", "/git/trees", {"base_tree": commit["tree"]["sha"], "tree": elements})
    new_commit = api("POST", "/git/commits", {"message": title, "tree": new_tree["sha"], "parents": [base_sha]})
    api("POST", "/git/refs", {"ref": "refs/heads/" + branch, "sha": new_commit["sha"]})
    try:
        pr = api("POST", "/pulls", {"title": title, "head": branch, "base": "main", "draft": True,
                                    "body": description + "\n\nAgent validation: tests not yet verified. No APK requested. Human review and merge required."})
    except GitHubError:
        return {"branch": branch, "commit_sha": new_commit["sha"],
                "error": "Branch created; PR creation failed. Open it manually; do not repeat the whole write.",
                "compare_url": f"https://github.com/{REPO}/compare/main...{branch}"}
    return {"branch": branch, "commit_sha": new_commit["sha"], "pr_number": pr["number"], "url": pr["html_url"]}


def run_checks(branch: str) -> dict:
    """AFTER CONFIRMATION dispatch existing compile/unit tests for an agent branch; no APK."""
    if not BRANCH.fullmatch(branch):
        raise ValueError("Tests can only be dispatched for a dua-agent branch.")
    api("POST", "/actions/workflows/checks.yml/dispatches", {"ref": branch})
    return {"status": "requested; not a passing result", "branch": branch,
            "url": f"https://github.com/{REPO}/actions"}


def check_results(branch: str, commit_sha: str) -> dict:
    """Read checks for exact agent branch and commit. Empty results mean pending/unknown."""
    if not BRANCH.fullmatch(branch):
        raise ValueError("Use a dua-agent branch.")
    validate_sha(commit_sha)
    data = api("GET", f"/actions/workflows/checks.yml/runs?branch={quote(branch, safe='')}&per_page=20")
    runs = [{"id": r["id"], "status": r["status"], "conclusion": r.get("conclusion"), "url": r["html_url"]}
            for r in data.get("workflow_runs", []) if r["head_sha"] == commit_sha]
    return {"runs": runs, "note": "No run means unknown/pending. Only completed success counts as passed."}
