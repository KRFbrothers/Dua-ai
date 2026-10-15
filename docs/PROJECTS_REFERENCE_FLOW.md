# Dedicated Projects flow

The Projects list reads saved project definitions and project chat IDs. It never seeds example projects or conversations. A fresh profile retains only the internal `personal` Home workspace; that workspace is not a Projects row. Existing user projects are retained. Untouched legacy Coding/Business starter definitions are hidden; legacy definitions with real chats, tasks, instructions or memory remain accessible.

## Screens and actions

- Projects: All / Created by you / Shared with you, live name search, actual update dates, persisted icon/color and pin state. Shared with you is honestly empty: the current owner-only backend has no sharing service.
- New Project: name and badge selection, Homework/Investing/Writing form presets, an instructions editor and explicit Create Project. Presets do not create records.
- Project detail: the selected project's real Chats and Sources. Chat rows open the existing conversation handlers. The Chat/Work subtitle menu preserves the project frame; Work is Coming soon and sends no simulated request.
- The overflow menu starts a project chat, pins/unpins, edits the project/instructions, requests an Android launcher shortcut, or confirms project deletion. Deletion retains an archived definition/tombstone and existing chats/files. Those remain in History/Data controls; stale snapshot uploads cannot resurrect the deleted project.
- Camera, Photos, Files and Plugins are the four confirmed attachment actions. Chat attachments reuse the existing readers/upload handlers. Plugins opens its existing Coming soon destination.
- Existing tasks, memory, planner and archive settings remain available from Edit project → Tasks, memory & planner.

## Sources and backend setup

Source uploads use the existing authenticated `/api/files/sign` and private signed-upload flow. Only file references enter the synced project definition; signed URLs, access tokens and binary contents are not stored in the snapshot. The Sources tab displays actual upload progress/errors and Retry. Removing a source detaches project context; it does not delete a stored file used elsewhere.

The Vercel backend's project validator must be deployed with this APK so it accepts optional `details` and `sources`. No SQL migration or new Supabase SDK/API is needed. Existing owner auth, storage policies and cloud snapshot revision checks remain in use. Foreign-account source paths are rejected on writes; cross-project source reuse is rejected during context resolution. Keep separate copies for different projects.

For base-backend chat, source refs come from the authenticated server snapshot. For a separate Cloud Run engine, Android first loads the authenticated owner snapshot and rejects unsynced context differences; instructions, notes and sources come from that server definition. It hydrates the selected project's sources through owner-authorized signed URLs into the existing inline protocol on send/retry. Combined source/message limits remain 3 files / 2 MB; UTF-8 text sources are at most 256 KB. Use PDF, supported images, CSV or text/code with Cloud Run. Source-load failures fail the request with a retryable error rather than silently omitting project context. Cloud Run's bounded preference context increases to 8192 characters so a project's 4000-character instructions and up to 10 memory notes fit; deploy the included engine change for long instructions.

Notion/Drive cards are decorative empty-state artwork, not connected integrations. Real sharing, Work automation and Plugins require their own future integrations. Project voice uses the existing hands-free chat loop so it retains project context. Launcher support and microphone/camera/file-app availability depend on the device; the app reports unavailable actions.

## Validation and remaining visual verification

Native Compose checks cover empty real-data lists, presets, both detail tabs, project-only chats, menus, expanded input, light/dark themes, 320dp phones and 2× font scale. Project codecs and server validators cover metadata, sources, deletion retention, ownership and context isolation. Existing project/task/memory/history tests remain part of the full suite.

The seven reference screenshots and missing `ds_*` SVG paths were not attached to this task. Layout dimensions, typography and copy follow the written specification. Existing exact reference search/folder/new-chat/plugin vectors are reused; other controls use native app glyphs. Exact glyph/pixel matching and physical-device/permission checks still need the reference assets and a device. Preview conversations exist only in test fixtures, never production data.
