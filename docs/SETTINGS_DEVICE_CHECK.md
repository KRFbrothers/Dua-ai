# Settings completion

The Settings hub keeps its existing theme, account card, destinations and touch targets. Long summaries now wrap below their label when the measured text cannot fit beside it, including at larger Android font scales. Short On/Off values stay alongside the label.

| Entry | What it opens |
| --- | --- |
| Personalization | Existing appearance, font, haptics and persisted personality presets |
| Memory | Personal memory switch plus add, edit and confirmed delete; up to 10 notes of 200 characters |
| Plugins | Existing capability manager and connector setup |
| Imagine | Existing image/video defaults and authenticated daily usage |
| Accent color | Existing independent accent picker and reset |
| General | Device haptics plus appearance, notification and voice-language settings |
| Feature setup | Existing provider and integration diagnostics |
| Usage and limits | Links to the existing image/video, live-voice and private-storage meters; unavailable services are identified |
| Notifications | Separate device consent for reply alerts and project-task reminders, Android permission request and channel-settings recovery |
| Voice | Existing device/generated/live voice controls |
| Safety | Review guidance and links to real memory, plugin, data and Android-permission controls |
| Security and login | Account sign-in/password/sign-out/deletion flow, cloud sync, data controls and Android app permissions |

No backend contract or provider configuration changes are needed. Chat-wide token/cost totals are not supplied by the current backend; the usage page says so rather than displaying a zero or a paid plan. Voice/image/file usage still needs the corresponding configured service and owner sign-in. Safety does not claim an additional content filter or expose unsupported switches.

## Automated checks

```sh
./gradlew :app:testDebugUnitTest --no-daemon --max-workers=2
```

Focused coverage is in `SettingsCompletionUiTest`, `SettingsFunctionalNavigationTest`, `PersonalMemoryTest`, and `NotificationAccessTest`. Existing Settings, appearance, voice, Imagine, reminder and navigation tests remain in the standard suite. With `-Pdua.ui-record`, the UI tests also write light/dark hub review images under `app/build/approved-ui/`.

## Phone checks

1. Open Settings in Light and Dark. At large Android font sizes, check that Personalization, Imagine, Voice and Feature setup summaries wrap without hiding their labels. Check each screenshot entry and system Back.
2. In Memory, add a note, edit it, cancel a delete and then confirm a delete. Turn memory off and reopen the app: notes remain. In a project, verify its separate notes were unaffected. Changes are cached on the device; use the existing Cloud sync review flow to update the server copy. Do not treat local deletion as confirmed cloud deletion.
3. Deny notification permission: enabling an alert must not save new consent. Grant it, then enable reply alerts and task reminders separately. Block each notification channel in Android settings and return: the hub should show `Blocked by Android` when a saved consent cannot deliver. Re-enable the channel and return to refresh status.
4. Leave the app during a reply and check the generic private reply alert. Create a due task in a real project and check reminders using the existing planner flow. Delivery is subject to Android battery scheduling; the app does not request exact-alarm access.
5. In Security and login, verify signed-out Sign in opens the existing account flow; signed-in Manage account opens the correct account, and sign-out/deletion still ask for confirmation. Android settings must open for Dua AI only.
6. In Usage and limits, open each configured service to see its actual meter. Signed-out or unconfigured services must not show fabricated usage or a successful connection.

Storage, Search settings, Edit profile photo, Privacy, Sharing, Permissions and Screenshare destinations retain their prior availability; this change targets the entries in the supplied Settings screenshot. Android permission links on the new pages open system settings directly.
