# Dua AI Roadmap Tracking

This file summarizes GitHub tracking for the phases in [`ROADMAP.md`](ROADMAP.md), which remains the source of truth for phase scope and success criteria.

## Phase Tracking

| Phase | Epic Issue # | Status | Timeline | Key Blockers | Success Criteria |
| --- | --- | --- | --- | --- | --- |
| Phase 1: Core Product Stability | Not created | Not tracked in GitHub | Weeks 1–4 | Real Android-device persistence checks and remaining recovery verification | Zero critical data-loss bugs; stable sending and retry behavior; consistent UI across platforms |
| Phase 2: Design and UX Polish | Not created | Not tracked in GitHub | Weeks 5–8 | Design-polish success criteria remain open | Premium, readable messages; intuitive settings/onboarding; aligned Android and web UI; responsive layouts |
| Phase 3: AI Provider Validation | Not created | Not tracked in GitHub | Weeks 9–12 | Live provider, model, attachment, and web-search verification | Providers activate and test safely; failures do not break chat; attachments work reliably |
| Phase 4: Cloud, Identity, and Sync | Not created | Not tracked in GitHub | Weeks 13–16 | Production configuration and live cross-device verification are unknown | Secure, user-controlled sync; cross-device cloud history; clear and enforced ownership protections |
| Phase 5: Privacy, Security, and Trust | Not created | Not tracked in GitHub | Weeks 17–20 | Privacy documentation and release checks remain to be completed | Users understand local vs. cloud data; reliable deletion/export; no credentials exposed |
| Phase 6: Advanced Features | [#47](https://github.com/KRFbrothers/Dua-ai/issues/47) | Closed; no child issues | Weeks 21–24 | #47 is closed, but the roadmap says the full advanced-feature vision is not complete; prior phases and feature-specific security reviews are dependencies | Features gated behind setup; no unverified feature ships; platform complexity is well-tested |

## Proposed Phase 6 Sub-Issue Breakdown

The following breakdown is for GitHub issue creation; these issues have not been created.

| Proposed issue | Goal | Work items | Requires |
| --- | --- | --- | --- |
| Phase 6.1: Connector Framework | Establish the foundation for connectors | Define connector interface; base classes; auth pattern; testing | Phase 5 complete |
| Phase 6.2: GitHub Connector | Deliver the first production connector | GitHub auth; repository reading; issues integration; testing | Phase 6.1 |
| Phase 6.3: Memory & Persistent Context System | Provide persistent knowledge and custom instructions | Store/retrieve memory; memory UI; edit/delete; testing | Phase 5 complete |
| Phase 6.4: Plugin System & Developer Extensibility | Allow developers to extend Dua | Plugin API; registry; sandbox; examples; testing | Phase 6.1 |
| Phase 6.5: Voice Generation & Audio Features | Support generated reply voice | API integration; voice selection UI; caching; testing | None |
| Phase 6.6: Computer Mode & Collaboration | Support screen/file workflows and collaboration | Screen capture; file browser; team workspaces; testing | Phase 4 |

All proposed sub-issues should include the requested Android + Web and real-use-case acceptance checks, a review requirement, effort estimate, labels `phase-6`, `sub-issue`, `enhancement`, and a parent reference to #47.

## GitHub Tracking Setup Status

- Phase 1–5 epic issues: not created.
- Phase 6 epic #47: closed; no child issues are linked.
- Project board: not created.
- GitHub issue creation/editing and Projects-board tools were unavailable in this session, so no issue or board changes are claimed.
