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


class CodingError(Exception):
    """Message is safe to show to the user."""


class BlockedRequest(CodingError):
    pass


def repo_name():
    repo = os.environ.get("DUA_REPO", "KRFbrothers/Dua-ai").strip()
    if not re.fullmatch(r"[A-Za-z0-9-]{1,39}/[A-Za-z0-9._-]{1,100}", repo):
        raise CodingError("DUA_REPO is not a valid owner/name.")
    return repo


def slugify(text, limit=40):
    slug = SLUG.sub("-", (text or "change").lower()).strip("-")[:limit].strip("-")
    return slug or "change"


def allowed(method, path, body, repo):
    """The only GitHub requests this server may send. Returns a reason string when blocked, else None."""
    r = re.escape(repo)
    base = f"/repos/{r}"
    rules = {
        "GET": [rf"^{base}$", rf"^{base}/git/trees/[A-Za-z0-9._\-/]+$", rf"^{base}/contents/[^?]*$",
                rf"^{base}/git/ref/heads/[A-Za-z0-9._\-/]+$", r"^/search/code$"],
        "POST": [rf"^{base}/git/blobs$", rf"^{base}/git/trees$", rf"^{base}/git/commits$",
                 rf"^{base}/git/refs$", rf"^{base}/pulls$"],
    }
    if method not in rules or not any(re.match(p, path) for p in rules[method]):
        return f"{method} {path} is not allowed"
    if method == "POST" and path.endswith("/git/refs"):
        ref = str((body or {}).get("ref", ""))
        if not ref.startswith("refs/heads/" + BRANCH_PREFIX) or ".." in ref:
            return "only new dua-agent/ branches may be created"
    if method == "POST" and path.endswith("/pulls"):
        if (body or {}).get("base") != BASE_BRANCH or not str((body or {}).get("head", "")).startswith(BRANCH_PREFIX):
            return "pull requests must go from a dua-agent/ branch into main"
    return None


class GitHubClient:
    def __init__(self, token, repo, opener=None):
        self.token, self.repo = token, repo
        self.opener = opener or build_opener(type("NoRedirect", (HTTPRedirectHandler,), {"redirect_request": lambda *a, **k: None}))

    def request(self, method, path, body=None, query=None):
        reason = allowed(method, path, body, self.repo)
        if reason:
            raise BlockedRequest(f"Blocked GitHub request: {reason}.")
        if method == "GET" and path == "/search/code":
            q = str((query or {}).get("q", ""))
            if f"repo:{self.repo}" not in q.split():
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
                raise CodingError("GitHub refused access. Check that the GitHub token is valid and has Contents and Pull requests (read and write) on the repository.")
            if status == 404:
                raise CodingError("not found")
            raise CodingError(f"GitHub request failed ({status}).")

    # --- read helpers used by model tools ---
    def tree(self):
        data = self.request("GET", f"/repos/{self.repo}/git/trees/{BASE_BRANCH}", query={"recursive": "1"})
        return [item["path"] for item in data.get("tree", []) if item.get("type") == "blob"]

    def read(self, path):
        if not PATH_OK.match(path or ""):
            raise CodingError("Invalid path.")
        data = self.request("GET", f"/repos/{self.repo}/contents/{path}", query={"ref": BASE_BRANCH})
        if not isinstance(data, dict) or data.get("type") != "file":
            raise CodingError("Not a file.")
        return base64.b64decode(data.get("content", "")).decode("utf-8", errors="replace")

    def search(self, text):
        data = self.request("GET", "/search/code", query={"q": f"{text} repo:{self.repo}", "per_page": "20"})
        return [item.get("path") for item in (data or {}).get("items", [])][:20]

    # --- the only write sequence: new branch + one commit + PR into main ---
    def open_pull_request(self, files, title, description, branch):
        if not branch.startswith(BRANCH_PREFIX):
            raise BlockedRequest("Blocked GitHub request: branch must start with dua-agent/.")
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
        path = str((item or {}).get("path", "")).strip()
        content = (item or {}).get("content")
        if not PATH_OK.match(path) or path.startswith(".github/") or path in seen or not isinstance(content, str):
            raise CodingError(f"Invalid file change: {path[:100]}")
        size = len(content.encode("utf-8"))
        total += size
        if size > MAX_FILE_BYTES or total > MAX_TOTAL_CHANGE_BYTES:
            raise CodingError("Change is too large.")
        seen.add(path)
        clean.append({"path": path, "content": content})
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
                text = self.github.read(str(args.get("path", "")))
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


def run_coding(model, contents, github, emit, request_text, tools=None, tool_response=None, clock=time.monotonic):
    """Tool loop. Returns the final user-facing text (with the PR link when one was opened)."""
    tools = tools if tools is not None else default_tools()
    tool_response = tool_response or default_tool_response
    session = CodingSession(github, emit, clock)
    started = clock()
    history = list(contents)
    for step in range(1, MAX_TOOL_STEPS + 1):
        if clock() - started > MAX_SECONDS:
            return "Coding stopped: time limit reached before a change was ready. Try a smaller request."
        response = model.generate_content(history, generation_config={"max_output_tokens": STEP_TOKENS, "temperature": 0.2}, tools=tools)
        calls = function_calls(response)
        if not calls:
            text = ""
            try:
                text = response.text or ""
            except (AttributeError, ValueError):
                pass
            return (text.strip() or "I could not prepare a code change for this request.") + "\n\n(No pull request was opened.)"
        history.append(response.candidates[0].content)
        results = []
        for name, args in calls:
            if name == "propose_changes":
                return finish(github, emit, args, request_text)
            emit(f"- Step {step}: {name} {json.dumps({k: v for k, v in args.items() if k != 'content'})[:120]}\n")
            results.append((name, session.run_tool(name, args)))
        history.append(tool_response(results))
    return f"Coding stopped after {MAX_TOOL_STEPS} steps without a finished change. Try a smaller, more specific request."


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
