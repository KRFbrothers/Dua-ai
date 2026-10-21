# Reviewed fixes to an existing agent PR

This sixth phase extends the pinned workspace, [CI evidence](CODING_CI.md),
[Android activity](CODING_ACTIVITY.md) and opt-in [draft checkpoints](CODING_CHECKPOINTS.md).
It uses the existing GitHub REST client/allowlist, Google ADK tools and owner
approval endpoint. No additional dependency or cloud setting is introduced.

## Flow

In a fresh Agent task, ask to fix a specific PR. Each request permits at most two
PR-workspace opens and one repair approval. The normal loop and ADK Code helper expose:

1. `inspect_pr_ci(number)` reads a CI snapshot without restoring or changing files.
2. `open_pr_workspace(number)` resolves the current PR/branch head and starts a
   text workspace at that exact SHA. Read files and scoped `AGENTS.md` from this
   workspace before using `edit_file`. Existing draft/checkpoint/command state
   cannot be discarded by opening a different PR.
3. Inspect `workspace_diff`. Optional configured `run_command` and explicit text
   import use the same pinned snapshot and limits as before. This phase does not
   enable the runner or independently run commands/tests.
4. `propose_pr_fix(title)` materializes **all staged files** and requests a separate
   owner approval. The server supplies the PR number, repository, branch and head;
   the model cannot choose replacement targets or approval file contents. A main
   workspace cannot propose a repair, and a PR workspace cannot use the normal
   new-PR `propose_changes` route.
5. The approval card identifies the PR, branch and reviewed head and contains a
   bounded, redacted diff of the stored files. If the workspace changed before a
   preview could be made, no repair approval is queued. The new Android UI disables
   repair approval if its preview is missing. The decision request still contains
   only the approval ID and decision; the server owns the action arguments.
6. Approving consumes that owner-scoped action once. The server validates the
   current PR/branch again, creates a tree preserving existing executable modes,
   creates one commit whose sole parent is the reviewed head, checks the target
   again, and updates only that agent branch using `force: false`.

PR targets must be open and unmerged, with both head and base in `DUA_REPO`, a
`dua-agent/` branch matching the existing flat branch naming rule, and base branch
`main`. Forks, arbitrary owner branches (including `codex/`), closed/merged PRs,
protected file paths, folders, links and submodules are refused. No merge, rebase,
main write, workflow edit/rerun or branch deletion is added. The GitHub allowlist
is not widened by this phase.

## Concurrency and uncertain outcomes

Target identity is checked when opening, when preparing an approval, before Git
object creation and immediately before branch publication. A changed head/branch,
closed PR, changed repository or mismatched PR/ref requires a fresh workspace and
new review. The commit is always constructed from the approved head and stored
file contents, never a newly observed live head. Unrelated newer branch commits
cannot be overwritten by the non-forced fast-forward update.

GitHub's REST ref update is not an atomic expected-SHA/PR-state compare-and-swap.
The final check and PATCH are separate calls: a PR can close/retarget in that
window, or a concurrent rewind to an ancestor can still permit a fast-forward.
This implementation does not claim a transaction spanning PR state and the ref.
Concurrent normal forward updates that are not ancestors of the approved commit
are rejected by GitHub; there is no fallback, rebase, force push or automatic retry.

Tree/commit objects can exist without a branch update if later validation fails.
A timeout or ambiguous response during PATCH is reported as **unconfirmed**, with
the candidate commit SHA. Inspect the PR/commit before requesting another approval;
a failed/expired single-use action is not blindly replayed. A confirmed update
reports the PR and commit, with CI explicitly unverified. GitHub may automatically
start its configured workflows after a branch update; this tool does not dispatch
or rerun them and does not wait for them to pass.

## Saved drafts and Android

Workspace identity/fingerprints include the repair target, and private checkpoint
payload version 2 stores it alongside staged text. Version 1 checkpoints remain
readable as main-based drafts. Version 2 validates the target against the saved
head; it cannot silently become a new-PR workspace on restore. No SQL schema or
migration is added. Older engines reject version 2, so rolling back the engine
leaves those drafts unavailable until a compatible engine is restored. Deploy
matching engine/app code deliberately; no deployment was performed here.

Opening a PR workspace has a 75-second internal budget (including waiting for
its session lock), cancellation checks before installing the new workspace and a
120-second shared tool deadline. The existing per-read timeout still applies.
A failed/late open keeps the previous workspace.

A restored repair draft retains its original head. Successful publication does
not rewrite an already saved checkpoint to the new commit. It is not silently rebased if
the PR moved or closed; proposal/publication checks refuse a stale target. Source
reads/guidance are refreshed normally, and no command is replayed. The saved-draft
list still returns only existing minimal checkpoint metadata; its target is shown
in the coding activity card after the Agent actually restores it.

Android shows the PR number and branch with the draft's pinned commit. Approval
status/results use the existing approval flow. Draft/approval activity remains in
memory outside the explicit chat/cloud/backup codecs. Composer styling and the
attachment sheet are unchanged. The legacy `PrFlow.push_fix` path is unchanged;
this new reviewed repair path does not call it.

## Verification status

Only Kotlin compilation, Python syntax parsing and diff-format checks were run.
The existing tool-declaration assertion was updated. No local tests, live GitHub
repair/tool calls, model calls, SQL writes/migrations, deployments, runner
activation or APK releases were performed at the owner's request.

Keep this phase a draft until later checks cover owner/approval isolation,
server-selected target/files, main-vs-PR routing, modes and protected paths,
stale head/ref/closed/fork/base changes, concurrent fast-forwards, ambiguous PATCH
outcomes, one-use approvals, version 1/2 restore, missing previews and device
rendering. The parent implementation phases must land before activation.
