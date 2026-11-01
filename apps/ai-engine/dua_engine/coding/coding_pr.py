"""Owner-reviewed fixes on existing same-repository dua-agent/ PR branches.

Read targets from GitHub, not model arguments. Create one commit with the reviewed
head as parent, then fast-forward that branch without ever rebasing or forcing.
"""
import re

import dua_engine.coding.coding_agent as coding

SHA = re.compile(r"[0-9a-f]{40}")


def clean_target(value, base_sha=None):
    if not isinstance(value, dict) or set(value) != {"number", "branch", "head_sha"}:
        raise coding.CodingError("Invalid PR repair target.")
    number, branch, sha = value["number"], value["branch"], value["head_sha"]
    if (isinstance(number, bool) or not isinstance(number, int) or not 1 <= number < 10**9 or
            not coding.valid_agent_branch(branch) or not isinstance(sha, str) or not SHA.fullmatch(sha) or
            (base_sha is not None and base_sha != sha)):
        raise coding.CodingError("Invalid PR repair target or reviewed head.")
    return {"number": number, "branch": branch, "head_sha": sha}


def current_target(github, number, expected=None):
    if isinstance(number, bool) or not isinstance(number, int) or not 1 <= number < 10**9:
        raise coding.CodingError("Provide a positive PR number from the configured repository.")
    data = github.request("GET", f"/repos/{github.repo}/pulls/{number}")
    if (not isinstance(data, dict) or data.get("number") != number or data.get("state") != "open" or
            data.get("merged") is not False):
        raise coding.CodingError("Repair requires an open, unmerged pull request.")
    head, base = data.get("head"), data.get("base")
    if not isinstance(head, dict) or not isinstance(base, dict):
        raise coding.CodingError("GitHub returned an invalid PR target.")
    for side in (head, base):
        repository = side.get("repo")
        if not isinstance(repository, dict) or str(repository.get("full_name", "")).lower() != github.repo.lower():
            raise coding.CodingError("Repair only supports PRs whose head and base belong to the configured repository.")
    if base.get("ref") != coding.BASE_BRANCH:
        raise coding.CodingError("Repair only supports agent pull requests targeting main.")
    target = clean_target({"number": number, "branch": head.get("ref"), "head_sha": head.get("sha")})
    if expected is not None and target != clean_target(expected):
        raise coding.CodingError("PR head or branch changed. Open a fresh PR workspace, review the new diff and request a new approval.")
    ref = github.request("GET", f"/repos/{github.repo}/git/ref/heads/{target['branch']}")
    if (not isinstance(ref, dict) or ref.get("ref") != "refs/heads/" + target["branch"] or
            not isinstance(ref.get("object"), dict) or ref["object"].get("sha") != target["head_sha"]):
        raise coding.CodingError("PR and branch head disagree. Refresh before preparing or approving a fix.")
    return target


def validate_proposal(args):
    if not isinstance(args, dict) or not coding.valid_repository(args.get("repository")):
        raise coding.CodingError("Invalid repair repository.")
    target = clean_target(args.get("pr_target"), args.get("base_sha"))
    if args.get("base_sha") != target["head_sha"]:
        raise coding.CodingError("Repair must include the reviewed head commit.")
    files = coding.validate_changes(args.get("files"))
    title = args.get("title")
    if not files or not isinstance(title, str) or not title.strip() or len(title) > 120:
        raise coding.CodingError("Repair needs staged changes and a bounded commit title.")
    return target, files, " ".join(title.split())


def _sha(response):
    value = response.get("sha") if isinstance(response, dict) else None
    if not isinstance(value, str) or not SHA.fullmatch(value):
        raise coding.CodingError("GitHub did not return a valid repair object.")
    return value


def apply_fix(github, args):
    target, files, title = validate_proposal(args)
    if github.repo != args["repository"]:
        raise coding.CodingError("Configured repository changed. Request a new approval.")
    current_target(github, target["number"], expected=target)
    from dua_engine.coding.coding_workspace import Workspace
    workspace = Workspace(github)
    workspace.start(target["head_sha"])
    tree_sha = workspace.tree_sha
    if not isinstance(tree_sha, str) or not SHA.fullmatch(tree_sha):
        raise coding.CodingError("The reviewed source tree is unavailable.")
    entries = []
    for item in files:
        path = item["path"]
        workspace.check_path(path, write=True)
        entry = workspace.entries.get(path)
        entries.append({"path": path, "mode": entry["mode"] if entry else "100644",
                        "type": "blob", "content": item["content"]})
    # Inline content creates the blobs in one bounded tree request. Preserve
    # existing executable modes; do not use the legacy push_fix's live-head path.
    tree = _sha(github.request("POST", f"/repos/{github.repo}/git/trees", {"base_tree": tree_sha, "tree": entries}))
    commit = _sha(github.request("POST", f"/repos/{github.repo}/git/commits", {
        "message": title, "tree": tree, "parents": [target["head_sha"]]}))
    # Objects may now exist without a ref. Recheck immediately before publication.
    current_target(github, target["number"], expected=target)
    try:
        result = github.request("PATCH", f"/repos/{github.repo}/git/refs/heads/{target['branch']}",
                                {"sha": commit, "force": False})
        if (not isinstance(result, dict) or result.get("ref") != "refs/heads/" + target["branch"] or
                not isinstance(result.get("object"), dict) or result["object"].get("sha") != commit):
            raise coding.CodingError("Unconfirmed branch update.")
    except Exception:
        raise coding.CodingError("Fix publication could not be confirmed. Do not repeat it blindly; inspect PR "
                                 f"#{target['number']} and commit {commit} before requesting another approval.") from None
    return (f"Fix committed to PR #{target['number']}: https://github.com/{github.repo}/pull/{target['number']}\n\n"
            f"Commit: {commit}\nCI has not been verified for this commit. Review its checks before merging.")
