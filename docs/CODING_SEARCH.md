# Pinned workspace text search

`search_workspace` searches a validated source snapshot from the workspace's
fixed commit, with its current staged text applied. Existing `search_code` stays
a live GitHub index hint and does not include staged changes. Both normal Agent
tools and the ADK code helper can use the new search tool.

```json
{
  "query": "syncBlocked",
  "path": "app/src/main/java",
  "case_sensitive": true,
  "max_results": 30
}
```

`query` is a non-empty single-line literal of at most 200 characters; regex/glob
syntax is escaped. `case_sensitive` is a boolean (default true). `path` is empty
for the whole source or an exact relative file/directory, without a trailing slash;
directory scope uses a slash boundary, so `src` does not search `src2`.
Case-insensitive matching uses Python's Unicode-aware literal regex behavior.
`max_results` is an integer from 1 to 50 (default 30), counting matching lines
rather than every occurrence on a line.

## Source and limits

The tool reuses the existing authenticated GitHub archive client and validates
archive content/modes against the pinned Git tree before applying staged edits.
The bounded immutable archive is cached for the task. Each search overlays the
latest staged changes, including new files; it never switches to a newer live
branch head. Commands and search now share that same source-view helper, while
only commands pack/send a bundle to the runner. Search does not need a configured
runner and never executes repository code or a host shell.

Existing archive limits remain: 16 MiB compressed, 64 MiB expanded tar,
32 MiB total source, 8 MiB per source file and 25,000 archive members. Unsupported
links/submodules, missing files, incorrect hashes/modes, malformed archives and
over-limit source fail explicitly; there is no live-index fallback.

Search examines UTF-8 files without NUL bytes, up to 1,000,000 bytes per text file.
Larger and binary/non-UTF-8 files are skipped and counted. Results include file
path, one-based logical line number, up to 320 preview characters and a `clipped`
flag. The complete matching line is filtered for known secret patterns/values
before its preview is cropped. This filter does not establish that arbitrary
repository content is free of every possible secret.

Result rows share a 16,000-byte serialized JSON budget. `truncated` indicates that
the matching-line or output budget stopped the scan. `complete` is true only when
the scope was scanned without skipped files or result truncation. `scope_files`,
`files_examined`, `skipped_binary_files` and `skipped_large_files` describe coverage;
they are not a claim that the entire repository was searched when coverage is
partial. An unknown non-empty path is an error rather than an empty success.

There are at most six searches per Agent request. Search has a 75-second inner
budget, with a 120-second outer tool deadline; existing GitHub HTTP timeouts still
apply. Cancellation is checked before source preparation, between files, during
line scanning and before returning. Searches are not automatically retried.

## Draft consistency and guidance

A result includes `base_sha` and the draft fingerprint it searched. If the draft
changes during scanning, the result is refused so the agent can search again.
Long scans release the session lock; an active-search guard prevents opening or
restoring another workspace until the scan unwinds. No checkpoint is written.

Matching snippets are untrusted tool data. They do not populate the original-file
read cache or mark scoped `AGENTS.md` guidance as seen. The agent must still read
matching files and applicable guidance before editing, inspect the actual draft
diff, and use existing approval tools for any GitHub write. Search is discovery,
not code execution or proof that a suggested edit is correct.

## Verification status

Prepared offline cases cover staged/new content, moved live heads, archive reuse,
literal/case/path behavior, malformed arguments, skipped/oversized files, UTF-8
result budgets, filtering, cancellation, archive tampering, changed drafts,
command/search source identity, active-search cleanup and ADK/untrusted wiring.
Python syntax and staged diff formatting are checked locally. Test execution
remains deferred at the owner's request; final-head CI and actual repository
archive behavior still need verification.

No new dependency, model/runner configuration, secret, cloud service, deployment,
database initialization or APK release is introduced by this phase. Android UI,
composer and existing file-read contracts are unchanged.
