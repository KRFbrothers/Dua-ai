# Audit verification and fixes

Source baseline: `569b70f`, the same commit named in the supplied DUA audit.
This follow-up verifies the ranked fix list and selected security claims. It is
not a fresh line-by-line audit of every repository file or a production audit.

## Findings and disposition

| Original fix | Verification | Result |
| --- | --- | --- |
| 1–2: documents and note reindex bypass maintenance | Confirmed in `app.py`. Document POST also performs paid semantic searches. | Added maintenance and Agent pause checks before configuration, storage or provider initialization. Document GET stays available. |
| 3: embedding outages return raw 500 | Incorrect for the HTTP route: `agent_documents_api` already catches provider/storage exceptions and returns a generic no-store 503. | Preserved existing handling; added a regression test. A library having no `except` block is not itself a defect. |
| 4: no burst limiter | Confirmed in `access-policy.mjs`. | Added an authenticated-account token bucket before daily quota consumption. Default capacity/refill is 20 requests per minute; `DUA_MINUTE_REQUEST_LIMIT` overrides it. Invalid settings fail closed. |
| 5: approvals only live in memory | Incomplete: `agent_store.approvals()` already selects `SqlPendingStore` when `ADK_SESSION_DB_URL` is configured. Atomic claims precede writes. | Corrected the stale `agent_safety.py` module documentation. Shared PostgreSQL readiness remains a deployment check. `min-instances=1` would not guarantee a single instance or prevent restart loss. |
| 6: Docker resource limits missing | Incorrect: `automation_terminal.Session` already specifies `--memory 512m`, `--cpus 1`, and `--pids-limit 128`, plus disabled networking, read-only root, unprivileged user and tmpfs limits. | No duplicate flags added. Live isolation is not verified here. |
| 7: unset app token is silent | Confirmed; owner authentication remains mandatory. | Added one startup warning, without credential values. |
| 8: provider-list assertions crash on malformed provider | Not reproduced by source: `chatConfiguration` only asks for Dua/Gemini on a separate engine, and `chatProvider` always constructs those supported providers. | No `mapNotNull` change that could silently drop required capabilities. |
| 9: attachment preview assertion | Guarded: thumbnail is invoked only when the image flag is true; that flag requires a non-null immutable upload reference. | No confirmed crash; unchanged. |
| 10: unpublished proposal assertion | Guarded: `proposalStage` returns Created only when the same immutable proposal has a pull request. | No confirmed crash; unchanged. |
| 11: image-grid assertion | Caller filters out items without media before passing them to `ImagesContent`. | No current production crash established; unchanged. |
| 12: viewer assertion | `open` sets preview only for immutable items with non-null media. | No confirmed crash; unchanged. |
| 13: Build provider selection throws | Confirmed: availability checks accept any configured supported provider, while fallback selection uses `first` on only Dua/Gemini. | Snapshot configuration and use `firstOrNull`; missing fallback displays an error before changing composer modes or repository. Added Android regression case. |
| 14: backup owner assertion | Guarded: `owner` already is a local `val`; a non-null owner is required to compute the restore path before reaching the download. | Proposed audit change would duplicate existing protection. |
| 15: voice assertions | Chunk zero schedules `next` for every later chunk; `playing` is assigned synchronously before `output.play` on the main dispatcher. | No demonstrated ordering defect; unchanged. |
| 16: navigation collects in background | Confirmed use of non-lifecycle-aware state collection. | Converted data StateFlow collections to `collectAsStateWithLifecycle`, with explicit lifecycle-runtime-compose dependency matching 2.8.7. The lifecycle-state observer remains unconditional so it can immediately block external entries while backgrounded. |
| 17: missing lint block disables lint | Incorrect causal claim: Android Gradle plugin supplies lint tasks and defaults without the block. CI omits lint. | Made error/report settings explicit; documented `:app:lintDebug`. No fabricated baseline, suppressed findings or CI edit. Lint execution remains unverified. |
| 18: font subsetting | Font exists; size alone does not establish unused glyphs/weights. Chat can display arbitrary multilingual text. | Deferred pending an explicit glyph/language/weight coverage target and rendering checks; no asset removed or restricted to guessed languages. |
| 19: maintenance documentation | Coverage was incomplete. | Updated docs to describe implemented document/reindex guards, semantic search, title generation and diagnostic exceptions. |
| 20: Google sign-in has no accessible label | Incorrect target: `GoogleSignInButton.kt` contains sign-in orchestration, not a button. `OnboardingContent` uses an OutlinedButton with visible “Continue with Google” text, which supplies Compose semantics. | No redundant description added to decorative Google artwork. |

Additional source checks:

- `page-reader.mjs` validates public HTTPS hostnames, rejects non-public DNS
  results, pins the selected address in the HTTPS lookup callback, and repeats
  URL/DNS checks for each redirect. The report's unverified SSRF concern is not
  evidence of a missing guard.
- `PendingStore` already caps retained actions at `MAX_STORED = 50`; the audit's
  uncapped-memory claim is incorrect.
- `/api/agent/capabilities` now reports ADK unavailable when Agent is paused or
  maintenance is active, consistent with the root Agent capability.
- `app/src/androidTest` is absent. Existing Compose/Robolectric tests are JVM
  tests, and the separate benchmark module's device tests do not establish app
  instrumentation coverage. `docs/TESTING.md` now says this explicitly.
- Literal `http://localhost` in the Node server is used to parse request paths;
  its other HTTP literal prints the local/container listener address. Neither
  establishes an insecure upstream provider connection.

## Validation observed in this environment

- PASS: `node --test --test-isolation=none --test-reporter=spec test/request-rate-limit.test.mjs test/web-agent-action.test.mjs`
  from `web/`: 11 tests passed. Covers bucket refill/account isolation, bounded
  state, quota ordering, refreshed cookies, invalid config and approval routing.
- PASS: Python `compileall` for modified app and kill-switch test files. This
  checks syntax only, not Flask request behavior.
- PASS: `git diff --check`.
- BLOCKED: full `npm test` fails loading missing `cfb`; dependencies could not
  be installed because network-permission tool calls did not complete. This is
  not a passing full Node regression run.
- BLOCKED: Python request tests need pytest/Flask (not installed). Installation
  initially failed with a proxy/socket permission error; permission attempts
  were interrupted. Added tests have not been executed.
- BLOCKED: `:app:testDebugUnitTest :app:lintDebug` could not download the Gradle
  distribution (`SocketException: Operation not permitted`). Retried using a
  writable Gradle cache. Android compilation, added regression test, lifecycle
  behavior and lint findings have not been verified.

Burst limits are local to each Node process and reset on restart. They cover
authenticated requests through the shared Node access guard, including web chat,
chat/search/connector paths; they do not impose a global provider billing cap or
limit direct requests to the separate Python engine. Keep durable daily quotas
and provider budgets. No deployment, database migration, workflow, protected
layout, vendor bundle, font, commit, push or live service was changed.
