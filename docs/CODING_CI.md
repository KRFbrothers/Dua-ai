# Read-only CI evidence for coding tasks

`inspect_pr_ci(number)` is available when the existing GitHub integration is
configured. It works in the normal Agent loop and ADK Code helper, independently
of `AGENT_TEST_CODE`. For example, ask: “PR #336 ke CI failures explain karo.”
The tool reads existing results; it does not run tests or rerun Actions.

The tool resolves the numbered PR in `DUA_REPO`, observes its head SHA and branch,
and reads checks and workflow runs for that exact commit. It currently requires
the PR head repository to be the configured repository; fork heads are refused.
Each returned check/workflow identity is checked before its IDs are used for
further reads. Older workflow runs with the same workflow/event are replaced by
the latest run number. Jobs are read from the specific workflow attempt, so a
rerun does not mix jobs from different attempts.

For failures it can return check summaries, file/line annotations, failed jobs
and step names. It does **not** download job logs, artifacts, ZIP files, signed
storage links or JUnit reports. Annotations may contain actual test failures,
but many projects publish only an exit code. When detailed failures are absent,
the Agent must say so and use the GitHub job/checks page instead of guessing.

## Status and freshness

The result is a snapshot of the observed head commit, not a live subscription.
A final PR read checks whether the head changed while gathering evidence. Changed
heads are labelled stale; an unavailable final read produces unknown status.
Checks may finish or rerun after the snapshot without the head changing. Ask the
Agent to inspect the PR again for updated evidence.

- An observed failure can be reported even if other sources are unavailable.
- A passing report requires both lists to be complete and every returned check
  and selected workflow to have completed successfully. Missing permissions,
  invalid identities, capped lists and unknown conclusions cannot produce pass.
- Skipped/neutral checks have a separate state; an empty result is “no checks
  reported,” not success. Pending checks remain pending.
- Required-check policy, merge-commit/merge-queue checks, branch protections,
  coverage and merge eligibility are not evaluated. “Reported checks passed”
  therefore never means “safe to merge.”

The existing legacy `check_pr_ci` / `PrFlow` polling behavior is unchanged. The
new inspection tool can diagnose a PR in a later task, unlike that request-local
legacy polling tool. It does not restore the PR into a coding workspace:
`workspace_status` still starts from `main`. Repairing an existing PR branch is
not implemented by this read-only feature; external writes retain their existing
separate owner-approval flow.

## Limits and permissions

Uses the existing allowlisted GitHub REST client and Python standard library;
no additional framework, copied vendor code, dependency or secret is introduced.
New allowlist entries permit only PR reads, check annotations and attempt-specific
job reads in the configured repository. No rerun, merge, log-download or write
endpoint is added. Existing GitHub redirects remain disabled.

One inspection makes at most nine API calls, checks cancellation and a 75-second
budget between calls, and has the existing shared tool deadline set to 120
seconds. An in-flight GitHub read retains the existing client's 30-second socket
timeout. The Agent can inspect at most twice per request and does not
automatically retry the whole multi-read snapshot.

Read limits: 50 checks, 20 workflow runs before deduplication, annotations from
three failed checks (up to 20 annotations total), and jobs from two failed workflow
attempts (up to 20 jobs each, ten failed step names per job). Further evidence is
labelled omitted/shortened. Returned source text shares a 24,000 UTF-8-byte budget
and the existing configured-secret/pattern redactor. Results are fenced as
untrusted data for the model; repository/CI text cannot authorize tool calls.
The existing GitHub response byte cap still applies before JSON decoding.

Repository PR access, Checks read access and Actions read access are needed.
Missing permissions produce explicit incomplete/unknown evidence, not a prompt
to paste credentials. This implementation does not change token permissions.

## Android

The coding activity panel holds at most two PR snapshots in memory. Each shows
the PR, observed commit/time, status and a collapsible plain-text evidence preview,
plus an “Open GitHub checks” link built from validated repository/PR identifiers.
Incomplete/current-head warnings remain visible. Shortened CI evidence has its
own label. Snapshots are not written by the explicit history/cloud/backup codecs.
Existing workspace, terminal, draft, approval and composer flows are preserved.
Older app versions ignore the optional CI activity update.

## Verification status

Kotlin compilation, Python syntax parsing and staged diff formatting were checked.
The existing tool-declaration assertion was updated, but no tests were run at the
owner's request. No live tool/API inspection, model calls, workflow runs/reruns,
runner activation, SQL changes, deployment or APK release was performed.

Keep this phase as a draft until later verification covers successful/failed/
pending/neutral/missing checks, permissions, capped lists, changed heads, rerun
attempts, cancellation, redaction/fencing and device rendering. The parent
workspace/runner/checkpoint/UI phases must land first.
