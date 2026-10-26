# Android settings cards and loading ownership

Audit completion and remaining validation: [AUDIT_STATUS.md](AUDIT_STATUS.md).

These components were checked after the original Dua logo + Chat/Task Home was
restored. Similar shapes alone do not establish duplicate or unused behavior.

## Settings and page containers

| Component | Owner and contract | Decision |
| --- | --- | --- |
| `SettingsCard` | Internal container in `ui/components/SettingsCards.kt`: full width, settings shape, clipping, fill and border. | One shared implementation. |
| `SettingsGroup` | Same file; retains the optional section heading and outer spacing, then passes the caller's modifier to `SettingsCard`. | Keep its public API and heading semantics. |
| `SettingsCardBox` | `ui/settings/SettingsScreen.kt`; existing Settings/Account helper delegates to `SettingsCard`. | Keep as a thin compatibility helper. |
| Settings rows | `SettingsItemRow` in `SettingsScreen.kt` and navigation/toggle/choice/value rows in `SettingsCards.kt`. The hub includes animated icons and click labels; inner rows own switch/radio roles and value reflow. | Keep their distinct contracts. |
| `PageCard`, `PagePaddedCard` | `ui/components/PageParts.kt`; Material surface/content colors, elevation and outline alpha; the padded variant adds page spacing. | Keep. Replacing these with a settings card would change visuals. |
| `BackupRow`, `BackupPageScaffold` | `ui/settings/BackupLayoutParts.kt`; backup typography, optional click/trailing actions, page insets and scroll ownership. Used by backup/storage screens. | Keep. These are rows/scaffolding, not duplicate card containers. |

Only the identical settings container is shared. Its modifier order and content
remain the same; no extra layout wrapper is added. Headings, selectable-group
modifiers, row semantics, callbacks, typography and theme tokens stay with their
existing owners. No token or locked sheet/composer changes are needed.

## Reply and media loading

| State | Owner / call path | Decision |
| --- | --- | --- |
| Live Home/feature wait with no streaming message | `MessageList.kt` → `TypingIndicator`. | Keep the existing Thinking text and polite `Dua is thinking` announcement. |
| Streaming reply before the first answer token, without other progress | `AssistantMessage.kt` → `ReplyPlaceholder` in `StreamingPolish.kt` → `TypingIndicator`. | Already delegates to one indicator; keep the placeholder's size/tag contract. |
| Indicator artwork | `TypingIndicator` → `RiveOrFallback` → `ThinkingOrb`. | Keep the orb fallback; it is not an unused second status component. |
| Thinking and Agent steps | `ThinkingBlock.kt`, `AgentProgressView.kt`; reuse `rememberShimmerAlpha`. | Keep expandable/progress behavior and their shared pulse helper. |
| Loading/generated media | `ImagineMedia.kt`, `ImagineReply.kt` → `ShimmerBox` in `ui/components/Shimmer.kt`. | Keep media geometry and the shared pulse/sweep. |
| Removed parallel design | Alternate chat/composer/bubbles/shimmer and supplied-style branches have been deleted. | Tests now exercise the single live Dua Home instead of retaining an archived runtime implementation. |

Motion follows the existing reduced-motion/foreground guards. Combining these
drawings or replacing loading artwork would be a visual change, so this cleanup
does not do that to live indicators. Only the unused alternate-shell shimmer is
removed; shared media shimmer and live reply/progress behavior are unchanged.

## Verification

Existing `SettingsScreenTest`, `InnerSettingsRowsTest`, settings navigation,
appearance/personality and backup tests cover the affected callers, including
switch/radio roles, selection, callbacks and touch targets. Run the existing full
Android unit/UI suite and compare its recorded settings/account/page renders.
Lint, opt-in screenshot-package runs and phone/TalkBack checks must be reported
separately from default CI. No manual setup or backend deployment is required.
