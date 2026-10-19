"""Dua Agent: one general tool-calling loop on Gemini (function calling).

The model decides which tools to call; every tool runs here on the server with its own caps. Safety lives in code:
- Web pages: http(s) only, ports 80/443, every resolved IP must be public (no private, loopback, link-local or
  metadata addresses), the connection is pinned to the checked IP (no DNS rebinding), redirects are re-checked,
  size and time limits.
- GitHub: the existing locked-down coding_agent client (allowlist, new dua-agent/ branch + PR only, never merges).
- Images: the existing Imagine path (owner storage, daily cap), one image per request.
- Memory: `remember` sends the fact back to the app as a suggestion; the user reviews it in the existing Memory
  review list before it is saved (nothing is stored on the server). `recall` reads the Memory the app sent.
- Write tools (agent_safety.WRITE_TOOLS: propose_changes, create_issue, comment_on_issue, save_note, delete_note)
  never run inside the loop: the call is stored server-side and the user approves or rejects it in the app.
- Untrusted tool output goes back to the model inside UNTRUSTED_DATA markers (agent_safety.fence).
- env["cancelled"] (the app disconnected): the loop stops before the next model call or tool.
Caps per request: AGENT_MAX_STEPS model calls (default 12), per-tool limits below and a total time limit.
"""
import ast
import datetime
import html.parser
import http.client
import ipaddress
import json
import math
import operator
import os
import re
import socket
import ssl
import time
from urllib.parse import urljoin, urlsplit

MAX_STEPS = int(os.environ.get("AGENT_MAX_STEPS", "12"))
# Argon thinks longer (output cap includes thinking), so it gets 32768 per step unless AGENT_STEP_TOKENS is set.
STEP_TOKENS = int(os.environ.get("AGENT_STEP_TOKENS") or
                  ("32768" if os.environ.get("AGENT_MODEL", "").startswith("gemini-4") else "16384"))
SUB_TOKENS = int(os.environ.get("AGENT_SUB_TOKENS", "3072"))
MAX_SECONDS = int(os.environ.get("AGENT_MAX_SECONDS", "600"))
# Hard per-call deadlines so one slow model step or tool can never freeze the reply.
STEP_SECONDS = int(os.environ.get("AGENT_STEP_SECONDS", "150"))
TOOL_SECONDS = int(os.environ.get("AGENT_TOOL_SECONDS", "45"))
SLOW_TOOL_SECONDS = {"google_search": 90, "run_python": 90, "generate_image": 120}
TICK_SECONDS = 15
LIMITS = {"google_search": 3, "fetch_url": 5, "run_python": 2, "generate_image": 1, "remember": 3,
          "calculator": 20, "current_time": 5, "recall": 3,
          # Issues: list/read/search share one read budget; one new issue and one comment per request.
          "issue_reads": 5, "create_issue": 1, "comment_on_issue": 1,
          # One pull request per request (counted when it is queued for the user's approval).
          "propose_changes": 1,
          # Agent notes (agent_notes.py): the agent's own private lessons, separate from the app's Memory.
          "save_note": 3, "search_notes": 3, "delete_note": 3}
NOTE_TOOLS = ("save_note", "search_notes", "delete_note")
NOTES_HINT = (". Your private notes from earlier tasks may appear below; save_note a durable lesson (preference, what "
              "worked or failed, repo fact) when you learn one, search_notes to look one up, delete_note to remove a "
              "wrong one. Never put secrets or emails in notes")
ISSUE_READ_TOOLS = ("list_issues", "read_issue", "search_issues")
FETCH_MAX_BYTES = 2 * 1024 * 1024
FETCH_MAX_CHARS = 20_000
FETCH_TIMEOUT = 10
MAX_REDIRECTS = 3
MAX_SOURCES = 12
IST = datetime.timezone(datetime.timedelta(hours=5, minutes=30), "IST")

AGENT_PROMPT = (
    "You are the Dua Agent inside the user's own Dua AI app. Use tools when they help: google_search for current "
    "facts, fetch_url to read a specific page, run_python for exact calculations or data work, calculator for quick "
    "math, current_time for today's date/time in India (IST), generate_image when the user wants a picture, "
    "remember/recall for short personal facts the user asks you to keep{github}. Plan briefly, call only the tools "
    "you need (each has a small limit), then answer in simple, Hinglish-friendly English. Cite web facts like [1] "
    "using the source ids returned by google_search. Never claim you did something a tool did not do."
)
GITHUB_HINT = (", and the GitHub tools to read the Dua AI repository {repo} and propose_changes to open a pull "
               "request (read before you change; it never merges)")
ISSUES_HINT = (". For GitHub issues on {repo}: list_issues/search_issues/read_issue to look, create_issue only when the "
               "user asks for a new issue (search first to avoid duplicates), comment_on_issue only when asked; you "
               "cannot close, edit, assign or delete issues")

SEARCH_PROMPT = ("Use Google Search to find current, real information for: {query}\n"
                 "Write compact factual notes (names, dates, numbers). No advice.")
PYTHON_PROMPT = ("Write and run Python code to do this task, then state the result in one short line.\nTask: {task}")

PROGRESS = {
    "google_search": ("search", "Searching the web\u2026"),
    "fetch_url": ("fetch", "Opening page\u2026"),
    "run_python": ("python", "Running code\u2026"),
    "calculator": ("calc", "Calculating\u2026"),
    "current_time": ("time", "Checking the time\u2026"),
    "generate_image": ("image", "Creating image\u2026"),
    "remember": ("memory", "Saving to memory\u2026"),
    "recall": ("memory", "Checking memory\u2026"),
    "list_files": ("read", "Reading files\u2026"),
    "read_file": ("read", "Reading files\u2026"),
    "search_code": ("search_code", "Searching the code\u2026"),
    "propose_changes": ("pr", "Opening PR\u2026"),
    "list_issues": ("issues", "Reading issues\u2026"),
    "read_issue": ("issues", "Reading issues\u2026"),
    "search_issues": ("issues", "Searching issues\u2026"),
    "create_issue": ("issue_new", "Creating issue\u2026"),
    "comment_on_issue": ("issue_comment", "Commenting on issue\u2026"),
    "check_pr_ci": ("ci", "Checking CI\u2026"),
    "save_note": ("notes", "Saving a note\u2026"),
    "search_notes": ("notes", "Checking notes\u2026"),
    "delete_note": ("notes", "Updating notes\u2026"),
}

S, O = "STRING", "OBJECT"
BASE_TOOLS = [
    {"name": "google_search", "description": "Search the web (Google) for current facts. Returns notes and numbered sources.",
     "parameters": {"type": O, "properties": {"query": {"type": S}}, "required": ["query"]}},
    {"name": "fetch_url", "description": "Open one public web page (http/https) and return its readable text.",
     "parameters": {"type": O, "properties": {"url": {"type": S}}, "required": ["url"]}},
    {"name": "run_python", "description": "Write and run Python in a sandbox for exact math, data or text processing. Describe the task.",
     "parameters": {"type": O, "properties": {"task": {"type": S}}, "required": ["task"]}},
    {"name": "calculator", "description": "Evaluate a math expression, e.g. '(12.5*3)/7' or 'sqrt(2)'.",
     "parameters": {"type": O, "properties": {"expression": {"type": S}}, "required": ["expression"]}},
    {"name": "current_time", "description": "Current date and time in India (IST, UTC+05:30)."},
    {"name": "generate_image", "description": "Create ONE image from a description; it is shown in the chat and saved to the user's files.",
     "parameters": {"type": O, "properties": {"prompt": {"type": S},
                                              "aspect_ratio": {"type": S, "enum": ["1:1", "3:4", "4:3", "9:16", "16:9"]}},
                    "required": ["prompt"]}},
    {"name": "remember", "description": "Save one short fact about the user (max 200 characters) to the app's Memory.",
     "parameters": {"type": O, "properties": {"fact": {"type": S}}, "required": ["fact"]}},
    {"name": "recall", "description": "Read the facts the user saved in the app's Memory."},
]


ISSUE_TOOLS = [
    {"name": "list_issues", "description": "List recent issues (not pull requests) in the Dua AI repository.",
     "parameters": {"type": O, "properties": {"state": {"type": S, "enum": ["open", "closed", "all"]}}}},
    {"name": "read_issue", "description": "Read one issue with its comments.",
     "parameters": {"type": O, "properties": {"number": {"type": "INTEGER"}}, "required": ["number"]}},
    {"name": "search_issues", "description": "Search issues in the Dua AI repository by plain words.",
     "parameters": {"type": O, "properties": {"query": {"type": S}}, "required": ["query"]}},
    {"name": "create_issue", "description": "Open ONE new issue (only when the user asks). Labels must already exist.",
     "parameters": {"type": O, "properties": {"title": {"type": S}, "body": {"type": S},
                                              "labels": {"type": "ARRAY", "items": {"type": S}}},
                    "required": ["title", "body"]}},
    {"name": "comment_on_issue", "description": "Add ONE comment to an existing issue (only when the user asks).",
     "parameters": {"type": O, "properties": {"number": {"type": "INTEGER"}, "body": {"type": S}},
                    "required": ["number", "body"]}},
]


def declarations(github, issues=False, notes=False):
    import agent_notes
    import coding_agent
    import pr_flow
    ci = [coding_agent.CHECK_CI_TOOL] if github and pr_flow.enabled() else []
    return (BASE_TOOLS + (coding_agent.TOOLS if github else []) + ci + (ISSUE_TOOLS if github and issues else []) +
            (agent_notes.TOOLS if notes else []))


def default_tools(github, issues=False, notes=False):
    from google.genai import types
    return [types.Tool(function_declarations=[types.FunctionDeclaration(**tool)
                                              for tool in declarations(github, issues, notes)])]


# ---------------------------------------------------------------- calculator
_OPS = {ast.Add: operator.add, ast.Sub: operator.sub, ast.Mult: operator.mul, ast.Div: operator.truediv,
        ast.FloorDiv: operator.floordiv, ast.Mod: operator.mod, ast.Pow: operator.pow,
        ast.USub: operator.neg, ast.UAdd: operator.pos}
_FUNCS = {name: getattr(math, name) for name in ("sqrt", "sin", "cos", "tan", "log", "log10", "log2", "exp",
                                                 "floor", "ceil", "factorial", "radians", "degrees")}
_FUNCS.update(abs=abs, round=round, min=min, max=max)
_CONSTS = {"pi": math.pi, "e": math.e}


def calculate(expression):
    text = str(expression or "").replace("^", "**").replace("\u00d7", "*").replace("\u00f7", "/")
    if not text.strip() or len(text) > 300:
        raise ValueError("Give a short math expression.")
    tree = ast.parse(text, mode="eval")

    def ev(node):
        if isinstance(node, ast.Expression):
            return ev(node.body)
        if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)) and not isinstance(node.value, bool):
            return node.value
        if isinstance(node, ast.Name) and node.id in _CONSTS:
            return _CONSTS[node.id]
        if isinstance(node, ast.BinOp) and type(node.op) in _OPS:
            left, right = ev(node.left), ev(node.right)
            if isinstance(node.op, ast.Pow) and (abs(right) > 100 or abs(left) > 1e6):
                raise ValueError("Number too large.")
            return _OPS[type(node.op)](left, right)
        if isinstance(node, ast.UnaryOp) and type(node.op) in _OPS:
            return _OPS[type(node.op)](ev(node.operand))
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Name) and node.func.id in _FUNCS and not node.keywords:
            args = [ev(a) for a in node.args]
            if node.func.id == "factorial" and (not args or args[0] > 170):
                raise ValueError("Number too large.")
            return _FUNCS[node.func.id](*args)
        raise ValueError("Only numbers, + - * / // % ** and simple math functions are allowed.")
    value = ev(tree)
    if isinstance(value, float) and (math.isnan(value) or math.isinf(value)):
        raise ValueError("The result is not a finite number.")
    return value


def now_ist(clock=None):
    current = (clock or (lambda: datetime.datetime.now(datetime.timezone.utc)))().astimezone(IST)
    return {"iso": current.isoformat(timespec="seconds"), "readable": current.strftime("%A, %d %B %Y, %I:%M %p IST"),
            "timezone": "Asia/Kolkata (UTC+05:30)"}


# ---------------------------------------------------------------- fetch_url (SSRF-safe)
class FetchError(Exception):
    pass


def public_ips(host, resolver=socket.getaddrinfo):
    """All addresses of [host]; refuses the whole host if ANY of them is not a public address."""
    try:
        infos = resolver(host, None, proto=socket.IPPROTO_TCP)
    except (socket.gaierror, UnicodeError):
        raise FetchError("That address could not be found.") from None
    ips = []
    for info in infos:
        ip = ipaddress.ip_address(info[4][0].split("%")[0])
        if isinstance(ip, ipaddress.IPv6Address) and ip.ipv4_mapped:
            ip = ip.ipv4_mapped
        if not ip.is_global or ip.is_multicast or ip.is_reserved or ip.is_loopback or ip.is_link_local or ip.is_private:
            raise FetchError("Private or internal addresses cannot be opened.")
        ips.append(str(ip))
    if not ips:
        raise FetchError("That address could not be found.")
    return ips


def check_url(url):
    parts = urlsplit(str(url or "").strip())
    if parts.scheme not in ("http", "https") or not parts.hostname or parts.username or parts.password:
        raise FetchError("Only public http(s) links can be opened.")
    port = parts.port or (443 if parts.scheme == "https" else 80)
    if port not in (80, 443):
        raise FetchError("Only standard web ports (80/443) are allowed.")
    host = parts.hostname.rstrip(".").lower()
    if host == "localhost" or host.endswith((".localhost", ".internal", ".local")) or host == "metadata.google.internal":
        raise FetchError("Private or internal addresses cannot be opened.")
    return parts, host, port


class _PinnedHTTP(http.client.HTTPConnection):
    def __init__(self, host, ip, port, timeout):
        super().__init__(host, port, timeout=timeout)
        self._ip = ip

    def connect(self):
        self.sock = socket.create_connection((self._ip, self.port), self.timeout)


class _PinnedHTTPS(http.client.HTTPSConnection):
    def __init__(self, host, ip, port, timeout):
        super().__init__(host, port, timeout=timeout, context=ssl.create_default_context())
        self._ip = ip

    def connect(self):
        sock = socket.create_connection((self._ip, self.port), self.timeout)
        self.sock = self._context.wrap_socket(sock, server_hostname=self.host)


class _TextExtractor(html.parser.HTMLParser):
    SKIP = {"script", "style", "noscript", "svg", "template", "iframe", "head"}

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.parts, self.skip, self.title, self._in_title = [], 0, "", False

    def handle_starttag(self, tag, attrs):
        if tag in self.SKIP:
            self.skip += 1
        if tag == "title":
            self._in_title = True
        if tag in ("p", "br", "li", "h1", "h2", "h3", "h4", "tr", "div", "section"):
            self.parts.append("\n")

    def handle_endtag(self, tag):
        if tag in self.SKIP and self.skip:
            self.skip -= 1
        if tag == "title":
            self._in_title = False

    def handle_data(self, data):
        if self._in_title and not self.title:
            self.title = data.strip()[:200]
        elif not self.skip:
            self.parts.append(data)


def extract_text(raw, content_type):
    if "html" not in content_type:
        return "", re.sub(r"[ \t]+", " ", raw).strip()
    parser = _TextExtractor()
    parser.feed(raw)
    text = re.sub(r"[ \t\r\f\v]+", " ", "".join(parser.parts))
    text = re.sub(r"\n\s*\n+", "\n\n", text).strip()
    return parser.title, text


def fetch_page(url, connect=None, resolver=socket.getaddrinfo):
    """Readable text of one public page. [connect] (tests) returns an http.client-like connection."""
    current = url
    for _ in range(MAX_REDIRECTS + 1):
        parts, host, port = check_url(current)
        ip = public_ips(host, resolver)[0]
        conn = (connect or (lambda scheme, h, i, p: (_PinnedHTTPS if scheme == "https" else _PinnedHTTP)(h, i, p, FETCH_TIMEOUT)))(
            parts.scheme, host, ip, port)
        try:
            path = (parts.path or "/") + (("?" + parts.query) if parts.query else "")
            conn.request("GET", path, headers={"Host": parts.netloc.split("@")[-1], "User-Agent": "DuaAgent/1.0 (+reader)",
                                                "Accept": "text/html,text/plain,application/json;q=0.9", "Accept-Encoding": "identity"})
            response = conn.getresponse()
            if response.status in (301, 302, 303, 307, 308):
                location = response.getheader("Location")
                if not location:
                    raise FetchError("The page redirected without a location.")
                current = urljoin(current, location)
                continue
            if response.status != 200:
                raise FetchError(f"The page answered with status {response.status}.")
            content_type = (response.getheader("Content-Type") or "").lower()
            if not any(kind in content_type for kind in ("text/html", "text/plain", "application/json", "application/xhtml")):
                raise FetchError("Only web pages and text can be read (not files or media).")
            body = response.read(FETCH_MAX_BYTES + 1)
            truncated = len(body) > FETCH_MAX_BYTES
            charset = re.search(r"charset=([\w-]+)", content_type)
            raw = body[:FETCH_MAX_BYTES].decode(charset.group(1) if charset else "utf-8", errors="replace")
            title, text = extract_text(raw, content_type)
            return {"url": current, "title": title, "text": text[:FETCH_MAX_CHARS],
                    "truncated": truncated or len(text) > FETCH_MAX_CHARS}
        except (OSError, http.client.HTTPException, LookupError):
            raise FetchError("The page could not be opened.") from None
        finally:
            conn.close()
    raise FetchError("Too many redirects.")


# ---------------------------------------------------------------- built-in sub-calls (Search, code execution)
def search_tool():
    from google.genai import types
    return types.Tool(google_search=types.GoogleSearch())


def code_tool():
    from google.genai import types
    return types.Tool(code_execution=types.ToolCodeExecution())


def code_parts(response):
    code, output = [], []
    try:
        parts = response.candidates[0].content.parts or []
    except (AttributeError, IndexError, TypeError):
        parts = []
    for part in parts:
        executable = getattr(part, "executable_code", None)
        result = getattr(part, "code_execution_result", None)
        if executable is not None and getattr(executable, "code", None):
            code.append(executable.code)
        if result is not None and getattr(result, "output", None) is not None:
            output.append(str(result.output))
    return "\n\n".join(code)[:6000], "\n".join(output)[:6000]


# ---------------------------------------------------------------- the loop
class AgentRun:
    """State for one agent request. [env] gives the tool implementations their dependencies (mocked in tests)."""

    def __init__(self, env):
        self.env = env
        self.counts = {}
        self.sources = []
        self.media = []
        self.facts = []
        self.coding = None
        self.steps = []
        self.prflow = None
        self.evidence = []  # short tool results, for the optional self-check (agent_verify)
        self.pending = []  # write actions waiting for the user's approval (agent_safety)
        self.cancelled = False

    def stop_requested(self):
        cancelled = self.env.get("cancelled")
        if cancelled is not None and cancelled():
            self.cancelled = True
        return self.cancelled

    def use_budget(self, name):
        """Counts one use of [name]; returns an error dict when its per-request limit is reached."""
        budget = "issue_reads" if name in ISSUE_READ_TOOLS else name
        limit = LIMITS.get(budget)
        if limit is not None:
            if self.counts.get(budget, 0) >= limit:
                return {"error": f"{name} limit reached for this request ({limit}). Answer with what you have."}
            self.counts[budget] = self.counts.get(budget, 0) + 1
        return None

    def request_approval(self, name, args):
        """A write tool call: checked and stored for the user's approval, never executed here."""
        import agent_safety
        env = self.env
        if name == "propose_changes" and env.get("github") is None:
            return {"error": "GitHub tools are not set up."}
        if name in ("create_issue", "comment_on_issue") and (env.get("github") is None or not env["github"].issues_enabled()):
            return {"error": "Issue tools are not set up for this repository."}
        if name in NOTE_TOOLS and env.get("notes") is None:
            return {"error": "Agent notes are turned off."}
        if len(self.pending) >= agent_safety.MAX_PENDING_PER_REQUEST:
            return {"error": "Enough actions are already waiting for the user's approval. Finish your answer now."}
        try:
            stored = agent_safety.prepare(name, args)
        except ValueError as error:
            return {"error": str(error)[:600]}
        except Exception:
            return {"error": "The tool failed."}
        over = self.use_budget(name)
        if over is not None:
            return over
        store = env.get("approvals")
        action = (agent_safety.STORE if store is None else store).create(env.get("owner_id"), name, stored,
                                                                         env.get("request_text", ""))
        self.pending.append(action)
        self.emit(agent_safety.event(action))
        log(f"approval requested: {name}")
        return dict(agent_safety.PENDING_RESULT)

    def emit(self, event):
        return self.env["enqueue"]("event", event)

    def add_sources(self, found):
        ids = []
        for item in found:
            existing = next((s for s in self.sources if s["url"] == item["url"]), None)
            if existing is None:
                if len(self.sources) >= MAX_SOURCES:
                    continue
                existing = dict(item, id=len(self.sources) + 1)
                self.sources.append(existing)
            ids.append({"id": existing["id"], "title": existing["title"], "url": existing["url"]})
        return ids

    def pr_flow_for(self, env):
        import pr_flow
        if self.prflow is None:
            self.prflow = pr_flow.PrFlow(env["github"], env["thinking"], env["progress"], env["request_text"])
        return self.prflow

    def run_tool_timed(self, name, args):
        """run_tool with a hard deadline; a slow tool returns an error to the model instead of hanging."""
        seconds = SLOW_TOOL_SECONDS.get(name, TOOL_SECONDS)
        started = time.monotonic()
        try:
            result = call_with_deadline(lambda: self.run_tool(name, args), seconds, cancel=self.env.get("cancelled"))
        except Cancelled:
            self.cancelled = True
            result = {"error": "Stopped: the app disconnected."}
        except DeadlineExceeded:
            result = {"error": f"{name} timed out after {seconds}s. Continue without it."}
        if sum(len(e) for e in self.evidence) < 12000:
            try:
                self.evidence.append(f"{name}: {json.dumps(result, ensure_ascii=False, default=str)[:1500]}")
            except (TypeError, ValueError):
                pass
        log(f"tool {name}: {time.monotonic() - started:.1f}s{' (error)' if isinstance(result, dict) and 'error' in result else ''}")
        return result

    def run_tool(self, name, args):
        over = self.use_budget(name)
        if over is not None:
            return over
        env = self.env
        try:
            if name == "google_search":
                query = str(args.get("query", "")).strip()[:300]
                if not query:
                    return {"error": "Empty query."}
                response = env["model"].generate_content(env["contents_for"](SEARCH_PROMPT.format(query=query)),
                                                         generation_config={"max_output_tokens": SUB_TOKENS, "temperature": 0.2},
                                                         tools=[env["search_tool"]()])
                notes = env["chunk_text"](response)[:8000]
                sources = self.add_sources(env["grounding_sources"](response))
                return {"notes": notes or "(no notes)", "sources": sources}
            if name == "fetch_url":
                page = env["fetch"](str(args.get("url", "")))
                self.add_sources([{"title": page["title"] or page["url"], "url": page["url"], "domain": urlsplit(page["url"]).hostname or ""}]
                                 if page["url"].startswith("https://") else [])
                return page
            if name == "run_python":
                task = str(args.get("task", "")).strip()[:2000]
                if not task:
                    return {"error": "Describe the task."}
                response = env["model"].generate_content(env["contents_for"](PYTHON_PROMPT.format(task=task)),
                                                         generation_config={"max_output_tokens": SUB_TOKENS, "temperature": 0.0},
                                                         tools=[env["code_tool"]()])
                code, output = code_parts(response)
                if not code:
                    return {"error": "Code could not be run for this task.", "answer": env["chunk_text"](response)[:2000]}
                return {"code": code, "output": output, "answer": env["chunk_text"](response)[:2000]}
            if name == "calculator":
                return {"result": calculate(args.get("expression"))}
            if name == "current_time":
                return now_ist(env.get("clock_utc"))
            if name == "generate_image":
                if not env.get("imagine"):
                    return {"error": "Image creation is not available here."}
                prompt = str(args.get("prompt", "")).strip()
                aspect = args.get("aspect_ratio") if args.get("aspect_ratio") in ("1:1", "3:4", "4:3", "9:16", "16:9") else "1:1"
                result = env["imagine"]({"prompt": prompt, "aspectRatio": aspect})
                images = result.get("images") or []
                self.media.extend(images)
                if images and not self.emit({"type": "media", "images": images, "model": result.get("model", "")}):
                    return {"error": "Stopped."}
                return {"created": len(images), "note": "The image is already shown to the user in the chat; do not link it."}
            if name == "remember":
                fact = " ".join(str(args.get("fact", "")).split())[:200]
                if not fact:
                    return {"error": "Nothing to remember."}
                self.facts.append(fact)
                self.emit({"type": "memory", "fact": fact})
                return {"suggested": True, "note": "Suggested to the user's Memory; they confirm it in the app before it is saved."}
            if name == "check_pr_ci":
                if self.prflow is None:
                    return {"error": "No pull request was opened in this request yet."}
                return self.prflow.ci_status()
            if name in NOTE_TOOLS:
                import agent_notes
                return agent_notes.run_tool(env.get("notes"), name, args)
            if name == "recall":
                memory = env.get("memory") or ""
                return {"facts": memory or "(Memory is empty or turned off in the app.)"}
            if name in ISSUE_READ_TOOLS + ("create_issue", "comment_on_issue"):
                github = env.get("github")
                if github is None or not github.issues_enabled():
                    return {"error": "Issue tools are not set up for this repository."}
                if name == "list_issues":
                    return {"issues": github.list_issues(str(args.get("state", "open")))}
                if name == "read_issue":
                    return github.read_issue(args.get("number"))
                if name == "search_issues":
                    return {"issues": github.search_issues(str(args.get("query", "")))}
                if name == "create_issue":
                    labels = args.get("labels") if isinstance(args.get("labels"), list) else []
                    return dict(github.create_issue(args.get("title"), args.get("body"), labels), created=True)
                return dict(github.comment_issue(args.get("number"), args.get("body")), commented=True)
            if name in ("list_files", "read_file", "search_code"):
                if self.coding is None:
                    return {"error": "GitHub tools are not set up."}
                return self.coding.run_tool(name, args)
            return {"error": "Unknown tool."}
        except Exception as error:  # every tool failure goes back to the model as a safe message
            message = str(error) if type(error).__name__ in ("FetchError", "ValueError", "ImagineError", "CodingError",
                                                             "BlockedRequest", "ZeroDivisionError") else "The tool failed."
            if type(error).__name__ == "ImagineError":
                message = getattr(error, "message", message)
            return {"error": message[:300]}


def function_calls(response):
    calls = getattr(response, "function_calls", None) or []
    return [(call.name, dict(call.args or {})) for call in calls]


class DeadlineExceeded(Exception):
    pass


class Cancelled(Exception):
    """The app disconnected (env["cancelled"]() is true): stop spending on this request."""


CANCEL_POLL_SECONDS = 1.0


def call_with_deadline(fn, seconds, tick=None, every=TICK_SECONDS, cancel=None):
    """Runs fn() in a daemon thread; raises DeadlineExceeded after [seconds]. tick(elapsed) every [every] s.
    With [cancel], checks it about once a second and raises Cancelled as soon as it returns true.

    A timed-out call keeps running in the background until its own HTTP timeout ends it, but the loop moves on."""
    import threading
    box, done = {}, threading.Event()

    def work():
        try:
            box["value"] = fn()
        except BaseException as error:  # handed back to the caller
            box["error"] = error
        finally:
            done.set()
    if cancel is not None and cancel():
        raise Cancelled()
    threading.Thread(target=work, name="dua-agent-call", daemon=True).start()
    started = time.monotonic()
    next_tick = every
    while True:
        elapsed = time.monotonic() - started
        left = seconds - elapsed
        if left <= 0:
            raise DeadlineExceeded()
        wait = min(next_tick - elapsed, left)
        if cancel is not None:
            wait = min(wait, CANCEL_POLL_SECONDS)
        if done.wait(max(wait, 0)):
            break
        if cancel is not None and cancel():
            raise Cancelled()
        # Same ticks as before: after each full [every] wait, and once more when the deadline arrives.
        if tick is not None and time.monotonic() - started >= min(next_tick, seconds):
            next_tick += every
            tick(int(time.monotonic() - started))
    if "error" in box:
        raise box["error"]
    return box.get("value")


def log(message):
    print(f"[dua-agent] {message}", flush=True)


STOPPED_TEXT = "Agent stopped: the app disconnected."


def run(env, history, tools, tool_response, clock=time.monotonic):
    """Tool loop. Returns (final_text, run). The caller streams the text and the sources footer."""
    import agent_safety
    import coding_agent
    agent = AgentRun(env)
    if env.get("github") is not None:
        agent.coding = coding_agent.CodingSession(env["github"], lambda d: True, clock)
    progress = env["progress"]
    started = clock()
    history = list(history)
    for step in range(1, MAX_STEPS + 1):
        if agent.stop_requested():
            log(f"step {step}: app disconnected; stopping")
            return STOPPED_TEXT, agent
        if clock() - started > MAX_SECONDS:
            return "Agent stopped: time limit reached. Here is what I could not finish; try a smaller request.", agent
        # Slow steps only: a spinning "Still thinking" chip (own id per step, since the app never un-finishes a chip);
        # the next chip finishes it. Fast steps add no chips.
        think_id = "think_" + chr(ord("a") + step - 1)
        snapshot = list(history)
        step_started = time.monotonic()
        try:
            response = call_with_deadline(
                lambda: env["model"].generate_content(snapshot, generation_config={
                    "max_output_tokens": STEP_TOKENS, "temperature": 0.3, "timeout_seconds": STEP_SECONDS}, tools=tools),
                STEP_SECONDS, tick=lambda s: progress(think_id, f"Still thinking (step {step}, {s}s)\u2026"),
                cancel=env.get("cancelled"))
        except Cancelled:
            agent.cancelled = True
            log(f"step {step}: app disconnected during the model call; stopping")
            return STOPPED_TEXT, agent
        except DeadlineExceeded:
            log(f"step {step}: model timed out after {STEP_SECONDS}s")
            progress(think_id, "Model took too long", "done")
            return (f"Agent stopped: the model took longer than {STEP_SECONDS}s on step {step}. Please try again, "
                    "or ask a simpler question."), agent
        log(f"step {step}: model {time.monotonic() - step_started:.1f}s")
        calls = function_calls(response)
        if not calls:
            return env["chunk_text"](response).strip() or "I could not finish this request.", agent
        history.append(response.candidates[0].content)
        results = []
        for name, args in calls:
            if agent.stop_requested():
                return STOPPED_TEXT, agent
            if name in PROGRESS and name not in agent_safety.WRITE_TOOLS:
                progress(*PROGRESS[name])
            shown = {k: (v if not isinstance(v, str) else v[:100]) for k, v in args.items() if k not in ("content", "files")}
            line = f"- Step {step}: {name} {json.dumps(shown, ensure_ascii=False)[:160]}\n"
            agent.steps.append(line)
            env["thinking"](line)
            if clock() - started > MAX_SECONDS:
                # Keeps the whole request inside Cloud Run's 900 s: tools that would start after 600 s are skipped.
                results.append((name, {"error": "Time limit reached; answer with what you have."}))
                continue
            if name in agent_safety.WRITE_TOOLS:
                # Never executed here: stored for the user's approval (POST /api/agent/action runs it).
                results.append((name, agent.request_approval(name, args)))
                continue
            results.append((name, agent_safety.fence(name, agent.run_tool_timed(name, args))))
            if name in PROGRESS:
                progress(PROGRESS[name][0], PROGRESS[name][1].rstrip("\u2026"), "done")
        if agent.pending:
            # Stop here: the next step depends on the user's decision (they approve or reject in the app).
            progress("approval", "Waiting for your approval", "done")
            return agent_safety.pending_text(agent.pending).lstrip(), agent
        if agent.cancelled:
            return STOPPED_TEXT, agent
        history.append(tool_response(results))
    return f"Agent stopped after {MAX_STEPS} steps. Try a smaller, more specific request.", agent
