# Monorepo target structure

Status: step 1 (documentation) and step 2 (database/tooling paths) are complete.
The database migrations are now under `infra/database/` and the root helper
scripts under `tooling/scripts/`. Applications remain at `app/`, `web/`,
`website/` and `google-cloud-backend/`; Android Gradle roots stay at the repository
root. See [ARCHITECTURE.md](../ARCHITECTURE.md) for the current runtime layout.

This document records completed and future migrations. Step 2 moves SQL files
and the APK verification helper without changing their contents, and updates
their documentation and workflow references. It does not move application code,
tests, service Dockerfiles or deployment scripts. Do not create empty `apps/`,
`packages/` or `infra/cloudrun/` directories to match this diagram.

## Target tree

```text
Dua-ai/
├── apps/
│   ├── android/                 # Android application and its build workspace
│   ├── web/                     # Browser frontend, currently website/
│   ├── api/                     # Node dua-api, currently web/
│   └── ai-engine/               # Python dua-ai-engine, currently google-cloud-backend/
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
| `web/` | `apps/api/` | Node backend, deployed as `dua-api` |
| `website/` | `apps/web/` | Browser frontend, built and served through `dua-api` |
| `google-cloud-backend/` | `apps/ai-engine/` | Python engine, deployed as `dua-ai-engine` |
| `app/` | `apps/android/` | Android application; coordinate its Gradle workspace in the final migration |
| `infra/database/` | `infra/database/` | Supabase migrations; moved from the former root directory in step 2 |
| `tooling/scripts/` | `tooling/scripts/` | Repository helpers; moved from the former root directory in step 2 |

The Android row identifies the destination responsibility, not a standalone
`mv app apps/android` command. The Android migration must account for the root
Gradle files, wrapper, version catalog, benchmark module and all build consumers
together. Their current locations remain unchanged after step 2.

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
| 3. API / website | Move `web/` to `apps/api/` and `website/` to `apps/web/` together. | Update frontend build output, API static serving, imports, workspace/build and deployment references atomically. Keep tests with their owners and run backend tests plus the website build. |
| 4. Engine | Move `google-cloud-backend/` to `apps/ai-engine/`. | Update engine import/build/deployment paths and dependent references; preserve runtime behavior and run engine tests. |
| 5. Android | Migrate the Android application and coordinated Gradle workspace to `apps/android/`. | Review wrapper, Gradle roots, modules, benchmark, signing-file references and CI paths together. Preserve `com.dua.ai`, resources and tests; run Android build/tests and relevant lint/signing checks. |

Step 2 updates only the APK helper path in the existing personal APK workflow;
its job names, signing settings and build paths stay unchanged. There is no
versioned Supabase CLI config to relocate; see the [CLI workspace note](../../SUPABASE.md#repository-paths-and-optional-cli-workspace).
Source-level setup messages retain some former migration paths until their
owning source is separately updated; these are informational strings, not file
loads. Use `infra/database/migrations/` to find the referenced files.

Future migration PRs must explicitly include any required workflow, Dockerfile or
build-path edits. Separate path moves from behavior changes,
keep CI job names stable, and follow the repository's protected-path rules.
Use [TESTING.md](../TESTING.md) for existing checks and
[OPERATIONS.md](../OPERATIONS.md) for deployment procedures.
