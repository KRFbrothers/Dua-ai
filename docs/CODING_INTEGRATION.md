# Coding agent integration

The coding phases were developed as stacked pull requests. Merging a child PR
into its parent feature branch does not add that child to `main`. This integration
combines phases 2–7 with the current `main`, which already includes the phase 1
text workspace, Gemini authentication/retry changes and website chat.

## Included behavior

| Part | Implementation and operating limits |
| --- | --- |
| Isolated command execution | [Separate Docker runner](CODING_RUNNER.md), pinned source snapshots, bounded output, cancellation and no network inside job containers |
| Saved drafts and command edits | [Owner-scoped checkpoints](CODING_CHECKPOINTS.md), explicit import of selected text exports and draft fingerprint checks |
| Android review | [Coding activity and saved drafts](CODING_ACTIVITY.md), command outcomes, diff previews and approval controls |
| CI evidence | [PR check snapshots](CODING_CI.md), bounded annotations and failed steps tied to the observed commit |
| Existing PR repairs | [Separate repair approval](CODING_PR_REPAIR.md), pinned agent PR head and non-forced branch updates |
| Toolchains | [Python, JVM and Android profiles](CODING_PROFILES.md), fixed resources and digest-bound image contracts |
| Live output | [Command activity updates](CODING_LIVE_OUTPUT.md), bounded redacted output during runner polling |

Both the normal tool loop and Google ADK code helper use these implementations.
The integration does not introduce another agent runtime.

## Configuration and activation

The text workspace remains usable without a runner or checkpoint database.
Command execution requires a separately provisioned runner, reviewed installed
images, an HTTPS endpoint and a dedicated token. Saved drafts require the existing
explicit database initialization and `DUA_CODING_CHECKPOINTS=1`. Missing
configuration produces an unavailable state; it does not run commands on the
engine host or claim that drafts were saved.

The image recipes are preparation templates. The default Gradle dependency seed
is empty, and image discovery reports configuration rather than verified build
readiness. No image build, runner activation, database initialization, engine
deployment or APK release is performed by this integration.

## Remaining work

The implementation still needs focused regression and isolation tests, actual
image builds, runner provisioning, database verification, end-to-end coding-task
evaluation and device checks before activation. Interactive terminal sessions and
dependency downloads during a job are not implemented. Detailed limits and
verification requirements remain in the linked operating guides.
