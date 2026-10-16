"""Parallel Dua Agent (AGENT_PARALLEL=1): plan -> up to 3 sub-agents in parallel -> merge.

In-house on purpose: concurrent.futures threads around the existing agent_tools.run loop, so every tool keeps its
caps and #214 deadlines, and the app's existing progress/thinking/sources events are reused (no app change).
- Plan and merge use the Agent model (AGENT_MODEL, e.g. Argon); sub-agents use the cheap chat model (VERTEX_MODEL).
- Cost caps: at most MAX_SUBTASKS sub-agents, each SUB_STEPS model steps; one overall deadline; only one sub-agent
  may use GitHub (propose_changes). If planning fails or says "no split", the normal single loop runs instead.
"""
import concurrent.futures
import json
import os
import re
import time

MAX_SUBTASKS = 3
SUB_STEPS = int(os.environ.get("AGENT_SUB_STEPS", "5"))
PARALLEL_SECONDS = int(os.environ.get("AGENT_PARALLEL_SECONDS", "300"))
PLAN_TOKENS = 2048
MERGE_TOKENS = int(os.environ.get("AGENT_MERGE_TOKENS", "8192"))
KINDS = ("research", "code", "notes", "general")
CHIPS = ("sub_a", "sub_b", "sub_c")

PLAN_PROMPT = (
    "You are the planner for the Dua Agent. Decide whether the user's request has 2-3 INDEPENDENT parts that can "
    "run at the same time (for example: research a topic, change the app's code, check saved notes). If it is one "
    "task, or the parts depend on each other, do not split. Reply with JSON only:\n"
    '{{"split": true|false, "subtasks": [{{"title": "2-4 words", "kind": "research|code|notes|general", '
    '"task": "self-contained instruction"}}]}}\nAt most 3 subtasks; at most one with kind "code".\n\n'
    "User request:\n{request}"
)
MERGE_PROMPT = (
    "You are the Dua Agent. Your helpers worked on parts of the user's request in parallel. Write ONE final answer "
    "in simple, Hinglish-friendly English. Keep citations like [3] exactly as given (they are already global). Keep "
    "any pull request links. Say honestly if a part failed. Do not invent results.\n\n"
    "User request:\n{request}\n\nHelper results:\n{results}"
)


def enabled():
    return os.environ.get("AGENT_PARALLEL", "0").strip() == "1"


def parse_plan(text):
    try:
        data = json.loads(text or "{}")
    except (ValueError, TypeError):
        return []
    if not isinstance(data, dict) or not data.get("split"):
        return []
    subtasks, code = [], 0
    for item in data.get("subtasks") or []:
        if not isinstance(item, dict):
            continue
        task = " ".join(str(item.get("task", "")).split())[:2000]
        if not task:
            continue
        kind = item.get("kind") if item.get("kind") in KINDS else "general"
        if kind == "code":
            if code:
                kind = "general"
            code += kind == "code"
        title = " ".join(str(item.get("title") or kind).split())[:30]
        subtasks.append({"title": title, "kind": kind, "task": task})
        if len(subtasks) == MAX_SUBTASKS:
            break
    return subtasks if len(subtasks) >= 2 else []


def plan(model, request_text, chunk_text):
    response = model.generate_content(PLAN_PROMPT.format(request=request_text[:6000]), generation_config={
        "max_output_tokens": PLAN_TOKENS, "temperature": 0.0, "response_mime_type": "application/json",
        "timeout_seconds": 90})
    return parse_plan(chunk_text(response))


class Merged:
    """Run-like result for the caller: global sources and step lines for notes learning."""

    def __init__(self):
        self.sources, self.steps = [], []

    def add_sources(self, local):
        """Adds a sub-agent's sources; returns {local id: global id}."""
        mapping = {}
        for item in local:
            existing = next((s for s in self.sources if s["url"] == item["url"]), None)
            if existing is None:
                if len(self.sources) >= 12:
                    continue
                existing = dict(item, id=len(self.sources) + 1)
                self.sources.append(existing)
            mapping[item["id"]] = existing["id"]
        return mapping


def renumber(text, mapping):
    return re.sub(r"\[(\d+)\]", lambda m: f"[{mapping[int(m.group(1))]}]" if int(m.group(1)) in mapping else "",
                  text or "")


def run(base_env, make_sub_model, merge_model, history_for, tools_for, tool_response, log=print):
    """Returns (text, Merged) or None when the request should use the normal single loop."""
    import agent_tools
    progress, chunk_text = base_env["progress"], base_env["chunk_text"]
    try:
        subtasks = plan(merge_model, base_env["request_text"], chunk_text)
    except Exception as error:
        log(f"[dua-agent] parallel plan failed ({type(error).__name__}); using single loop")
        return None
    if not subtasks:
        return None
    started = time.monotonic()
    log(f"[dua-agent] parallel: {len(subtasks)} subtasks " + ", ".join(t["kind"] for t in subtasks))
    merged = Merged()

    def worker(index, sub):
        chip, letter = CHIPS[index], "ABC"[index]
        env = dict(base_env)
        env["model"] = make_sub_model(sub)
        env["github"] = base_env.get("github") if sub["kind"] == "code" else None
        env["request_text"] = sub["task"]
        # One chip per sub-agent; its label follows the sub-agent's current tool.
        env["progress"] = lambda step, label, state="active": (
            progress(chip, f"{sub['title']}: {label.rstrip(chr(0x2026))}\u2026") if state == "active" else None)
        env["thinking"] = lambda delta: base_env["thinking"](f"[{letter}] {delta}")
        text, agent = agent_tools.run(env, history_for(sub), tools_for(env["github"] is not None), tool_response,
                                      max_steps=SUB_STEPS)
        return text, agent

    results = [None] * len(subtasks)
    pool = concurrent.futures.ThreadPoolExecutor(max_workers=len(subtasks), thread_name_prefix="dua-sub")
    futures = {}
    for i, sub in enumerate(subtasks):
        progress(CHIPS[i], f"{sub['title']}: starting\u2026")
        futures[pool.submit(worker, i, sub)] = i
    try:
        for future in concurrent.futures.as_completed(futures, timeout=PARALLEL_SECONDS):
            i = futures[future]
            try:
                results[i] = future.result()
            except Exception as error:
                log(f"[dua-agent] subtask {i + 1} failed ({type(error).__name__})")
                results[i] = (f"This part failed ({type(error).__name__}).", None)
            progress(CHIPS[i], f"{subtasks[i]['title']}: done", "done")
    except concurrent.futures.TimeoutError:
        log(f"[dua-agent] parallel deadline {PARALLEL_SECONDS}s reached")
    pool.shutdown(wait=False, cancel_futures=True)

    blocks = []
    for i, sub in enumerate(subtasks):
        text, agent = results[i] or (f"This part did not finish within {PARALLEL_SECONDS}s.", None)
        mapping = merged.add_sources(agent.sources) if agent is not None else {}
        if agent is not None:
            merged.steps.extend(f"[{'ABC'[i]}] {line}" for line in agent.steps)
        blocks.append(f"## {sub['title']} ({sub['kind']})\nTask: {sub['task']}\nResult:\n{renumber(text, mapping)[:6000]}")
    progress("merge", "Combining results\u2026")
    joined = "\n\n".join(blocks)
    try:
        response = merge_model.generate_content(MERGE_PROMPT.format(request=base_env["request_text"][:4000], results=joined),
                                                generation_config={"max_output_tokens": MERGE_TOKENS, "temperature": 0.3,
                                                                   "timeout_seconds": 150})
        final = chunk_text(response).strip()
    except Exception as error:
        log(f"[dua-agent] merge failed ({type(error).__name__}); returning helper results")
        final = ""
    log(f"[dua-agent] parallel total {time.monotonic() - started:.1f}s")
    return (final or joined), merged
