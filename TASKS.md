# Project tasks

This first to-do version keeps Fareed's Personal, Coding, Business and custom project tasks separate. It adds a list, not reminder alarms or a daily planner.

## Use

1. Open **Projects → Tasks** beside a project.
2. **Add task**, enter a title, and **Save task**. Edit a title, check completion, or uncheck to reopen it. Delete asks for confirmation.
3. Enable foreground automatic Cloud Sync, or use **Cloud sync → Review upload → Confirm**. The phone caches edits; the owner server snapshot is authoritative after sync.
4. Completed tasks stay visible below open tasks. Archived projects keep their readable tasks; restore the project to edit them.

Each project allows 100 tasks including completed ones, with titles up to 200 UTF-16 code units. Delete a finished task to make room. Titles are plain text. Tasks are not automatically sent to the AI provider or changed by AI chat. Notifications, due dates, recurring tasks and a planner remain follow-ups.

## Persistence and recovery

Tasks are an optional `tasks` array on each project in the existing version-1 owner JSON snapshot. Each item has a unique project-local `id`, a `title`, and a Boolean `completed`. The existing owner authentication, Supabase RLS, revision checks and 4 MiB snapshot limit apply; no SQL migration is needed.

Projects without tasks retain their old wire representation and sync signature. The updated Android app reads older snapshots, preferences and exports. `/api/config.projects.tasks` must be true alongside `projects.enabled` before the app allows task edits. Deploy the new backend and install the updated APK together. Older APKs cannot read task-bearing project definitions; use the updated APK for cloud reviews and backups after adding tasks.

Automatic sync includes task changes in each project's existing digest. One-sided edits upload/download; differing edits on both copies pause for manual review, including when different tasks in one project changed. Reviewed download uses the server's complete project settings and task list; export the phone cache first if you want to preserve different local tasks. The review displays task titles and completion. Reviewed upload replaces the server snapshot, and clearing the cloud snapshot clears its tasks too.

JSON chat exports include project tasks even with no chats. Import reuses identical definitions and creates a separate project for differing instructions or task lists, retaining existing tasks. Renaming, changing instructions, archiving/restoring, clearing chat cache and resetting appearance/memory preserve readable task settings. Android backup/device transfer may include cached project preferences, including tasks; chat history, account sessions and sync consent remain excluded. Keep exported files private.

## Verification

Automated coverage checks codec limits/malformed data, old sync signatures, completion/deletion/conflicts, task-only exports/import copies, restart/reset preservation, backend capability gating, provider prompt privacy and Compose add/edit/complete/delete flows. Physical-device and production task sync checks remain pending: add tasks to Coding/Business, sync, restart, verify separation and completion, then review a download and export/import a task-only backup.
