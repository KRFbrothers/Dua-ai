# Design system token migration

UI-only changes. `DuaTheme.kt` supplies the existing `LocalDuaColors` with light/dark `DuaColors` from `ui/theme/Color.kt`. Screens read `DuaTheme.colors` or `MaterialTheme.colorScheme`; draw/highlight helpers receive the current tokens as arguments. Original logo artwork, accent choices, navigation, callbacks, storage and backend APIs are unchanged.

## Replaced colors

Tokens below are relative to `DuaTheme.colors`, unless marked M3. D/L means dark/light; eight-digit values include alpha. Repeated uses share the same token.

| Previous value / use | New token |
| --- | --- |
| Diff added D/L `#7EE787` / `#116329` (CodeBlock and BuildProposalCard) | `diff.added` |
| Diff added background D/L `#332EA043` / `#262DA44E` | `diff.addedBackground` |
| Diff removed D/L `#FF7B72` / `#82071E` (both cards) | `diff.removed` |
| Diff removed background D/L `#33F85149` / `#26FF8182` | `diff.removedBackground` |
| Diff hunk D/L `#79C0FF` / `#0550AE` | `diff.hunk` |
| Voice background D/L `#050506` / Neutral97 `#F6F6F7` | `voice.background` |
| Orb gradient `#141518` → `#0B0B0D` in both themes | `voice.orbSurfaceTop` → `voice.orbSurfaceBottom` |
| Orb cream `#F3E3B8` | `voice.orbCream` |
| Live speaking glow `#8B7CF6` | `voice.speakingGlow` (original `#8B7CF6` in both appearances) |
| End-call red `#E5484D`, white icon | `danger` (`#E5484D`) / `onDanger` (white), unchanged |
| Voice glow gold D/L Gold80 `#E8C46A` / Gold40 `#A67C1E` | Existing `sparkle` |
| Voice glow silver D/L Neutral88 `#DDDEE2` / Neutral72 `#B4B7BE` | `voice.glowSilver` |
| Orb silver Neutral72 `#B4B7BE` → Neutral88 `#DDDEE2`; Gold80 `#E8C46A` | `voice.orbSilverLow` → `voice.orbSilverHigh`; `voice.orbGold` |
| White voice glow/orb/shimmer/ripple/live highlight | `voice.highlight` (white, decorative) |
| Status pill D/L white at 6% / Ink `#111214` at 5% | `voice.statusSurface` |
| Orb rim D/L white at 10% / black at 12% | `voice.orbRim` |
| Speaking status dot D/L Neutral88 `#DDDEE2` / Ink `#111214`; idle dot Neutral45 `#6E717A` | M3 `onSurface`; M3 `onSurfaceVariant` |
| Snackbar D/L Neutral16 `#292A2F` / Ink `#111214`; text Neutral93 `#ECECEF` | M3 `surfaceContainerHighest` / `inverseSurface`; `onSurface` / `inverseOnSurface` |
| Voice controls D/L Neutral13 `#212226` / Neutral100 white; light border Neutral90 `#E3E4E8` | M3 `surfaceContainerHigh` / `surface`; `outlineVariant` |
| Permission icon background D/L Neutral16 `#292A2F` / Neutral93 `#ECECEF` | M3 `secondaryContainer` |
| Media canvas black; play/duration overlays black at 55%; viewer icons/labels white | `mediaBackground`; `mediaOverlay`; `onMedia` |
| Home logo sweep white; Google badge white | `logoHighlight`; `brandBadgeBackground` (artwork colors stay fixed) |
| Cube highlight RGB `(0.88, 0.88, 0.90)`; computed grayscale face shade | `voiceCubeHighlight`; theme helper `voiceCubeFaceColor` |
| Black vector strokes (DuaIcons, sidebar cube) | Theme `DuaVectorStroke` tint mask; visible icon tint still follows the caller |
| Third-party syntax RGB values | `syntaxTheme` (original Atom dark/light palettes) and theme `syntaxTokenColor`; RGB values are decoded unchanged |

Voice captions and bottom status retain the original background without an extra card. Media viewer header/footer retain their original transparent rows. `mediaOverlay` is only used by the original video poster play/duration overlays.

## Replaced typography

All sizes and proportional sizing helpers live in `ui/theme/Type.kt`. Android font scaling stays enabled.

| Previous inline size / line height | New token |
| --- | --- |
| Home tagline `20sp / 28sp`, compact `18sp / 28sp`, InterFont Medium | `HomeTaglineStyle`, `HomeTaglineCompactStyle` (exact original sizing) |
| Voice greeting `26sp / 34sp`, InterFont Medium | `VoiceGreetingStyle` (exact original sizing) |
| Voice user transcript `16sp / 22sp` | Theme `voiceTranscriptStyle` (`16sp / 22sp`), derived from the selected M3 body family |
| Voice reply/Thinking caption `24sp / 33sp`, normal weight | Theme `VoiceReplyCaptionStyle` (`24sp / 33sp`, original default family/normal weight) |
| Permission heading `22sp / 28sp`, InterFont Medium | Theme `VoicePermissionHeadingStyle` (`22sp / 28sp`, InterFont Medium) |
| Markdown line height = body size × `1.55`, inline code = body size × `0.9` | Theme `markdownBodyStyle`, `markdownInlineCodeStyle` (same proportional sizing) |

The caption toggle uses `Sizes.touchTarget` (48dp); existing action targets remain intact. `Color.Transparent` and `Color.Unspecified` are rendering/default sentinels, not palette colors. The white JPEG flattening in `PhotoAttachmentReader` is file processing, not UI, and remains unchanged.

## Checks

```sh
rg 'Color\(0x' apps/android/app/src/main          # only theme/Color.kt
rg '\.sp\b' apps/android/app/src/main            # only theme/Type.kt
bash apps/android/gradlew -p apps/android :app:testDebugUnitTest --no-daemon --max-workers=2
```

`DesignSystemContrastTest` checks AA text contrast (4.5:1) on the existing diff/badge/voice-base/media surfaces in both themes. The restored end-call color is checked at the non-text icon threshold (3:1); syntax highlighting is checked against the exact original Atom colors. It does not claim every original syntax hue or text over arbitrary artwork/animated glow meets text AA. `AccentContrastTest` iterates `AccentPreset.entries`, currently seven presets including Copper. Default selection remains Purple in light mode and Orange in dark mode. UI rendering uses sample data; physical-device/TalkBack verification is separate from automated checks.

For dated current-build results and checks still outstanding, see [Android UI verification](ANDROID_UI.md#current-verification-2026-10-26).
