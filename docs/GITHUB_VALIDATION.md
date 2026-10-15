# GitHub repository and path policy

The Vercel backend's pure policy lives in [github-validation.mjs](../web/src/core/github-validation.mjs). It contains no HTTP calls, credentials, filesystem access or user-facing messages. The read/write clients keep their independent method/path allowlists and tokens; central validation does not grant write access.

## Call sites and policies

| Input | Shared helper | Preserved behavior |
| --- | --- | --- |
| Repository names | `validRepository` | String `owner/name`, using the existing protocol character/length pattern. Used by chat/PR validation, read/write clients, configured PR repository lists and saved metadata. `REPOSITORY_NAME_PATTERN` also supplies the repository fragment in both HTTP clients' allowlists. |
| Connector reads | `normalizeRepositoryReadPath` | Trim whitespace, strip one leading `./`, reject absolute paths, backslashes, control characters, empty segments and `.` / `..` segments, then escape each segment for the GitHub URL. A literal `%2e%2e` filename is escaped again rather than decoded into traversal. |
| Saved read metadata / approved PR paths | `validRepositoryPath` | Must already be canonical: no trimming or leading `./` normalization. Also reject an initial drive prefix such as `C:`. The read encoder historically treats such a string as a literal GitHub name; it never resolves it against a local filesystem. |
| Live and saved write proposals | `repositoryWritePathIssue` | Apply canonical-path validation plus case-insensitive protection for any `.git` segment, root `.gitmodules` and root `.github/workflows/`. Issue categories become recovery copy in `build-pr.mjs`; saved snapshots reject them outright. Read metadata may still describe protected files. |

All repository file paths share the existing **400 UTF-16 code-unit** cap (`MAX_REPOSITORY_PATH_CHARS`) before URL encoding. HTTP allowlists and response bounds remain additional limits. Folder-prefix filtering in Build chat is not a file write or a filesystem lookup. Model-proposed changes retain their existing normalization before passing through strict write validation; an owner-approved PR request is validated without normalization.

Consumers: [read client](../web/src/core/github.mjs), [write client](../web/src/core/github-write.mjs), [Build chat](../web/src/core/build-chat.mjs), [PR validation](../web/src/core/build-pr.mjs), and [snapshot validation](../web/src/core/cloud-snapshot.mjs). Existing `github.mjs` / `build-chat.mjs` exports remain available for callers.

Android and the Python Cloud Run agent have their own runtime implementations. This refactor centralizes the Vercel policies without replacing those platform contracts or broadening Cloud Run permissions. See [architecture boundaries](ARCHITECTURE.md#github-permission-boundaries) before changing either backend.

## Related size limits

These bounds serve different protocols and units; they must not be silently replaced with one global limit. Values below are defaults unless noted. `KiB`/`MiB` mean multiples of 1024; JavaScript string lengths count UTF-16 code units.

| Layer / operation | Bound and source |
| --- | --- |
| Vercel inline chat body | 4 MiB JSON bytes, with up to 3 attachments / 2 MiB decoded total and 256 KiB UTF-8 per text file: [attachments.mjs](../web/src/core/attachments.mjs). Message history has its own 2.5 MiB serialized-byte cap: [providers.mjs](../web/src/core/providers.mjs). |
| GitHub repository reads | File responses are bounded to 3 MiB JSON, tree responses to 8 MiB JSON; decoded read output is truncated to 100 × 1024 string code units by default. Files GitHub does not return as base64 text are rejected: [github.mjs](../web/src/core/github.mjs). |
| Vercel Build read budget | 100 × 1024 code units per file, 600 × 1024 total and 40 files per reply, configured by `DUA_BUILD_*`: [build-chat.mjs](../web/src/core/build-chat.mjs). These are read/context bounds, not write byte limits. |
| Vercel PR creation | 20 files, 200 KiB UTF-8 per new file content, 1 MiB UTF-8 total by default; bounded request size includes JSON escaping overhead: [prLimits / handleBuildPr](../web/src/core/build-pr.mjs). |
| Stored snapshots | JSON-compatible metadata caps include up to 512 × 1024 code units per saved proposal file; total snapshot payload is bounded to 4 MiB. A restorable snapshot is not permission to submit an oversized PR: [cloud-snapshot.mjs](../web/src/core/cloud-snapshot.mjs), [snapshot migration](../supabase/migrations/202610040001_dua_cloud.sql). |
| Stored uploads | Separate private Storage path, normally 50 MiB per file, further limited by the bucket/plan. Not the inline chat limit: [SUPABASE.md](../SUPABASE.md#1b-stored-files-bucket-phase-2-uploads-pdfs-spreadsheets-audio). |

Provider-specific and Cloud Run limits remain documented in [PROVIDERS.md](../PROVIDERS.md) and the [engine README](../google-cloud-backend/README.md). Raising one limit does not raise another layer's bound.

## Verification

[github-validation.test.mjs](../web/test/github-validation.test.mjs) exercises valid/invalid names, normalization versus strict paths, encoding, controls, path boundaries, protected-file read/write differences, and live/snapshot policy consistency. Existing Build tests also verify owner gates, allowed methods, no network/writes for rejected requests, stale versions and PR idempotency. From `web/`:

```sh
node --test test/github-validation.test.mjs test/build-chat.test.mjs test/build-pr.test.mjs test/cloud-snapshot.test.mjs
npm run test:ci
```

When changing policy, test saved snapshots and live requests together. For user-facing recovery steps, see [GitHub troubleshooting](GITHUB_TROUBLESHOOTING.md).
