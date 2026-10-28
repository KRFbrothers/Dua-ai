# Gemini Android recording: observed UI and interactions

This is reference analysis, not a report of changes to Dua AI.

## Source and method

- Owner-selected reference: Gemini Android app.
- Source: owner-supplied private screen recording; its share link is not published.
- Container: MP4, 21,182,770 bytes, 159.858356 seconds; video is 720 × 1584 pixels.
- SHA-256: `de2f456b7b1f93e2a5e1c3576303e1525a6131b3325bb35443bfb2651cae603b`.
- Reviewed 40 overview samples at approximately four-second intervals and
  17 additional full-resolution samples. This is sampled video analysis, not
  a live interaction test or frame-by-frame verification of every transition.
- Time ranges below refer approximately to elapsed media time. The phone's
  recording timer can differ slightly from media timestamps.
- Raw video and extracted frames remain outside versioned source. Account
  details, notebook names, conversation titles, and media shown in the recording
  must not become app fixtures or hardcoded interface content.

## Observations

| Evidence id | Approximate time | Visible state or transition | What this establishes |
| --- | --- | --- | --- |
| G01 | 00:04–00:08 | Light Home: menu, brand/model label, new-chat icon, avatar, centered mark/greeting, Chat/Task selector and rounded floating composer | Home composition and visible controls; model choices were not opened |
| G02 | 00:08–00:10 | Task selection changes the welcome text and shows three starter prompts | Chat and Task have different empty content |
| G03 | 00:12–00:16 | Keyboard is visible; composer sits above it; typed text exposes an arrow action | Focused/draft layout and a visible send affordance |
| G04 | 00:16–00:26 | Sent user bubble, animated-looking gradient/progress labels, square composer action, then response content | Distinct pending and response states; no network/progress timing contract is established |
| G05 | 00:28–00:46 | New Home/Task interaction, another sent message, Thinking it through label and changing gradient | Empty/task/pending/reply progression appears more than once |
| G06 | 00:48–00:54 | Reply toolbar has thumbs up/down, share, copy, overflow and speaker; explanatory AI caption below | Action affordances and arrangement; their side effects were not exercised |
| G07 | 00:55–00:57 | Header overflow opens a rounded menu: Pin, Rename, Help, Feedback, Delete | Conversation-level options and menu appearance; no rename/delete/pin result or confirmation is established |
| G08 | 00:58–01:02 | Compact attachment sheet has Photos, Camera, Files and a Computer row | One captured attachment-menu variant; selecting a picker was not demonstrated |
| G09 | 01:04–01:15 | Voice action transitions to controls for plus, video, central animated-looking surface, microphone and close; voice plus sheet shows Screen share, Photos, Files | Voice entry and overlay layout; live speech, video, permission handling and screen-share success are unverified |
| G10 | 01:16–01:28 | History/navigation opens with title, search, close, navigation rows, notebook area, chat area, account footer and settings | Drawer/navigation hierarchy, scrolling list and footer composition |
| G11 | 01:28–01:34 | History status menu exposes All, Ready, Needs attention, In progress | Filter affordance and menu choices; actual filtering results are not verified |
| G12 | 01:34–01:40 | History scrolls and pinned chat indicators are visible; returns through Home/navigation | Pinned and unpinned visual treatment; no pin mutation is demonstrated |
| G13 | 01:40–01:51 | Settings scrolls through grouped cards and chevron rows | Visible settings hierarchy; only selected subpages were visited |
| G14 | Around 01:52 | Account/profile overlay shows account card, account/subscription controls, Settings, Feedback, Help and close | Profile overlay composition; subscription/account actions were not executed |
| G15 | 01:56–02:00 | Media page is visited; back/title are visible with an otherwise blank body during the captured interval | Captured Media screen appearance; not proof of a successful empty-state load |
| G16 | 02:02–02:13 | Library first shows placeholders/loading, then document cards and a media grid | Library loading-to-content progression; opening individual items is not shown |
| G17 | Around 02:20 | More expands additional drawer items, including Customise, Videos and Gems | Expanded navigation state, not verified destination behavior |
| G18 | 02:24–02:35 | Home attachment sheet expands; horizontal quick-action carousel scrolls; visible rows include Create images/videos/music, Canvas, Deep Research, Guided Learning and a Personal Intelligence toggle | Expanded attachment variant and carousel behavior; feature results and toggle persistence are not verified |
| G19 | 02:36–end | Returns to Home with Chat/Task selector and composer | Visible dismissal/return to Home |

### Settings labels observed

The recording shows Manage account and Usage limits; Notifications, Personal
Intelligence, Avatar, Task settings, Theme, Skills, Voice, Media, Device controls,
Notebook and Browser; and lower data/privacy entries. Labels describe the
reference. They do not authorize Google branding or imply that Dua has those
services. Only wire the corresponding Dua actions where a real capability exists.

## Interpretation boundaries

- The model label is visible, but the model-selection menu is not captured.
- Draft growth across many lines, keyboard dismissal order, rotation, TalkBack,
  font scaling, and dark appearance were not demonstrated.
- Reply actions are visible; copy/share/speak/feedback results and message
  long-press menus were not demonstrated.
- Upload completion, file-preview editing, retry/error, microphone permissions,
  camera permissions, and offline states were not demonstrated.
- The two attachment variants are observed in different contexts. Their exact
  rule is not established; do not infer a condition solely from proximity in time.
- A screen recording cannot establish backend implementation, persisted state,
  API authorization, automation execution, accessibility semantics, or exact
  animation curves. The actual density and app version were not provided.

## Dua mapping for implementation review

These are existing source boundaries to inspect fully before implementation,
not proposed replacement source or claims of runtime equivalence.

| Reference area | Existing Dua boundary | Mapping decision |
| --- | --- | --- |
| Home/header/composer | `ChatScreen`, `ChatTopBar`, `ChatContent`, `ChatComposer`, `ui/theme/` | Apply observed layout/state patterns while using Dua artwork and tokens |
| Message state/actions | `ChatUiState`, `ChatActions`, `MessageList`, `MessageActions` | Preserve real send/stop/retry/copy/share/speech behavior and request guards |
| History/options | `HistoryDrawer`, chat history and `ChatActions` | Use real chat identities, persistence, pin/rename/delete capabilities |
| Attachments | Protected `AttachmentSheet`, existing picker/upload actions | Scope any layout change explicitly; preserve real picker and upload paths |
| Voice | Existing voice destinations and `ChatActions` callbacks | Expose only actual available dictation/live capabilities |
| Profile/settings | `DuaNavHost`, settings/account features | Use existing account, preference and navigation owners |
| Library/more destinations | Existing Library, Images, Projects and other feature routes | Match useful layout patterns with real Dua content and honest availability |

This document records reference analysis only. Implementation ownership is in
`LEGACY_CHAT_SHELL.md`; actual verification results are in `tasks/todo.md`.
