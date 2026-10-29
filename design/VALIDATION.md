# Preview verification

Verified in local headless Chromium 151 with Python Playwright.

- 14 screens × 2 themes × 4 viewport widths (320, 768, 1024, 1440):
  **112 layout cases passed**.
- One screen heading; no page/phone horizontal overflow; every measured enabled
  control at least 48 × 48 px; no missing icons or broken images.
- Primary interaction checks passed: draft retention, Task starters, model
  selection by click and keyboard, empty-send protection, sample replies, literal
  rendering of HTML-like messages, clipboard copy, history search/filter/recovery,
  project creation, document editing, display-name persistence, theme persistence
  and System changes, notification switches, reduced-motion voice preview, local
  file metadata and clear-history confirmation/Cancel.
- 48-character unbroken display names passed overflow checks for Profile, Home,
  Task and History at 320 and 390 px.
- No browser console errors or failed asset requests.
- Inspected light/dark desktop and mobile captures, including Home, Task,
  Conversation, Models, Profile, Settings and Canvas.
- The single-file HTML export passed the same suite with all other network
  requests blocked. Fonts, original artwork, styles, scripts and the complete
  font license are embedded.
- An intentionally reversed draft/Voice/Send condition was rejected by the
  browser test. The original source was restored before the final export/check.
- JavaScript syntax check passed: `node --check design/preview.js`.

The final export command and browser check exited 0:

```sh
python design/export_preview.py /workspace/Dua-AI-Android-preview.html
python design/verify_preview.py --offline-html /workspace/Dua-AI-Android-preview.html --output /workspace/dua-html-validation/final
```

This validation applies to the HTML design preview; it does not establish Android
permission behavior or live AI functionality.
