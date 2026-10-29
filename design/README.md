# Dua AI Android design preview

A browser-based, interactive showcase of fourteen Dua screens, inspired by the
owner's [Orhan Android showcase](https://krfbrothers.github.io/orhan-android/).
This is a design document alongside the existing app, with no production routes,
authentication changes, API calls or extra Android implementation.

Open `index.html` with this folder's assets, or serve this directory:

```sh
python -m http.server 4173 --directory design
```

Open `http://localhost:4173` in your browser. No npm packages or build are needed.
To produce one offline HTML file with all fonts, artwork, styles and scripts:

```sh
python design/export_preview.py /tmp/Dua-AI-Android-preview.html
```

## Screens and working interactions

Chat home, Task home, Conversation, History, Models, Projects, Library, Canvas,
Voice, Profile, Settings, Appearance, Notifications and Storage.

- Screen navigation, in-app back buttons and mobile screen selector.
- Chat/Task changes preserve the draft; Task starters fill without sending.
- Empty sends are ignored; messages render as text with clearly marked sample
  replies. Copy uses the browser clipboard when supported.
- History search, pinned filtering and empty-state recovery.
- Project creation and Canvas editing, kept in the current tab.
- Display-name editing updates the greeting. Theme and display name persist in
  browser storage when available; System follows the browser's color preference.
- Local file name/size listing, notification switches, voice-orb preview and a
  confirmation before clearing sample chats or resetting the preview.

Sample replies, notification switches and the voice orb are demonstrations.
No microphone, account, model provider or cloud storage is connected. File
contents are never read or uploaded. Sample conversations, project/document edits
and file metadata live only in the tab; reloading restores the sample content.

## Source and design contract

- The original calligraphy in `assets/dua-{light,dark}.svg` is converted from
  `apps/android/app/src/main/res/drawable/ic_dua_logo_option1_{light,dark}.xml`.
  Its path geometry and gradient stops are retained.
- Surfaces and text use the native `Color.kt` neutral roles. Light uses Dua's
  purple family and dark uses its orange accent family, with readable foreground
  colors. This document does not change native theme tokens.
- Inter is copied from the app's existing `inter_regular.ttf`, with its full
  SIL Open Font License in `assets/Inter-OFL.txt`.
- The small inline outline-icon sprite comes from the owner's supplied Orhan
  showcase source. No Orhan logo, account, avatar, Lato font or social feed is used.
- One current screen, one current greeting and one composer are rendered at a
  time. The canonical connected clients remain in `apps/android` and `apps/web`.
- Controls have at least 48 px touch targets, labelled inputs, visible keyboard
  focus and native modal focus handling. Motion respects reduced-motion settings.

## Browser verification

With Python Playwright and Chromium available, start the server above, then run:

```sh
python design/verify_preview.py --output /tmp/dua-preview-checks
python design/verify_preview.py --offline-html /tmp/Dua-AI-Android-preview.html --output /tmp/dua-preview-offline-checks
```

The checks exercise 14 screens in light/dark at 320, 768, 1024 and 1440 px (112
layout cases), assets/icons, touch targets, one-heading ownership, primary
interactions, escaping, persistence, keyboard radio/modal behavior and reduced
motion. Reports and browser screenshots go to the selected output directory.
This verifies the HTML preview, not Android permissions or backend behavior.

The offline check serves the exported HTML from memory and blocks all other
network requests. Recorded results are in [VALIDATION.md](VALIDATION.md).
