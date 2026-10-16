"""Parallel multi-agent mode on Google ADK (AGENT_ENGINE=adk; default is the existing agent_tools loop).

Flow per request:
1. Controller (ADK LlmAgent on AGENT_MODEL, e.g. Argon) plans: does the request have 2-3 independent parts
   among research / code / notes? If not, return None so the old loop runs.
2. ADK SequentialAgent [ParallelAgent(specialists on VERTEX_MODEL Flash), merger on AGENT_MODEL].
   Specialists wrap the EXISTING tool functions through one shared agent_tools.AgentRun, so the same per-tool caps,
   #214 deadlines (run_tool_timed) and globally numbered web sources apply. Tools are async (asyncio.to_thread),
   so the specialists really run at the same time.
3. Progress goes out as the app's existing "progress" chips (sub_a/sub_b/sub_c, merge) and "thinking" lines.
Any ADK error -> None (caller falls back to the old loop). A total deadline (AGENT_ADK_SECONDS) stops the run.
ADK is imported lazily, so the normal engine never loads it.
"""
import asyncio
import json
import os
import time
import uuid

SUB_STEPS = int(os.environ.get("AGENT_SUB_STEPS", "5"))
TOTAL_SECONDS = int(os.environ.get("AGENT_ADK_SECONDS", "300"))
PLAN_TOKENS = 2048
SUB_TOKENS = 8192
MERGE_TOKENS = 8192
CHIPS = ("sub_a", "sub_b", "sub_c")

SPECIALISTS = {
    "research": ("Research", ("google_search", "fetch_url", "calculator", "current_time"),
                 "You research on the web. Use google_search/fetch_url, then write compact factual findings citing "
                 "sources like [1] with the ids the tools return."),
    "code": ("Code", ("list_files", "read_file", "search_code", "propose_changes"),
             "You work on the user's GitHub repository. Read before you change. Call propose_changes at most once, "
             "only if the part asks for a change; it opens a pull request (never merges)."),
    "notes": ("Notes", ("search_notes", "save_note"),
              "You manage your private notes from earlier tasks: search_notes to look things up, save_note for a "
              "durable lesson. Never store secrets or emails."),
}

PLAN_PROMPT = (
    "You are the controller of the Dua Agent. Decide whether the user's LAST message has 2-3 INDEPENDENT parts that "
    "fit these helpers: research (web facts), code (read/change the app repository{code_note}), notes (the agent's "
    "private notes{notes_note}). If it is one task or parts depend on each other, do not split. Each kind at most "
    'once. Reply with JSON only: {{"split": true|false, "subtasks": [{{"kind": "research|code|notes", '
    '"task": "self-contained instruction"}}]}}'
)
MERGE_PROMPT = (
    "You are the Dua Agent. Helpers worked on parts of the user's request in parallel. Write ONE final answer in "
    "simple, Hinglish-friendly English. Keep citations like [2] exactly as given and keep pull request links. Say "
    "honestly if a part failed or is missing. Do not invent results.\n\nUser request:\n{request}\n\nHelper results:\n{results}"
)


def enabled():
    return os.environ.get("AGENT_ENGINE", "").strip().lower() == "adk"


def load_adk():
    """Real ADK as a small namespace (replaced by a fake in tests)."""
    from types import SimpleNamespace
    from google.adk.agents import LlmAgent, ParallelAgent, SequentialAgent
    from google.adk.agents.run_config import RunConfig
    from google.adk.runners import InMemoryRunner
    from google.genai import types
    return SimpleNamespace(LlmAgent=LlmAgent, ParallelAgent=ParallelAgent, SequentialAgent=SequentialAgent,
                           RunConfig=RunConfig, InMemoryRunner=InMemoryRunner, types=types)


def parse_plan(text, allowed):
    try:
        data = json.loads(text or "{}")
    except (ValueError, TypeError):
        return []
    if not isinstance(data, dict) or not data.get("split"):
        return []
    subtasks, seen = [], set()
    for item in data.get("subtasks") or []:
        if not isinstance(item, dict):
            continue
        kind, task = item.get("kind"), " ".join(str(item.get("task", "")).split())[:2000]
        if kind in allowed and kind not in seen and task:
            seen.add(kind)
            subtasks.append({"kind": kind, "task": task, "title": SPECIALISTS[kind][0]})
    return subtasks[:3] if len(subtasks) >= 2 else []


def final_text(events, author):
    """Last final-response text written by [author]."""
    text = ""
    for event in events:
        if getattr(event, "author", None) != author:
            continue
        content = getattr(event, "content", None)
        parts = getattr(content, "parts", None) or []
        chunk = "".join(getattr(p, "text", None) or "" for p in parts if not getattr(p, "thought", False))
        if chunk.strip():
            text = chunk
    return text.strip()


def run_agent(adk, agent, message, max_calls):
    runner = adk.InMemoryRunner(agent=agent, app_name="dua-agent")
    session = runner.session_service.create_session_sync(app_name="dua-agent", user_id="owner")
    content = adk.types.Content(role="user", parts=[adk.types.Part(text=message)])
    return list(runner.run(user_id="owner", session_id=session.id, new_message=content,
                           run_config=adk.RunConfig(max_llm_calls=max_calls)))


def make_tools(names, call):
    """Async ADK function tools with explicit signatures; every call goes through call(name, args)."""
    async def google_search(query: str) -> dict:
        """Search the web (Google) for current facts. Returns notes and numbered sources."""
        return await call("google_search", {"query": query})

    async def fetch_url(url: str) -> dict:
        """Open one public web page (http/https) and return its readable text."""
        return await call("fetch_url", {"url": url})

    async def calculator(expression: str) -> dict:
        """Evaluate a math expression, e.g. '(12.5*3)/7'."""
        return await call("calculator", {"expression": expression})

    async def current_time() -> dict:
        """Current date and time in India (IST)."""
        return await call("current_time", {})

    async def list_files(prefix: str = "") -> dict:
        """List file paths in the repository (main branch). Optional prefix filter."""
        return await call("list_files", {"prefix": prefix})

    async def read_file(path: str) -> dict:
        """Read one text file from main."""
        return await call("read_file", {"path": path})

    async def search_code(query: str) -> dict:
        """Search code in the repository; returns matching file paths."""
        return await call("search_code", {"query": query})

    async def propose_changes(title: str, description: str, paths: list[str], contents: list[str]) -> dict:
        """Finish code work: open ONE pull request. paths[i] gets the full new content contents[i]."""
        return await call("propose_changes", {"title": title, "description": description,
                                              "files": [{"path": p, "content": c} for p, c in zip(paths, contents)]})

    async def check_pr_ci() -> dict:
        """Check the GitHub CI status of the pull request you opened in this request."""
        return await call("check_pr_ci", {})

    async def search_notes(query: str) -> dict:
        """Search your private notes from earlier tasks."""
        return await call("search_notes", {"query": query})

    async def save_note(note: str, kind: str = "fact") -> dict:
        """Save one short durable lesson (preference|worked|failed|repo|fact). Never secrets or emails."""
        return await call("save_note", {"note": note, "kind": kind})

    table = {f.__name__: f for f in (google_search, fetch_url, calculator, current_time, list_files, read_file,
                                     search_code, propose_changes, check_pr_ci, search_notes, save_note)}
    import pr_flow
    if "propose_changes" in names and pr_flow.enabled():
        names = tuple(names) + ("check_pr_ci",)
    return [table[n] for n in names]


class Result:
    """Run-like object for the caller (sources footer, notes learning)."""

    def __init__(self, agent):
        self.sources = agent.sources
        self.steps = agent.steps
        self.evidence = agent.evidence


def run(env, request_text, flash_model, agent_model, config_for, adk=None, log=print):
    """Returns (text, Result) or None (use the old loop)."""
    import agent_tools
    import coding_agent
    try:
        adk = adk or load_adk()
    except ImportError:
        log("[dua-agent] ADK not installed; using the normal loop")
        return None
    progress, thinking = env["progress"], env["thinking"]
    shared = agent_tools.AgentRun(env)
    if env.get("github") is not None:
        shared.coding = coding_agent.CodingSession(env["github"], lambda d: True, time.monotonic)
    allowed = ["research"] + (["code"] if env.get("github") is not None else []) + \
              (["notes"] if env.get("notes") is not None else [])
    started = time.monotonic()
    pr_text = []

    def specialist_call(index, title, letter):
        async def call(name, args):
            label = agent_tools.PROGRESS.get(name, ("", name))[1].rstrip("\u2026")
            progress(CHIPS[index], f"{title}: {label}\u2026")
            shown = {k: (v if not isinstance(v, str) else v[:100]) for k, v in args.items() if k not in ("files",)}
            line = f"- {name} {json.dumps(shown, ensure_ascii=False)[:160]}\n"
            shared.steps.append(f"[{letter}] {line}")
            thinking(f"[{letter}] {line}")
            if name == "propose_changes":
                if pr_text:
                    return {"error": "A pull request was already opened for this request."}
                flow_env = dict(env, request_text=request_text,
                                progress=lambda step, label, state="active": progress(CHIPS[index], f"{title}: {label}"))
                done, result, text = await asyncio.to_thread(shared.pr_flow_for(flow_env).propose, args)
                if not done:
                    return result
                pr_text.append(text)
                return {"result": text[:2000], "note": "Done. Do not call propose_changes again."}
            return await asyncio.to_thread(shared.run_tool_timed, name, args)
        return call

    def pipeline():
        cfg_plan = config_for(agent_model, PLAN_TOKENS, json_out=True)
        planner = adk.LlmAgent(name="controller", model=agent_model, generate_content_config=cfg_plan,
                               instruction=lambda ctx: PLAN_PROMPT.format(
                                   code_note="" if "code" in allowed else "; NOT available now",
                                   notes_note="" if "notes" in allowed else "; NOT available now"))
        subtasks = parse_plan(final_text(run_agent(adk, planner, request_text, 2), "controller"), allowed)
        if not subtasks:
            return None
        log(f"[dua-agent] adk plan: {', '.join(s['kind'] for s in subtasks)}")
        helpers = []
        for i, sub in enumerate(subtasks):
            title, names, role = SPECIALISTS[sub["kind"]]
            progress(CHIPS[i], f"{title}: starting\u2026")
            text = f"{role}\nYour part of the user's request: {sub['task']}\nFinish with a short summary of results."
            helpers.append(adk.LlmAgent(
                name=f"{sub['kind']}_agent", model=flash_model, include_contents="none",
                generate_content_config=config_for(flash_model, SUB_TOKENS), output_key=f"{sub['kind']}_result",
                instruction=(lambda t: (lambda ctx: t))(text),
                tools=make_tools(names, specialist_call(i, title, "ABC"[i]))))

        def merge_instruction(ctx):
            for i, sub in enumerate(subtasks):
                progress(CHIPS[i], f"{sub['title']}: done", "done")
            progress("merge", "Combining results\u2026")
            results = "\n\n".join(f"## {s['title']}\nTask: {s['task']}\nResult:\n"
                                  f"{str(ctx.state.get(s['kind'] + '_result') or '(no result)')[:6000]}"
                                  for s in subtasks)
            return MERGE_PROMPT.format(request=request_text[:4000], results=results)
        merger = adk.LlmAgent(name="merger", model=agent_model, include_contents="none",
                              generate_content_config=config_for(agent_model, MERGE_TOKENS),
                              instruction=merge_instruction)
        root = adk.SequentialAgent(name="dua_root", sub_agents=[
            adk.ParallelAgent(name="helpers", sub_agents=helpers), merger])
        events = run_agent(adk, root, request_text, SUB_STEPS * len(helpers) + 2)
        return final_text(events, "merger")

    try:
        import pr_flow
        answer = agent_tools.call_with_deadline(pipeline, TOTAL_SECONDS + (pr_flow.CI_SECONDS if pr_flow.enabled() else 0))
    except agent_tools.DeadlineExceeded:
        log(f"[dua-agent] adk deadline {TOTAL_SECONDS}s reached")
        answer = "Agent stopped: the parallel helpers took longer than the time limit. Please try a smaller request."
        if pr_text:
            answer += "\n\n" + pr_text[0]
        elif shared.prflow is not None and shared.prflow.pr:
            answer += f"\n\nA pull request was opened: {shared.prflow.pr['url']}"
        return answer, Result(shared)
    except Exception as error:  # any ADK problem: the old loop answers instead
        if shared.steps or pr_text:  # tools already ran: do not repeat side effects in the old loop
            log(f"[dua-agent] adk failed mid-run ({type(error).__name__})")
            answer = "Agent stopped: the parallel helpers hit an error. Please try again."
            return answer + ("\n\n" + pr_text[0] if pr_text else ""), Result(shared)
        log(f"[dua-agent] adk failed ({type(error).__name__}); using the normal loop")
        return None
    if answer is None:
        return None
    log(f"[dua-agent] adk total {time.monotonic() - started:.1f}s")
    if pr_text and "/pull/" not in answer:
        answer += "\n\n" + pr_text[0]
    return (answer or "I could not combine the results."), Result(shared)
