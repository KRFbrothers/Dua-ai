# Toolchain profiles for the separate coding runner

This seventh phase adds three fixed profile contracts to the existing Docker
runner. It uses the existing Docker CLI, Python standard library, Google ADK
wiring and image build recipes. No new agent framework, controller/engine Python dependency,
network permission or engine secret is introduced. No image was built/pulled,
runner activated, cloud setting changed or deployment performed here.

## Agent flow

`list_runner_profiles` reads authenticated configuration metadata from the runner's
new `GET /v1/capabilities` endpoint. It does not inspect Docker or execute a
command. Configured image contracts have `liveVerified: false`; this is not proof
that SDKs/dependencies are usable. The model receives only profile IDs, intended
toolchain versions, fixed limits and image digests, not registry URLs or tokens.

`run_command` accepts optional `profile` (`python`, `jvm`, `android`; default
`python`). Before a non-Python submission the engine discovers the profile and
binds the job/result to its configured image digest. An unknown/unconfigured
profile, unsupported discovery endpoint, different profile or changed digest is
an error; there is no substitution or host-shell fallback. Commands remain pinned
to their source snapshot, and explicit text exports retain their existing
fingerprint/import checks. Retained exports also identify the profile/digest.

```json
{
  "profile": "android",
  "argv": ["dua-gradle", ":app:compileDebugKotlin", "-Dorg.gradle.jvmargs=-Xmx1536m", "-Pkotlin.compiler.execution.strategy=in-process"],
  "timeout_seconds": 120,
  "export_paths": []
}
```

This is a future usage example, not an executed build. `dua-gradle` uses the baked
Gradle distribution with `--offline --no-daemon --max-workers=2` and disables Java
toolchain auto-download. Repository scripts can still request other flags or run
arbitrary code inside their container, but Docker networking stays disabled.
Missing plugin/artifact/SDK packages fail offline instead of being installed at
runtime. There is no on-demand dependency warmup or package network access.

The Android activity card displays the selected profile on command requests and
results. A selected profile on a failed/preparing card is not a claim of execution.
As before, only a validated final runner result reports command success. The new
field stays in memory outside history/cloud/backup codecs; composer UI is unchanged.

## Fixed limits

Operators enable profiles by supplying images, not resource overrides. Neither
tool callers nor runner request payloads can set images, mounts, environment,
network, runtimes or resource limits.

| Profile | Intended toolchain | CPU | Memory/swap cap | Workspace | Private cache | /tmp | PIDs | File cap | Setup |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| python | Python 3.12 | 1 | 512 MiB | 256 MiB | none | 64 MiB | 128 | 64 MiB | 10 s |
| jvm | JDK 17, Gradle 8.11.1 | 2 | 2 GiB | 512 MiB | 1 GiB | 128 MiB | 256 | 128 MiB | 20 s |
| android | JDK 17, Gradle 8.11.1, SDK 35 / Build Tools 35.0.0 | 2 | 4 GiB | 2 GiB | 2 GiB | 256 MiB | 512 | 256 MiB | 30 s |

All profiles retain UID/GID 10001, capability drop, no-new-privileges, default
seccomp (optional configured runsc), read-only root/input, `--network=none`,
`--pull=never`, no persistent image volumes and per-run cleanup. Toolchain/SDK
locations are read-only image paths. Writable caches are per-run tmpfs copied
from the image's public seed, not host/shared/user credential directories.
Tmpfs and JVM heap share the cgroup memory cap; the advertised directory sizes
are ceilings, not reserved extra RAM. OOM/setup timeout is a failure, not a reason
to relax limits or rerun automatically.

Command time stays 1–120 seconds. Container lifetime adds the profile setup budget
plus 15 seconds for export/overhead (the original Python total remains +25 s).
The existing 240-second engine tool deadline and its cleanup reserve are unchanged;
preparation/upload/polling also consume that budget. Source/archive, terminal
output and text-export limits are unchanged. Large/cold Android builds and long
tests still belong in CI; these profiles are for bounded offline checks.

The existing two-job controller cap is unchanged. Enabling two simultaneous
Android jobs can consume roughly 8 GiB plus host/controller/Docker overhead;
provision a dedicated host with sufficient headroom (typically 16 GiB or more)
before enabling that profile. No host has been provisioned or resized here.

## Host configuration and compatibility

Keep `DUA_SANDBOX_IMAGE` for the default Python image, or supply the host-only
`DUA_SANDBOX_IMAGES_JSON` mapping with any subset of the known IDs:

```text
DUA_SANDBOX_IMAGES_JSON={"python":"registry.example/dua-python@sha256:<digest>","jvm":"registry.example/dua-jvm@sha256:<digest>","android":"registry.example/dua-android@sha256:<digest>"}
```

Replace placeholders with actual reviewed, installed 64-hex digests; the example
is deliberately not an active configuration. Unknown keys, duplicate JSON keys,
mutable tags, empty maps and conflicting Python definitions are rejected. No
profile is inferred from argv, repository content or file extensions. If Python
is not configured, default/old-client commands fail rather than using another
profile. Catalogue access uses the existing dedicated token and per-request scope,
with no-store responses; no owner or engine credentials enter a job container.

The controller checks the installed image's volume declarations and profile label
before creation. JVM/Android images must declare `dev.dua.coding.profile` matching
the selected ID. Legacy unlabelled Python images remain accepted; the descriptor
is still only an intended contract and their actual versions need verification.
Inside-container bootstrap checks required tool/SDK paths before preparing source.
This is not a dependency or container-isolation evaluation.

Upgrade controller and engine together for discovery/advanced profiles. Default
Python requests retain the original request fields; a new engine can use the
original Python controller, and an old engine can use a new controller when the
Python profile is enabled. Advanced jobs require the new endpoint and explicit
profile/digest response fields. The engine rejects changed profile limits/versions
rather than silently using an incompatible controller. The default Python path
skips discovery for compatibility and therefore reports a digest only if the
controller supplies one.

## Prepared image recipes — build separately

Files are under `google-cloud-backend/coding-runner`, which is the Docker build
context. `.dockerignore` includes only recipes, the build verifier, wrapper and
explicit dependency seed. These templates were not built or run in this change.

- `Sandbox.Dockerfile` remains the pinned Python reference and now labels its
  profile. No extra packages/dependencies are included.
- `Jvm.Dockerfile` takes a reviewed digest-pinned `JVM_TOOLCHAIN_IMAGE` exposing
  `/opt/java/openjdk` and `/opt/gradle`. It copies only those toolchains into the
  existing pinned Python base, avoiding inherited volume/user/env/entrypoint
  configuration. Use compatible Linux/architecture/runtime libraries; its build
  verifier fails if the copied tools cannot run or are not JDK 17/Gradle 8.11.1.
- `Android.Dockerfile` takes your installed/published JVM profile digest as
  `DUA_JVM_BASE` and a separately prepared `ANDROID_SDK_IMAGE` digest. Override
  `ANDROID_SDK_SOURCE` only if the reviewed source image uses a different SDK
  directory. It copies that SDK into `/opt/android-sdk`, checks platform/build
  tool versions, and overrides the profile label. It does not fetch SDK packages
  or automatically accept SDK license prompts.

Use reviewed upstream distributions, for example [Gradle's official image source](https://github.com/gradle/docker-gradle)
and [Adoptium's OpenJDK container source](https://github.com/adoptium/containers).
Preserve their notices; Android SDK distribution/usage follows its own terms.
No third-party source/assets were copied by this implementation. Image build ARGs
are operator inputs; review/pin them **before** starting a build (the recipe's
argument-format check runs after Docker has resolved its FROM stages).

The `gradle-seed` default is empty apart from `.keep`, so project dependency
resolution will fail offline until an operator prepares a reviewed public-only
seed. Build/warm dependencies separately in an authorized preparation environment;
never copy an account home or engine configuration. Only public module artifacts/
metadata and optional Gradle wrapper distributions belong in the seed. Do not
include `gradle.properties`, init scripts, configuration caches, private repository
credentials, signing material, service accounts or API keys.

`verify_profile_image.py` runs only during an operator image build. It checks tool
versions, selected SDK paths, seed path/type/size limits (768 MiB JVM, 1536 MiB
Android), and normalizes seed permissions. It rejects links/special files, hard
links and unexpected cache directories. It is not a secret scanner or proof of
all dependency provenance/correctness; operators still review the public seed.
For a larger Android seed, prepare the JVM image with its smaller/empty seed first,
then use that pinned JVM base with a separately reviewed Android build context.
After a future build, publish/review/install the resulting digest on the dedicated
runner and separately verify a real job before setting profile configuration.
No such build, publish, install or activation was performed here.

## Verification status

Final Kotlin compilation, Python syntax parsing (including the image-build
verifier), shell syntax and staged diff formatting were checked. The existing tool
declaration assertion was updated; no tests, Docker/image builds, container runs,
SDK downloads, live runner/model calls, SQL migrations, manual workflow runs,
deployments or APK releases were performed at the owner's request.

Keep this draft until later verification covers profile/schema/digest tampering,
unknown/unavailable images, labels/volumes, offline cache misses, modes/exports,
OOM/setup/command deadlines, cancellation and cleanup, concurrency/headroom,
no-network/no-secret boundaries, Python compatibility, actual image builds and
Android device rendering. Parent coding phases must land first.
