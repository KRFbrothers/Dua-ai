# GitHub request and stage logs

This implements the Node-backend portion of [roadmap section 2.4](ROADMAP.md#24-add-structured-logging). It applies to the shared Node router on Cloud Run and local development; it does not instrument the separate Python engine.

## Find a request

The backend writes one JSON object per stdout line with `source: "dua-github"`. In Cloud Run, open the `dua-api` service logs and filter by this source, then use `requestId` to group one request's events. For example, in Logs Explorer:

```text
resource.type="cloud_run_revision"
resource.labels.service_name="dua-api"
jsonPayload.source="dua-github"
```

Add `jsonPayload.requestId="ID_FROM_A_LOG_ENTRY"` to follow that request. Local `npm start` stdout contain the same JSON records. There is no new credential, environment setting or external logging dependency.

Each `/api/chat`, `/api/connectors/github` and `/api/build/pr` request reaching [handleService](../web/src/server/service-handler.mjs) receives a fresh server-generated UUID. Incoming request-ID headers are ignored; concurrent async operations retain their own context through Node's `AsyncLocalStorage`. Account/file/voice/setup and unrelated routes are not part of this logger. Hosting/burst rejections before the shared router are outside its scope.

## Events and fields

| Event | Meaning |
| --- | --- |
| `request.started` | Shared routing begins, before access/validation checks. |
| `request.response` | The handler produced a response, with its HTTP `status` and elapsed `durationMs`. For SSE this means response creation, **not** completed generation or a saved reply. |
| `request.failed` | An exception escaped the handler. Contains elapsed time and numeric status if available, never exception text. |
| `stage.started` / `stage.finished` | A GitHub/proposal operation began/ended. The finish includes elapsed time, `outcome` (`success`, `refused`, `error`) and numeric error status when available. |

Every record contains `source`, `requestId`, the fixed `route` pathname and `event`. Stages are fixed identifiers:

- `repository_list`, `repository_load`, `repository_tree`: connector listing and Build repository preparation.
- `file_read`, `repository_search`: bounded GitHub reads/searches.
- `proposal_creation`: synchronous model proposal validation; `refused` means no proposal was produced.
- `pr_submission`: existing owner-approved PR creation/recovery; success can reuse an existing PR. It does not mean a PR was merged.

Access/setup/input rejection may return before a stage starts; its status still appears in `request.response`. A stage error can be handled by the existing application logic, so a later response can have a different status. Stage success reports operation completion, not complete/non-truncated repository data.

## Privacy and behavior

[request-logging.mjs](../web/src/core/request-logging.mjs) constructs a fixed-field record instead of serializing requests, results or errors. It excludes tokens, account identifiers, repository names, file paths/contents, search queries, model prompts, proposal IDs/titles/diffs, branch names, PR URLs, response bodies, headers and query strings. Use existing user-facing errors for recovery guidance; do not add sensitive context to these records.

The logger does not read or drain response bodies, add response headers, change validation/permissions, retry GitHub calls or create extra writes. A synchronous log-sink failure is ignored. Operations called outside a router logging context remain unlogged; the synchronous proposal API stays synchronous. These are operational diagnostics, not a durable audit ledger or an authentication mechanism.

## Verify

From `web/`:

```sh
node --test test/request-logging.test.mjs test/build-chat.test.mjs test/build-pr.test.mjs
npm test
```

Tests mock all external services. They cover concurrent request isolation, safe fields and sensitive-data exclusion, repository preparation, proposal acceptance/refusal, owner-approved PR success/conflicts, original error propagation, failed log sinks and response bodies left unread. Passing tests do not verify live hosting log ingestion or deploy the service.
