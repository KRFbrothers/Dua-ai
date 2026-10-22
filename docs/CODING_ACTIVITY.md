# Android coding activity and saved drafts

This UI builds on the pinned workspace, separate terminal runner, and opt-in
checkpoint storage in [CODING_RUNNER.md](CODING_RUNNER.md) and
[CODING_CHECKPOINTS.md](CODING_CHECKPOINTS.md). It does not enable either service.

The Agent reply can show a coding workspace card with the pinned commit, changed
paths, last save status, and up to four command cards. Command requests appear
while preparing; validated final runner results supply the exit status and bounded
output. The engine also forwards bounded cumulative output snapshots from the
existing runner poll while a job is queued or running; see
[live command output](CODING_LIVE_OUTPUT.md). An
unconfigured runner shows an error; it never falls back to running on the engine.
When a reply stops without a final command result, the UI marks the outcome as
unavailable rather than successful. The existing Stop control cancels the reply
and uses the runner cancellation path; there is no separate per-command control.

`workspace_diff` supplies a collapsible, bounded draft preview. Edits, imports and
restores invalidate that preview. PR approvals carry a separate diff generated
from the exact validated files stored with that approval, not a later mutable
draft. Model-supplied preview fields are discarded. Existing secret filtering is
applied before previews/output reach the owner. A shortened preview is labelled;
it is not a substitute for reviewing full changed files. Both panels render
selectable plain text, never executable HTML or Markdown links.

The Saved drafts entry is available on the Agent home and coding activity card.
`GET /api/agent/workspaces` returns only the authenticated owner's unexpired
checkpoint metadata for the configured repository, with no-store caching. It
returns a stable `not_configured` state when checkpoints are off. The Android
client caps the response, rejects redirects, uses the existing owner session and
refresh mechanism, cancels abandoned requests, and clears rows on account change.
It distinguishes loading, disabled, empty, failure and retry states.

**Continue with Agent** asks for confirmation, then starts a fresh Agent task
requesting `restore_workspace` and `workspace_diff` for that checkpoint ID. It is
not a direct restore endpoint or a claim that restore already succeeded. The
Agent must actually invoke the existing owner/repository-checked tool. Busy,
unsigned-in, offline, paused/unavailable Agent, attachments and unsent composer
content block the transition. No commands are replayed and no GitHub write is
approved by this button. Restore errors (including expiry or a moved main at PR
time) remain visible through the existing tool/reply flow. Saves and restore are
latest-revision drafts, not process or full invocation resume.

Activity and approval previews stay in memory. The explicit history/cloud/backup
codecs do not serialize the new activity field; reopening a saved chat does not
restore a terminal or a live approval. Existing approvals without a diff still
work. Current composer styling and attachment sheet are unchanged.

## Verification status

Only Kotlin compilation, Python syntax parsing and diff-format checks were run.
Automated tests and live engine/runner/database/device verification are deferred
at the owner's request. No database migration, deployment, runner activation or
APK release is performed by this change. Merge the parent workspace/runner/
checkpoint phases before this UI phase, deploy the matching engine separately,
and verify authentication, cancellation, diff/approval identity and restore on a
device before treating the feature as ready.

Read-only PR CI snapshots are described in [CODING_CI.md](CODING_CI.md).

[Reviewed PR repair](CODING_PR_REPAIR.md) adds the PR/branch label to repair drafts
and a separate stored-diff fix approval. Its approval is disabled in the new UI
when the required diff preview is absent.

[Toolchain profiles](CODING_PROFILES.md) add the selected profile label to command activity without persisting it in chat codecs.
