# Dua AI Personality

Open **Settings → Personalization → Dua AI Personality** and select one of:

| Preset | Response style |
| --- | --- |
| 🎓 Deep Researcher | In-depth analysis, structured citations |
| ⚡ Direct & Concise | Short, straight-to-the-point answers |
| 💻 Senior Architect | Clean code, zero fluff, production standards |
| 💡 Creative Brainstormer | Witty, ideas, storytelling |

The choice is stored in the existing `dua_appearance` DataStore on this device.
Theme, accent, font and the memory switch are independent. Existing installations
start without a personality override until a preset is selected. Reset settings
clears the override. Unknown/retired stored IDs are ignored.

`ChatViewModel` snapshots the selected preset's instruction for each send or retry.
`DuaApiClient` sends it as the optional, bounded `personality` request field, separate
from `memory`, conversation turns and project context. Both chat backends place
this style instruction after the base system prompt and before memory/context.
The instruction is a user preference and cannot override system rules. Deep
Researcher requests citations when sources exist; it does not enable web search
or invent citations.

Node project resolution still uses authoritative project instructions/notes and
the existing Personal memory switch. A personality alone never enables remembered
Personal notes or copies them into another project. The style applies to normal
chat, Search, Reasoning, Agent and Build requests through their existing paths.
It does not alter Imagine, title generation or the separate live-audio protocol.

## Backend rollout

Deploy the updated chat backend(s) using the existing [Node deployment steps](../apps/api/README.md)
or [Google Cloud engine deployment steps](../google-cloud-backend/README.md):

- Node `apps/api/`: `/api/config` must include `"personalization":{"personality":true}`.
- Python `google-cloud-backend/`: `GET /` must advertise the same object. This is
  the capability the Android app checks when a separate chat engine is configured.

After deployment, refresh **Feature setup** or restart the app. If the actual chat
backend has not advertised support, a selected preset produces an explicit update
message before a chat is sent. A device with no selection keeps the existing
request format. No new secrets, account setup or database migration are required.

## Validation

Tests cover DataStore restart/reset and invalid IDs, four selectable accessible
rows in light/dark at normal and 200% font sizes, independent appearance/memory
choices, both Android request destinations, capability parsing, and backend
instruction placement alongside authoritative project context. Existing Build
tool-loop coverage also checks personality forwarding. Phone acceptance check:
select a preset, restart, verify the selection, then send and retry a chat with
memory off and with project instructions enabled.
