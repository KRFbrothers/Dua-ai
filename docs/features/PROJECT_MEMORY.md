# Project memory

Dua remains Fareed’s private, single-owner, server-first assistant. Personal keeps Settings → Memory and its existing device-controlled on/off switch. Coding, Business and custom projects have Projects → Memory with their own saved notes and on/off switches, initially off. Reviewed remember-statement proposals remain Personal-only; no automatic memory extraction was added.

## Editing and replies

- Add, edit and confirm deletion of individual notes; at most 10 per project and 200 UTF-16 code units per note. Turning memory off keeps notes. Archived projects show notes read-only.
- Save edits to the disposable cache, then sync before using them. `/api/config.projects.memory` must be explicitly true alongside `projects.enabled` to edit non-Personal memory. Refresh Feature setup after deploying the backend.
- Project-aware chat authenticates the allowed owner, reads the selected project from the authoritative server snapshot and includes only its enabled notes and instructions. Coding/Business/custom project requests never use client-supplied memory or global Personal notes. Personal uses its existing nonempty client memory as the enable signal, but actual note text comes from the server.
- Notes apply to text chat, retry, Build chat and hands-free Voice mode through the chat endpoint. They are untrusted preferences, not system rules. Other project notes/tasks are not added. Previously generated messages and rolling summaries are not rewritten when notes change; removing a note cannot erase context already present in a conversation. Start a new chat when that matters.
- Live realtime voice remains limited to Personal without custom instructions and uses its existing session protocol. Imagine prompts and generated-media endpoints do not apply project memory. Old APK requests without `projectId` retain the legacy Personal protocol.

## Persistence, sync and export

Non-Personal projects gain optional `memory: { enabled, notes }` in the existing version-1 JSON snapshot. Empty/off defaults omit the field to preserve old signatures. No database migration, additional API endpoint, permission or AI call is needed. Strict Android/backend validation rejects bad types, fields and bounds before writing. Personal project definitions cannot contain a duplicate nonempty memory object.

Cloud sync, preferences and JSON exports retain notes and switches with their project. Project settings/task edits and device-settings reset preserve project memory; resetting Personal memory retains its existing behavior. Automatic sync pauses for review when both copies changed the same project. Reviewed download replaces that project’s cached notes/switch with the server version; phone-only projects remain cached. The review displays notes and whether memory is on. The editor blocks saving/deleting a row that changed while it was open.

JSON exports **include project-specific memory**, alongside instructions and tasks, but continue to exclude global Personal notes, tokens and raw file bytes. Treat exports as private files. Imports with differing project settings create separate projects instead of overwriting current notes. Android backup/device transfer can include project memory in preferences, according to device settings; restored cache remains non-authoritative. Older APKs reject snapshots containing this new field, so update every device before syncing project notes.

## Verification

Automated coverage checks cloud/preferences/export round trips, legacy signatures, limits, sync conflict/download behavior, import copies, backend owner/project isolation and authoritative note resolution, editor actions and ViewModel restart/reset. Source implementation does not establish live provider or physical-device behavior. Phone checks: sync distinct Coding/Business notes, verify replies and memory-off behavior, restart, review a server download and inspect export/import in a separate project.
