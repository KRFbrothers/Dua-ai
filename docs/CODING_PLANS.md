# Coding task plans

For multi-step coding work, `update_plan` lets the agent show its current checklist
in the Android coding activity card. The normal tool loop and ADK code helper use
the same server implementation. A simple read or small edit does not need a plan.
This uses the existing Google ADK runtime and Python/Compose components, without a
new dependency, secret, environment setting or deployment service.

## Contract

The normal tool loop supplies the complete replacement checklist:

```json
{
  "steps": [
    {"step": "Read the existing implementation", "status": "completed"},
    {"step": "Prepare the change", "status": "in_progress"},
    {"step": "Review the resulting diff", "status": "pending"}
  ],
  "explanation": "The relevant files are read; preparing the edit."
}
```

The ADK wrapper takes matching `steps: list[str]` and `statuses: list[str]` plus
optional `explanation`. It refuses unequal lists before forwarding, so it cannot
silently drop an item when constructing the same server contract.

- There must be 1–8 distinct, non-empty, single-line step labels, each at most
  140 characters. Status is `pending`, `in_progress` or `completed`, with at most
  one in progress. The optional explanation is at most 500 characters.
- Each accepted replacement receives a server-selected revision. Invalid input
  leaves the plan revision unchanged; at most 12 updates are accepted per task.
  The outer tool budget counts unsuccessful calls too. Updates are serialized by
  the existing coding-session lock and are not automatically retried.
- Updating a plan needs configured coding tools, but does not read GitHub, start a
  workspace, execute a command, save a checkpoint or create an approval. The
  checklist is a request-local result, separate from draft files and command data.

## Android behavior

The engine emits the validated checklist as a redacted `coding.plan` event.
Android validates its bounds/statuses and replaces only an older plan revision.
Duplicate/out-of-order revisions cannot regress the checklist. Other coding
activity, diff previews, command cards and approvals are retained.

The card labels this an **Agent-reported plan**. Completed steps reflect what the
agent reported; they are not verified command results, test evidence or approval
to change GitHub. Existing tool-result and human-approval rules remain in force.
Labels/explanations render as plain text, without executing Markdown, links or
terminal content.

When the reply ends or is stopped, the app freezes the visible checklist. An
unfinished active step becomes **Not completed**, without changing its stored
status to completed; the card shows **Reply ended · plan incomplete**. A late
revision cannot reopen a frozen plan. Existing command interruption handling is
unchanged.

The plan is not stored in chat history, backups, SQL checkpoints or ADK session
state. Restoring a saved workspace starts with a fresh task plan; it does not
resume checklist status or replay actions. Reopening a saved chat does not
restore this activity. Old clients can ignore the added field; a matching engine
and app are needed to display it.

## Verification status

Prepared regression cases cover malformed bounds/statuses, duplicate steps,
server revision limits, no-GitHub plan calls, UI redaction, ADK list mismatches,
Android ordering, reply termination and stream decoding. Kotlin app/test-source
compilation, Python syntax parsing and diff checks are the local implementation
checks. Automated execution remains deferred at the owner's request; GitHub CI
and device verification remain separate evidence.

No live provider call, runner activation, database initialization, image build,
cloud deployment or APK release is performed by this change.
