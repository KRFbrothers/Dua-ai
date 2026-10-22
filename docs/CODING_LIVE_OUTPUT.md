# Live command output

The normal tool loop and ADK helper now forward command progress to the existing
Android coding card while `run_command` polls the separate runner. Previously,
the card stayed at preparing until the final result arrived.

## Flow

1. The existing preparing event creates one activity ID for that tool invocation.
2. After validating the runner's job ID, source snapshot, base commit and selected
   profile/image identity, the engine can send queued/running events on that same
   ID. These events contain a cumulative output preview, not appended text deltas.
3. Android replaces that card's preview and keeps its command label, profile and
   position. Expand **command output** to read updates as they arrive.
4. Only the existing validated final result supplies success/failure and exit
   status. Reply termination marks unfinished cards interrupted and retains the
   last preview. Late live events cannot reopen a final/interrupted card or move
   a running card back to queued.

The runner already exposes bounded output in its poll response; no controller
endpoint, interactive shell, WebSocket, stdin or new dependency is added. Output
delivery is normally about once per second, subject to command buffering and SSE
delivery. Unchanged snapshots are suppressed, and output-only changes are limited
to one event per second. State changes can be sent immediately. Old Android
clients can continue using the final command event. A matching new engine and
Android app are needed for live cards.

## Bounds and filtering

Each preview uses at most the existing first 32,000 output bytes (and at most
32,000 displayed characters after redaction). A new redactor processes each
bounded cumulative snapshot with the existing secret rules. It holds potential
partial keys and unfinished private-key blocks until enough output arrives to
redact them. This can delay a suffix of the preview. Text remains selectable
plain text; terminal escape sequences, links and repository output are not
executed by the card. The filter does not prove arbitrary output free of every
possible secret.

Callbacks are local to a single invocation, not shared mutable session settings.
Closing the callback after completion, cancellation or a tool deadline suppresses
new updates without waiting on a blocked SSE enqueue. An event already in flight
can still arrive late; Android's state checks keep the final card intact. Existing
runner cancellation and independent container deadlines remain responsible for
job cleanup. No command is replayed or retried to obtain progress.

Live previews are owner UI events only: they do not become new model turns,
approval arguments, checkpoints, saved history, cloud backups or trace payloads.
The normal final tool result still goes to the model through its existing
untrusted-output fencing. The four-card limit and Stop control are unchanged.

## Verification and activation

Focused offline regression cases are included for identity mismatch, partial
secret redaction, coalescing, callback closure, invocation wiring, cumulative
replacement and late/interrupted events. Automated test execution remains
deferred at the owner's request. Kotlin compilation, Python syntax and diff
formatting are the local implementation checks; these do not establish live
runner or device readiness.

The runner still requires its documented separate provisioning and configuration.
No image/container build, runner activation, database initialization, live model
call, cloud deployment or APK release is performed by this change. Interactive
terminal sessions and dependency downloads during a job remain separate work.
