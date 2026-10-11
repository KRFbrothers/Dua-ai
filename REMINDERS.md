# Task due dates and device reminders

Task deadlines belong to the server-backed project snapshot. Reminder consent and notification delivery history belong to this phone. No new server job, push service or SQL migration is needed.

## Use

1. **Projects → Tasks → Add task/Edit**: optionally enter **YYYY-MM-DD** and a 24-hour **HH:mm** time. Both fields must be filled or both empty. The editor shows the phone's time zone. **Remove due date** clears both.
2. Save and sync. The deadline is an absolute epoch-millisecond timestamp (`dueAt`); another device displays it in its own zone. A title-only edit preserves the exact saved timestamp.
3. **Settings → Notifications → Task reminders on this phone**: turn it on and grant Android notification permission when asked. This is separate from reply notifications and off by default.
4. **Check task reminders** retries scheduling from the current cache. **Reset task reminder settings** turns reminders off and clears delivery history, keeping task dates. Enabling again can remind about overdue tasks again.

Invalid dates, partial input and nonexistent times during a clock-forward change are rejected. An ambiguous clock-back time uses its earlier occurrence for a new deadline. Stored timestamps remain unchanged when a title is edited, even during such an overlap. Past deadlines are allowed and eligible for one overdue reminder when reminders are enabled.

## Scheduling and privacy

The phone uses one inexact Android alarm for the earliest undelivered, incomplete task in an active project. A due alarm checks the latest cache and posts one generic notification for the due tasks; private task titles are not shown. Delivery keys hash project/task IDs and deadline, so completed/reopened tasks do not repeatedly notify for the same deadline. Changing the due date creates a new delivery key.

Task edits, completion, deletion, archive/restore, import/download and settings reset reconcile that alarm. Launch/resume, reboot, package updates and clock changes rebuild scheduling from the cache. Completed, deleted or archived tasks cannot trigger stale pending notifications. Notifications can work without network access; they reflect the last synced cache, so edits on another device apply here after sync.

Delivery is best effort. Android may delay alarms under battery saving/Doze; force-stop, denied/blocked notifications or storage failure can prevent delivery. A process interruption between persisting a delivery marker and posting can also lose a notification. These are task reminders, not exact alarm-clock alerts. No exact-alarm permission is requested. The task list and due dates remain available for manual review.

`dua_task_reminders` stores this device's opt-in and delivery digests, and is excluded from Android backup/device transfer. Deadlines stay in project preferences, cloud snapshots and JSON exports/imports. Task titles/deadlines are not automatically added to AI provider prompts. Resetting local appearance/memory settings also turns reminder consent off while preserving readable projects and task dates.

## Compatibility and verification

Due dates require `/api/config.projects.enabled`, `projects.tasks` and `projects.dueDates`. Deploy the new backend and updated APK before adding them. Older snapshots/tasks omit `dueAt` and keep their existing representations/digests; the new APK reads them. Older APKs cannot decode dated tasks and must be updated before cloud reviews or imports.

Automated tests cover strict timestamp/date validation, zones/clock changes, backup/sync persistence, UI date edits/removal, one-alarm scheduling, consent, delivery markers, reboot reconciliation, stale broadcasts, notification privacy and backup exclusions. Phone/production verification remains pending: set a near-future deadline, enable reminders, background the app, then test sync/restart, completion/date edits, reboot and denied notifications. A daily planner and recurring tasks remain follow-ups.
