#!/usr/bin/env python3
"""Dua Agent eval runner: 20 fixed requests, a pass/fail score per case, a total score and a report file.

Two modes:
  offline (default)  Free and deterministic. Runs the REAL engine code (Flask /api/chat, router, agent_tools loop,
                     agent_adk path, approvals, untrusted-data fences, caps) with a scripted fake model and fake
                     GitHub/web/notes/image tools. No network, no keys, no cost. Used by pytest.
  live (--live)      Opt-in. Sends the cases to a deployed engine (DUA_ENGINE_URL) with the owner's sign-in.
                     Costs real money (Vertex AI calls, web search). Never approves anything: every approval card
                     the Agent creates is rejected right away, so nothing is written.

Usage:
  python3 evals/run_eval.py                       # offline
  python3 evals/run_eval.py --live --yes          # live (see evals/README.md for the one-line Cloud Shell command)

Live sign-in (the token is kept in memory only, never printed or saved):
  DUA_EVAL_TOKEN      owner access token, or
  DUA_EVAL_EMAIL + DUA_API_URL   then the email password is asked with a hidden prompt and exchanged through
                      dua-api's owner-only email sign-in.
  DUA_EVAL_APP_TOKEN  optional, only if the engine uses DUA_APP_TOKEN.
Standard library only (offline mode also needs the engine's own requirements: Flask).
"""
import argparse
import contextlib
import datetime
import getpass
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from types import SimpleNamespace

HERE = os.path.dirname(os.path.abspath(__file__))
ENGINE_DIR = os.path.dirname(HERE)
EVAL_FILE = os.path.join(HERE, "agent_eval_set.json")
MAX_LIVE_CASES = 20  # hard cap per live run
DEFAULT_REPORT_DIR = os.path.join(os.path.expanduser("~"), "dua-eval-reports")
CATEGORIES = {"casual_chat", "factual", "web_research", "github_read", "issue_summary", "code_change_approval",
              "save_note_approval", "prompt_injection", "secret_refusal", "multi_step_plan", "step_time_cap",
              "image_routing", "owner_rules"}
EXPECT_KEYS = {"route", "must_call", "must_call_any", "must_not_call", "approval_expected", "approval_tools",
               "must_contain", "must_not_contain", "must_match", "must_not_match", "min_sources", "media_expected",
               "finishes"}
KNOWN_TOOLS = {"google_search", "fetch_url", "run_python", "calculator", "current_time", "generate_image", "remember",
               "recall", "list_files", "read_file", "search_code", "propose_changes", "check_pr_ci", "list_issues",
               "read_issue", "search_issues", "create_issue", "comment_on_issue", "save_note", "search_notes",
               "delete_note"}
COST_WARNING = (
    "LIVE MODE COSTS MONEY. Each case is a real Agent request to your engine: several Vertex AI model calls "
    "(Argon or Flash), web searches and GitHub reads. Expect roughly the cost of {n} normal Agent questions, and "
    "they count toward any daily limits. Approval cards are rejected automatically (nothing is written), but the "
    "Agent may still save small lessons to its private notes after a reply.")
TOOL_LINE = re.compile(r"^(?:\[[A-C]\] )?- (?:Step (\d+): )?([a-z_]+) [{\[]")


# ------------------------------------------------------------------ eval file
def load_eval(path=EVAL_FILE):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def validate(data):
    """List of problems in the eval file (empty = valid)."""
    problems = []
    cases = data.get("cases") if isinstance(data, dict) else None
    if not isinstance(cases, list):
        return ["'cases' must be a list"]
    if len(cases) != 20:
        problems.append(f"expected 20 cases, found {len(cases)}")
    seen = set()
    for index, case in enumerate(cases):
        where = f"case {index} ({case.get('id', '?') if isinstance(case, dict) else '?'})"
        if not isinstance(case, dict):
            problems.append(f"{where}: not an object")
            continue
        for key in ("id", "prompt", "category", "expect", "max_steps", "notes"):
            if key not in case:
                problems.append(f"{where}: missing '{key}'")
        if case.get("id") in seen:
            problems.append(f"{where}: duplicate id")
        seen.add(case.get("id"))
        if not isinstance(case.get("prompt"), str) or not case.get("prompt", "").strip():
            problems.append(f"{where}: empty prompt")
        if case.get("category") not in CATEGORIES:
            problems.append(f"{where}: unknown category {case.get('category')!r}")
        if not isinstance(case.get("max_steps"), int) or not 0 <= case["max_steps"] <= 30:
            problems.append(f"{where}: max_steps must be 0-30")
        expect = case.get("expect") or {}
        unknown = set(expect) - EXPECT_KEYS
        if unknown:
            problems.append(f"{where}: unknown expect keys {sorted(unknown)}")
        for key in ("must_call", "must_call_any", "must_not_call", "approval_tools"):
            bad = [t for t in expect.get(key, []) if t not in KNOWN_TOOLS]
            if bad:
                problems.append(f"{where}: {key} has unknown tools {bad}")
        overlap = set(expect.get("must_call", [])) & set(expect.get("must_not_call", []))
        if overlap:
            problems.append(f"{where}: tools both required and forbidden {sorted(overlap)}")
        if not isinstance(expect.get("approval_expected"), bool):
            problems.append(f"{where}: approval_expected must be true/false")
        for group in expect.get("must_contain", []):
            if not isinstance(group, list) or not group or not all(isinstance(x, str) for x in group):
                problems.append(f"{where}: must_contain items are lists of alternative strings")
        for pattern in expect.get("must_match", []) + expect.get("must_not_match", []):
            try:
                re.compile(pattern)
            except re.error as error:
                problems.append(f"{where}: bad regex {pattern!r}: {error}")
        offline = case.get("offline")
        if not isinstance(offline, dict) or offline.get("route") not in ("chat", "agent", "code"):
            problems.append(f"{where}: offline.route must be chat|agent|code")
    return problems


# ------------------------------------------------------------------ observing one reply (same for both modes)
def parse_sse(text):
    events = []
    for block in text.replace("\r\n", "\n").split("\n\n"):
        data = [line[6:] for line in block.split("\n") if line.startswith("data: ")]
        if data:
            try:
                events.append(json.loads("\n".join(data)))
            except ValueError:
                continue
    return events


def observe(events):
    obs = {"route": "chat", "tools": [], "steps": 0, "approvals": [], "text": "", "sources": 0, "media": 0,
           "error": None, "done": False}
    thinking = []
    for event in events:
        kind = event.get("type")
        if kind == "progress" and event.get("step") in ("plan", "sub_a", "sub_b", "sub_c", "merge"):
            obs["route"] = "agent"
        elif kind == "thinking":
            thinking.append(str(event.get("delta", "")))
        elif kind == "approval":
            obs["approvals"].append({"id": event.get("id"), "tool": event.get("tool"), "title": event.get("title")})
        elif kind == "text":
            obs["text"] += str(event.get("delta", ""))
        elif kind == "sources":
            obs["sources"] = len(event.get("sources") or [])
        elif kind == "media":
            obs["media"] += len(event.get("images") or [])
        elif kind == "error":
            obs["error"] = str(event.get("message", "error"))[:300]
        elif kind == "done":
            obs["done"] = True
    step_numbers = []
    for line in "".join(thinking).splitlines():
        match = TOOL_LINE.match(line.strip())
        if match:
            obs["tools"].append(match.group(2))
            if match.group(1):
                step_numbers.append(int(match.group(1)))
    obs["steps"] = max(step_numbers) if step_numbers else len(obs["tools"])
    return obs


def score(case, obs, extra=None):
    """[(check, ok, detail)] for one case. extra: offline-only invariants."""
    expect, checks = case["expect"], []
    tools, text = set(obs["tools"]), obs["text"]
    lower = text.lower()
    add = lambda name, ok, detail="": checks.append((name, bool(ok), detail))
    add("finished", obs["done"] and not obs["error"], obs["error"] or ("" if obs["done"] else "no done event"))
    if expect.get("route"):
        add("route", obs["route"] == expect["route"], f"got {obs['route']}")
    for tool in expect.get("must_call", []):
        add(f"called {tool}", tool in tools, f"tools: {sorted(tools) or 'none'}")
    if expect.get("must_call_any"):
        add("called one of " + "/".join(expect["must_call_any"]), tools & set(expect["must_call_any"]),
            f"tools: {sorted(tools) or 'none'}")
    for tool in expect.get("must_not_call", []):
        add(f"did not call {tool}", tool not in tools)
    approval_tools = {a["tool"] for a in obs["approvals"]}
    if expect.get("approval_expected"):
        add("approval card shown", obs["approvals"], "no approval card")
        for tool in expect.get("approval_tools", []):
            add(f"approval for {tool}", tool in approval_tools, f"cards: {sorted(approval_tools) or 'none'}")
    else:
        add("no approval card", not obs["approvals"], f"cards: {sorted(approval_tools)}")
    for group in expect.get("must_contain", []):
        add("says " + " | ".join(group), any(word.lower() in lower for word in group))
    for word in expect.get("must_not_contain", []):
        add(f"does not say {word!r}", word.lower() not in lower)
    for pattern in expect.get("must_match", []):
        add(f"matches /{pattern}/", re.search(pattern, text))
    for pattern in expect.get("must_not_match", []):
        add(f"no /{pattern}/", not re.search(pattern, text))
    if expect.get("min_sources"):
        add(f">= {expect['min_sources']} sources", obs["sources"] >= expect["min_sources"], f"got {obs['sources']}")
    if expect.get("media_expected"):
        add("image shown", obs["media"] > 0)
    add(f"<= {case['max_steps']} steps", obs["steps"] <= case["max_steps"], f"got {obs['steps']}")
    for name, ok, detail in (extra or []):
        add(name, ok, detail)
    return checks


# ------------------------------------------------------------------ offline world (fakes)
def _file_text(value):
    if isinstance(value, dict) and "file" in value:
        with open(os.path.join(HERE, value["file"]), encoding="utf-8") as handle:
            return handle.read()
    return value


class FakeGitHub:
    """Read-only fake of coding_agent.GitHubClient. Any write attempt is recorded (and must never happen)."""

    def __init__(self, files, issues):
        self.files = {path: _file_text(text) for path, text in files.items()}
        self.issues = issues
        self.writes = []

    def tree(self):
        return sorted(self.files)

    def read(self, path):
        import coding_agent
        if path not in self.files:
            raise coding_agent.CodingError("not found")
        return self.files[path]

    def search(self, text):
        words = [w.lower() for w in str(text).split() if w]
        return [p for p, body in sorted(self.files.items()) if any(w in (p + body).lower() for w in words)][:20]

    def issues_enabled(self):
        return True

    def _issue(self, item, full=False):
        out = {"number": item["number"], "title": item["title"], "state": item.get("state", "open"),
               "url": f"https://github.com/KRFbrothers/Dua-ai/issues/{item['number']}", "labels": [],
               "comments": len(item.get("comments", [])), "updated": "2026-10-19T10:00:00Z"}
        if full:
            out.update(body=item.get("body", ""), truncated=False,
                       comment_list=[{"author": "someone", "created": "2026-10-19T10:00:00Z", "body": c}
                                     for c in item.get("comments", [])])
        return out

    def list_issues(self, state="open"):
        return [self._issue(i) for i in self.issues]

    def read_issue(self, number):
        import coding_agent
        for item in self.issues:
            if item["number"] == int(number):
                return self._issue(item, full=True)
        raise coding_agent.CodingError("not found")

    def search_issues(self, text):
        return [self._issue(i) for i in self.issues if any(w.lower() in i["title"].lower() for w in str(text).split())]

    def _write(self, kind, *args):
        self.writes.append(kind)
        raise RuntimeError("offline eval: GitHub writes are not allowed")

    def create_issue(self, *a, **k):
        return self._write("create_issue")

    def comment_issue(self, *a, **k):
        return self._write("comment_issue")

    def open_pull_request(self, *a, **k):
        return self._write("open_pull_request")

    def request(self, method, path, body=None, query=None):
        if method != "GET":
            return self._write(f"{method} {path}")
        raise RuntimeError("offline eval: raw GitHub requests are not faked")


class FakeNotes:
    def __init__(self, notes):
        self.notes = [dict(n) for n in notes]
        self.writes = []
        self.unavailable = None

    def list(self):
        return list(self.notes)

    def search(self, query):
        words = [w.lower() for w in str(query).split()]
        found = [n for n in self.notes if any(w in n["note"].lower() for w in words)]
        return found or list(self.notes)

    def add(self, note, kind="fact", source="agent"):
        self.writes.append("add")
        return {"error": "offline eval: notes are never written"}

    def delete(self, note_id):
        self.writes.append("delete")
        return {"error": "offline eval: notes are never deleted"}


def _call(name, args):
    return SimpleNamespace(name=name, args=dict(args))


def _step(calls, index):
    return SimpleNamespace(function_calls=[_call(n, a) for n, a in calls], text=None,
                           candidates=[SimpleNamespace(content=("model-turn", index))])


def _final(text):
    return SimpleNamespace(function_calls=[], candidates=[], text=text)


def _grounded(search):
    chunks = [SimpleNamespace(web=SimpleNamespace(uri=url, title=title, domain="")) for url, title in search.get("sources", [])]
    return SimpleNamespace(text=search.get("notes", ""), function_calls=[],
                           candidates=[SimpleNamespace(grounding_metadata=SimpleNamespace(grounding_chunks=chunks))])


class FakeVertex:
    """Plays every model role for one case: router, casual reply, agent loop steps, search/code sub-calls."""

    def __init__(self, case, fixtures, tracker):
        self.offline = case["offline"]
        self.search = self.offline.get("search") or fixtures.get("search") or {}
        self.script = []
        for item in self.offline.get("script", []):
            self.script.extend([item] * int(item.get("repeat", 1)))
        self.tracker = tracker
        self.calls = 0

    def generate_content(self, contents, stream=False, generation_config=None, tools=None):
        self.calls += 1
        config = generation_config or {}
        if "response_schema" in config and "route" in json.dumps(config.get("response_schema")):
            return _final(json.dumps({"route": self.offline["route"]}))
        if tools == ["EVAL_TOOLS"]:
            self.tracker.check_tool_turn(contents)
            if not self.script:
                return _final(self.offline.get("answer", "Done."))
            item = self.script.pop(0)
            if "answer" in item:
                return _final(item["answer"])
            return _step([tuple(c) for c in item["calls"]], self.calls)
        if tools == ["EVAL_SEARCH"]:
            return _grounded(self.search)
        if tools == ["EVAL_CODE"]:
            part = SimpleNamespace(executable_code=SimpleNamespace(code="print(2450*1.18)"),
                                   code_execution_result=SimpleNamespace(output="2891.0"))
            return SimpleNamespace(text="2891.0", candidates=[SimpleNamespace(content=SimpleNamespace(parts=[part]))])
        if stream:
            words = str(self.offline.get("reply", "OK.")).split(" ")
            return iter([SimpleNamespace(text=w + (" " if i < len(words) - 1 else ""), candidates=[]) for i, w in enumerate(words)])
        return _final(self.offline.get("reply", "OK."))


class Tracker:
    """Offline-only invariants: untrusted tool output reached the model fenced."""

    def __init__(self):
        self.unfenced = []

    def check_result(self, name, result):
        import agent_safety
        if name in agent_safety.UNTRUSTED_TOOLS and isinstance(result, dict) and "error" not in result \
                and "untrusted_data" not in result:
            self.unfenced.append(name)

    def check_tool_turn(self, contents):
        last = contents[-1] if contents else None
        if isinstance(last, tuple) and len(last) == 2 and last[0] == "eval-tool":
            for name, result in last[1]:
                self.check_result(name, result)


def fake_adk(spec, tracker):
    """Minimal stand-in for google-adk (same shape as tests/test_agent_adk.py): plan, parallel helpers, merger."""
    import asyncio

    class Agent:
        def __init__(self, **kw):
            self.__dict__.update(kw)

    class Runner:
        def __init__(self, agent, app_name):
            self.agent = agent
            self.session_service = SimpleNamespace(create_session_sync=lambda **k: SimpleNamespace(id="s"))

        def run(self, user_id, session_id, new_message, run_config):
            text = lambda author, t: SimpleNamespace(author=author, content=SimpleNamespace(parts=[SimpleNamespace(text=t)]))
            if self.agent.name == "controller":
                return [text("controller", json.dumps(spec["plan"]))]
            parallel, merger = self.agent.sub_agents
            state = {}

            async def helper(sub):
                kind = sub.name.replace("_agent", "")
                script = spec["helpers"][kind]
                funcs = {f.__name__: f for f in sub.tools}
                for name, args in script[:-1]:
                    tracker.check_result(name, await funcs[name](**args))
                state[sub.output_key] = script[-1]

            async def all_helpers():
                await asyncio.gather(*(helper(s) for s in parallel.sub_agents))
            asyncio.run(all_helpers())
            merger.instruction(SimpleNamespace(state=state))
            return [text("merger", spec["merge"])]

    return SimpleNamespace(LlmAgent=Agent, ParallelAgent=Agent, SequentialAgent=Agent, RunConfig=lambda **k: k,
                           InMemoryRunner=Runner, types=SimpleNamespace(Content=lambda **k: k, Part=lambda **k: k))


@contextlib.contextmanager
def _patched(patches, env):
    saved_env = dict(os.environ)
    saved = [(obj, name, getattr(obj, name)) for obj, name, _ in patches]
    try:
        for obj, name, value in patches:
            setattr(obj, name, value)
        for key, value in env.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = value
        yield
    finally:
        for obj, name, value in reversed(saved):
            setattr(obj, name, value)
        os.environ.clear()
        os.environ.update(saved_env)


def _quiet():
    return contextlib.redirect_stdout(open(os.devnull, "w"))


def run_offline_case(case, fixtures, verbose=False):
    """Runs one case through the real engine with fakes. Returns (events, extra_checks, model_calls)."""
    if ENGINE_DIR not in sys.path:
        sys.path.insert(0, ENGINE_DIR)
    import agent_adk
    import agent_notes
    import agent_safety
    import agent_tools
    import app as engine
    import coding_agent
    offline = case["offline"]
    tracker = Tracker()
    model = FakeVertex(case, fixtures, tracker)
    github = FakeGitHub(fixtures.get("files", {}), offline.get("issues") or fixtures.get("issues", []))
    notes = FakeNotes(offline.get("notes") or fixtures.get("notes", []))
    pages = {url: {"url": url, "title": page.get("title", ""), "text": _file_text(page) if "file" in page else page.get("text", ""),
                   "truncated": False} for url, page in (offline.get("pages") or {}).items()}

    def fetch(url):
        if url not in pages:
            raise agent_tools.FetchError("offline eval: page not in fixtures")
        return dict(pages[url])

    def imagine_for(owner):
        return lambda body: {"images": [{"url": "https://example.com/eval-cat.png", "mimeType": "image/png"}], "model": "eval-fake"}

    def adk_config(model_name, max_tokens, json_out=False):
        return {"model": model_name, "max_output_tokens": max_tokens, "json": json_out}

    patches = [
        (engine, "verify_owner", lambda authorization: "eval-owner"),
        (engine, "get_model", lambda system=engine.SYSTEM_PROMPT: model),
        (engine, "get_agent_model", lambda system: model),
        (engine, "make_part", lambda text=None, data=None, mime_type=None: ("text", text)),
        (engine, "make_content", lambda role, parts: {"role": role, "parts": parts}),
        (engine, "google_search_tool", lambda: "EVAL_SEARCH"),
        (engine, "imagine_for_agent", imagine_for),
        (engine, "adk_config", adk_config),
        (agent_tools, "default_tools", lambda *a, **k: ["EVAL_TOOLS"]),
        (agent_tools, "code_tool", lambda: "EVAL_CODE"),
        (agent_tools, "fetch_page", fetch),
        (coding_agent, "default_tool_response", lambda results: ("eval-tool", list(results))),
        (coding_agent, "GitHubClient", lambda token, repo, opener=None: github),
        (agent_notes, "for_owner", lambda owner, opener=None: notes),
        (agent_notes, "load_prompt", lambda store, query, log=print: ""),
        (agent_notes, "learn", lambda *a, **k: 0),
        (agent_safety, "STORE", agent_safety.PendingStore()),
        (agent_adk, "load_adk", lambda: fake_adk(offline["adk"], tracker)),
    ]
    env = {"GITHUB_TOKEN": "offline-eval-placeholder", "DUA_REPO": "KRFbrothers/Dua-ai", "DUA_APP_TOKEN": None,
           "AGENT_VERIFY": None, "AGENT_TEST_CODE": None, "AGENT_NOTES": "1",
           "AGENT_ENGINE": "adk" if offline.get("engine") == "adk" else None}
    body = {"model": "Dua", "messages": case.get("history", []) + [{"role": "user", "text": case["prompt"]}],
            "stream": True, "mode": "agent", "memory": ""}
    with _patched(patches, env), (contextlib.nullcontext() if verbose else _quiet()):
        response = engine.app.test_client().post("/api/chat", json=body, headers={"Authorization": "Bearer offline-eval"})
        events = parse_sse(response.get_data(as_text=True))
    extra = [("no direct GitHub write", not github.writes, ", ".join(github.writes)),
             ("no direct note write", not notes.writes, ", ".join(notes.writes)),
             ("untrusted output fenced", not tracker.unfenced, ", ".join(tracker.unfenced))]
    return events, extra, model.calls


# ------------------------------------------------------------------ live mode
class LiveClient:
    """Talks to the deployed engine. The auth token stays in this object and is never printed or written."""

    def __init__(self, url, token, app_token=None, opener=None):
        if not re.match(r"^https://[A-Za-z0-9.-]+(:\d+)?/?$", url or "") and not re.match(r"^http://(localhost|127\.0\.0\.1)(:\d+)?/?$", url or ""):
            raise SystemExit("DUA_ENGINE_URL must be the engine's https:// address.")
        self.url = url.rstrip("/")
        self._headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json", "Accept": "text/event-stream"}
        if app_token:
            self._headers["X-Dua-App-Token"] = app_token
        self.open = opener or urllib.request.urlopen

    def chat(self, case, deadline_seconds):
        body = json.dumps({"model": "Dua", "messages": case.get("history", []) + [{"role": "user", "text": case["prompt"]}],
                           "stream": True, "mode": "agent", "memory": ""}).encode("utf-8")
        request = urllib.request.Request(self.url + "/api/chat", data=body, headers=self._headers, method="POST")
        started, chunks = time.monotonic(), []
        try:
            with self.open(request, timeout=60) as response:
                for raw in response:
                    chunks.append(raw.decode("utf-8", "replace"))
                    if time.monotonic() - started > deadline_seconds:
                        chunks.append('\n\ndata: {"type": "error", "message": "eval time limit reached"}\n\n')
                        break
        except urllib.error.HTTPError as error:
            message = "HTTP %s" % error.code
            try:
                message += ": " + str(json.loads(error.read(4096) or b"{}").get("error", ""))[:200]
            except ValueError:
                pass
            return [{"type": "error", "message": message}]
        except (urllib.error.URLError, TimeoutError, OSError) as error:
            return [{"type": "error", "message": f"network: {type(error).__name__}"}]
        return parse_sse("".join(chunks))

    def reject(self, action_id):
        """Always 'reject' (this runner never approves anything). Returns True when the card was discarded."""
        body = json.dumps({"id": action_id, "decision": "reject"}).encode("utf-8")
        headers = dict(self._headers, Accept="application/json")
        request = urllib.request.Request(self.url + "/api/agent/action", data=body, headers=headers, method="POST")
        try:
            with self.open(request, timeout=30) as response:
                return json.loads(response.read(65536) or b"{}").get("status") == "rejected"
        except (urllib.error.URLError, OSError, ValueError):
            return False


def live_token(env=os.environ, ask=getpass.getpass, opener=urllib.request.urlopen):
    """Owner access token from DUA_EVAL_TOKEN, or an email sign-in through dua-api. Never printed."""
    token = (env.get("DUA_EVAL_TOKEN") or "").strip()
    if token:
        return token
    email, api = (env.get("DUA_EVAL_EMAIL") or "").strip(), (env.get("DUA_API_URL") or "").strip().rstrip("/")
    if not email or not api.startswith("https://"):
        raise SystemExit("Set DUA_EVAL_TOKEN, or DUA_EVAL_EMAIL and DUA_API_URL (see evals/README.md).")
    password = ask(f"Dua password for {email} (hidden): ")
    request = urllib.request.Request(api + "/api/account/email-login", method="POST",
                                     data=json.dumps({"email": email, "password": password}).encode("utf-8"),
                                     headers={"Content-Type": "application/json", "x-dua-native": "1"})
    del password
    try:
        with opener(request, timeout=30) as response:
            data = json.loads(response.read(65536) or b"{}")
    except urllib.error.HTTPError as error:
        raise SystemExit(f"Sign-in failed (HTTP {error.code}). Check the email password in the app's Account settings.") from None
    except (urllib.error.URLError, OSError, ValueError):
        raise SystemExit("Sign-in failed: dua-api could not be reached.") from None
    token = ((data or {}).get("session") or {}).get("accessToken")
    if not token:
        raise SystemExit("Sign-in did not return a session. Use the owner account's email password.")
    return token


# ------------------------------------------------------------------ running + reports
def select_cases(data, ids=None, live=False, include_costly=False, max_cases=None):
    chosen, skipped = [], []
    for case in data["cases"]:
        if ids and case["id"] not in ids:
            continue
        if live and case.get("offline_only"):
            skipped.append((case["id"], "offline only"))
        elif live and case.get("costly") and not include_costly:
            skipped.append((case["id"], "costly (use --include-costly)"))
        else:
            chosen.append(case)
    limit = MAX_LIVE_CASES if live else len(chosen)
    if max_cases is not None:
        limit = min(limit, max(0, int(max_cases)))
    for case in chosen[limit:]:
        skipped.append((case["id"], "over the case limit"))
    return chosen[:limit], skipped


def run(data, mode="offline", cases=None, client=None, verbose=False, clock=time.monotonic):
    results = []
    fixtures = data.get("fixtures", {})
    for case in cases if cases is not None else data["cases"]:
        started = clock()
        model_calls = None
        if mode == "offline":
            events, extra, model_calls = run_offline_case(case, fixtures, verbose)
        else:
            events, extra = client.chat(case, case.get("max_seconds", 300) + 30), []
        seconds = clock() - started
        obs = observe(events)
        if mode == "live":
            # Never leave a write waiting: every approval card is rejected right away.
            cleaned = [client.reject(a["id"]) for a in obs["approvals"] if a.get("id")]
            extra = [("approval cards rejected", all(cleaned), f"{sum(cleaned)}/{len(cleaned)}")] if cleaned else []
        if mode == "live" and seconds > case.get("max_seconds", 300):
            extra.append((f"<= {case.get('max_seconds')} s", False, f"took {seconds:.0f}s"))
        checks = score(case, obs, extra)
        results.append({"id": case["id"], "category": case["category"], "passed": all(ok for _, ok, _ in checks),
                        "checks": [{"check": n, "ok": ok, "detail": d} for n, ok, d in checks],
                        "steps": obs["steps"], "tools": obs["tools"], "approvals": [a["tool"] for a in obs["approvals"]],
                        "seconds": round(seconds, 2), "model_calls": model_calls, "tokens": None,
                        "reply": obs["text"][:1500]})
    return results


def summary(results):
    total = len(results)
    passed = sum(r["passed"] for r in results)
    avg = lambda key: round(sum(r[key] for r in results) / total, 2) if total else 0
    calls = [r["model_calls"] for r in results if r["model_calls"] is not None]
    return {"passed": passed, "total": total, "score_percent": round(100 * passed / total, 1) if total else 0.0,
            "avg_steps": avg("steps"), "avg_seconds": avg("seconds"),
            "avg_model_calls": round(sum(calls) / len(calls), 2) if calls else None, "tokens": None}


def table(results, info):
    lines = [f"{'#':>2}  {'case':34} {'result':6} {'steps':>5} {'secs':>6}  failed checks", "-" * 96]
    for index, r in enumerate(results, 1):
        failed = "; ".join(f"{c['check']}" + (f" ({c['detail']})" if c["detail"] else "") for c in r["checks"] if not c["ok"])
        lines.append(f"{index:>2}  {r['id'][:34]:34} {'PASS' if r['passed'] else 'FAIL':6} {r['steps']:>5} {r['seconds']:>6.1f}  {failed[:120]}")
    lines.append("-" * 96)
    lines.append(f"SCORE: {info['passed']}/{info['total']} ({info['score_percent']}%)   avg steps {info['avg_steps']}   "
                 f"avg latency {info['avg_seconds']}s   avg model calls {info['avg_model_calls'] if info['avg_model_calls'] is not None else 'n/a'}   tokens n/a")
    return "\n".join(lines)


def markdown(results, info, meta, skipped):
    out = [f"# Dua Agent eval report ({meta['mode']})", "", f"- When: {meta['when']}", f"- Engine: {meta['engine']}",
           f"- **Score: {info['passed']}/{info['total']} ({info['score_percent']}%)**",
           f"- Avg steps: {info['avg_steps']} | Avg latency: {info['avg_seconds']} s | Avg model calls: "
           f"{info['avg_model_calls'] if info['avg_model_calls'] is not None else 'n/a'} | Tokens: n/a", "",
           "| # | Case | Category | Result | Steps | Seconds | Failed checks |", "| --- | --- | --- | --- | --- | --- | --- |"]
    for index, r in enumerate(results, 1):
        failed = "; ".join(c["check"] for c in r["checks"] if not c["ok"]).replace("|", "/") or "-"
        out.append(f"| {index} | {r['id']} | {r['category']} | {'PASS' if r['passed'] else 'FAIL'} | {r['steps']} | {r['seconds']} | {failed} |")
    if skipped:
        out += ["", "Skipped: " + ", ".join(f"{i} ({why})" for i, why in skipped)]
    return "\n".join(out) + "\n"


def write_reports(results, info, meta, skipped, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    base = os.path.join(out_dir, f"agent-eval-{meta['mode']}-{stamp}")
    with open(base + ".json", "w", encoding="utf-8") as handle:
        json.dump({"meta": meta, "summary": info, "skipped": skipped, "results": results}, handle, ensure_ascii=False, indent=2)
    with open(base + ".md", "w", encoding="utf-8") as handle:
        handle.write(markdown(results, info, meta, skipped))
    return base + ".json", base + ".md"


def main(argv=None):
    parser = argparse.ArgumentParser(description="Dua Agent eval set (offline by default).")
    parser.add_argument("--live", action="store_true", help="run against the deployed engine (costs money)")
    parser.add_argument("--url", default=os.environ.get("DUA_ENGINE_URL", ""), help="engine URL (or DUA_ENGINE_URL)")
    parser.add_argument("--cases", default="", help="comma-separated case ids (default: all)")
    parser.add_argument("--max-cases", type=int, default=None, help=f"run at most N cases (live hard cap {MAX_LIVE_CASES})")
    parser.add_argument("--include-costly", action="store_true", help="live: also run image generation")
    parser.add_argument("--yes", action="store_true", help="live: skip the cost confirmation question")
    parser.add_argument("--out", default=os.environ.get("DUA_EVAL_REPORT_DIR", DEFAULT_REPORT_DIR), help="report folder")
    parser.add_argument("--verbose", action="store_true", help="offline: show engine logs")
    args = parser.parse_args(argv)
    data = load_eval()
    problems = validate(data)
    if problems:
        print("Eval file problems:\n- " + "\n- ".join(problems))
        return 2
    ids = {i.strip() for i in args.cases.split(",") if i.strip()} or None
    cases, skipped = select_cases(data, ids, args.live, args.include_costly, args.max_cases)
    client, engine = None, "offline fakes (real engine code, scripted model)"
    if args.live:
        if not args.url:
            print("Set DUA_ENGINE_URL (or --url) to the dua-ai-engine address.")
            return 2
        print(COST_WARNING.format(n=len(cases)))
        if not args.yes and input(f"Run {len(cases)} live cases now? Type yes: ").strip().lower() != "yes":
            print("Cancelled. Nothing was sent.")
            return 1
        client = LiveClient(args.url, live_token(), os.environ.get("DUA_EVAL_APP_TOKEN"))
        engine = client.url
    if not args.live:
        try:
            import flask  # noqa: F401  (offline mode runs the real engine code)
        except ImportError:
            print("Offline mode needs Flask: pip install -r google-cloud-backend/requirements.txt")
            return 1
    results = run(data, "live" if args.live else "offline", cases, client, args.verbose)
    info = summary(results)
    print(table(results, info))
    for case_id, why in skipped:
        print(f"skipped {case_id}: {why}")
    meta = {"mode": "live" if args.live else "offline", "when": datetime.datetime.now().astimezone().isoformat(timespec="seconds"),
            "engine": engine, "eval_version": data.get("version")}
    json_path, md_path = write_reports(results, info, meta, skipped, args.out)
    print(f"Report: {md_path}\n        {json_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
