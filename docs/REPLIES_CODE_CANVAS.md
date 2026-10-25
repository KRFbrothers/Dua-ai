# Replies: thinking, code and Artifacts Canvas

How the Android app shows thinking summaries, code blocks, local previews, provider code runs and the Artifacts Canvas. (Merged from the former `THINKING_AND_CODE.md` and `ARTIFACTS_CANVAS.md`.)

## Thinking summaries

The Thinking badge reads `Thinking for Xs` while the provider summary streams, and `Thought for Xs` when finished. Expanded numbered/Markdown headings become a vertical step tree. Unstructured text stays one node; the UI does not invent analysis/search stages or request hidden model reasoning. Existing follow-stream/collapse behavior remains. The badge animation respects the system animation setting.

## Code blocks

Code fences retain the existing Atom syntax colors (keywords, strings, comments and numbers), diff colors, asynchronous highlighting and plain-text fallback above 20,000 characters. They use the exported `KrfBrothersCodeFont` token. Copy preserves the entire source, including whitespace and final newlines, with a temporary Copied checkmark. Share uses the Android text chooser.

## Preview renderer (shared by code blocks and the Canvas)

Preview is explicit and renders below the code (or inside the Canvas):

- Markdown uses the existing native renderer and safe-link handler.
- HTML and SVG use a bounded WebView with JavaScript, network, file/content access, persistent storage and popups disabled. Scripts and remote resources in generated HTML do not run. This is a static preview, not an application runtime.
- Mermaid uses the pinned, offline Mermaid 12.2.0 bundle in strict mode with strict sanitization. Only that exact bundled script can load. Diagram source is JSON-escaped into a trusted template, external loads/navigation are blocked, and no app JavaScript bridge exists. Invalid diagrams show a syntax message. The bundle and dependency notices are kept under `app/src/main/assets/vendor/mermaid/`.

WebViews block network loads/navigation, file/content access and persistent storage and expose no app JavaScript bridge. Previews over 100,000 characters are rejected; source can still be copied/shared. Switching artifact type recreates the WebView, so a Mermaid view cannot leave JavaScript enabled for HTML/SVG. Views are destroyed on disposal. Physical-device WebView rendering still needs checking; Robolectric tests verify document construction, resource interception and settings rather than executing Chromium's renderer.

## Provider code execution (Run)

Run supports Python through the existing provider-hosted Run code pipeline, with real execution/output records displayed inline in the next assistant reply's code-results block. It never executes generated code on Android. Existing backend configuration, selected provider, online/busy state and draft/attachment guards determine availability. Starting a snippet cannot overwrite an unsent draft or attach unrelated files.

The separate Python Cloud Run chat engine explicitly reports standalone `code` requests as unsupported today (`apps/ai-engine/app.py`), even when the base Node API advertises a sandbox. This view therefore reports Run as unavailable on that route. It does not fake an output or silently reroute the request. HTML/SVG/Markdown/Mermaid previews remain available offline. Other languages report that Python is required. No backend endpoint or provider capability was added by this UI change.

## Artifacts Canvas

Completed or stopped assistant replies with a supported artifact show an **Open Canvas** pill. User messages, plain replies and actively streaming/unfinished fences do not. This is a viewer of real reply content; it does not add demo data, execute generated application code or call a backend.

### Detection and views

- Closed backtick/tilde fences support HTML, SVG, Mermaid and Markdown (including common aliases). The closing marker must match the opening marker's character and minimum length.
- Whole HTML/SVG documents and unfenced Markdown tables are detected too. Ordinary prose and Python/other non-UI source do not gain a Canvas button.
- Multiline UI source (for example React JSX/TSX or Kotlin Compose) opens in **Code**. **Preview** clearly explains that a separate UI runtime is required. No compiled result is simulated.
- Multiple artifacts in one reply have a selector. **Preview | Code** switches between formatted content and selectable, highlighted source. Copy/Share use the selected artifact source. Fenced-source trailing blank lines are retained; the Library's prior extraction behavior remains unchanged through the shared parser's default options.
- Detection is capped at eight artifacts and skips messages over 500,000 characters. The shared renderer's 100,000-character preview limit (above) still applies. These bounds do not modify saved replies.

The viewer opens a snapshot of the selected reply artifacts. Its state lives above the virtualized message rows, so background streaming/scrolling does not dispose an open sheet. Switching chats or removing the source message closes it. Sheet state is transient and source bodies are never placed in Android saved-state bundles.

### Renderer release

The Canvas reuses the preview renderer above. Switching to Code, changing artifacts, closing, system Back or removing the sheet releases its WebView. The release path stops loading, disables scripts, navigates to `about:blank`, clears history/children, detaches from the view parent, removes callbacks and destroys the WebView. There is no global WebView cache. The layout uses existing theme roles, typography, insets and 48dp controls.

## Tests and device checks

- Thinking and code: tests cover tree parsing/toggle/completion, exact share/copy payloads, font and syntax roles, run delegation and rejection, preview opt-in, JSON script escaping, sandbox settings and exact offline-resource matching. Existing Home/header/composer/theme and message-selection tests remain part of the full Android suite.
- Canvas: unit/Compose tests cover detection, formatted tables, source/copy fidelity, multiple artifacts, small-screen/font scaling and renderer release across tab switching, artifact changes, chat switching, Back and repeated open/close in both themes. Native screenshot captures are written under `app/build/approved-ui/` when `-Pdua.ui-record` is set.
- Still needs a device: physical-device WebView rendering and Android memory profiling; release assertions do not prove that Chromium has terminated its shared renderer process.

Focused test classes are listed in [TESTING.md](TESTING.md).
