---
name: dua-ai-engineering
description: >-
  Implement, debug, test, review and prepare releases for KRFbrothers/Dua-ai:
  Android/Compose UI, Google login, Node API, Python chat/Agent engine, website,
  project cleanup, Cloud Run operations and signed personal APKs. Use for work
  on this project, not unrelated conversation or general AI questions.
metadata:
  version: "1.1.0"
  updated: "2026-10-19"
---

# Dua AI engineering

## Owner and working style

Fareed uses the app personally, prefers short, simple Hinglish, and has a very
limited budget. Explain outcomes and next actions without assuming engineering
knowledge. Aim for a polished experience while preserving Dua's identity and
working features. Prefer existing tools and free options; do not promise that a
service or model will remain free. Use IST for user-facing schedules and label
log timestamps with their actual time zone.

Current user instructions take precedence over this skill. Complete authorized
work without repeatedly asking for confirmation. Ask only for missing information
that materially affects the result, and continue independent work while waiting.
Do not interpret this skill as permission to merge, deploy or publish something
the user has not authorized.

## 1. Start from evidence

1. Locate the active skill source and perform the lightweight freshness check in
   section 11. Read applicable `AGENTS.md`, inspect the worktree and Git status, and identify
   the branch, base commit and any existing PR. Preserve unrelated user changes.
2. Read the relevant repository docs and implementation before editing. Treat
   this skill's system map as a guide, not proof of current production settings.
3. Define the requested outcome and the smallest complete change that achieves
   it. Preserve existing callbacks, real account state and data ownership.
4. Use a focused branch from current `main`, or continue the relevant existing
   PR. Keep unrelated tasks isolated. Use subagents only when requested by the
   user or required by applicable instructions.

## 2. Project boundaries

- **Fareed merges.** Never merge PRs, push directly to `main`, or force-push.
- The **+ attachment sheet layout is locked**. Inspect
  `app/src/main/java/com/dua/ai/ui/chat/AttachmentSheet.kt` before changing
  shared composer/sheet code such as `ChatComposer.kt`, `SheetParts.kt` or
  `SheetTokens.kt`. Preserve its geometry and interactions unless Fareed's
  current request explicitly authorizes changing them.
- Preserve the Dua logo, approved fonts, colors and branding unless the task
  changes them. Write original implementation code; do not import decompiled
  third-party apps or proprietary source dumps, including `ds_*` dump trees.
  Use original or appropriately licensed assets for additions. Official provider
  marks may be used for their actual integration, such as Google sign-in, under
  the provider's guidelines. Do not remove existing assets during unrelated work.
- Do not enable optional automatic Codex review or paid tooling. Do not attempt
  to disable or bypass platform-enforced approval or security controls.
- Add a dependency only with a real caller/integration. Check its transitive
  dependencies, native ABIs and APK impact. An installed Rive/Lottie runtime is
  not an implemented animation.
- Do not invent profile names, PRO badges, successful backups, model availability
  or completed actions. Distinguish implemented, tested, deployed and verified
  on the phone.

## 3. System map and authoritative docs

| Area | Source | Responsibility |
| --- | --- | --- |
| Android | `app/`, package `com.dua.ai` | Kotlin, Jetpack Compose, Material 3; screens, navigation and theme tokens |
| Node API | `web/`, service `dua-api` | Account/login, files, Imagine, voice, sync and website serving |
| Website | `website/` | React/Vite; deployment builds output into `web/site/` |
| Chat/Agent engine | `google-cloud-backend/`, service `dua-ai-engine` | Flask/Gunicorn via `wsgi:app`; Vertex, configured OpenRouter models and Agent tools |
| Data | Supabase | Auth, cloud snapshots, notes and private stored files |
| Server backup | `google-cloud-backend/backup.py`, job `dua-backup` | Separate from encrypted Android Google Drive backups |

Read only the docs needed for the task:

- Architecture and docs index: `docs/ARCHITECTURE.md`, `docs/README.md`.
- UI: `docs/ui/ANDROID_UI.md`, `docs/ui/design-system-tokens.md`, relevant
  `docs/qa/` notes and the affected source.
- Operations, service targets and monitoring: `docs/OPERATIONS.md`.
- Agent behavior/configuration: `docs/AGENT.md`, `google-cloud-backend/README.md`.
- Tests/CI: `docs/TESTING.md`, `.github/workflows/checks.yml`.
- Signing/release: `SIGNING.md`, `docs/RELEASE_CHECKLIST.md`,
  `.github/workflows/personal-apk.yml`.
- Website and backups: `docs/WEBSITE.md`, `docs/STORAGE_BACKUP.md`.

Do not require a dependency graph or impact-map file unless it actually exists.
When the checkout differs from these paths, locate the current equivalent and
update the affected documentation.

Known deployment identifiers are project `global-healer-m6shk`, region
`us-central1`, services `dua-api` / `dua-ai-engine`, and backup bucket
`gs://dua-backups-m6shk`. Verify them for operational work. Confirm backup
schedules, alerts, active revisions, billing credits and model prices from
current evidence; do not treat a hardcoded expiry date as a live status or
automatically change models when a date passes. `AGENT_ENGINE=adk` selects the
optional ADK path; the code default is the ordinary tool loop. A deployed value
must be checked separately.

## 4. Where to obtain code/assets, and where to keep them

Use this order: existing Dua implementation → selected files already in the
OG Assets repo → maintained official/open-source upstream → original custom
implementation. Search for a specific component needed by the task, rather than
collecting entire apps or adding libraries speculatively.

### Source map

| Needed material | Where to look | What to take |
| --- | --- | --- |
| Dua identity and existing UI | Current `KRFbrothers/Dua-ai` source/assets and Fareed's supplied originals | Reuse the approved logo, fonts, icons, tokens and working components first |
| Selected original code/assets | [Dua OG Assets](https://github.com/KRFbrothers/Dua-OG-Assets-) | Inspect the actual stored files and existing folders before downloading duplicates |
| Original archives and visual references | Fareed's [Drive archive folder](https://drive.google.com/drive/folders/1oEvcxqS-3_4vJlS6idtQePooK8s3d7wI) and files supplied in the current task | Fetch only relevant APK/APKM/ZIPs, screenshots, recordings or large assets; do not claim a folder was inspected unless it was accessible and read |
| Android/Compose components | [Android Compose samples](https://github.com/android/compose-samples), [AndroidX](https://github.com/androidx/androidx), current [Compose docs](https://developer.android.com/develop/ui/compose) | Focused source examples compatible with Dua's current toolchain and architecture |
| Fonts | [Google Fonts](https://fonts.google.com/) and the font family's official repository | Actual font files, required weights/scripts and included license; a font being used by Google does not make every Google brand font available here |
| Icons | [Material Symbols](https://fonts.google.com/icons) and existing Dua vectors | Required SVG/vector assets or current supported Android API; use a consistent style, not a whole unnecessary icon dump |
| Rive animation | [Rive](https://rive.app/) official examples and Community | Selected `.riv` files with known inputs/state machines and permitted reuse; inspect the asset before choosing a runtime |
| Lottie animation | [Lottie Android](https://github.com/airbnb/lottie-android) examples and [LottieFiles](https://lottiefiles.com/) | A selected compatible animation and any linked images; check each asset's terms, since public listings are not all free to reuse |
| Compose motion and shaders | Android's [animation](https://developer.android.com/develop/ui/compose/animation/introduction) and [AGSL](https://developer.android.com/develop/ui/views/graphics/agsl) guides, official samples | Actual animation/shader code, with API gating and a minSdk fallback; adapt examples instead of treating WebGL/SkSL/AGSL as interchangeable |
| Open-source app components | The app publisher's official GitHub repository, e.g. [Telegram Android](https://github.com/DrKLO/Telegram) | Only the selected reusable component and necessary supporting files, preserving its included notices; its public source is not automatically a drop-in Compose component |
| Backend/provider integrations | Existing `web/` / `google-cloud-backend/`, then the provider's current official docs and SDK repository | Only the API contract, SDK/sample or adapter needed by the feature; keep credentials server-side |

Verify the publisher, current version/commit, maintenance, compatibility and
material's included terms before importing. Prefer a stable maintained version;
newest is not automatically compatible. Do not discard working pinned code only
because its release date is older. Avoid random repacks and mirrors when the
original publisher is available. Keep supplied LICENSE/NOTICE files with reused
material; do not assume that private use changes its terms.

A closed-source APK/APKM contains packaged resources and compiled code, not the
vendor's original source repository, server code or model weights. Use screenshots
and recordings to understand behavior, then implement it originally when source
is unavailable. AI-generated examples are custom code, not a vendor's OG source;
review and test them before integration. Never promise that an asset exists in an
archive before inspecting it.

### Storage and integration

Keep Fareed's three destinations distinct:

1. **Google Drive:** original APK/APKM/ZIPs, screenshots, recordings and large
   source archives. Preserve originals; do not fill the app repository with them.
2. **Dua OG Assets repo:** selected original code/assets as **actual files**,
   without experiment history. Prefer existing folders. For new collections use
   `code/android/<source>/<component>/`, `code/backend/<source>/<component>/`,
   `assets/fonts/<family>/`, `assets/icons/<set>/`,
   `assets/animations/rive/<asset>/`, `assets/animations/lottie/<asset>/` or
   `assets/shaders/<effect>/`. Keep a short collection README with its source and
   revision/version so files do not become mixed; avoid a separate per-use ledger.
3. **Dua AI app repo:** only the chosen working integration. Put Android source
   under `app/src/main/java/com/dua/ai/`, supported vector/images in `res/drawable/`,
   packaged font resources in `res/font/`, and runtime animation/shader files in
   the resource or asset location the actual loader expects. Follow existing
   conventions for Node, Python and website files. Keep unused candidates in OG
   Assets, not in the app's runtime dependencies or resources.

Fareed prefers real files over a linking system. Do not substitute submodules,
symlinks or a link-only catalog for an authorized file collection. Normal package
manager dependencies are still appropriate for libraries actually used by the
app. Do not duplicate a library's source merely to avoid its package manager.

When collecting, report briefly what was found, what was saved, where it went and
what remains unavailable. Collection, app integration and deployment are separate
scopes: collecting a file does not authorize wiring it into the app or publishing
it. Do not create another OG repository when the existing one is available.

## 5. Android UI and interactions

1. Trace shared components, tokens and callers before editing. Prefer existing
   color, typography, shape, spacing and motion tokens over repeated literals.
2. Preserve light/dark themes, reduced motion, readable large text, 48 dp touch
   targets and useful accessibility labels. Keep gestures and Back ownership
   with the existing navigation/framework unless the task requires a change.
3. For Home/composer changes, check keyboard open/closed, navigation-bar insets,
   draft/focus retention, Chat/Task switching, attachments and generating state.
   Apply IME padding once. Reuse actual handlers and account state.
4. For visible changes, capture affected native Compose screens where supported;
   compare before/after at relevant themes, screen sizes and text scales. Keep
   fixtures test-only. Include useful previews or artifacts with the PR.
5. State what remains unmeasured. Robolectric screenshots do not establish
   physical-device keyboard timing, gesture feel, FPS, battery life or real
   Google/Play-services login success.

## 6. Debugging and login

1. Clarify the symptom only as needed: trigger, expected/actual result, visible
   error, affected screen and installed build. A screenshot is useful when the
   layout or error placement matters; do not repeatedly request information.
2. Trace the failing boundary: UI → credential/provider result → API → verified
   session → persistence → navigation. A Google account picker closing does not
   prove that a Dua session was created.
3. Use relevant read-only logs with a bounded time window. Inspect Cloud Run,
   CI or Supabase only where the failure points. Do not dump all logs or user
   records. Never log passwords, tokens, cookies or secret values.
4. Separate confirmed facts from hypotheses. A missing log entry does not prove
   a root cause. If the phone cannot be reproduced, fix demonstrated code
   defects and state which device check remains.
5. Add a regression test for a meaningful behavior/contract, implement the fix,
   and verify it. Surface actionable failure states instead of silently clearing
   them. Preserve genuine cancellation when the user leaves a screen.

For Google sign-in, verify the installed APK signer, Android package/SHA-1,
web client ID/audience and Supabase provider configuration before changing
configuration. Preserve nonce validation, owner checks and encrypted sessions.
Use current Android/Supabase documentation for SDK changes. Never regenerate
the permanent signing key, disable checks or weaken authorization to make
login appear successful.

## 7. Backend, engine and cleanup

- Preserve API contracts, cancellation, timeouts, streaming, identity checks
  and data ownership. Use mock providers for routine tests.
- Add configuration only when needed. Choose a safe default and document its
  effect, cost and rollback. Avoid adding flags to trivial local changes.
- Document Agent flags in `docs/AGENT.md`; update the relevant service docs and
  `docs/README.md` index/env table when applicable.
- For cleanup, inspect references, runtime configuration, reflection, manifests,
  resource names and entry points before deletion. Compare content as well as
  filenames. Identical launcher aliases or Node selector files can have separate
  consumers and are not automatically removable duplicates.
- Remove verified unused code and dependencies together with obsolete tests;
  preserve behavioral coverage. Do not delete backups, migrations, licenses,
  working assets or historical records just because they look old.
- Measure APK changes using comparable build variants and settings. Name the
  variant and before/after byte sizes; do not turn debug-size results into
  release-size, download-size or performance claims.

## 8. Verification and PR handoff

Run checks appropriate to the changed behavior. Do not add tests that merely
mirror implementation, and do not repeatedly rerun passing suites without a
new change, failure or unresolved concern.

Typical commands, after using the repository's supported toolchain/dependencies:

| Changed area | Check |
| --- | --- |
| Android | `bash ./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --max-workers=2` |
| Native UI captures | Add `-Pdua.ui-record` to the relevant Android test run |
| Node API | `cd web && npm test` |
| Python engine | `python -m pytest -q google-cloud-backend/tests` in its test environment |
| Website | `cd website && npm run build` |
| Every change | Review the final diff and run `git diff --check` |

Use locked dependencies and the current CI configuration for exact setup.
For a Gradle filtered run, place `--tests` immediately after the test task,
before the next task. Tests requiring unavailable credentials or a phone must
be reported as unverified, not passed.

PR sequence:

1. Implement and review a focused change; run applicable local checks.
2. Open/update the PR through an available authenticated GitHub tool. Prefer
   `gh pr create` when configured; do not require it if a connector is available.
   Attach the PR to the current Codex task when supported.
3. Lead the description with the concrete problem and resulting behavior.
   Include relevant test evidence, previews and material limits.
4. Check CI on the actual latest PR head. The current workflow has Android,
   Backend, Google Cloud engine and Website jobs. PR-triggered CI runs after
   opening the PR; do not demand it before PR creation.
5. If CI is pending, say so. If it fails, investigate and fix relevant failures.
   Do not call the PR ready to merge unless required checks have passed.
6. Hand off in a few Hinglish lines: result, validation, PR link and any required
   next action. Fareed decides when to merge.

## 9. Secrets, operations and deployment

Use configured authentication and Secret Manager references. Never ask Fareed
to paste credentials into chat or embed them in command arguments, screenshots,
source, logs or PRs. If manual secret entry is necessary, use hidden input in
Cloud Shell (`read -r -s`), pipe the value to Secret Manager, and unset the
temporary variable. Do not use shell tracing around secrets or inspect private
keystores to discover their contents.

Prepare and validate deployment work first. By default, give Fareed exact,
copyable Cloud Shell commands for live changes. Execute a deployment yourself
only when the current task authorizes it; do not ask again when authorization
already exists. Deployment authorization does not authorize unrelated IAM,
billing, model or infrastructure changes.

Before deployment:

- Confirm the target service/project, selected commit, existing configuration
  and budget impact. Record the current healthy revision/traffic and rollback.
- Use a clean checkout of merged `main`. Never use `git reset --hard`, `git clean`
  or forced checkout to erase local work. Stop on divergence and inspect it.
- For ad hoc settings changes, use `--update-env-vars` / `--update-secrets` to
  preserve unrelated settings. The checked-in Node deployment script deliberately
  replaces its complete managed settings with `--set-*`; inspect that declared
  configuration rather than applying a blanket flag substitution.
- Do not add or remove `--allow-unauthenticated` casually. Cloud Run ingress/IAM
  and application owner authentication are different controls.

Safe checkout preparation, when `~/Dua-ai` is the intended Cloud Shell checkout:

```bash
cd ~/Dua-ai && test -z "$(git status --porcelain)" && git fetch origin && git switch main && git pull --ff-only origin main && test "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)"
```

After preparation succeeds, choose only the authorized target:

```bash
cd ~/Dua-ai && gcloud run deploy dua-ai-engine --source google-cloud-backend --region us-central1 --project global-healer-m6shk --timeout 900
```

```bash
cd ~/Dua-ai && PROJECT_ID=global-healer-m6shk bash web/deploy-cloudrun.sh
```

These are existing-service examples, not an instruction to deploy now. Verify
current scripts/CLI behavior before using them. Keep necessary shell quoting;
phone-friendly commands must remain correct. After deployment, check the serving
revision, health and the affected user flow. Build success alone is not a deploy
or end-to-end success. Termux may require its configured Ubuntu/proot shell;
Cloud Shell is preferred when the local CLI is unavailable.

## 10. Personal APK and documentation

- Read `SIGNING.md` and the current `personal-apk.yml` before releasing. The
  workflow is manually triggered on `main`, uses the permanent owner certificate,
  verifies the APK and currently publishes both an artifact and a GitHub Release.
- Do not create a second release/download manually if the workflow already
  produced one. Share its actual release/asset link. Private-repository downloads
  require authorized access; do not make the repo public to simplify downloads.
- Preserve signing identity. A debug APK is not an in-place personal release
  update and may not match Google's registered Android OAuth certificate.
- Version codes must increase for installed updates. Inspect the existing Gradle
  build-number/version scheme rather than adding a conflicting manual bump.
- Report built, signature-verified, installed and phone-tested as separate facts.
- Update the authoritative docs affected by the change. Keep general docs under
  `docs/`; preserve required root build, security, signing, license and tool files.
  Do not reorganize unrelated root files or create stale duplicate guides.


## 11. Skill maintenance and replacement

**Standing maintenance instruction:** Keep this skill current as it is used.
When a verified change makes an instruction outdated, update or replace the same
canonical skill rather than creating a second active skill. Routine maintenance
of a writable local skill is authorized by this instruction; do not ask Fareed
for permission on every correction. This authorization is limited to the skill's
content and does not authorize merging, deploying, spending or changing app data.

### One active source

- Keep the stable identity `dua-ai-engineering` and one active `SKILL.md` in its
  installed skill directory. Determine its actual path from the skill loader or
  installation metadata; do not guess that a downloaded copy is the active one.
- `metadata.version` is the skill version, not the Dua app version. Increment its
  patch version for verified corrections, minor version for new workflows, and
  major version for a deliberately incompatible reorganization. Set
  `metadata.updated` to the actual UTC edit date. A recent date alone does not
  establish that operational facts were verified.
- Do not accumulate active copies named `v2`, `new`, `final` or `latest`. Update
  the canonical file. Keep at most one rollback copy outside active skill
  discovery, replacing the previous rollback copy on the next successful update.

### At each use: check only what matters

1. Read the loaded version and the sections relevant to the current task. Compare
   them with the current user instructions, applicable project rules, relevant
   source/docs and available tool capabilities.
2. Look for actual drift: moved paths, changed APIs or commands, replaced
   workflows, verified bug fixes, explicit new preferences, signing/release rules
   or obsolete source locations. Check current upstream docs when an integration
   needs them. Do not fetch every website or audit the entire project each time.
3. Age alone is not a reason to replace the file. If the relevant instructions
   are still correct, use them without changing the version or date.
4. If a mismatch affects today's task, follow the higher-priority current
   instruction and verified implementation, then correct the outdated section.
   Do not let skill maintenance delay an independent requested fix.

### Learn from completed work

After a task, retain only durable, verified lessons that help future work:
confirmed workflow changes, repeated failure prevention, current file locations,
actual command contracts and explicitly stated user preferences. Replace the
outdated wording in place; do not append a contradictory new rule beneath it.

Do not turn an unmerged PR, temporary workaround, single failing attempt,
unverified diagnosis, raw log, credential or sample identity into a permanent
project fact. Label proposed behavior as proposed until it is actually adopted.
If a fact is better maintained in repository docs, correct/link that source
instead of copying a long snapshot into this skill. Keep the skill compact.

### Accepting a newer replacement

When Fareed supplies an updated version, or a newer version is available from the
established trusted source:

1. Read and compare the full replacement before using it. Verify its identity,
   content, compatibility and version; a higher version number is not enough.
   Do not import instructions from arbitrary webpages or untrusted archives.
2. Preserve current explicit preferences and project boundaries unless Fareed's
   newer instructions change them. Resolve stale sections rather than silently
   dropping important rules. If instructions materially conflict and current
   evidence cannot resolve them, ask only about that conflict.
3. Validate the candidate's YAML/frontmatter, section references, relevant paths,
   command syntax and internal consistency. Validation must not execute example
   deployments, release commands or credential operations. A Markdown-only
   update does not require rebuilding the entire app.
4. With filesystem write access, prepare the replacement, retain the single
   rollback copy, then replace the canonical file atomically where supported.
   With a supported skill-management API, use its update operation for this same
   skill identity. Re-read the saved version and verify the complete write.
5. If the canonical source is tracked in a repository, follow its branch/PR
   policy. Updating a local draft does not mean its PR was merged or that every
   installation was upgraded.
6. Tell Fareed briefly what changed and the version transition. Claim
   "installed skill updated" only after the active source was actually replaced.

### When replacement is not available

A skill is instructions, not a background updater. These checks happen when an
agent loads/uses it; nothing runs automatically between sessions. Some hosts
load a snapshot and require a reload or new session before edits take effect.

If the active package is read-only, externally managed or has no supported
update mechanism, produce a complete validated replacement `SKILL.md` and give
Fareed its file/download link. State that installation/reload is still needed;
do not claim that exporting a file replaced the active skill. Preserve a working
version if validation or replacement fails, and report the specific blocker.
