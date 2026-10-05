"""Store concrete patches in session state; only reviewed, confirmed content can publish."""
import asyncio
import difflib
import hashlib
import json
import time
from google.adk.tools import ToolContext
from google.adk.tools.agent_tool import AgentTool
from . import github_tools as github
from .budget import shared_budget
from .reviewer import reviewer, ReviewReport

KEY = "dua:proposals"
MAX_PROPOSALS = 4
TTL = 3600
REVIEW_TOOL = AgentTool(agent=reviewer, include_plugins=False)


def digest(proposal):
    patch = {k: proposal[k] for k in ("base_sha", "title", "description", "changes", "originals", "diff")}
    return hashlib.sha256(json.dumps(patch, sort_keys=True, ensure_ascii=False,
                                    separators=(",", ":")).encode()).hexdigest()


def get_proposal(context, proposal_id):
    proposal = context.state.get(KEY, {}).get(proposal_id)
    if not proposal or digest(proposal) != proposal_id:
        raise ValueError("Proposal missing or changed. Prepare and review it again.")
    if time.time() - proposal["created"] > TTL:
        raise ValueError("Proposal expired. Re-read current main and review a fresh proposal.")
    return proposal


def save(context, proposal_id, proposal):
    proposals = dict(context.state.get(KEY, {}))
    proposals[proposal_id] = proposal
    context.state[KEY] = proposals


def prepare_proposal(base_sha: str, title: str, description: str,
                     changes: list[dict[str, str]], tool_context: ToolContext) -> dict:
    """Prepare a pinned patch and readable diff; no GitHub mutation. Full text per file."""
    github.validate_sha(base_sha)
    github.validate_changes(changes)
    if not title.strip() or len(title) > 160 or len(description) > 10_000:
        raise ValueError("Use a short title and bounded description.")
    if github.repository_info()["main_sha"] != base_sha:
        raise ValueError("Main changed. Re-read current source before preparing a patch.")
    tree = github.api("GET", f"/git/trees/{base_sha}?recursive=1")
    if tree.get("truncated"):
        raise ValueError("Cannot inspect a truncated tree.")
    entries = {e["path"]: e for e in tree["tree"]}
    originals, differences = {}, []
    changes = sorted([dict(change) for change in changes], key=lambda e: e["path"])
    for change in changes:
        path = change["path"]
        entry = entries.get(path)
        if entry and (entry["type"] != "blob" or entry["mode"] not in ("100644", "100755")):
            raise ValueError("Only regular source files can be replaced.")
        old = github.read_file(path, base_sha)["content"] if entry else ""
        new = change["content"]
        if old == new:
            raise ValueError(f"No change for {path}; remove unchanged files from the proposal.")
        originals[path] = old
        diff = "\n".join(difflib.unified_diff(
            old.splitlines(), new.splitlines(), lineterm="",
            fromfile="a/" + path if entry else "/dev/null", tofile="b/" + path,
        ))
        # Make changes to a final line without a newline legible in the preview.
        differences.append(diff + (f"\n[{path}: final newline before={old.endswith(chr(10))}, after={new.endswith(chr(10))}]\n"
                           if old.endswith("\n") != new.endswith("\n") else ""))
    diff = "\n".join(differences)
    if len(diff) > 80_000:
        raise ValueError("Diff too large for phone review; split the task.")
    proposal = {"base_sha": base_sha, "title": title, "description": description,
                "changes": changes, "originals": originals, "diff": diff,
                "created": time.time(), "status": "prepared", "review": None}
    if len(json.dumps(proposal, ensure_ascii=False)) > 160_000:
        raise ValueError("Patch/context too large for independent review; split the task.")
    proposal_id = digest(proposal)
    proposals = tool_context.state.get(KEY, {})
    if proposal_id in proposals:
        existing = get_proposal(tool_context, proposal_id)
        return {"proposal_id": proposal_id, "diff": existing["diff"], "status": existing["status"]}
    if len(proposals) >= MAX_PROPOSALS:
        raise ValueError("Session proposal limit reached. Start a new session for a new task.")
    save(tool_context, proposal_id, proposal)
    return {"proposal_id": proposal_id, "base_sha": base_sha, "title": title,
            "diff": diff, "status": "prepared; independent review required"}


async def review_proposal(proposal_id: str, tool_context: ToolContext) -> dict:
    """Run an independent reviewer on stored content. Coordinator cannot supply its verdict."""
    proposal = dict(get_proposal(tool_context, proposal_id))
    if proposal["status"] in ("publishing", "published", "write_unknown"):
        return {"status": proposal["status"], "result": proposal.get("result")}
    if proposal.get("review"):
        return {"proposal_id": proposal_id, "review": proposal["review"], "diff": proposal["diff"]}
    if proposal.get("review_attempts", 0) >= 2:
        return {"status": "review_failed", "error": "Review attempt limit reached; inspect the failure before retrying."}
    proposal["review_attempts"] = proposal.get("review_attempts", 0) + 1
    save(tool_context, proposal_id, proposal)
    request = json.dumps({k: proposal[k] for k in
                          ("base_sha", "title", "description", "originals", "changes", "diff")},
                         ensure_ascii=False)
    try:
        with shared_budget(tool_context):
            result = await asyncio.wait_for(REVIEW_TOOL.run_async(
                args={"request": request}, tool_context=tool_context), timeout=120)
        report = ReviewReport.model_validate(result).model_dump()
        if any(f["severity"] == "blocking" for f in report["findings"]):
            report["verdict"] = "changes_requested"
    except Exception:
        # No write is permitted on malformed, timed out or unauthenticated review.
        proposal["status"] = "review_failed"
        save(tool_context, proposal_id, proposal)
        return {"status": "review_failed", "error": "Independent review failed. Check authentication/status; no write allowed."}
    proposal["review"] = report
    proposal["status"] = "reviewed" if report["verdict"] == "ready" else "changes_requested"
    save(tool_context, proposal_id, proposal)
    return {"proposal_id": proposal_id, "review": report, "diff": proposal["diff"],
            "status": proposal["status"], "note": "Review is not user approval or passing tests."}


def publish_proposal(proposal_id: str, reviewed_diff: str, tool_context: ToolContext) -> dict:
    """CONFIRMATION REQUIRED: publish stored reviewed patch. reviewed_diff must match exactly.

    The approval panel shows the proposal hash and full focused diff. It cannot
    change the saved content. No arbitrary files, unreviewed patches or replay writes.
    """
    proposal = dict(get_proposal(tool_context, proposal_id))
    if reviewed_diff != proposal["diff"]:
        raise ValueError("Approval diff does not match stored patch. Review exact diff first.")
    if proposal["status"] in ("published", "write_unknown", "publishing"):
        return {"status": proposal["status"], "result": proposal.get("result"),
                "note": "No repeated write. Inspect the existing branch/PR."}
    if proposal["status"] != "reviewed" or not proposal["review"] or proposal["review"]["verdict"] != "ready":
        raise ValueError("A successful independent review is required before publishing.")
    if not tool_context.tool_confirmation or not tool_context.tool_confirmation.confirmed:
        raise ValueError("Framework confirmation is required; a text approval is insufficient.")
    # Replays of the same immutable proposal use one deterministic branch.
    branch = "dua-agent/" + proposal_id[:24]
    proposal["status"] = "publishing"
    save(tool_context, proposal_id, proposal)
    try:
        result = github.create_draft_pr(proposal["base_sha"], proposal["title"],
            proposal["description"] + "\n\nIndependent review: " + proposal["review"]["summary"],
            proposal["changes"], branch=branch)
    except Exception:
        proposal["status"] = "write_unknown"
        proposal["result"] = {"branch": branch,
            "compare_url": f"https://github.com/{github.REPO}/compare/main...{branch}",
            "error": "Write failed or outcome unknown. Inspect GitHub before any new attempt; do not repeat blindly."}
        save(tool_context, proposal_id, proposal)
        return {"status": proposal["status"], "result": proposal["result"]}
    proposal["status"] = "published" if "pr_number" in result else "write_unknown"
    proposal["result"] = result
    save(tool_context, proposal_id, proposal)
    return {"status": proposal["status"], "result": result}
