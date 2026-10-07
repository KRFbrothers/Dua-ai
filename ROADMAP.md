# Dua AI — Product Roadmap

This roadmap reflects the path from a feature-rich prototype to a polished, production-ready AI chat application.

## Product Vision

Dua AI is a privacy-conscious AI chat application for Android, backed by a server-side API hosted on Vercel. The web client was removed; `web/` now contains only the backend. The product combines:

- **Local-first chat experience** — conversations, drafts, and history live on your device by default
- **Multiple AI provider support** — Gemini, ChatGPT, Claude, Grok, and Kimi
- **Voice and media** — speech recognition, text-to-speech, camera, photos, file attachments
- **Strong privacy and data control** — export, import, delete, and manage your data locally
- **Optional cloud sync and accounts** — Supabase-based sign-in and cross-device sync
- **Polished, low-friction UX** — consistent, intuitive experience across Android phones and tablets

## Current Status

The repository contains the implementation described below, but this checkout cannot inspect Vercel or Supabase production settings or verify live external services. “Configured” therefore means the required settings are present in the active deployment, not merely supported by code. Do not treat a mocked test as live verification.

| Feature | Implemented in repository | Configured in production | Verified |
| --- | --- | --- | --- |
| Android app and backend API | Yes (Android-only client; web client removed) | Backend deployed on Vercel; settings unknown here | Source/tests; physical-device behavior pending |
| Local chat history, drafts and search | Yes | N/A | Automated tests; Android backup/restart checks pending |
| Attachments and photo resizing | Yes | Provider/model capabilities are environment-dependent; production unknown | Automated/mock tests; live providers and physical Android pending |
| Camera and photo selection | Yes | Device permission is user-controlled | Automated coverage; physical-device checks pending |
| Voice input, read-aloud and hands-free | Yes | Speech service is device-dependent; generated voice is optional | Automated/mock tests; speech/audio behavior on devices pending |
| Accounts and profile | Yes; native Google sign-in is optional | Supabase, SMTP and OAuth settings are production-controlled; unknown here | Mocked tests; live sign-in/recovery pending |
| Cloud snapshots and foreground sync | Yes, reviewed manual and opt-in foreground flows | Supabase migration/environment and per-device consent are required; production unknown | Automated and migration tests; live cross-device checks pending |
| Gemini and optional AI providers | Adapters and provider selection are implemented | API keys/model IDs are production-controlled; unknown here | Mocked responses; live provider/model checks pending |
| Web search and link reading | Yes | Brave key needed for Search; production status unknown | Mocked tests; live retrieval/provider checks pending |
| Local memory and reviewed proposals | Yes | Local opt-in; no provider setup needed for notes | Automated tests; live prompt behavior pending |
| GitHub connector | Read-only repository listing is implemented | Token, owner restriction and feature flag are required; production unknown | Mocked tests; live authorization pending |
| Appearance and local notifications | Yes | Notification permission is user-controlled | Automated tests; physical delivery/rendering checks pending |
| Data controls and sharing | Local export/import/delete and reviewed cloud clearing are implemented | Cloud controls require Supabase; production unknown | Automated tests; physical file-picker checks pending |
| Login/owner/quota protection | Optional controls and durable daily request quota are implemented | Vercel variables, service key and usage migration are needed; production unknown | Mocked tests; production enforcement not verified |
| Release packaging and distribution | Android source builds; no release pipeline is configured | Signing credentials/store setup not verified | CI runs JVM tests, not a signed APK; release testing pending |

**Phase alignment:** Phase 1 is implemented in code but still awaits some physical-device verification. Phase 2 design-polish success criteria remain open. Phase 3 is implemented as configurable adapters but has not been validated against live provider APIs. Phase 4 account/snapshot/foreground-sync code is substantially implemented, while deployment and cross-device verification remain open. Phase 5 documentation and privacy accuracy need the corrections/checks tracked in the release runbook. Phase 6 contains selected opt-in features, not the full connector/computer/agent vision.

The separate Google ADK coding-agent component is not present in this repository checkout; its old workflow and documentation references should not be treated as an active feature.

---

## Roadmap Phases

### Phase 1: Core Product Stability  
**Priority: High | Timeline: Weeks 1–4**

Stabilize chat, history, and local data handling.

**Work:**
- Fix edge cases in message sending, retries, and state recovery
- Validate local persistence on real Android devices
- Stabilize error handling and recovery flows
- Ensure consistent UI states across Android screens
- Reduce crash paths and data loss scenarios

**Success:**
- Zero critical data loss bugs
- Stable sending and retry behavior
- Consistent UI across the app

---

### Phase 2: Design and UX Polish  
**Priority: High | Timeline: Weeks 5–8**

Bring UI to modern AI app quality.

**Work:**
- Improve message rendering: markdown, code blocks, syntax highlighting
- Add chat bubbles with avatars and better visual hierarchy
- Refine spacing, typography, and color consistency
- Improve empty states, loading indicators, and error messages
- Add smooth animations and micro-interactions
- Optimize tablet and large-screen layouts

**Success:**
- Messages feel premium and readable
- Settings and onboarding flows are intuitive
- Screens feel visually consistent
- Responsive on phones and tablets

---

### Phase 3: AI Provider Validation  
**Priority: High | Timeline: Weeks 9–12**

Move from mocked to verified live integrations.

**Work:**
- Configure and test provider credentials (OpenAI, Anthropic, xAI, Moonshot, Google)
- Validate API compatibility by model and provider
- Test attachment handling by provider (images, PDFs, text)
- Add clear error messaging for unsupported or failed requests
- Verify web search (Brave) and page reading workflows
- Test retry and fallback behavior

**Success:**
- Each provider can be activated and tested safely
- Provider failures are communicated without breaking chat
- Attachments work reliably across providers

---

### Phase 4: Cloud, Identity, and Sync  
**Priority: Medium-High | Timeline: Weeks 13–16**

Add cross-device features without sacrificing privacy.

**Work:**
- Complete Supabase email sign-in and logout flows
- Implement cloud snapshots with ownership and access controls
- Build durable sync with conflict resolution
- Support cloud memory notes and chat history
- Add device management and session controls
- Protect against unauthorized access

**Success:**
- Secure, user-controlled sync flows
- Cloud history works across devices
- Ownership and data protections are clear and enforced

---

### Phase 5: Privacy, Security, and Trust  
**Priority: High | Timeline: Weeks 17–20**

Make privacy a competitive advantage.

**Work:**
- Document data retention policies (local vs cloud)
- Improve consent flows and permission handling
- Clarify what is stored locally vs in the cloud
- Add stronger access checks for connectors and integrations
- Ensure API keys remain server-side only
- Add transparency to data deletion and export

**Success:**
- Users understand what is local vs cloud
- Data deletion and export flows are reliable and consistent
- No credentials are exposed in client or GitHub

---

### Phase 6: Advanced Features  
**Priority: Medium | Timeline: Weeks 21–24**

Expand beyond core chat once foundation is stable.

**Work:**
- Broader connector and plugin support (GitHub, Google Drive, etc.)
- Richer memory and context tools
- Computer mode and browser-assisted workflows
- Screen and camera capabilities
- Generated reply voice and personalization
- Advanced agent and coding features

**Success:**
- Advanced features are gated behind clear setup flows
- No feature ships before verification
- Platform-specific complexity is well-tested

---

## GitHub Tracking

Phase scope and success criteria live in this file. GitHub tracking status:

| Phase | Epic issue | Status |
| --- | --- | --- |
| Phases 1–5 | Not created | Not tracked in GitHub |
| Phase 6: Advanced Features | [#47](https://github.com/KRFbrothers/Dua-ai/issues/47) | Closed with no child issues, although the full advanced-feature vision is not complete; prior phases and feature-specific security reviews are dependencies |

No GitHub Project board has been created.

### Proposed Phase 6 sub-issues

These have not been created yet.

| Proposed issue | Goal | Work items | Requires |
| --- | --- | --- | --- |
| Phase 6.1: Connector Framework | Establish the foundation for connectors | Define connector interface; base classes; auth pattern; testing | Phase 5 complete |
| Phase 6.2: GitHub Connector | Deliver the first production connector | GitHub auth; repository reading; issues integration; testing | Phase 6.1 |
| Phase 6.3: Memory & Persistent Context System | Provide persistent knowledge and custom instructions | Store/retrieve memory; memory UI; edit/delete; testing | Phase 5 complete |
| Phase 6.4: Plugin System & Developer Extensibility | Allow developers to extend Dua | Plugin API; registry; sandbox; examples; testing | Phase 6.1 |
| Phase 6.5: Voice Generation & Audio Features | Support generated reply voice | API integration; voice selection UI; caching; testing | None |
| Phase 6.6: Computer Mode & Collaboration | Support screen/file workflows and collaboration | Screen capture; file browser; team workspaces; testing | Phase 4 |

When created, each sub-issue should include Android and backend acceptance checks based on real use cases, a review requirement, an effort estimate, the labels `phase-6`, `sub-issue` and `enhancement`, and a parent reference to #47.

---

## Execution Timeline

| Period | Focus |
|--------|-------|
| **Immediate (Weeks 1–4)** | Stabilize core chat, fix crashes, validate local data |
| **Short-term (Weeks 5–12)** | Polish UI/UX, validate provider APIs, test live integrations |
| **Mid-term (Weeks 13–20)** | Add cloud sync, improve privacy, complete accounts |
| **Long-term (Weeks 21–24)** | Launch advanced features, refine based on user feedback |

---

## Definition of Done

A feature is ready to ship when:

- ✅ It works on the targeted platform with real validation (not mocked)
- ✅ Failure states are safe and user-friendly
- ✅ Privacy and data handling are explicit and documented
- ✅ It has been reviewed for UX and reliability
- ✅ Tests or equivalent verification exist for critical paths
- ✅ No sensitive credentials are exposed

---

## Guiding Principles

1. **Quality over speed** — Dua should feel polished and trustworthy, not rushed
2. **Privacy by default** — Users own their data; cloud is optional
3. **Consistency** — Android screens and backend behavior feel like one product
4. **Real validation** — Features are tested live, not just mocked
5. **User feedback** — Design decisions are informed by actual usage
6. **Incremental improvement** — Ship complete phases, not partial features

---

## Notes

The Dua codebase is well-structured and feature-complete in breadth, but needs investment in depth:

- **Foundation is solid** — state management, architecture, and modularity are good
- **Polish is missing** — animations, design system, and premium UX need work
- **Validation is pending** — provider APIs and cloud sync need real-world testing
- **Scale is unclear** — performance and stability need load testing

This roadmap balances ambition with realism. Shipping a premium AI chat app requires not just features, but refinement, trust-building, and user validation.
