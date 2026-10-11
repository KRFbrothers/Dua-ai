# Projects and custom instructions

This first Projects version organizes Fareed’s one owner account into Personal, Coding and Business, with additional custom projects. It does not add other accounts or collaborators.

## How to use

1. Open **Projects** on Home or above the conversation.
2. Select a project. Switching saves the current draft and starts a new chat in the selected project. Open its existing chats from the history menu; search shows only that project’s history.
3. Tap **Edit**, enter a name and custom instructions, then **Save project**. Use instructions for preferred language, coding conventions or business context.
4. Enable automatic foreground Cloud Sync, or use **Cloud sync → Review upload → Confirm**. New project definitions and instructions must reach the server before they apply to replies. The editor shows a reminder to sync.
5. Archive a finished project to keep its chats while blocking new replies; Edit can restore it. At least one project must remain active.

The phone keeps a disposable cache. Projects and instructions are part of the authoritative owner snapshot. Clearing chat cache retains project settings; resetting appearance and memory preserves readable cached project settings. If damaged preferences lost project definitions, reviewed cloud download restores matching server definitions before using those chats.

## API and compatibility

- The existing version-1 cloud snapshot gains an optional `projects` collection. Each chat gains `projectId`. No database migration is needed; the existing JSON snapshot RPC stores these fields under the existing owner RLS rules.
- Old snapshots/chats without these fields use Personal/Coding/Business defaults and put old chats in Personal. Existing automatic-sync baselines remain readable; Personal chat digests retain their pre-Projects representation.
- `/api/config.projects.enabled` tells the updated APK whether the backend supports project-aware chat. The APK sends `projectId` beside its conversation `context`; instructions are never taken from a request instruction field.
- The backend authenticates the allowed owner and reads project instructions from the server snapshot before calling the provider. Missing or archived projects, mismatched saved conversation IDs, unavailable cloud reads and stored-file references found in another project are rejected.
- Older APK requests retain their old protocol. Install the new APK for project separation. All projects still use the same owner account and private Storage bucket; this is not an authorization boundary between different people.
- Limits: 20 projects including archived ones, unique IDs of 1–100 ASCII letters/digits/underscore/hyphen, 80 UTF-16 code units per name, 4,000 per instruction. Personal cannot be removed. The complete snapshot/export remains capped at 4 MiB.

## Sync and backup

- Automatic sync uploads project changes when the server still matches the last synced settings, accepts remote changes when the cache is unchanged, and pauses for reviewed recovery when both copies changed differently.
- Reviewed download uses server settings for shared project IDs and keeps phone-only project definitions so their chats retain a valid reference. It replaces differing cached instructions after confirmation; the review shows all project settings. Reviewed upload replaces the server snapshot.
- Chat JSON export includes project definitions, instructions and chat references. It still excludes global memory notes, account tokens and raw file bytes. Treat exports as private files.
- Import reuses identical project definitions and gives differing imported instructions a fresh project ID and an “(imported)” name. It creates fresh chat/message IDs, preserves current settings, and validates the combined limits before writing. Definitions are persisted before imported references; an interrupted import may leave unused project settings, but does not create chats with missing definitions.
- Android backup/device transfer exclude chat history, account sessions and sync consent. Other preferences, including cached memory and project instructions, may be backed up under device settings.

## What this version covers

Text chat, chat search/history, summaries, retries, Build chat, hands-free Voice mode, cloud snapshots and JSON backup carry project context. Global enabled memory and reviewed memory suggestions are Personal-only. Other projects use their instructions for context; dedicated editable memory per project is future work.

Files currently belong to the chats where they were attached. The model is not automatically given another project’s history or files. A dedicated project file library, per-project connected-tool configuration, daily planner/recurring tasks, calendar/email integration and recurring business workflows are follow-ups. Existing GitHub writes still require the owner’s diff review and explicit confirmation.

Live realtime voice currently works only in Personal with no custom instructions. Other projects use hands-free Voice mode so project instructions go through the scoped chat endpoint. Imagine prompts retain their chat’s project when saved but do not apply text-chat custom instructions to image/video generation.

Project to-do lists are now available through **Projects → Tasks**, with cloud/JSON persistence. Task titles are not automatically sent to the chat provider. See [TASKS.md](TASKS.md) for limits, compatibility and recovery, and [REMINDERS.md](REMINDERS.md) for due dates and opt-in device reminders.

## Verification

Code includes backend owner/auth/instruction isolation tests, Kotlin snapshot/preferences/backup and sync-conflict tests, plus real ViewModel switching, archiving and restore tests. CI checks these alongside the existing suites. Physical-device and live production Projects verification is pending: install a merged APK, sync different Coding/Business instructions, send a reply in each, restart, and check each project’s draft/history.
