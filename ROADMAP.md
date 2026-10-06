# Dua AI — Product Roadmap

This roadmap reflects the path from a feature-rich prototype to a polished, production-ready AI chat application.

## Product Vision

Dua AI is a privacy-conscious AI chat application for Android and the web. The product combines:

- **Local-first chat experience** — conversations, drafts, and history live on your device by default
- **Multiple AI provider support** — Gemini, ChatGPT, Claude, Grok, and Kimi
- **Voice and media** — speech recognition, text-to-speech, camera, photos, file attachments
- **Strong privacy and data control** — export, import, delete, and manage your data locally
- **Optional cloud sync and accounts** — Supabase-based sign-in and cross-device sync
- **Polished, low-friction UX** — consistent, intuitive experience across Android and web

## Current Status

**What exists:**
- Native Android Compose client (`app/`)
- Web app and server API (`web/`)
- Provider abstraction for 5 AI services
- Local features: history, attachments, voice, export/import, settings
- Google ADK coding agent (`google-agent/`)

**What's ready but needs work:**
- Provider credentials are configured but not fully live-tested
- Cloud sync and accounts are partially implemented
- UI is functional but not polished to premium app standards
- Web search and advanced integrations are prepared but not activated

---

## Roadmap Phases

### Phase 1: Core Product Stability  
**Priority: High | Timeline: Weeks 1–4**

Stabilize chat, history, and local data handling.

**Work:**
- Fix edge cases in message sending, retries, and state recovery
- Validate local persistence on real Android devices
- Stabilize error handling and recovery flows
- Ensure consistent UI states across Android and web
- Reduce crash paths and data loss scenarios

**Success:**
- Zero critical data loss bugs
- Stable sending and retry behavior
- Consistent UI across platforms

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
- Optimize desktop and tablet layouts

**Success:**
- Messages feel premium and readable
- Settings and onboarding flows are intuitive
- Android and web feel visually aligned
- Responsive on phones, tablets, and desktop

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
3. **Cross-platform consistency** — Android and web feel like one product
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
