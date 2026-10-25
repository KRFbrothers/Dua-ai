"""Optional self-check of Agent answers (AGENT_VERIFY=1), used after both the old loop and the ADK path.

One cheap critic call (VERTEX_MODEL Flash, JSON) compares the draft with the request and the tool evidence:
missing parts, unsupported claims, wrong numbers, unanswered sub-questions, broken citations. Only if it finds
issues: ONE bounded revision call. Each call has a hard deadline; any failure keeps the draft. Never runs for
casual replies or coding-mode answers.
"""
import json
import os
import re

CRITIC_TOKENS = 1024
REVISE_TOKENS = 8192
CRITIC_SECONDS = int(os.environ.get("AGENT_VERIFY_SECONDS", "45"))
REVISE_SECONDS = 90
MAX_EVIDENCE = 12000

CRITIC_PROMPT = (
    "You check an AI agent's draft answer before the user sees it. Compare it with the user's request and the tool "
    "evidence. Flag ONLY real problems: parts of the request not answered, claims or numbers not supported by the "
    "evidence or clearly wrong, citations [n] that do not match a listed source. Ignore style. Reply with JSON only: "
    '{{"ok": true|false, "issues": ["short issue", ...]}} (at most 5 issues).\n\n'
    "User request:\n{request}\n\nSources:\n{sources}\n\nTool evidence:\n{evidence}\n\nDraft answer:\n{draft}"
)
REVISE_PROMPT = (
    "Revise the draft answer to fix ONLY these issues; keep everything correct, keep citations [n] that match the "
    "sources, keep links, simple Hinglish-friendly English. If something cannot be supported by the evidence, say "
    "so honestly instead of guessing. Output only the final answer.\n\nIssues:\n{issues}\n\nUser request:\n{request}"
    "\n\nSources:\n{sources}\n\nTool evidence:\n{evidence}\n\nDraft answer:\n{draft}"
)


def enabled():
    return os.environ.get("AGENT_VERIFY", "0").strip() == "1"


def _sources(sources):
    return "\n".join(f"[{s.get('id')}] {s.get('title')} {s.get('url')}" for s in sources or []) or "(none)"


def parse_verdict(text):
    try:
        data = json.loads(text or "{}")
    except (ValueError, TypeError):
        return None
    if not isinstance(data, dict) or not isinstance(data.get("ok"), bool):
        return None
    issues = [" ".join(str(i).split())[:300] for i in data.get("issues") or [] if str(i).strip()][:5]
    return {"ok": data["ok"] or not issues, "issues": issues}


def check(request, draft, evidence, sources, critic, reviser, chunk_text, progress, log=print):
    """Returns the final text (draft or one revision)."""
    import agent_tools
    if not (draft or "").strip() or not (request or "").strip():
        return draft
    progress("verify", "Checking answer\u2026")
    evidence_text = "\n".join(evidence or [])[:MAX_EVIDENCE] or "(no tool results)"
    fields = {"request": request[:4000], "sources": _sources(sources), "evidence": evidence_text, "draft": draft[:12000]}
    try:
        response = agent_tools.call_with_deadline(lambda: critic.generate_content(
            CRITIC_PROMPT.format(**fields), generation_config={
                "max_output_tokens": CRITIC_TOKENS, "temperature": 0.0, "thinking_level": "low",
                "response_mime_type": "application/json", "timeout_seconds": CRITIC_SECONDS}), CRITIC_SECONDS)
        verdict = parse_verdict(chunk_text(response))
    except Exception as error:
        log(f"[dua-agent] verify: critic skipped ({type(error).__name__})")
        verdict = None
    if verdict is None or verdict["ok"]:
        log(f"[dua-agent] verify: {'ok' if verdict else 'no verdict'}")
        progress("verify", "Answer checked", "done")
        return draft
    log(f"[dua-agent] verify: {len(verdict['issues'])} issue(s): {verdict['issues'][0][:120]}")
    progress("verify", "Improving answer\u2026")
    try:
        response = agent_tools.call_with_deadline(lambda: reviser.generate_content(
            REVISE_PROMPT.format(issues="\n".join("- " + i for i in verdict["issues"]), **fields), generation_config={
                "max_output_tokens": REVISE_TOKENS, "temperature": 0.2, "timeout_seconds": REVISE_SECONDS}),
            REVISE_SECONDS)
        revised = chunk_text(response).strip()
    except Exception as error:
        log(f"[dua-agent] verify: revision skipped ({type(error).__name__})")
        revised = ""
    progress("verify", "Answer checked", "done")
    links = set(re.findall(r"https://github\.com/\S+/pull/\d+", draft))
    if not revised or any(link not in revised for link in links):
        return draft
    return revised
