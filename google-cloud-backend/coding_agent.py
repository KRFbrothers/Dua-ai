"""Dua Agent phase 2: coding on the owner's GitHub repository through a locked-down GitHub client.

Safety is enforced here on the server, not by the model:
- Only one repository (env DUA_REPO, default KRFbrothers/Dua-ai).
- GitHubClient only sends requests that match an explicit (method, path) allowlist: reads, creating
  blobs/trees/commits, creating a NEW `refs/heads/dua-agent/...` branch and opening a pull request
  against main. Merging, deleting, force updates and any write to main are impossible.
- The model only gets READ tools plus `propose_changes`; the server itself performs the writes.
- Caps on tool steps, files read/changed, bytes and total time.
"""
import base64
import json
import os
import re
import time
from urllib.error import HTTPError
from urllib.parse import quote, urlencode
from urllib.request import Request, build_opener, HTTPRedirectHandler

API = "https://api.github.com"
BASE_BRANCH = "main"
BRANCH_PREFIX = "dua-agent/"
# Matches the generated flat slug/timestamp branch; never accept Git ref syntax from a tool caller.
AGENT_BRANCH = re.compile(r"dua-agent/[a-z0-9](?:[a-z0-9-]{0,78}[a-z0-9])?")
MAX_TOOL_STEPS = int(os.environ.get("CODING_MAX_STEPS", "15"))
MAX_FILES_READ = 40
MAX_READ_BYTES = 60_000           # per file returned to the model
MAX_TOTAL_READ_BYTES = 600_000
MAX_FILES_CHANGED = 10
MAX_FILE_BYTES = 200_000          # per changed file
MAX_TOTAL_CHANGE_BYTES = 600_000
MAX_SECONDS = int(os.environ.get("CODING_MAX_SECONDS", "600"))  # deploy with --timeout 900
STEP_TOKENS = int(os.environ.get("CODING_STEP_TOKENS", "16384"))

REPO_RULES = """Repository rules (must follow):
- Android app in Kotlin with Jetpack Compose and Material 3.
- No raw Color(0x...) and no .sp values outside the ui/theme/ package; use theme tokens.
- Touch targets at least 48dp; every interactive element needs a TalkBack label (contentDescription/semantics).
- Support light and dark themes.
- Do NOT touch the + attachment sheet (AttachmentSheet) UI.
- Keep existing tests passing; add or update unit tests when you change behaviour.
- Never use OpenAI/ChatGPT logos, brand assets or fonts.
- Never put secrets, keys or tokens in code."""

CODING_PROMPT = (
    "You are the Dua Agent coding step for the GitHub repository {repo}. Use the tools to look at the tree, "
    "search and read the files you need (read before you change). Keep changes small and focused. When ready, "
    "call propose_changes ONCE with the FULL new content of each changed or new file (max {max_files} files), a "
    "short PR title and a simple-language description (Hinglish-friendly) of what changed and how to test it. "
    "If the request is unclear or too big, call propose_changes with no files and explain why in description.\n\n"
    + REPO_RULES
)

SLUG = re.compile(r"[^a-z0-9]+")
PATH_OK = re.compile(r"^(?!/)(?!.*\.\.)(?!\.git/)[A-Za-z0-9._\-/ ]{1,300}$")


def valid_repository(repo):
    return (isinstance(repo, str) and
            re.fullmatch(r"[A-Za-z0-9-]{1,39}/[A-Za-z0-9._-]{1,100}", repo) is not None and
            repo.split("/")[1] not in (".", ".."))


def valid_path(path, write=False):
    if not isinstance(path, str) or not PATH_OK.fullmatch(path) or path != path.strip():
        return False
    if any(part in ("", ".", "..") or part.lower() == ".git" for part in path.split("/")):
        return False
    return not write or (not path.lower().startswith(".github/") and path.lower() not in (".github", ".gitmodules"))


def scoped_search(query, repo):
    if not isinstance(query, str) or re.search(r'["\\()\x00-\x1f\x7f]|\b(?:OR|NOT)\b', query, re.I):
        return False
    scopes = list(re.finditer(r"\b(?:repo|org|user):\S*", query, re.I))
    return (len(scopes) == 1 and scopes[0].group().lower() == f"repo:{repo}".lower() and
            (scopes[0].start() == 0 or query[scopes[0].start() - 1].isspace()))


# Issue tools are locked to this one repository, even if DUA_REPO points elsewhere.
ISSUES_REPO = "KRFbrothers/Dua-ai"
ISSUE_FOOTER = "\n\n---\n_Created by Dua Agent_"
COMMENT_FOOTER = "\n\n---\n_Comment by Dua Agent_"
MAX_ISSUE_TITLE = 200
MAX_ISSUE_BODY = 20_000
MAX_ISSUE_TEXT = 4_000        # per issue/comment body returned to the model
MAX_ISSUE_COMMENTS = 20


class CodingError(Exception):
    """Message is safe to show to the user."""


class BlockedRequest(CodingError):
    pass


def repo_name():
    repo = os.environ.get("DUA_REPO", "KRFbrothers/Dua-ai").strip(" ")
    if not valid_repository(repo):
        raise CodingError("DUA_REPO is not a valid owner/name.")
    return repo


def slugify(text, limit=40):
    slug = SLUG.sub("-", (text or "change").lower()).strip("-")[:limit].strip("-")
    return slug or "change"


def valid_agent_branch(branch):
    return isinstance(branch, str) and AGENT_BRANCH.fullmatch(branch) is not None


def conflicting_change_paths(paths):
    """Call only after path validation. Reject duplicate files and file/folder clashes in either order."""
    seen = set(paths)
    if len(seen) != len(paths):
        return True
    for path in paths:
        parent = path
        while "/" in parent:
            parent = parent.rsplit("/", 1)[0]
            if parent in seen:
                return True
    return False


def allowed(method, path, body, repo):
    """The only GitHub requests this server may send. Returns a reason string when blocked, else None."""
    if not valid_repository(repo) or not isinstance(path, str) or re.search(r"[\x00-\x1f\x7f]", path):
        return "invalid repository or request path"
    if any(part in ("", ".", "..") for part in path.split("/")[1:]):
        return "non-canonical request path"
    r = re.escape(repo)
    base = f"/repos/{r}"
    rules = {
        "GET": [rf"^{base}$", rf"^{base}/git/trees/[A-Za-z0-9._\-/]+$", rf"^{base}/contents/[^?]*$",
                rf"^{base}/git/ref/heads/[A-Za-z0-9._\-/]+$", r"^/search/code$"],
        "POST": [rf"^{base}/git/blobs$", rf"^{base}/git/trees$", rf"^{base}/git/commits$",
                 rf"^{base}/git/refs$", rf"^{base}/pulls$"],
    }
    if repo.lower() == ISSUES_REPO.lower():
        # Issues: read, create and comment only (no PATCH/PUT/DELETE: no close, edit, assign, lock or delete).
        rules["GET"] += [rf"^{base}/issues$", rf"^{base}/issues/[1-9][0-9]{{0,8}}$",
                         rf"^{base}/issues/[1-9][0-9]{{0,8}}/comments$", rf"^{base}/labels$", r"^/search/issues$"]
        rules["POST"] += [rf"^{base}/issues$", rf"^{base}/issues/[1-9][0-9]{{0,8}}/comments$"]
    if method not in rules or not any(re.fullmatch(p, path) for p in rules[method]):
        return f"{method} {path} is not allowed"
    if "/contents/" in path and not valid_path(path.split("/contents/", 1)[1]):
        return "invalid file path"
    if method == "POST" and path.endswith("/git/trees"):
        entries = body.get("tree") if isinstance(body, dict) else None
        if not isinstance(entries, list) or not entries or len(entries) > MAX_FILES_CHANGED or not all(
                isinstance(item, dict) and valid_path(item.get("path"), write=True) and
                item.get("type") == "blob" and item.get("mode") in ("100644", "100755") for item in entries):
            return "git trees must contain allowed regular file paths"
        if conflicting_change_paths([item["path"] for item in entries]):
            return "git trees cannot contain duplicate files or file/folder conflicts"
    if method == "POST" and path.endswith("/git/refs"):
        if not isinstance(body, dict) or set(body) != {"ref", "sha"}:
            return "new branch requests may only set ref and sha"
        ref = body.get("ref")
        if not isinstance(ref, str) or not ref.startswith("refs/heads/") or not valid_agent_branch(ref[len("refs/heads/"):]):
            return "only valid new dua-agent/ branches may be created"
    if method == "POST" and re.fullmatch(rf"{base}/issues", path):
        if not isinstance(body, dict) or set(body) - {"title", "body", "labels"} or not isinstance(body.get("title"), str):
            return "new issues may only set title, body and labels"
    if method == "POST" and path.endswith("/comments"):
        if not isinstance(body, dict) or set(body) != {"body"}:
            return "comments may only set a body"
    if method == "POST" and path.endswith("/pulls"):
        if not isinstance(body, dict) or body.get("base") != BASE_BRANCH or not valid_agent_branch(body.get("head")):
            return "pull requests must go from a valid dua-agent/ branch into main"
    return None


class GitHubClient:
    def __init__(self, token, repo, opener=None):
        if not valid_repository(repo):
            raise CodingError("DUA_REPO is not a valid owner/name.")
        self.token, self.repo = token, repo
        self.opener = opener or build_opener(type("NoRedirect", (HTTPRedirectHandler,), {"redirect_request": lambda *a, **k: None}))

    def request(self, method, path, body=None, query=None):
        reason = allowed(method, path, body, self.repo)
        if reason:
            raise BlockedRequest(f"Blocked GitHub request: {reason}.")
        if method == "GET" and path == "/search/issues":
            if not scoped_search((query or {}).get("q", ""), self.repo):
                raise BlockedRequest("Blocked GitHub request: issue search must stay inside the repository.")
        if method == "GET" and path == "/search/code":
            q = (query or {}).get("q", "")
            if not scoped_search(q, self.repo):
                raise BlockedRequest("Blocked GitHub request: code search must stay inside the repository.")
        url = API + quote(path, safe="/._-~") + (("?" + urlencode(query)) if query else "")
        data = json.dumps(body).encode("utf-8") if body is not None else None
        req = Request(url, data=data, method=method, headers={
            "Authorization": f"Bearer {self.token}", "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "dua-agent", "Content-Type": "application/json"})
        try:
            with self.opener.open(req, timeout=30) as response:
                return json.loads(response.read(5_000_000) or b"null")
        except HTTPError as error:
            status = error.code
            if status in (401, 403):
                raise CodingError("GitHub refused access. Check that the GitHub token is valid and has Contents, Pull requests and Issues (read and write) on the repository.")
            if status == 404:
                raise CodingError("not found")
            raise CodingError(f"GitHub request failed ({status}).")

    # --- read helpers used by model tools ---
    def tree(self):
        data = self.request("GET", f"/repos/{self.repo}/git/trees/{BASE_BRANCH}", query={"recursive": "1"})
        return [item["path"] for item in data.get("tree", []) if item.get("type") == "blob"]

    def read(self, path):
        if not valid_path(path):
            raise CodingError("Invalid path.")
        data = self.request("GET", f"/repos/{self.repo}/contents/{path}", query={"ref": BASE_BRANCH})
        if not isinstance(data, dict) or data.get("type") != "file":
            raise CodingError("Not a file.")
        return base64.b64decode(data.get("content", "")).decode("utf-8", errors="replace")

    def search(self, text):
        text = re.sub(r"\b(?:repo|org|user):\S*", " ", text, flags=re.I) if isinstance(text, str) else ""
        text = " ".join(text.split())[:200]
        if not text or not scoped_search(f"{text} repo:{self.repo}", self.repo):
            raise CodingError("Use plain search terms without quotes, parentheses, backslashes or OR/NOT operators.")
        data = self.request("GET", "/search/code", query={"q": f"{text} repo:{self.repo}", "per_page": "20"})
        items = data.get("items") if isinstance(data, dict) else None
        if not isinstance(items, list) or any(
                not isinstance(item, dict) or not isinstance(item.get("repository"), dict) or
                not valid_repository(item["repository"].get("full_name")) or
                item["repository"]["full_name"].lower() != self.repo.lower() or not valid_path(item.get("path")) for item in items):
            raise CodingError("GitHub returned search results outside the selected repository or with invalid paths.")
        return [item["path"] for item in items[:20]]

    # --- issues (ISSUES_REPO only) ---
    def issues_enabled(self):
        return self.repo.lower() == ISSUES_REPO.lower()

    @staticmethod
    def _issue(item, full=False):
        text = str(item.get("body") or "")
        out = {"number": item.get("number"), "title": str(item.get("title") or "")[:MAX_ISSUE_TITLE],
               "state": item.get("state"), "url": item.get("html_url"),
               "labels": [str(l.get("name")) for l in item.get("labels") or [] if isinstance(l, dict)][:20],
               "comments": item.get("comments", 0), "updated": item.get("updated_at")}
        if full:
            out["body"] = text[:MAX_ISSUE_TEXT]
            out["truncated"] = len(text) > MAX_ISSUE_TEXT
        return out

    def list_issues(self, state="open"):
        state = state if state in ("open", "closed", "all") else "open"
        data = self.request("GET", f"/repos/{self.repo}/issues", query={"state": state, "per_page": "30"})
        return [self._issue(i) for i in data or [] if isinstance(i, dict) and "pull_request" not in i][:30]

    def read_issue(self, number):
        number = int(number)
        if not 1 <= number < 10**9:
            raise CodingError("Invalid issue number.")
        item = self.request("GET", f"/repos/{self.repo}/issues/{number}")
        if not isinstance(item, dict) or "pull_request" in item:
            raise CodingError("That number is a pull request, not an issue.")
        comments = self.request("GET", f"/repos/{self.repo}/issues/{number}/comments", query={"per_page": str(MAX_ISSUE_COMMENTS)})
        result = self._issue(item, full=True)
        result["comment_list"] = [{"author": (c.get("user") or {}).get("login"), "created": c.get("created_at"),
                                   "body": str(c.get("body") or "")[:MAX_ISSUE_TEXT]} for c in comments or [] if isinstance(c, dict)][:MAX_ISSUE_COMMENTS]
        return result

    def search_issues(self, text):
        text = re.sub(r"\b(?:repo|org|user|is|type):\S*", " ", text, flags=re.I) if isinstance(text, str) else ""
        text = " ".join(text.split())[:200]
        q = f"{text} repo:{self.repo} is:issue"
        if not text or not scoped_search(q, self.repo):
            raise CodingError("Use plain search terms without quotes, parentheses, backslashes or OR/NOT operators.")
        data = self.request("GET", "/search/issues", query={"q": q, "per_page": "20"})
        items = (data or {}).get("items") if isinstance(data, dict) else None
        return [self._issue(i) for i in items or [] if isinstance(i, dict) and "pull_request" not in i][:20]

    def labels(self):
        data = self.request("GET", f"/repos/{self.repo}/labels", query={"per_page": "100"})
        return {str(l.get("name")) for l in data or [] if isinstance(l, dict)}

    def create_issue(self, title, body, labels=None):
        title = " ".join(str(title or "").split())[:MAX_ISSUE_TITLE]
        if not title:
            raise CodingError("The issue needs a title.")
        payload = {"title": title, "body": str(body or "").strip()[:MAX_ISSUE_BODY] + ISSUE_FOOTER}
        wanted = [str(l) for l in (labels or []) if isinstance(l, str)][:5]
        if wanted:
            existing = self.labels()
            unknown = [l for l in wanted if l not in existing]
            if unknown:
                raise CodingError(f"Unknown label(s): {', '.join(unknown)[:200]}. Only existing labels can be used.")
            payload["labels"] = wanted
        item = self.request("POST", f"/repos/{self.repo}/issues", payload)
        return {"number": item.get("number"), "url": item.get("html_url")}

    def comment_issue(self, number, body):
        number = int(number)
        text = str(body or "").strip()[:MAX_ISSUE_BODY]
        if not 1 <= number < 10**9 or not text:
            raise CodingError("Give an issue number and a comment.")
        self.read_issue(number)  # must exist and be an issue, not a pull request
        item = self.request("POST", f"/repos/{self.repo}/issues/{number}/comments", {"body": text + COMMENT_FOOTER})
        return {"url": item.get("html_url")}

    # --- the only write sequence: new branch + one commit + PR into main ---
    def open_pull_request(self, files, title, description, branch):
        if not valid_agent_branch(branch):
            raise BlockedRequest("Use a new dua-agent/ branch with 1 to 80 lowercase letters, digits or dashes; start and end with a letter or digit.")
        files = validate_changes(files)
        if not files:
            raise CodingError("No file changes to submit. No pull request was opened.")
        base_sha = self.request("GET", f"/repos/{self.repo}/git/ref/heads/{BASE_BRANCH}")["object"]["sha"]
        base_commit = self.request("GET", f"/repos/{self.repo}/git/trees/{base_sha}")
        entries = []
        for item in files:
            blob = self.request("POST", f"/repos/{self.repo}/git/blobs", {"content": item["content"], "encoding": "utf-8"})
            entries.append({"path": item["path"], "mode": "100644", "type": "blob", "sha": blob["sha"]})
        tree = self.request("POST", f"/repos/{self.repo}/git/trees", {"base_tree": base_commit["sha"], "tree": entries})
        commit = self.request("POST", f"/repos/{self.repo}/git/commits", {"message": title, "tree": tree["sha"], "parents": [base_sha]})
        self.request("POST", f"/repos/{self.repo}/git/refs", {"ref": "refs/heads/" + branch, "sha": commit["sha"]})
        pr = self.request("POST", f"/repos/{self.repo}/pulls", {"title": title, "head": branch, "base": BASE_BRANCH, "body": description})
        return pr.get("number"), pr.get("html_url")


def validate_changes(files):
    if not isinstance(files, list):
        raise CodingError("Invalid change list.")
    if len(files) > MAX_FILES_CHANGED:
        raise CodingError(f"Too many files changed (max {MAX_FILES_CHANGED}).")
    total, clean, seen = 0, [], set()
    for item in files:
        if not isinstance(item, dict):
            raise CodingError("Invalid file change.")
        path = item.get("path")
        path = path.strip(" ") if isinstance(path, str) else path
        content = item.get("content")
        if not valid_path(path, write=True) or not isinstance(content, str):
            raise CodingError("Invalid or protected file change.")
        if path in seen:
            raise CodingError("Duplicate file change. Keep each file path only once before retrying.")
        size = len(content.encode("utf-8"))
        total += size
        if size > MAX_FILE_BYTES or total > MAX_TOTAL_CHANGE_BYTES:
            raise CodingError("Change is too large.")
        seen.add(path)
        clean.append({"path": path, "content": content})
    if conflicting_change_paths([item["path"] for item in clean]):
        raise CodingError("A file change conflicts with another file's folder. Split or correct the conflicting paths before retrying.")
    return clean


TOOLS = [
    {"name": "list_files", "description": "List file paths in the repository (main branch). Optional prefix filter.",
     "parameters": {"type": "OBJECT", "properties": {"prefix": {"type": "STRING"}}}},
    {"name": "read_file", "description": "Read one text file from main.",
     "parameters": {"type": "OBJECT", "properties": {"path": {"type": "STRING"}}, "required": ["path"]}},
    {"name": "search_code", "description": "Search code in the repository; returns matching file paths.",
     "parameters": {"type": "OBJECT", "properties": {"query": {"type": "STRING"}}, "required": ["query"]}},
    {"name": "propose_changes", "description": "Finish: full new contents of changed/new files plus PR title and simple description.",
     "parameters": {"type": "OBJECT", "properties": {
         "title": {"type": "STRING"}, "description": {"type": "STRING"},
         "files": {"type": "ARRAY", "items": {"type": "OBJECT", "properties": {
             "path": {"type": "STRING"}, "content": {"type": "STRING"}}, "required": ["path", "content"]}}},
         "required": ["title", "description", "files"]}},
]


def default_tools():
    from google.genai import types
    return [types.Tool(function_declarations=[types.FunctionDeclaration(**tool) for tool in TOOLS])]


def default_tool_response(calls_and_results):
    from google.genai import types
    return types.Content(role="user", parts=[types.Part.from_function_response(name=name, response=result) for name, result in calls_and_results])


def function_calls(response):
    calls = getattr(response, "function_calls", None) or []
    return [(call.name, dict(call.args or {})) for call in calls]


class CodingSession:
    def __init__(self, github, emit, clock=time.monotonic):
        self.github, self.emit, self.clock = github, emit, clock
        self.files_read, self.bytes_read, self.tree_cache = 0, 0, None

    def run_tool(self, name, args):
        try:
            if name == "list_files":
                if self.tree_cache is None:
                    self.tree_cache = self.github.tree()
                prefix = str(args.get("prefix", ""))
                paths = [p for p in self.tree_cache if p.startswith(prefix)]
                return {"paths": paths[:800], "truncated": len(paths) > 800}
            if name == "read_file":
                if self.files_read >= MAX_FILES_READ or self.bytes_read >= MAX_TOTAL_READ_BYTES:
                    return {"error": "Read limit reached; propose changes now."}
                text = self.github.read(args.get("path", ""))
                self.files_read += 1
                clipped = text[:MAX_READ_BYTES]
                self.bytes_read += len(clipped)
                return {"content": clipped, "truncated": len(text) > len(clipped)}
            if name == "search_code":
                return {"paths": self.github.search(str(args.get("query", ""))[:200])}
            return {"error": "Unknown tool."}
        except BlockedRequest as error:
            return {"error": str(error)}
        except CodingError as error:
            return {"error": str(error)[:300]}


def finish(github, emit, args, request_text):
    files = validate_changes(args.get("files") or [])
    title = " ".join(str(args.get("title") or "Dua Agent change").split())[:120]
    description = str(args.get("description") or "").strip()[:6000]
    if not files:
        return (description or "No change was needed.") + "\n\n(No pull request was opened.)"
    branch = f"{BRANCH_PREFIX}{slugify(title)}-{int(time.time()) % 100000}"
    emit(f"\n**Opening pull request** on `{branch}` ({len(files)} file(s))\n")
    body = (f"{description}\n\n---\nOpened by the Dua Agent for: \u201c{request_text[:500]}\u201d\n"
            "Review the changes and CI before merging. The agent never merges.")
    number, url = github.open_pull_request(files, f"Dua Agent: {title}", body, branch)
    changed = "\n".join(f"- `{item['path']}`" for item in files)
    return (f"Pull request ready: **#{number}** \u2014 {url}\n\n**{title}**\n\n{description}\n\nChanged files:\n{changed}\n\n"
            "Please review it and wait for CI before merging. I never merge or push to main.")
