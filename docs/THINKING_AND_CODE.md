# Thinking summaries and code artifacts

The Thinking badge reads `Thinking for Xs` while the provider summary streams, and `Thought for Xs` when finished. Expanded numbered/Markdown headings become a vertical step tree. Unstructured text stays one node; the UI does not invent analysis/search stages or request hidden model reasoning. Existing follow-stream/collapse behavior remains. The badge animation respects the system animation setting.

Code fences retain the existing Atom syntax colors (keywords, strings, comments and numbers), diff colors, asynchronous highlighting and plain-text fallback above 20,000 characters. They use the exported `KrfBrothersCodeFont` token. Copy preserves the entire source, including whitespace and final newlines, with a temporary Copied checkmark. Share uses the Android text chooser.

## Local previews

Preview is explicit and renders below the code:

- Markdown uses the existing native renderer and safe-link handler.
- HTML and SVG use a bounded WebView with JavaScript, network, file/content access, persistent storage and popups disabled. Scripts and remote resources in generated HTML do not run. This is a static preview, not an application runtime.
- Mermaid uses the pinned, offline Mermaid 12.2.0 bundle and strict sanitization. Only that exact bundled script can load. Diagram source is JSON-escaped into a trusted template, external loads/navigation are blocked, and no app JavaScript bridge exists. Invalid diagrams show a syntax message. The bundle and dependency notices are kept under `app/src/main/assets/vendor/mermaid/`.

Previews over 100,000 characters are rejected; source can still be copied/shared. Switching artifact type recreates the WebView, so a Mermaid view cannot leave JavaScript enabled for HTML/SVG. Views are destroyed on disposal. Physical-device WebView rendering still needs checking; Robolectric tests verify document construction, resource interception and settings rather than executing Chromium's renderer.

## Provider execution

Run supports Python through the existing provider-hosted Run code pipeline, with real execution/output records displayed inline in the next assistant reply's code-results block. It never executes generated code on Android. Existing backend configuration, selected provider, online/busy state and draft/attachment guards determine availability. Starting a snippet cannot overwrite an unsent draft or attach unrelated files.

The separate Python Cloud Run chat engine explicitly reports standalone `code` requests as unsupported today (`google-cloud-backend/app.py`), even when the base Node API advertises a sandbox. This view therefore reports Run as unavailable on that route. It does not fake an output or silently reroute the request. HTML/SVG/Markdown/Mermaid previews remain available offline. Other languages report that Python is required. No backend endpoint or provider capability was added by this UI change.

Tests cover tree parsing/toggle/completion, exact share/copy payloads, font and syntax roles, run delegation and rejection, preview opt-in, JSON script escaping, sandbox settings and exact offline-resource matching. Existing Home/header/composer/theme and message-selection tests remain part of the full Android suite.
