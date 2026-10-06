# Roadmap

This roadmap reflects the current state of the Dua AI project and the path to moving from a feature-rich prototype into a polished, production-ready AI assistant.

## Product vision

Dua AI is a privacy-conscious AI chat application for Android and the web. The product aims to combine:

- local-first chat experience
- multiple AI provider support
- voice and media interactions
- strong privacy and data control
- optional cloud sync and account features
- a polished, low-friction user experience across devices

## Current status

The repository already includes:

- a native Android Compose client in `app/`
- a web app and server integration in `web/`
- provider abstraction for Gemini, ChatGPT, Claude, Grok, and Kimi
- local features such as history, attachments, voice input, export/import, and settings
- a separate `google-agent` developer workflow for repository-aware coding assistance

The app is functionally rich, but several areas still need real-world validation, polish, and production hardening before it can be considered a premium consumer-grade AI product.

## Roadmap phases

### Phase 1 — Core product stability

Priority: High

Goal: Make the main chat experience stable, polished, and reliable.

Planned work:
- improve app reliability for chat history, drafts, attachments, and retry flows
- stabilize Android and web state management
- validate local persistence and recovery flows on real devices
- reduce edge-case crash paths and invalid state transitions
- finalize UX for empty states, error messages, and loading states

Success criteria:
- zero critical local data loss paths
- stable message sending and retry behavior
- consistent UI states across Android and web

### Phase 2 — Design and experience polish

Priority: High

Goal: Bring the UI closer to modern AI app quality.

Planned work:
- improve layout spacing, density, and visual hierarchy
- add richer message rendering for markdown, code blocks, and sources
- refine chat bubbles, avatars, actions, and empty states
- improve desktop and tablet responsiveness
- add stronger animations and micro-interactions
- standardize color, typography, and iconography across app surfaces

Success criteria:
- messages feel premium and consistent
- onboarding and settings flows are easier to navigate
- Android and web feel visually aligned

### Phase 3 — AI provider readiness

Priority: High

Goal: Move from mocked/provider-ready code to verified live integrations.

Planned work:
- configure and verify provider credentials in production-like environments
- validate API compatibility by model and provider
- harden attachment handling by provider and MIME type
- add clear fallback/error messaging for unsupported models
- test real web-search and page-read workflows

Success criteria:
- each provider can be configured safely and verified live
- provider failures are communicated without breaking the chat flow
- models and attachments behave predictably

### Phase 4 — Cloud, identity, and sync

Priority: Medium-High

Goal: Add account-level features and cross-device behavior without sacrificing privacy.

Planned work:
- finalize Supabase account and login flow
- validate cloud snapshot, recovery, and ownership rules
- implement durable sync with conflict handling
- support reviewable cloud memory and chat history workflows
- protect against unauthorized access and misuse

Success criteria:
- secure, reviewed sync flows for owners and devices
- cloud history works consistently across devices
- ownership and data protections are visible and enforceable

### Phase 5 — Privacy, security, and trust

Priority: High

Goal: Make privacy and data handling a clear product advantage.

Planned work:
- review data retention and local/cloud storage boundaries
- improve consent flows and account protections
- clarify ownership, sharing, and deletion behavior
- add stronger access checks for connectors and integrations
- ensure API keys remain server-side only

Success criteria:
- users understand what is local vs cloud
- data deletion and export flows are consistent
- sensitive credentials are never exposed in client code or GitHub

### Phase 6 — Advanced features and expansion

Priority: Medium

Goal: Add the more advanced product capabilities after the core foundation is stable.

Planned work:
- broader connector and plugin support
- richer memory and context tools
- computer mode and browser-assisted workflows
- screen/camera capabilities where appropriate
- optional generated reply voice and personalized experiences

Success criteria:
- advanced features are gated behind clear setup and approval flows
- no feature is shipped before verification
- platform-specific complexity is contained and tested

## Timeline

### Near term
- stabilize core chat and local-first behavior
- improve Android and web UI polish
- verify provider integrations in staging

### Mid term
- complete account and sync setup
- launch more polished design system
- validate real-world provider flows on devices

### Long term
- ship premium AI app experience with polished UX, cloud services, and stronger trust/controls
- expand advanced agent and connector features safely

## Definition of done

A feature is ready to ship when:

- it works on the targeted platform with real validation
- failure states are understandable and safe
- privacy and data handling are explicit
- it has been reviewed for UX and reliability
- tests or equivalent verification exist for core behavior

## Notes

This roadmap intentionally balances feature ambition with reliability. The project already contains a strong set of capabilities, but the path to a polished end-user product requires more validation, design refinement, and real deployment readiness than the current codebase alone suggests.

This repo should continue to prioritize product quality over feature sprawl.

"""









































































































































