# Dua AI animation and theme previews

Original standalone HTML previews supplied by Fareed. Open either file directly
in a modern browser; the CSS, Canvas drawing and JavaScript are embedded, with
no external assets or package installation required.

| Preview | Supplied file | Contents |
| --- | --- | --- |
| [Chat ambient motion](chat-ambient-motion.html) | `index (1).html` | Animated backdrop and thinking orb, light/dark palette, before/after examples and proposed motion timings |
| [App-wide theme](app-wide-theme.html) | `index.html` | Theme and animated visual references across the app |

Both HTML files are preserved byte-for-byte, including their existing preview
media guard. The descriptive filenames distinguish the two uploaded index files.

These HTML files remain design references rather than the running app. The old
native Home backdrop was removed during the legacy Home cleanup. Current Home
Chat, Task and Work use
[`SuppliedChatUi.kt`](../../../apps/android/app/src/main/java/com/dua/ai/ui/chat/SuppliedChatUi.kt).
The animated orb remains a legacy reply-indicator fallback in
[`ThinkingOrb.kt`](../../../apps/android/app/src/main/java/com/dua/ai/ui/chat/ThinkingOrb.kt),
using `AmbientTokens.kt`; this does not mean the preview's backdrop is active on Home.
See [current shell ownership and known gaps](../LEGACY_CHAT_SHELL.md). The broader
app-wide theme is still a reference; it has not been applied across features or the website.

The originals' claims about matching Kotlin filenames, performance budgets or
prior builds are not verification of the native implementation. The attachment-sheet layout and account/actions retain
their existing ownership.
