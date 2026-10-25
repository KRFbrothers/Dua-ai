# Monorepo target structure

Status: all five stages are complete: documentation, database/tooling paths,
API/website paths, engine path and the Android Gradle workspace.
The database migrations are now under `infra/database/` and the root helper
scripts under `tooling/scripts/`. The Node API and website now live at `apps/api/`
and `apps/web/`; the Python engine now lives at `apps/ai-engine/`, including its
`coding-runner/`, tests and evals. Android's Gradle workspace is at `apps/android/`,
including `app/`, `benchmark/`, the wrapper, version catalog and root Gradle files.
See [ARCHITECTURE.md](../ARCHITECTURE.md) for the current runtime layout.

This document records completed and future migrations. Step 2 moves SQL files
and the APK verification helper without changing their contents, and updates
their documentation and workflow references. It does not move application code,
tests, service Dockerfiles or deployment scripts. Step 3 moves the API and website
with their tests and deployment files, and updates their path references. The
website builds into `apps/api/site/`, served by the existing `dua-api` service.
Step 4 moves the engine as one unit and updates paths, without reorganizing its
`agent_*.py` or `coding_*.py` modules. Step 5 moves the complete Android Gradle
workspace without changing module names, packages, signing or versioning. Do not create empty `packages/` or
`infra/cloudrun/` directories to match this diagram.

## Target tree

```text
Dua-ai/
├── apps/
│   ├── android/                 # Android Gradle workspace; moved in step 5
│   │   ├── app/                 # Existing :app module, all source/resources/tests
│   │   ├── benchmark/           # Existing :benchmark module and device tests
│   │   ├── gradle/              # Version catalog and wrapper files
│   │   ├── gradlew, gradlew.bat
│   │   ├── settings.gradle.kts
│   │   ├── build.gradle.kts
│   │   └── gradle.properties
│   ├── web/                     # Browser frontend; moved in step 3
│   ├── api/                     # Node dua-api; moved in step 3
│   └── ai-engine/               # Python dua-ai-engine; moved in step 4; includes coding-runner/
├── packages/                    # Future shared contracts only when actually needed
│   └── contracts/               # API/event schemas; create with a real consumer
├── infra/
│   ├── database/                # Migrations moved here in step 2
│   └── cloudrun/                # Future shared infrastructure; not created yet
├── tooling/
│   └── scripts/                 # Repository-wide development/build helper scripts
├── docs/
│   ├── README.md                # Documentation index
│   ├── architecture/            # Structure and architecture guidance
│   ├── decisions/               # Architecture decisions, when needed
│   ├── development/             # Development guides, when needed
│   ├── operations/              # Runbooks, when needed
│   └── audits/                  # Audit reports, when needed
├── third_party/                 # Vendored dependencies and license metadata
├── .github/
│   └── workflows/               # GitHub Actions; stays at its required location
├── README.md
├── AGENTS.md
├── SECURITY.md
└── LICENSE
```

This is a responsibility map, not a complete list of root files or an instruction
to move existing documentation. Existing guides keep their paths until a specific
migration updates their links and consumers. Native manifests, resources,
lockfiles and build configuration remain with their owning build workspace.

**Turborepo/Vercel is naming inspiration only. Deployment stays on Cloud Run;
Vercel is retired.** This plan does not adopt Vercel hosting, install Turborepo,
change runtime services or require a new JavaScript workspace manager. The naming
convention is illustrated in the [Turborepo repository guide](https://turborepo.dev/docs/crafting-your-repository/structuring-a-repository).

## Current → Target

| Current path | Target path | Responsibility |
| --- | --- | --- |
| `apps/api/` | `apps/api/` | Node backend, deployed as `dua-api`; moved from `web/` in step 3 |
| `apps/web/` | `apps/web/` | Browser frontend, built and served through `dua-api`; moved from `website/` in step 3 |
| `apps/ai-engine/` | `apps/ai-engine/` | Python engine, deployed as `dua-ai-engine`; moved from `google-cloud-backend/` in step 4 |
| `apps/android/` | `apps/android/` | Android Gradle workspace; moved with `app/`, `benchmark/`, wrapper and build files in step 5 |
| `infra/database/` | `infra/database/` | Supabase migrations; moved from the former root directory in step 2 |
| `tooling/scripts/` | `tooling/scripts/` | Repository helpers; moved from the former root directory in step 2 |

The Android workspace keeps the existing `:app` and `:benchmark` modules. Open
`apps/android/` in Android Studio. Run `./gradlew :app:testDebugUnitTest` from that
directory, or `bash apps/android/gradlew -p apps/android :app:testDebugUnitTest`
from the repository root. Shared backend fixtures and the website logo continue
to use the same Android-owned files through updated paths.

## Placement rules

- Put feature-specific code with its feature inside the owning application.
  Put app-wide technical foundations in that application's core/platform modules.
- Keep each application's assets, native resources, configuration and container
  build recipe with that application. Shared provisioning belongs in
  `infra/cloudrun/`; general repository helper scripts belong in `tooling/scripts/`.
- Keep unit, integration and Android instrumented tests with their owner, using
  native test conventions. A future component relocation must preserve its tests
  and fixtures together; do not delete tests or centralize them for neatness.
- Create `packages/contracts/` only when actual consumers need a shared API/event
  contract. Keep one authoritative schema and generate language-specific outputs
  where useful. Kotlin, JavaScript and Python do not automatically share source.
- Keep database migrations and their ordering under one authoritative database
  owner. A future path migration must preserve SQL contents and migration identity.
- Keep GitHub workflows under `.github/workflows/` and vendored dependencies and
  license notices under `third_party/`. Preserve existing required license paths.
- Give files descriptive homes. Do not use `misc/`, `_to_delete/` or an unbounded
  `utils/` folder as a dumping ground for code without an owner.
- Keep generated outputs and local caches out of tracked source unless an
  explicit build convention requires them. Never put secret values in docs,
  source files or shared contracts.

## Dependency rules

1. Applications do not import one another's implementation files. Communicate
   through declared HTTP APIs or events; do not use cross-app relative imports.
2. Applications may consume compatible shared contracts. Shared packages must
   not depend on application implementations or expose server-only credentials.
3. Feature modules may depend on their application's core/platform facilities;
   core/platform must not depend on feature implementations. Application assembly
   and navigation belong in the app entry layer.
4. Feature-to-feature dependencies use explicit public interfaces. Avoid circular
   dependencies and access to another feature's private storage or UI internals.
5. Declare dependencies in native package/build configuration. Folder names alone
   do not enforce boundaries; any future lint/build enforcement needs its own
   reviewed change and must keep existing checks passing.

## Migration order

Each numbered stage is one separate PR. Complete and validate a stage before
starting the next. This document authorizes no path migration by itself.

| Order | PR scope | Required review and validation |
| --- | --- | --- |
| 1. Docs — done | Record this target and link it from the docs index. | Verify links and the documentation-only diff. |
| 2. Infra/tooling paths — done | Move the seven database migrations to `infra/database/` and the repository helper to `tooling/scripts/`; update docs and the APK workflow invocation. Service-owned deployment files stay in place; no shared Cloud Run directory is needed yet. | Preserve file contents/modes and migration identity; verify references and unchanged application/test/build paths. No database apply or redeploy is required for this move. |
| 3. API / website — done | Moved `web/` to `apps/api/` and `website/` to `apps/web/` together, including tests and deployment files. | Updated frontend output (`apps/api/site/`), shared fixture/logo paths, CI, Dependabot and deployment/docs references. Keep Cloud Run service `dua-api`; run all four existing CI jobs. |
| 4. Engine — done | Moved `google-cloud-backend/` to `apps/ai-engine/` as one unit, including tests, evals and `coding-runner/`. | Updated CI, deployment/runner paths and documentation links; preserve module layout, Dockerfile/wsgi entrypoints and service `dua-ai-engine`. Run all four existing CI jobs. |
| 5. Android — done | Moved `app/`, `benchmark/`, `gradle/`, wrapper scripts and root Gradle files into `apps/android/` together. | Updated CI/personal APK cwd and report/input paths, Dependabot, signature-verifier certificate path and approved shared-fixture/logo consumers. Preserve all Android file contents, `com.dua.ai`, signing, resources, tests and versioning; run all four CI jobs. |

Step 2 updated only the APK helper path in the existing personal APK workflow.
Step 5 also updates the Android build cwd and APK source path; job names, signing
settings, the build-number rule and published release/artifact names stay unchanged. There is no
versioned Supabase CLI config to relocate; see the [CLI workspace note](../../SUPABASE.md#repository-paths-and-optional-cli-workspace).
Source-level setup messages now use `infra/database/migrations/` to locate the
referenced files; their path text was corrected before step 3.

The historical `apps/ai-engine/dua-adk-cloudshell.sh` bootstrap still fetches its
fixed reviewed commit (`cea9b6df188b8bd926bbf549419ef62e8e08643b`). Its internal
`google-cloud-backend/` paths refer to that snapshot, where the old directory
exists; they do not refer to the current checkout. Keep the pin and those paths
together. Normal current-checkout deployment uses `apps/ai-engine/`.

Future migration PRs must explicitly include any required workflow, Dockerfile or
build-path edits. Separate path moves from behavior changes,
keep CI job names stable, and follow the repository's protected-path rules.
Use [TESTING.md](../TESTING.md) for existing checks and
[OPERATIONS.md](../OPERATIONS.md) for deployment procedures.
