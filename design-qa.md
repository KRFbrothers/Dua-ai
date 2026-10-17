# Chat backup reference layout — design QA

final result: passed

## Visual target and scope

Source: user-selected `1000672108.jpg` (581 × 1280 pixels), attached to this task. The private source attachment is not committed. Source path in the task workspace: `/tmp/codex-remote-attachments/01a1238c-eeed-718a-9e36-cd68697a605e/7bd250ee-bd0b-48dd-9bea-8ee9064e71f5/1-1000672108.jpg`.

Target is the reference's flat backup settings layout, adapted to Dua AI's existing functionality and theme. This is the existing native Android project, not a web prototype. Compose/Robolectric native screenshots and UI interactions are the rendered evidence; no browser preview or browser console applies.

## Evidence and normalization

- Native viewport: 393 × 866 dp, mdpi (393 × 866 pixels), normal font scale. State: signed-in, connected, daily backup on, a verified archive, no active operation. Dates, email and size are isolated test fixtures; production renders the current owner and recorded verified archive metadata.
- Light implementation: `app/build/approved-ui/backup-reference-false.png`.
- Dark implementation: `app/build/approved-ui/backup-reference-true.png`.
- Source Android status/navigation chrome was excluded using crop `(0, 65, 581, 1254)`. The 581 × 1189 content region was normalized to 393 × 804 pixels by width. Its common 804-pixel content region was compared beside the native implementation, with a 16-pixel gutter.
- Initial full-view comparison: `/tmp/dua-backup-reference-comparison.png` (802 × 804 pixels).
- Post-fix full-view comparison: `/tmp/dua-backup-reference-comparison-final.png` (802 × 804 pixels). Source and implementation were opened together, not judged from separate paths or memory.
- Focused post-fix comparison: `/tmp/dua-backup-reference-detail-final.png` (802 × 395 pixels), covering header, descriptive copy, summary, primary button and account. Opened to inspect type, alignment and control spacing.
- Additional accessibility captures: `app/build/approved-ui/automatic-drive-{false,true}-{false,true}.png`, 320 × 740 dp, light/dark and 1×/1.8× text. Labels wrap, content scrolls, and controls remain reachable without overlapping.
- Implementation screenshots are reproducible with `:app:testDebugUnitTest -Pdua.ui-record` and retained by the existing CI artifact upload. Private reference/comparison images stay outside the repository.

## Comparison history

1. Initial comparison found [P2] excess row spacing and secondary retention/browse content pushing backup protection below the main reference region. Initial evidence is the full-view comparison above. Fixed flat-row and automatic-toggle padding from 20 dp to the shared 16 dp token; moved protection directly after the network row and the existing secondary backup notes/browser below it.
2. Captured again after the fix, opened the final full-view and focused comparisons, and checked light/dark plus enlarged text. Backup protection now follows the network setting within the reference's main content region. No actionable P0/P1/P2 differences remain within the approved scope.

## Required fidelity surfaces

- **Typography:** shared Dua font family retained; backup-specific normal-weight heading/row/body styles live in `theme/Type.kt`. Reference hierarchy is preserved. The bundled Dua font differs from the source app's font intentionally; no app-wide type changes or inline `sp` values were introduced.
- **Spacing/layout:** flat arrow header, left-aligned explanatory copy, date/size, compact Back up pill, account, management, daily setting, network and protection. No large rounded card encloses primary settings. Header has a 48 dp touch target; buttons, toggle and tappable rows preserve minimum touch sizes. Storage details are expandable below backup/restore controls.
- **Colors/tokens:** existing Page/Sheet theme roles supply backgrounds, text, dividers, switches and the purple primary action in both themes. The reference's green accent and white surface were intentionally adapted to Dua's existing palette; no raw screen colors or global palette changes.
- **Assets:** only standard native arrow/lock icons are needed. Existing Material library glyphs are used; there are no hero images, logos, custom illustrations, generated assets or rasterized app controls.
- **Copy/content:** app-owned metadata replaces the source app's account/date/9.1 GB example. Unknown historical size says Not recorded. Google storage management opens the real Google page rather than showing a fabricated quota. Password protection, hidden app-folder browsing, manual save/restore and storage usage remain accessible. Unsupported mobile-data/video filters were not added; the actual unmetered-network requirement is displayed. Protection is described as password-protected encryption, not a new optional encryption feature.

## Interactions and remaining device checks

Compose tests exercise Back, Back up, Google Account/reconnect callback, Google storage management callback, automatic Daily/Off state, disabled/busy controls, retained upload status/errors, manual backup/restore, storage details expansion and existing navigation routes. Tests cover legacy metadata/password compatibility and retaining the prior verified size after a failed backup.

Existing real Drive authorization, encryption and transfer flows are reused. The redesigned page and browser handoff still need a physical-phone smoke check after installing the signed update; native screenshot/UI tests do not claim that phone installation or a new live Drive upload was performed for this layout PR.

## Implementation checklist

- [x] Reference and implementation opened and normalized together.
- [x] Initial spacing/protection finding fixed and recaptured.
- [x] Light, dark, narrow and large-text states inspected/tested.
- [x] Primary callbacks and preserved controls tested.
- [x] Existing theme roles, system insets and touch targets retained.
- [x] No remaining actionable P0/P1/P2 visual findings.
