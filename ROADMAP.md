# Dua AI — Product Roadmap

This roadmap takes Dua from a feature-rich prototype to a clean, professional, Grok-style AI assistant for its owner's personal use. Phases 1–5 are implemented in the repository (PRs #66–#75); live provider and physical-device checks remain open. The original pre-Phase-1 gap analysis (`docs/grok-style-plan.md`) described code that has since been replaced and was removed; it remains in git history.

## Product direction

Dua is becoming a **Grok-style assistant**: one clean, professional Android app backed by a server-side API on Vercel. The web client was removed; `web/` contains only the backend, and provider API keys never leave the server.

**Personal, single-user app.** Dua is for the owner (Fareed) only. There are no clients, no other users, no Google Play release and no monetisation. The app is installed on his own device(s) as a debug or sideloaded APK (see [RELEASE-CHECKLIST.md](RELEASE-CHECKLIST.md#personal-install)). Owner-only access (`DUA_REQUIRE_LOGIN=1` plus `DUA_ALLOWED_EMAIL` set to his account) is the permanent access model, not a step before a launch: it keeps the server-side keys and the provider spend his alone.

**Target experience**

- **Chat that feels modern** — streamed answers, readable markdown and code, and a **Reasoning** mode with collapsible thinking
- **Long memory** — conversation length limited by the model's context, not by Dua
- **Live search** — model-driven web search plus real-time X search (when xAI is configured), shown as source chips
- **Files that stay in the conversation** — follow-up questions about a PDF, spreadsheet or code file work
- **Imagine** — image generation and editing, then short video
- **Voice** — selectable voices and languages, then realtime hands-free conversation you can interrupt
- **Coding** — hosted code execution, then a repo-aware **Build** mode that writes nothing without approval

**Still core to Dua** (unchanged from the original vision)

- **Local-first** — conversations, drafts and history live on the device by default
- **Multiple AI providers** — Gemini, ChatGPT, Claude, Grok and Kimi today; the final lineup is an [open decision](#open-decisions)
- **Privacy and data control** — local export, import and delete; the Supabase account and cloud sync stay optional and opt-in, and exist to back up and sync his own devices

The composer will offer one mode menu: **Auto** (default) · **Reasoning** · **Search** · **Imagine**, with **Build** added in Phase 5. A mode that is not configured explains why instead of disappearing.

**What personal use simplifies**

- Owner sign-in can be mandatory for every file, Imagine and voice feature; there is no anonymous or guest path to design.
- Cost controls protect one wallet: daily per-feature caps plus provider-side hard limits. No per-user quotas or plan tiers.
- Storage retention (files, generated media, cloud snapshots) is the owner's choice.
- Distribution is an APK on his own phone: no store listing, review, billing or app-store compliance work.

**Out of scope:** Google Play release and listing, Play Billing or paid tiers, public launch, multi-user accounts and quotas, team workspaces, onboarding for new users, and content-policy work aimed at other users.

## Feature status

Effort scale (one developer working with coding agents): **S** ≤ 2 days, **M** ≈ 1 week, **L** ≈ 2–3 weeks, **XL** > 1 month.

| Feature | Today | Target | Effort | Phase |
| --- | --- | --- | --- | --- |
| UI and navigation | Design tokens (DuaTheme, Inter type scale), Navigation Compose, full-width markdown replies with code blocks, new composer with mode chips, history drawer, regrouped settings, adaptive icon | Design tokens, Navigation Compose, full-width assistant replies, history drawer, regrouped settings, app icon and splash | L | 1 |
| Chat | Works with 6 provider choices; streaming replies (SSE, opt-in) with env-configurable output caps (8k–32k defaults) and a 280 s deadline; markdown | Opt-in streaming (SSE), markdown and code blocks, higher output caps | M | 1 |
| Reasoning mode | Reasoning chip with a collapsible "Thought for N s" block (Gemini, Grok, ChatGPT); live provider checks pending | Mode switch with a collapsible "Thought for N s" block (Gemini `thinkingLevel`, xAI `reasoning.effort`, OpenAI Responses reasoning) | M | 1 |
| Long memory / context | Per-provider context budget with a rolling summary cached with the chat; xAI prompt caching; local notes up to 10 × 200 chars | Per-provider token/byte budget, rolling conversation summary, xAI prompt caching; later, model-proposed notes the user approves | M | 2 |
| Web search | Provider-native search (Gemini Google Search, OpenAI/xAI `web_search`) with source chips; Brave for Claude/Kimi and as fallback. Live checks pending | Provider built-in search tools with Brave as fallback, structured sources as chips, Auto mode | M | 2 |
| X search | Grok X toggle (`x_search`, off by default). Live checks pending | xAI `x_search` in Search mode, X post chips | S–M after web search | 2 |
| Files | Inline JPEG, PNG, WebP, PDF, UTF-8 text/code (256 KB each; 3 files, 2 MB total) plus owner-only stored files up to 50 MB in a private Supabase bucket (PDF, spreadsheets, text, images, audio with transcription), re-attached on follow-ups. DOCX pending | Quick win: CSV, MD, JSON and code as text. Then Supabase Storage uploads kept in history; XLSX, DOCX, audio | S + L | 1 (quick win), 2 |
| Image generation and editing | Imagine chip: Grok Imagine or Gemini (Nano Banana), 1–4 images, aspect ratios, edits from a photo; grid, fullscreen viewer, save, share, "Edit"; durable daily cap. Live provider/device checks pending | `/api/imagine`, image messages, viewer, save and share, daily spend caps | M | 3 |
| Video | Async Grok Imagine jobs with polling that survives leaving the app; 480p/720p, 1080p gated; MP4 stored in `dua-files`; Media3 playback; durable daily seconds cap. Live checks pending | Async jobs with polling; 480p/720p default, 1080p gated | M–L | 3 |
| Voices and languages | Settings › Voice: device / Grok / Gemini / ElevenLabs reply voice, voice picker with samples, speed, reply and recognition language; chunked playback with an in-memory cache. Live checks pending | Voice and language pickers (device plus xAI or Gemini TTS) | S–M | 4 |
| Realtime voice | Live voice over xAI realtime (server-minted client secret, barge-in, microphone foreground service, daily minute cap); half-duplex hands-free as fallback. Live/device checks pending | Speech-to-speech over WebSocket, interrupt by talking | L | 4 |
| Code execution | Run code chip: Gemini `code_execution` (default on, no extra charge) with collapsible "Ran code" blocks (code, output, copy), kept in local history; ChatGPT/Grok code interpreters opt-in. Live provider check pending | Provider-hosted sandbox tool with collapsible code and output | S–M | 5 |
| Build mode | Repo-aware Build chat (read tools, diff answers, "files read" chips) and pull requests after explicit approval: review card, owner-confirmed Create PR, new `dua/…` branch + commit + draft PR with a separate write token. Live check pending | Commands via delegation only | M · L · XL | 5 |

## Current Status

The repository contains the implementation described below, but this checkout cannot inspect Vercel or Supabase production settings or verify live external services. “Configured” therefore means the required settings are present in the active deployment, not merely supported by code. Do not treat a mocked test as live verification.

| Feature | Implemented in repository | Configured in production | Verified |
| --- | --- | --- | --- |
| Android app and backend API | Yes (Android-only client; web client removed) | Backend deployed on Vercel; settings unknown here | Source/tests; physical-device behavior pending |
| Local chat history, drafts and search | Yes | N/A | Automated tests; Android backup/restart checks pending |
| Attachments and photo resizing | Yes | Provider/model capabilities are environment-dependent; production unknown | Automated/mock tests; live providers and physical Android pending |
| Camera and photo selection | Yes | Device permission is user-controlled | Automated coverage; physical-device checks pending |
| Voice input, read-aloud and hands-free | Yes, with voice/language pickers and Grok/Gemini/ElevenLabs reply voices | Speech service is device-dependent; AI voices need `GROK_API_KEY`/`GEMINI_API_KEY` and the owner lock | Automated/mock tests; speech/audio behavior on devices pending |
| Accounts and profile | Yes; native Google sign-in only (no email/password); owner-locked by `DUA_ALLOWED_EMAIL` | Supabase Google provider and OAuth settings are production-controlled; unknown here | Mocked tests; owner verifies live Google sign-in on his phone |
| Cloud snapshots and foreground sync | Yes, reviewed manual and opt-in foreground flows | Supabase migration/environment and per-device consent are required; production unknown | Automated and migration tests; live cross-device checks pending |
| Gemini and optional AI providers | Adapters and provider selection are implemented | API keys/model IDs are production-controlled; unknown here | Mocked responses; live provider/model checks pending |
| Web search and link reading | Yes | Brave key needed for Search; production status unknown | Mocked tests; live retrieval/provider checks pending |
| Local memory and reviewed proposals | Yes | Local opt-in; no provider setup needed for notes | Automated tests; live prompt behavior pending |
| GitHub connector | Read-only: repository list, file tree, file contents and code search | Token, owner restriction and feature flag are required; production unknown | Mocked tests; live authorization pending |
| Build mode (repo chat + approved PRs) | Yes: Gemini function calling over the read-only connector, diffs, files-read chips; proposals and owner-approved pull requests when `DUA_BUILD_PR=1` and `GITHUB_BUILD_WRITE_TOKEN` are set | Needs the GitHub connector, `DUA_ALLOWED_EMAIL`, sign-in and Gemini (`DUA_BUILD=0` turns it off). Pull requests also need the separate write token; production unknown | Mocked GitHub/Gemini tests and Roborazzi screenshots; live Gemini/GitHub, live write-API and physical-device checks pending |
| Appearance and local notifications | Yes | Notification permission is user-controlled | Automated tests; physical delivery/rendering checks pending |
| Data controls and sharing | Local export/import/delete and reviewed cloud clearing are implemented | Cloud controls require Supabase; production unknown | Automated tests; physical file-picker checks pending |
| Run code (code execution) | Yes: Gemini `code_execution`, opt-in OpenAI/xAI `code_interpreter` | Needs only the Gemini key (`DUA_CODE_EXECUTION=0` turns it off); production unknown | Mocked provider tests and Roborazzi screenshots; live Gemini and physical-device checks pending |
| Imagine (images and video) | Yes | Migration, `GROK_API_KEY`/`GEMINI_API_KEY` and caps are production-controlled; unknown here | Mocked provider/Supabase tests and a local smoke script; live generation and device checks pending |
| Login/owner/quota protection | Optional controls and durable daily request quota are implemented | Vercel variables, service key and usage migration are needed; production unknown | Mocked tests; production enforcement not verified |
| Personal install (APK) | Android source builds; a debug APK needs no signing setup; release signing with a personal key is optional | Not verified here | CI runs JVM tests, not an APK; install on the owner's phone pending |

---

## Roadmap Phases

### Phase 0: Configuration checks
**Priority: Immediate | Effort: about 1 hour, owner/deployer only, no code**

Confirm production can support the plan and is not already failing.

**Work:**
- Vercel: confirm Fluid compute is on and check the plan's function duration (Hobby: 300 s max; Pro: 800 s max). `web/vercel.json` sets `maxDuration` to 300 s and the chat deadline defaults to 280 s (`DUA_CHAT_DEADLINE_SECONDS`).
- Check `GEMINI_MODEL`: `gemini-2.0-flash` is shut down and 2.5 models are limited to existing users, so an old ID may already be failing. Recommended: `gemini-3.8-flash`.
- Decide which provider keys to fund ([Open decisions](#open-decisions) 1). If xAI is chosen, set `GROK_API_KEY` and `GROK_MODEL=grok-4.7`.
- Confirm the owner-only lock (`DUA_REQUIRE_LOGIN=1`, `DUA_ALLOWED_EMAIL` = owner) and set provider spending limits using [RELEASE-CHECKLIST.md](RELEASE-CHECKLIST.md).

**Success:**
- Function duration and Gemini model ID are known and recorded; no retired model is in use
- Keys needed for Phase 1 are configured; production accepts only the owner's account and every funded provider has a spending limit

---

### Phase 1: UI foundation and Reasoning mode
**Priority: High | Effort: L + M (two PRs) | Depends on: Phase 0**

Highest impact, lowest risk: make Dua look professional, then add streaming and reasoning. Absorbs the former *Core Product Stability* and *Design and UX Polish* phases.

**PR 1 — UI foundation (Android only, no API change, L):**
- `ui/theme` tokens: brand colors (light/dark), one type scale, shapes, 4-dp spacing — replacing template purples and ad-hoc font sizes
- Split `ChatScreen.kt` into focused components and move to Navigation Compose
- Markdown and code-block rendering with copy; full-width assistant replies, user messages as bubbles
- New composer with mode chips (Auto and Search map to today's web modes; Reasoning stays hidden until PR 2), attachment bottom sheet with thumbnail chips, source chips from existing reply links
- Empty state with suggestions, history drawer with date grouping, settings regrouped into Account · Personalization · Data & privacy · Connections · About (Diagnostics moves under About)
- Remove "coming soon" placeholders and duplicate entries (Computer mode / Plugins / Capabilities); add adaptive icon and splash screen; inline error cards; one UI language, moved to `strings.xml`
- Keep stop, retry, draft recovery and sync working and every JVM test passing; move code in one commit, redesign in a later one

**PR 2 — Reasoning mode, streaming, output limits, text files (backend + Android, M):** *Status: implemented in PR "Reasoning mode and streaming replies" — snapshots stay version 1 with optional `thinking` fields; text files are 256 KB each; Grok WebP is converted to JPEG on the device. Live provider and device checks remain open.*
- `/api/chat` accepts `mode: "auto" | "reasoning"` and opt-in `stream: true` (SSE); without the flag the JSON response is unchanged, so older APKs keep working
- Gemini `thinkingConfig {thinkingLevel, includeThoughts: true}`; Grok moves from the legacy `/v1/chat/completions` to `/v1/responses` with `reasoning.effort` and streamed reasoning summaries; OpenAI Responses reasoning effort; Claude and Kimi unchanged
- Raise output caps from 2048 to 8k or more; make the deadline env-configurable to fit the real Vercel `maxDuration` (`vercel.json`); Android read timeout becomes an idle gap with server heartbeats
- Store `ChatMessage.thinking` in history, cloud snapshots and export with a backward-compatible version bump; collapsible ThinkingBlock UI
- Accept CSV, MD, JSON and source-code text inline (about 512 KB each, total ≤ 3 MB raw)
- Drop WebP from Grok's allowed image types (or convert to JPEG)
- Backend tests: `node:test` coverage of each adapter's stream parsing and a CI job for `web/`

**Carried over (stability and provider validation):**
- Fix edge cases in sending, retries and state recovery; validate local persistence on real Android devices; reduce crash and data-loss paths
- Test each configured provider live: credentials, model compatibility, attachments by provider; clear messages for unsupported or failed requests; retry and fallback behavior

**Success:**
- Zero critical data-loss bugs; stable sending, stop and retry behavior
- Screens feel visually consistent and premium on the owner's devices; settings are easy to find
- Long answers stream and are not truncated; reasoning appears as a collapsible block
- Older APKs still work against the new backend; backend adapter tests run in CI
- Each provider can be activated and tested safely, and provider failures never break chat

**Risks:** PR 1 is a large diff. Gemini labels `generateContent` "Legacy", so keep the adapter thin. Reasoning tokens are billed as output. xAI returns reasoning summaries, not raw thoughts.

---

### Phase 2: Files, search and context
**Priority: High | Effort: L + M (+ M for context) | Depends on: Phase 1 streaming and timeouts; Supabase deployed**

Files stay in the conversation, search becomes model-driven with real sources, and long chats are limited by the model, not Dua. Absorbs the former *Cloud, Identity and Sync* phase.

**Work:**
- **Files v2:** private Supabase Storage bucket with row-level security, migration and `POST /api/files/sign`; Android uploads straight to Supabase (resumable above 6 MB), bypassing Vercel's 4.5 MB body limit; `fileRefs` stay in history so follow-ups work; providers receive short-lived signed URLs; XLSX→CSV on the server, DOCX, audio via speech-to-text
- **Search v2:** provider built-in tools (xAI `web_search` + `x_search`, Gemini `google_search` + `url_context`, OpenAI `web_search`); structured `sources[]` rendered as chips (X posts show @handle and date); Brave stays as fallback and is verified live together with link reading
- **Context:** replace the 39-message limit with a per-provider token/byte budget; rolling conversation summary; xAI prompt caching keyed by chat; later, model-proposed memory notes the user approves
- **Cloud and identity (carried over):** live-verify the owner's Supabase sign-in/logout, cloud snapshots and foreground sync across his own devices; keep ownership checks and the owner-only lock enforced; device and session controls
- Update PRIVACY.md (it currently promises file bytes are never stored) and data controls to cover stored files

**Success:**
- Follow-up questions about an attached file work across turns; the owner can see and delete stored files
- Search answers cite structured sources; X search works when xAI is configured
- Secure, owner-controlled sync works across his devices; ownership and data protections are enforced
- It is clear what is local and what is in the cloud

---

### Phase 3: Imagine (image, then video)
**Priority: Medium | Effort: M, then M–L | Depends on: Phase 2 storage; per-feature spend caps**

**Work:**
- `POST /api/imagine` for generation and editing (xAI `grok-imagine-image-2.0` or Gemini Nano Banana 2); provider URLs are temporary, so results are copied to Supabase Storage (no base64 through Vercel's 4.5 MB response limit)
- Android: image message, full-screen viewer, save to gallery, share, "Edit this image"
- Then async video: submit returns a request id and the app polls, so no function runs long; `grok-imagine-video-1.5`, 480p/720p by default, 1080p gated; MP4 copied to Storage; Media3/ExoPlayer playback
- Owner sign-in required; daily image and video caps

*Status: implemented in PR "Phase 3: Imagine (images and video)" — migration `202610090001_dua_imagine.sql` (video/mp4 + caps RPCs), `/api/imagine/{image,video,status,usage}`, Android Imagine chip, options sheet, image grid, viewer, video card and job resume, Settings › Imagine. Gemini video (Veo) deferred. Live provider and physical-device checks remain open.*

**Success:**
- Generated media survives provider URL expiry and can be saved and shared
- Daily caps keep image and video spend within the owner's budget

---

### Phase 4: Voice v2
**Priority: Medium | Effort: S–M, then L | Depends on: Phase 1 UI; voice provider decision**

Absorbs former Phase 6.5 (generated reply voice).

**Work:**
- Language picker for speech recognition and TTS, device voice list, and a server `voice` parameter for xAI TTS or Gemini `gemini-3.8-flash-tts` (alongside or instead of ElevenLabs); remove voice-screen placeholders
- Then realtime speech-to-speech: the server mints ephemeral tokens (`/v1/realtime/client_secrets`) so the key never reaches the phone; WebSocket audio with echo cancellation and server-side turn detection; interrupt by talking; microphone foreground service on Android 14+ for background use

*Status (4A): implemented in PR "Phase 4A: Voice v2" — `/api/voice/audio` takes `provider`, `voice`, `language`, `speed` (xAI `/v1/tts`, Gemini `gemini-3.8-flash-tts`, ElevenLabs), `/api/config.voice` feature flag, Settings › Voice with voice picker and samples, reply and recognition language, chunked prefetching playback and an in-memory cache, markdown-free speech. Live provider and physical-device checks remain open.*

*Status (4B): implemented in PR "Phase 4B: live voice" — `/api/voice/session` mints xAI realtime client secrets after reserving minutes in Postgres (`DUA_VOICE_DAILY_MINUTES`, default 30, migration `202610100001_dua_voice.sql`), `/api/voice/session-end` and `/api/voice/usage`; full-screen live voice with orb, mute, end, live captions and barge-in, microphone foreground service, Bluetooth routing, client-side session timer, transcript saved to the chat, `DUA_VOICE_REALTIME=0` falls back to hands-free. Live xAI sessions and physical-device audio (echo, Bluetooth, background) are not yet verified.*

**Success:**
- The owner can choose voice and language; a realtime session can be interrupted by speaking
- Voice minutes are covered by daily caps; behavior is verified on the owner's phone

---

### Phase 5: Coding and Build mode
**Priority: Medium | Effort: S–M, M, L, then XL | Depends on: Phase 2 files; connector security review**

Absorbs the former connector, coding-agent and computer-mode items.

**Work:**
- Provider-hosted code execution (xAI `code_interpreter`, Gemini `code_execution`, OpenAI `code_interpreter`) with collapsible code and output; pairs with CSV/XLSX files
- Repo-aware Build chat: the GitHub connector gains read actions (tree, file) exposed as function-calling tools in a chat tied to one repository; answers come back as diffs
- Approved changes: the backend creates a branch, commit and PR with a narrowly scoped token only after the user taps "Create PR"
- Running commands only through delegation (GitHub Copilot coding agent or Vercel Sandbox) — never on the phone or inside a Vercel function

*Status (5A): implemented in PR "Phase 5A: Run code" — `"code": true` on `/api/chat` adds Gemini `code_execution` (default on, `DUA_CODE_EXECUTION=0` off) or, opt-in, OpenAI/xAI `code_interpreter`; new `code` stream event and JSON `code[]`; `/api/config.code`; Android Run code chip and collapsible "Ran code" blocks saved in local history. Not automatic in Reasoning mode (Google warns code execution can degrade other output). Charts drawn by code are not shown yet. Live Gemini and physical-device checks remain open.*

*Status (5B, 5C): read-only Build chat implemented in PR "Phase 5B: read-only Build mode" (`mode: "build"` + `repository` on `/api/chat`, owner-only Gemini function-calling loop over `list_files`, `read_file`, `search_code`; GitHub GET requests only; bounded rounds and bytes; "Read N files" chips and coloured diffs). **Approved pull requests implemented in PR "Build mode: create a PR after approval" (owner approved this step):** the model proposes changes with `propose_changes` (validation only, no write path); the app shows a review card and only the owner's confirmed Create PR tap calls `POST /api/build/pr`, which checks paths, sizes and stale files and then creates a new `dua/…` branch, a commit (Git Data API) and a draft pull request with a separate fine-grained write token (`GITHUB_BUILD_WRITE_TOKEN`, off unless `DUA_BUILD_PR=1`). Dua never merges, force-pushes or deletes branches, and workflow files are rejected. **Still deferred, needs the owner's decision:** running commands, only through delegation (GitHub Copilot coding agent or Vercel Sandbox), never on the phone or in a Vercel function.*

**Success:**
- Nothing is written to a repository without explicit user approval
- Connector tokens are narrowly scoped and stay server-side
- Advanced features are gated behind clear setup flows and platform-specific complexity is well-tested

---

### Cross-cutting: privacy, security and cost
**Applies to every phase.** Absorbs the former *Privacy, Security and Trust* phase.

- Keep the owner-only lock (`DUA_REQUIRE_LOGIN=1`, `DUA_ALLOWED_EMAIL`) on every deployment; it is permanent and protects the keys and spend
- Move `access-policy.mjs` from a daily request count to **per-feature spend caps** (image, video, voice minutes, X posts) before enabling any paid media feature, alongside provider-side hard limits, so a bug or leaked session cannot run up the owner's bill
- Keep API keys server-side; give the phone only short-lived tokens or signed URLs
- Document retention for local data, cloud sync, stored files and generated media (periods are the owner's choice); keep deletion and export transparent
- Stronger access checks for connectors and integrations
- Update PRIVACY.md, FEATURES.md and PROVIDERS.md in the same PR as the behavior they describe

**Success:**
- It is clear what is local vs. cloud
- Data deletion and export flows are reliable and consistent
- No credentials are exposed in the client or GitHub

---

### Deferred

Former Phase 6 items not in the current plan: plugin system, Google Drive and other connectors, and screen capture. Revisit after Phase 5. Team workspaces and collaboration are dropped: Dua is single-user.

---

## Open decisions

Only the owner can make these.

Already settled: Dua is personal and owner-only, with no paid tiers and no store release; owner sign-in is required for files, Imagine and voice.

1. **Providers to fund** — an xAI API key (needed for X search, Grok Imagine and Grok voice; billed separately from SuperGrok)? Gemini as the default "Dua" brain, Grok, or both? Keep ChatGPT, Claude and Kimi, or simplify to two providers?
2. **Budget and caps** — monthly personal budget and daily per-feature caps. Reference prices at time of writing (Oct 2026): `grok-4.7` $2/$6 per 1M tokens; images $0.02–0.05 each; video $0.02–0.08 per second; X search $5 per 1k posts; realtime voice $0.08/min.
3. **Vercel and Supabase plans** — Hobby caps functions at 300 s, Pro at 800 s; Supabase Free caps files at 50 MB.
4. **File retention** — store uploads in Supabase Storage, and for how long (e.g. keep, or auto-delete after 30 days)?
5. **Thinking display** — show reasoning summaries collapsed by default?
6. **Brand and design** — Figma file, palette, typeface, icon and logo; dark-first like Grok; keep the cube motif?
7. **Language** — English-only UI, or Urdu/Hindi too (Urdu needs right-to-left layout)?
8. **Search** — keep Brave as a fallback (another key and bill) or rely on provider search?
9. **Imagine media** — should generated images and video sync to the cloud? Provider usage policies still apply to what is generated.
10. **Build mode scope** — read-only repo chat (Phase 5B) and pull requests after approval (Phase 5C, approved) are done. Still open: is handing off to the Copilot coding agent or Vercel Sandbox acceptable for running commands?
11. **Voice** — keep device voice as the free default? Which paid provider (xAI, Gemini or ElevenLabs)? Is $0.08/min realtime voice acceptable?

---

## GitHub Tracking

Phase scope and success criteria live in this file. GitHub tracking status:

| Phase | Epic issue | Status |
| --- | --- | --- |
| Phases 0–5 (this plan) | Not created | Not tracked in GitHub |
| Former Phase 6: Advanced Features | [#47](https://github.com/KRFbrothers/Dua-ai/issues/47) | Closed with no child issues, although the full advanced-feature vision is not complete; its scope is now spread across Phases 2–5 and Deferred, with feature-specific security reviews as dependencies |

No GitHub Project board has been created.

---

## Execution Order

| Step | Focus | Status |
| --- | --- | --- |
| Phase 0 | Configuration checks (owner/deployer) | Cannot be verified from this repository |
| Phase 1 | UI foundation; reasoning + streaming | Implemented (PRs #66, #67); live provider and device checks pending |
| Phase 2 | Files, search and context | Implemented (PRs #68, #69); live and cross-device checks pending |
| Phase 3 | Imagine (images, then video) | Implemented (PR #70); Gemini video deferred; live checks pending |
| Phase 4 | Voice v2, then live voice | Implemented (PRs #71, #73); live and device audio checks pending |
| Phase 5 | Run code, read-only Build mode, then pull requests after approval | Implemented (PRs #74, #75 and the Build PR); running commands still awaits the owner's decision |

The cross-cutting privacy, security and cost work runs alongside every step.

---

## Definition of Done

A feature is done when:

- ✅ It works on the owner's Android device with real validation (not mocked)
- ✅ Failure states are safe and clear
- ✅ Privacy and data handling are explicit and documented
- ✅ It has been reviewed for UX and reliability
- ✅ Tests or equivalent verification exist for critical paths
- ✅ No sensitive credentials are exposed
- ✅ Backend changes stay compatible with already-installed app versions
- ✅ Paid features have per-feature spend caps before they are switched on

---

## Guiding Principles

1. **Quality over speed** — Dua should feel polished and trustworthy, not rushed
2. **Privacy by default** — The owner's data stays local first; cloud is optional and only syncs his own devices
3. **Consistency** — Android screens and backend behavior feel like one product
4. **Real validation** — Features are tested live, not just mocked
5. **Built for one person** — Design decisions follow the owner's actual daily use, not hypothetical users
6. **Incremental improvement** — Ship complete phases, not partial features
7. **Keys stay on the server** — The phone never holds provider credentials
8. **Spend stays capped** — The backend stays owner-only and every paid feature has a limit

---

## Notes

- **Foundation is solid** — state management, safety handling (stop, retry, draft recovery) and accessibility are good
- **Phases 1–5 closed the earlier gaps in code** — design system, markdown rendering, streaming with longer deadlines, context budgets with summaries, stored files, Imagine, voice and coding features
- **Validation is pending** — provider APIs, cloud sync and physical-device behavior need real-world testing

Model names, endpoints and prices in this roadmap were checked against official provider documentation in October 2026. Prices change often; re-check before building.
