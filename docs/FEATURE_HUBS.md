# Images, Library and Plugins

These are native Compose destinations reached from the existing sidebar (Plugins also appears under Aura and Settings). They use the existing `DuaPageTheme`/Page tokens and `FeatureTopBar`, with no Home model selector, Chat/Task switcher or starter rows. Global theme/colors are unchanged.

## Images

- The two-column gallery derives real images from current/saved chat media, stored/inline image attachments, project sources and the authenticated cloud inventory. No example images or conversations are inserted.
- The aspect menu offers supported 1:1, 16:9 and 9:16 choices and refresh. Empty-state chips only fill a description.
- Create calls `ChatViewModel.generateImageFromHub`, which uses existing Imagine configuration, owner authentication, normalization, quotas and image generation. An unavailable Imagine request never becomes ordinary text chat. Existing text drafts are saved in their own conversation before a separate image request; pending attachments/edited messages retain their existing checks.
- Generation status/errors and Stop remain visible. The description stays in the Images input for reuse/retry. Results are saved by the existing Imagine/history flow.
- Tapping a tile reuses the full-screen media viewer with prompt/filename, Share and Save. Downloads use existing owner-authorized signed URLs and media cache; Save uses the existing MediaStore export and Android 8–9 storage-permission flow. Inline images use bounded app-private cache files and thumbnail decoding. Their original transient bytes are not added to snapshots.

## Library

The derived index combines file references, inline attachments, retained attachment text, generated images, fenced assistant code, provider code runs and project sources. Current messages replace the corresponding cached conversation, so stale cached images do not reappear after current-turn edits. References are deduplicated; real filenames retained in conversations/projects take precedence over cloud object UUID names.

All/Files/Images/Code filters and filename/content search run locally over that index. Images open the viewer; text/code opens a selectable/copyable preview; stored files open through the existing authenticated signed-link flow. Items can return to their originating conversation or project. Switching conversations with unsent attachments requires sending/removing them first.

The upload button uses the existing document picker and attachment/upload pipeline, including its size/type limits. It attaches to the current chat draft; **send that draft to retain the files in conversation history**. Upload/progress/failure state is displayed. This does not add a separate permanent import store. Back preserves that draft and attachments.

### Cloud inventory endpoint

`GET /api/files/list` on the **Node base API** is additive and read-only. It reuses `resolveOwner`, the user's bearer token and `listOwnerFiles` (private owner folder, existing pagination cap of 10,000 objects). The response is `{ files: [{ path, name, mimeType, size }], truncated }`; names for unreferenced objects are their stored UUID filenames. It contains no signed URLs, credentials or raw object metadata. Non-owner/anonymous requests and write methods are refused. Existing RLS, schema and keys are unchanged; no service-role key is used for listing.

**Deployment:** after merging, redeploy `apps/api/` on the existing `dua-api` service using [the current deployment instructions](../apps/api/README.md). No new secrets/migration are required. Older backends return 404; the app keeps chat/project items available and shows a retry/update notice for cloud inventory. Refresh cloud files retries the listing. Android checks the response owner prefix and hides an inventory whose owner no longer matches the signed-in account. The inventory is transient, not another authoritative snapshot.

## Plugins

- The base API configuration supplies native grounding, Code Runner model support, connector setup groups and configured repository results. Setup links open the existing Integration setup page; toggles call the existing `setWebMode`/`setRunCode` handlers and remain subject to their guards.
- **Separate Python chat engine:** standalone `web`/`code` flags are unsupported there. When it advertises Agent availability, Plugins links Search/Code to AI Agent and explains that Agent manages those tools. It does not display a working standalone switch or invent JavaScript support. Base-API connector status does not claim to verify the Python engine's GitHub token.
- Memory opens the actual memory-enabled preference and current notes, plus existing reviewed suggestions and Cloud sync controls. It does not auto-save proposed notes, bypass owner checks or enable a paid integration.
- Refresh reloads current capabilities. Unknown/missing capabilities show setup information rather than simulated activity.

## Validation

`LibraryIndexTest`, `CloudLibraryTest`, `ImageHubGuardTest`, `FeatureHubUiTest` and the updated `FeatureIsolationUiTest` cover real/empty inventory, deduplication, current-vs-cached messages, owner filtering, no text-chat fallback, explicit routes, callbacks, search/filters, feature isolation, full-screen Share/Save callbacks and capability routing. UI tests cover light/dark, 360dp and 320dp/2× fonts with deterministic media. Node file tests mock owner listing and unauthorized/cross-origin/wrong-method failures.

These tests do not verify live provider quotas, installed file/share apps, physical-device MediaStore behavior or deployed Storage/RLS. Use a signed-in owner device after deployment to verify those integrations.
