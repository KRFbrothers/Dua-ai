# Chat history search

Open the history menu and enter a word or literal phrase in **Search chats**. Search now covers the current project's cached chat titles, user messages, assistant replies, drafts, and saved Run code source/output, including text beyond the title and last reply. Markdown/code fences are searched as text. Matching is case-insensitive, with surrounding query whitespace ignored; it is not regex, fuzzy or semantic search.

Long-press a recent chat → **Rename** → edit **Chat name** → **Save**. Names allow 1–120 UTF-16 code units on a single line; surrounding whitespace is trimmed. Renaming keeps the original messages and chat ID. Older chats retain their derived titles. Custom titles survive restart, JSON backup/import and the existing authenticated Cloud Sync flow; saving locally does not claim that a cloud upload has completed. Refresh setup after deploying the matching backend before renaming. Finish an active reply or sync review before saving a name. Pin is still Coming soon.

Results stay grouped by last-updated date. Each matching chat appears once with a short snippet and highlighted match. Draft matches take priority; otherwise the most recent matching message/code/output supplies the snippet. Tap a result to open that conversation normally, or **Clear search** to restore the full list. Opening does not jump directly to the matching turn in this version. Queries allow up to 200 UTF-16 code units; snippets retain bounded surrounding context, collapse whitespace and preserve emoji pairs at their edges.

## Server-first data and project scope

The owner server snapshot remains authoritative. Search runs on the phone's currently loaded cache, including unsynced draft edits; sync or reviewed download to refresh it. It cannot find server chats absent from that cache, deleted cached copies, raw stored-file contents, attachment text, thinking summaries, rolling summaries, project instructions or memory. It makes no provider/API request and does not create an index or write chat data. Query/snippets are transient UI state, not part of cloud snapshots or JSON exports; standard Android UI saved-state restoration can retain the query. Switching projects clears the query and scopes results before scanning.

Nonblank queries are debounced by 150 ms and scanned on a background dispatcher, with cooperative cancellation between chats/messages/code runs. A new query, project or snapshot invalidates old results immediately; pending scans show **Searching chats…**. Empty/cleared search returns cached chats immediately. No extra background service, storage permission, migration or provider cost is added.

## Verification

Automated coverage checks older turns, title truncation, drafts, newest matches, literal code patterns, code output, emoji/multiline snippets, hidden-data exclusion, cancellation, opening/clearing results, project switching and snapshot/query changes. Physical-device typing/rendering and authenticated sync refresh remain pending. On the updated APK, sync a Coding conversation with a distinctive phrase in a middle reply, search for it, open/clear it, then verify the same phrase in Business cannot appear while Coding is selected.
