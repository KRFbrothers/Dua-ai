# Coding terminal runner — second implementation phase

The Agent's `run_command` tool runs a pinned source snapshot, including staged
workspace edits, in a separate Docker container. The Cloud Run chat engine never
gets a Docker socket or runs a host shell. Without runner configuration, the
tool returns an explicit not-configured error; there is no execution fallback.

This phase builds on [the coding workspace](AGENT.md#coding-workspace-first-implementation-phase).
It reuses the existing Google ADK tool/approval machinery, the official
[Docker CLI](https://github.com/docker/cli) and
[Docker Engine/Moby](https://github.com/moby/moby), Flask/Gunicorn already used by
the project, and Python standard-library archive/HTTP/process modules. No copied
vendor source or additional agent framework is introduced.

**Status:** implementation prepared for deferred verification. No runner was
provisioned, image built/pulled, command executed, cloud setting changed or
deployment performed during this phase. Tests and live verification are deferred
at the owner's request. A configured endpoint does not mean live readiness.

## Data and execution flow

1. `workspace_status` fixes a commit SHA; the model reads project guidance and
   stages changes with the existing tools.
2. `run_command` validates its bounded argument list, relative working directory
   and 1–120 second deadline. Applicable root/cwd `AGENTS.md` must have been
   returned by a workspace read/status tool first.
3. The engine downloads that commit's GitHub source archive. Only the exact
   GitHub codeload redirect for the configured repository/SHA is accepted. The
   GitHub token remains in the engine. Every regular file's Git blob SHA and
   executable mode must match the pinned tree; missing files fail closed.
4. The engine overlays staged text and sends the compressed snapshot plus its
   SHA-256, commit SHA and command to the configured HTTPS runner. Neither model
   credentials, GitHub token, owner session, engine environment nor approval
   storage is sent. The runner has its own dedicated control token.
5. The runner validates the bundle and writes regular files into a temporary
   input directory. It creates a container from a server-selected, locally
   installed digest-pinned image; no caller can select Docker flags, mounts,
   environment, image, runtime or network.
6. The input mount is read-only. The container copies it into bounded writable
   tmpfs and executes the argument list there. Output, exit status and matching
   snapshot digest are polled by the engine. Output is fenced as untrusted tool
   data and passes the existing secret filter before returning to the model.
7. Completion/cancellation removes only that run's generated container name and
   temporary input. Explicit `export_paths` retain selected text files for
   `import_command_changes`, followed by diff review and normal PR approval.
   Other command edits disappear. See [checkpoints and import](CODING_CHECKPOINTS.md).

The source is an exported tree, **not a Git clone**: `.git` metadata, credentials,
Git LFS object downloads and submodule checkouts are absent. Links/submodules,
invalid paths, duplicate paths and file/directory conflicts are rejected. Binary
regular source assets are supported within the bundle limits.

## Tool contract

```json
{
  "argv": ["python", "-m", "unittest", "discover", "-s", "tests"],
  "cwd": "google-cloud-backend",
  "timeout_seconds": 60,
  "export_paths": []
}
```

`argv` is passed as arguments, not interpolated into a host command. A shell
explicitly requested as part of `argv` still executes only inside the container.
The command's exit status comes from `docker exec`; successful container startup
is never reported as command success. Results identify the
exact source snapshot; later draft edits are not covered by earlier results.

The reference image is **Python-only**, with standard-library tools. It does not
provide pytest, Node, the Android SDK, Java or project dependencies. Bake reviewed
dependencies/toolchains into a suitable image before selecting it on the runner.
Runtime package downloads cannot succeed because network access is disabled.
Images used for text exports must also provide `/usr/local/bin/python` with
isolated-mode support (`-I -S`), plus the reference image's coreutils/shell tools.
Large/long Android builds exceed this first runner's limits and still belong in
the existing GitHub CI flow.

## Fixed boundaries

| Boundary | Value |
| --- | --- |
| Network | `none`; no host networking, cloud metadata or external APIs |
| User / privileges | UID/GID 10001, all capabilities dropped, no new privileges, default Docker seccomp |
| Filesystem | Read-only image and source mount; writable `/workspace` 256 MiB and `/tmp` 64 MiB tmpfs |
| Credentials | No engine environment or host credential mounts; clean Docker client config disables inherited proxy/credential defaults |
| CPU / memory / swap | 1 CPU, 512 MiB memory, memory+swap capped at 512 MiB |
| Processes / descriptors | 128 PIDs, 1,024 open files, core dumps disabled |
| File size | 64 MiB per generated file |
| Command duration | 1–120 seconds; independent in-container `timeout`, plus controller watchdog; container lifetime adds 25 seconds for setup/export |
| Output | First 32,000 bytes retained; remaining output drained/discarded, truncation reported |
| Source bundle | 16 MiB compressed, 64 MiB expanded tar, 32 MiB source, 8 MiB per source file, 25,000 archive members |
| Concurrency | At most 2 commands per controller process; excess work returns busy |
| Results | Up to 64 process-local records, reclaimed after 15 minutes on new submissions |
| Agent calls | At most 4 `run_command` calls per request; commands are never automatically retried |

The engine tool allows 240 seconds for source preparation, upload, execution,
polling and cancellation. It reserves time for cleanup. App disconnection and ADK
completion/deadline signals reach the command client; a lost POST response still
allows cancellation using its client-generated job ID and per-run scope. If the
runner cannot be reached, the independent container deadline remains active.
Cancellation is best effort over the network, not proof that remote execution
stopped immediately.

## Deployment templates — setup required, not activated

The control service belongs on a **dedicated Linux Docker host**, not inside the
chat container. Its Docker-socket access makes it trusted host administration
code. Only command containers process untrusted repository code. Docker shares
the host kernel; an operator may install gVisor and set `DUA_SANDBOX_RUNTIME=runsc`
for an additional isolation layer. Selecting an unavailable runtime fails; it
never falls back to host execution or an unconfined container.

Templates live under [`google-cloud-backend/coding-runner`](../google-cloud-backend/coding-runner):

- `Sandbox.Dockerfile`: reference Python image using the repository's pinned
  Python base. Build/publish a reviewed image and install its immutable digest
  on the runner in a separate setup step. The controller uses `--pull=never`,
  disables image health checks and rejects images declaring persistent volumes.
- `requirements.txt`: minimal controller dependencies, with the same versions
  as the engine's existing lock file.
- `dua-coding-runner.service`: one Gunicorn worker, eight threads, bound to
  loopback port 8091. Requires a pre-created `dua-runner` user/group with Docker
  socket access, code at `/opt/dua`, and a venv at `/opt/dua-runner-venv`.
- `Caddyfile.example`: TLS reverse-proxy template. Replace the example DNS name
  and configure DNS/TLS before use; only the TLS proxy should be exposed.

Create a restricted `/etc/dua-coding-runner.env` on that host with these settings;
generate the dedicated random token securely there, not in chat or source:

| Runner variable | Purpose |
| --- | --- |
| `DUA_CODING_RUNNER_TOKEN` | Dedicated random ASCII token, at least 32 characters; never a Google/GitHub/API token |
| `DUA_SANDBOX_IMAGE` | Installed immutable `registry/name@sha256:<64 lowercase hex>` image reference |
| `DUA_SANDBOX_RUNTIME` | Optional `runsc`; unset uses Docker's configured default runtime |
| `DUA_RUNNER_DATA_DIR` | Host-visible temporary source directory; supplied by the systemd template |

After the runner is installed and verified, configure the engine separately:

| Engine variable | Purpose |
| --- | --- |
| `DUA_CODING_RUNNER_URL` | HTTPS origin only, without path/query/userinfo; points to the TLS proxy |
| `DUA_CODING_RUNNER_TOKEN` | Secret binding holding the same dedicated control token |

No request/model parameter can change these settings. Redirects are refused.
Each run also uses a random scope checked on status/cancellation requests. The
controller is for the trusted engine, not a public browser API, and has no CORS
configuration. Keep engine and controller credentials separate from command
images. The service is intentionally not added to the engine's Cloud Run build
or deployment script.

Jobs and results do not survive a controller restart. The in-container deadline
still stops command execution, but stopped containers/temp directories can remain
after a controller/host crash; host maintenance must reclaim owned
`dev.dua.coding-sandbox=1` containers and its data directory after confirming no
run is active. Do not run broad Docker prune commands on a shared host.

## Deferred verification and remaining work

Before activation: verify token/scope rejection, hostile/malformed archive
handling, pinned-tree matching, command exit codes, output caps, timeout,
cancellation, cleanup, no-network/no-secret isolation, unavailable Docker/image
failure and the full engine → runner → result flow. Add mocked regression
coverage and isolated real-container checks, then run the existing suites.

Private SQL draft checkpoints and selected text-file import are implemented in
[the third phase](CODING_CHECKPOINTS.md), with setup/verification still pending.
Durable Git checkouts, interactive/background terminals, package-network policy,
multiple toolchain profiles and larger workers remain future work. Android
activity views are described in [CODING_ACTIVITY.md](CODING_ACTIVITY.md), and
read-only CI evidence in [CODING_CI.md](CODING_CI.md). Existing workspace approvals still control GitHub changes; this runner
does not commit, push, open/merge PRs, deploy or modify cloud settings.
