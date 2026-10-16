# Artifacts Canvas

Completed or stopped assistant replies with a supported artifact show an **Open Canvas** pill. User messages, plain replies and actively streaming/unfinished fences do not. This is a viewer of real reply content; it does not add demo data, execute generated application code or call a backend.

## Detection and views

- Closed backtick/tilde fences support HTML, SVG, Mermaid and Markdown (including common aliases). The closing marker must match the opening marker's character and minimum length.
- Whole HTML/SVG documents and unfenced Markdown tables are detected too. Ordinary prose and Python/other non-UI source do not gain a Canvas button.
- Multiline UI source (for example React JSX/TSX or Kotlin Compose) opens in **Code**. **Preview** clearly explains that a separate UI runtime is required. No compiled result is simulated.
- Multiple artifacts in one reply have a selector. **Preview | Code** switches between formatted content and selectable, highlighted source. Copy/Share use the selected artifact source. Fenced-source trailing blank lines are retained; the Library's prior extraction behavior remains unchanged through the shared parser's default options.
- Detection is capped at eight artifacts and skips messages over 500,000 characters. The inherited renderer rejects previews over 100,000 characters. These bounds do not modify saved replies.

The viewer opens a snapshot of the selected reply artifacts. Its state lives above the virtualized message rows, so background streaming/scrolling does not dispose an open sheet. Switching chats or removing the source message closes it. Sheet state is transient and source bodies are never placed in Android saved-state bundles.

## Renderer isolation and disposal

The Canvas reuses the Pillar 2 renderer: static HTML/SVG has JavaScript disabled; Markdown uses the native renderer; Mermaid uses the pinned local bundle in strict mode. WebViews block network loads/navigation, file/content access and persistent storage and expose no app JavaScript bridge. HTML scripts and external resources are not an interactive application runtime.

Switching to Code, changing artifacts, closing, system Back or removing the sheet releases its WebView. The release path stops loading, disables scripts, navigates to `about:blank`, clears history/children, detaches from the view parent, removes callbacks and destroys the WebView. There is no global WebView cache. The layout uses existing theme roles, typography, insets and 48dp controls.

Unit/Compose tests cover detection, formatted tables, source/copy fidelity, multiple artifacts, small-screen/font scaling and renderer release across tab switching, artifact changes, chat switching, Back and repeated open/close in both themes. Native screenshot captures are written under `app/build/approved-ui/` when `-Pdua.ui-record` is set. Physical-device WebView rendering and Android memory profiling still require a device check; release assertions do not prove that Chromium has terminated its shared renderer process.
