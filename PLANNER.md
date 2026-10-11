# Daily planner

**Projects → Daily planner** shows Fareed's current tasks by deadline and project. It starts on Today in the phone's time zone. Use Previous/Next to browse another date, Today to return, and the project filter to see Personal, Coding, Business or a custom active project. Every row shows its project; browsing the planner does not change the current chat's project or AI context.

## Day view

- **Overdue before this day** contains open tasks with a deadline on an earlier local calendar day. When browsing a future day, this section is **Earlier unfinished**: those tasks may not be overdue yet.
- **Due on this day** contains open tasks whose deadline falls on the selected local date, ordered by deadline. An elapsed deadline is marked **Overdue**, including tasks due earlier today.
- **Completed · due on this day** contains tasks currently marked complete with a deadline on the selected date. It is a current status view, not a record of when tasks were completed.
- **No due date** is the open, unscheduled inbox. These tasks appear on every browsed day until a deadline is assigned; completed undated tasks remain available in their project task list.

Archived projects are hidden. Select a project, then **Manage [project] tasks** to add a task even when the planner is empty. **Open project tasks** on a row opens that project's task list to edit the title/deadline or delete a task. **Back to planner** keeps the browsed day and project filter. Completion/reopen controls use the existing task save/sync path; failed saves leave the current state visible with the existing error notice. A missing or archived filter after sync/import falls back to all active projects.

## Data and privacy

The planner derives its view from the existing server-backed project/task snapshot and phone cache. It has no separate planner store, new backend endpoint, migration, API calls or AI prompt. Task titles and dates are not automatically sent to AI providers. Completion and date changes use existing cloud review, conflict handling and JSON backups. Dates and reminders follow [REMINDERS.md](REMINDERS.md); task limits and persistence follow [TASKS.md](TASKS.md).

Tasks remain readable when backend task editing is unavailable; completion is disabled until Tasks setup is ready and the current action finishes. Opening a project task list does not silently switch chat context or sync data. Refresh/sync to see edits from another device.

Deadlines are absolute timestamps displayed/grouped in the phone's current zone. Calendar day comparisons handle 23/25-hour clock-change days. While this screen is resumed, its time/zone is refreshed at most once per minute and immediately on resume; Today follows midnight. A date chosen with Previous/Next stays fixed until Today is tapped. Clock/zone changes may take up to one minute to appear while the planner remains open. There are no background planner timers or new notification permissions.

## Scope and verification

This first daily planner organizes deadlines and the unscheduled inbox. It does not assign separate planned dates, create recurring tasks, propose an AI schedule, or connect a calendar. These remain follow-ups.

Automated checks cover date boundaries, time zones, both clock-back occurrences, project-local task IDs, archived/removed filters, live snapshot changes, correct completion targeting, failed saves, read-only setup, date navigation and returning from Tasks. Device layout, midnight/resume and authenticated cross-device sync remain pending. Install the updated APK after merging the due-date/reminder and planner changes, sync several Personal/Coding/Business tasks, and verify those flows on the phone.
