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

These HTML files remain design references rather than the running app. The
approved backdrop/orb direction from the first file is now implemented natively
in `HomeBackdrop.kt` / `ThinkingOrb.kt`, with shared `AmbientTokens.kt`, Dua's
blue Chat/violet Task washes and existing logo, font and voice button. Standard
conversations use the subdued backdrop. The broader app-wide theme is still a
reference; it has not been applied across features or the website.

The originals' claims about matching Kotlin filenames, performance budgets or
prior builds are not verification of the native implementation. See
[native ambient QA](../../qa/home-ambient-motion.md) for actual scope, checks and
device limitations. The attachment-sheet layout and account/actions retain
their existing ownership.
