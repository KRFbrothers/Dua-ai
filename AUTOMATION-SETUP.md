# Automatic sync and memory suggestions

Automatic sync uses the existing Supabase snapshot API. No new cloud project, paid service, provider key, scheduled function, or schema migration is required beyond the existing account/cloud setup. It sends saved chat text, drafts, and confirmed memory notes; file/audio bytes are excluded. It remains off until the signed-in user explicitly enables it on this device.

## Web integration

Load `app-settings.js`, `cloud-merge.js`, `chat-history.js`, and `account-client.js` before `auto-sync.js`. Install one controller on each active history page:

```js
const sync = DuaAutoSync.install({onStatus: state => {
  statusElement.textContent = state.message;
}});
await sync.restore(); // Restores prior consent only; does not enable a new device/account.

// Explicit, labelled user actions:
await sync.enable();
sync.disable();
await sync.retry();
// After the user has reviewed the existing manual Cloud sync screen:
await sync.resumeAfterReview();
```

Dispatch `new Event('dua-history')` after successful same-tab history writes. Preference writes already dispatch `dua-settings`. Alternatively, call `sync.changed()` directly after persistence. Notifications are debounced by 1.2 seconds. `flush()` can be used for a user-triggered immediate check. Repeated unchanged snapshots do not upload again. The injected `DuaAutoSync.create({account, local, onStatus, persist, setTimer, clearTimer})` interface makes the coordinator independently testable.

The coordinator persists a bounded deletion baseline, last cloud revision, account binding, and review state under `dua-automation-sync-v1`. Reloads and navigation preserve those guards. Sign-out or an account switch pauses sync. Cloud reads return the verified `ownerId`; uploads send `expectedAccountId`, which the server compares with the authenticated user before writing. A second tab changing this record pauses the first controller, preventing two active tabs from repeatedly writing the same snapshot.

Sync checks while the page is visible and pauses on backgrounding. It does not run after the page closes. Network failures retry after 2, 4, 8, and 16 seconds; the fifth failure requires an explicit Retry. Browser timers can run later than requested. Snapshot limits remain 100 chats, 500 messages per chat, 10 memory notes of 200 characters, and 4 MiB in total.

## Changes that require review

- A locally or remotely removed chat, shortened message list, or removed memory note pauses automatic synchronization before merging/uploading. Deletions are never propagated in the background.
- A concurrent cloud revision change stops the upload. Remote additive changes are merged locally when safe, conflicts become stable separate copies, and further writes wait for manual review.
- Invalid data, excessive combined size/count, and account changes also pause for review.
- The existing manual Cloud sync page remains the place to inspect snapshots and intentionally upload, download/merge, or clear the cloud copy. Only choose **Resume after review** when the resulting local/cloud contents are understood. This acknowledges a new baseline; it can upload local data remaining after an intentional cloud clear.

Changes to an existing cloud chat are conservatively preserved as copies when they differ from local content. Automatic sync is a foreground snapshot coordinator, rather than a realtime collaborative document store. Original attachment bytes and simultaneous pending replies are outside its scope.

## Memory suggestions

`memory-suggestions.js` provides a local candidate generator. It does not contact a model and never invent personal facts. Extraction is off by default; candidates are limited to explicit instructions such as `Remember I prefer Hindi`, `Yaad rakho I like tea`, and `याद रखो मैं हिंदी पसंद करता हूँ`. Likely passwords/API keys/tokens are excluded from suggestions. Every note still requires individual confirmation:

```js
const suggestions = DuaMemorySuggestions.create({preferences: DuaPreferences,
  onChange: ({candidates}) => renderConfirmationButtons(candidates)});
suggestions.enable(); // Explicit extraction consent, independent from cloud sync.
suggestions.propose(recentUserPrompt);
suggestions.confirm(selectedCandidate); // Only candidates currently displayed are accepted.
suggestions.dismiss(selectedCandidate);
suggestions.disable();
```

Confirmation saves a local memory note but does not enable sending memories in future AI requests. The existing **Use saved memory** preference controls retrieval separately. There are no silent memory writes, and full/invalid memory storage preserves existing notes.

## Android foreground automatic sync

Install the updated APK, sign in, then open **Settings → Cloud sync → Automatic sync on this device**. Android uses the same Supabase snapshot API with explicit account-bound consent. It debounces local changes by 1.2 seconds and checks for cloud changes every 30 seconds while foreground, online and idle. Replies, account actions, manual snapshot reviews and import/export/clear operations pause it. Returning to the foreground can resume previously enabled consent for the same account; a new account/device must explicitly enable it.

`AutomaticCloudSync` runs in the ViewModel scope. Backgrounding, disabling, going offline or switching accounts invalidates pending work before another merge/upload. Active message/draft/model buffers and attachment selections stay intact. Changed cloud chats become stable separate copies, and both-side edits require review. Cloud uploads include the expected revision and account ID; revision conflicts pause writes. Deletions, invalid snapshots, combined storage limits and storage failures also pause. Retry is bounded to 2, 4, 8 and 16 seconds before explicit Retry is required.

Consent, review state, last revision and digest-only deletion/content baselines are synchronously stored in `dua_auto_sync.xml`, bound to the backend and account. This store is excluded from Android cloud backups and device transfer. A corrupt record leaves sync off until **Reset automatic sync settings**; resetting consent never deletes chats or notes. Use manual snapshot controls before **Resume after review**. Resuming acknowledges current data and may upload local data remaining after an intentional cloud clear.

This is foreground synchronization, with no WorkManager job, background service, file/audio upload or realtime collaborative editing. `AutomationMemoryCandidates` still uses individual local proposal/confirmation rules. Physical-device verification remains necessary for lifecycle, connectivity, storage-full behavior, process recreation and simultaneous web/Android edits.

Run `node --test automation.test.mjs` in `web` for coordinator and candidate checks. Native policy, coordinator, durable-store and account-race tests run with the Android unit-test task. These tests use injected local storage and transport; they do not send user data to a provider.
