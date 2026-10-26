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

These HTML files remain design references rather than the running app. The owner
restored the original Dua calligraphy Home and Chat/Task switcher after the
supplied-shell cleanup. Current Home uses
[`ChatContent.kt`](../../../apps/android/app/src/main/java/com/dua/ai/ui/chat/ChatContent.kt)
and the native
[`HomeBackdrop.kt`](../../../apps/android/app/src/main/java/com/dua/ai/ui/chat/HomeBackdrop.kt).
Work remains a drawer destination showing Coming soon without a composer.
The unused parallel supplied screen has been deleted; Home has one implementation.
The animated orb remains the reply-indicator fallback in
[`ThinkingOrb.kt`](../../../apps/android/app/src/main/java/com/dua/ai/ui/chat/ThinkingOrb.kt),
using `AmbientTokens.kt`. These native implementations do not establish that every
detail or performance claim in the HTML preview has been implemented or verified.
See [current shell ownership](../LEGACY_CHAT_SHELL.md) and
[audit status and remaining checks](../AUDIT_STATUS.md). The broader
app-wide theme is still a reference; it has not been applied across features or the website.

The originals' claims about matching Kotlin filenames, performance budgets or
prior builds are not verification of the native implementation. The attachment-sheet layout and account/actions retain
their existing ownership.
