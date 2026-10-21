# Coding draft checkpoints, resume and terminal-file import

This third phase builds on the pinned workspace and [terminal runner](CODING_RUNNER.md).
No database migration, cloud setting, runner provisioning or deployment was
performed. Local tests, live queries and container verification remain deferred
at the owner's request; this is an implementation draft for later verification.

## Checkpoint and resume

With `DUA_CODING_CHECKPOINTS=1` and server-side `ADK_SESSION_DB_URL`, successful
`edit_file` and `import_command_changes` calls attempt to save the staged draft
automatically. `checkpoint_workspace` saves explicitly. Results include checkpoint
ID, revision and `saved`; failures preserve the current in-memory draft and
explicitly report that durable saving failed. Without configuration, the draft
stays request-local. Configuration alone is not proof of live readiness.

In a fresh task, `list_workspaces` lists only the authenticated owner's unexpired
checkpoint metadata for the configured repository. `restore_workspace(id)` loads
that owner's saved commit and staged text. It refuses to discard an existing
dirty draft, active command or retained export. Read changed files/scoped
`AGENTS.md` again before editing or proposing a PR.

Only the latest checkpoint revision is retained. Updates compare owner,
repository, base and expected revision; stale writers get a conflict instead of
overwriting newer saved work. Resume does not silently rebase: existing PR
preflight still refuses a changed `main`, requiring a fresh proposal then.

No command is replayed. Container files/processes, unimported exports, command
output, credentials, full source archives and read/instruction caches are not
saved. This restores a draft, not a live terminal or complete ADK invocation.
Existing ADK `resumeRun` remains separate; callers explicitly list/restore the
draft after interruption or process restart.

### Private storage

- `dua_coding_workspaces` uses the existing private `dua_adk` schema/search path
  and dedicated ADK database role, not the Supabase Data API. No public schema
  exposure or application-role grants are added.
- The existing explicit `adk_cli.py init-db` creates the table and owner/update
  index. PostgreSQL revokes PUBLIC table access and enables RLS without API-role
  policies. The dedicated table-owner runtime role enforces owner predicates in
  every application query. Runtime tools do not migrate tables.
- Saves use per-owner quota locking and optimistic revision checks. PostgreSQL
  statements have a five-second local timeout. Existing short-lived connection
  and TLS rules apply; no additional pool or credential is introduced.
- Limit: 20 checkpoints per owner, 10 text files per draft, 200,000 UTF-8 bytes
  per file and 600,000 total; serialized JSON is separately bounded.
- Expiry is seven days, refreshed on save. Expired rows cannot be listed/restored
  and are removed on the owner's next save; no background deletion schedule is
  claimed. SQLite remains local-only; production needs private PostgreSQL.

For later setup, run the existing explicit `init-db` using the dedicated role,
then separately enable `DUA_CODING_CHECKPOINTS=1`. Keep `dua_adk` excluded from
exposed schemas. Neither operation was performed during implementation.

## Import terminal edits

`run_command` accepts optional `export_paths` (empty by default). Select up to ten
explicit unprotected paths and read their scoped guidance before the command.
Only successful commands export files. Export failure is reported independently
of the actual command exit status.
Upgrade engine and runner together for this extended request/result protocol;
an older controller refuses the new fields rather than falling back.

```text
workspace_status / read_file
  → run_command(..., export_paths=[...])
  → import_command_changes(command_id)
  → workspace_diff
  → propose_changes(files=[])
  → existing owner approval before GitHub writes
```

Exports stay server-side in the request workspace under the returned command ID.
They do not automatically change the draft. Import verifies that its fingerprint
still matches the executed draft, validates every file and applies the batch
atomically. If any draft edit changed meanwhile, rerun the command; no newer work
is overwritten. Import is single-use, shares normal change/byte limits, and
attempts a checkpoint when enabled.

The runner keeps bounded tmpfs alive briefly after `docker exec` completes. A
collector runs inside the image's `/usr/local/bin/python -I -S`, excluding project
imports/site initialization. Directory/file descriptors use no-follow traversal;
links, nonregular, oversized and binary files fail. Base64 exports carry file
digests; controller and engine both validate paths, hashes, contents, modes and
limits. Exported source is never executed or extracted on either host.

Protected paths, missing/deleted files, binary changes, symlinks, hardlinks,
submodules and executable mode changes cannot be imported. Failure leaves the
whole draft unchanged. The 32,000-byte terminal output cap is separate from the
600,000-byte text-export cap. Unexported edits and all container state disappear.

Commands retain a 1–120 second deadline. Container lifetime adds 25 seconds for
preparation/collection, with an eight-second collector watchdog and bounded
output. Exit codes come from `docker exec`, not successful container startup.
Cancellation, resource boundaries and PR approval remain in the runner guide.

## Deferred verification / remaining work

Before activation: add/run owner isolation, quota/expiry, stale-write conflict,
save failure, restore/guidance, no replay, stale-export, atomic/single-use import,
protected-path, hostile-link, output-limit and collector-failure regression
coverage. Then verify real containers and engine → edit → command → import → save
→ restart → restore → approved PR end to end. None of those checks ran here.

App terminal/diff/checkpoint views, persistent terminals, full Git checkout/rebase,
binary/deletion/mode import, richer CI evidence and larger workers remain future
work.

Android activity and saved-draft controls are described in [CODING_ACTIVITY.md](CODING_ACTIVITY.md).
