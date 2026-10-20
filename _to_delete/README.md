# Yeh files delete karne se pehle padho

Ye folder abhi **khaali** hai — sirf yahi README hai. Yahan aage woh files aayengi jo delete ke liye review mein hain (path structure same rahega, taaki `git diff` mein saaf dikhe).

Sab line numbers `main` branch ke commit **`e6fac6e`** par **20 Oct 2026** ko fresh grep se liye gaye hain. Purane numbers copy nahi kiye.

> **Pass 2 update (20 Oct 2026, `main` @ `c947d09`):** Neeche naya section **"Pass 2 — poore repo ka dead-code scan"** add hua hai. Us pass mein **0 files** dead mili, isliye is PR mein koi file move nahi ki gayi. Saath hi ek pichhli galti bhi sudhaari gayi hai — **"Ye teen pakka dead hain" wala section ab GALAT hai**, neeche correction padho.

---

## ⚠️ Traps — inhe delete MAT karo

Ye files ka naam dekh kar "0 reference" lagti hain, par zinda hain. Wajah: file ka naam kahin nahi aata, par **andar ka function/class** use hota hai.

| File path | Status | Kaun use karta hai (exact file:line) | Delete karne par kya hoga |
| --- | --- | --- | --- |
| `app/src/main/java/com/dua/ai/ui/screens/AssetFeatureRoutes.kt` | **ZINDA** | `ui/navigation/DuaNavHost.kt:227` → `ImagesScreen` (declared `AssetFeatureRoutes.kt:30`)<br>`ui/navigation/DuaNavHost.kt:231` → `LibraryScreen` (declared `AssetFeatureRoutes.kt:33`) | Build fail. Images aur Library screens nav se hat jaayengi — app mein woh pages khulne band ho jaayenge |
| `app/src/main/java/com/dua/ai/ui/screens/FeatureScreens.kt` | **ZINDA** | `ui/chat/ChatScreen.kt:102` (import) → `ChatScreen.kt:138` par call. `ProjectsFeaturePage` declared `FeatureScreens.kt:14` | Build fail. Chat ke andar Projects ka poora page toot jaayega |
| `app/src/main/java/com/dua/ai/ui/screens/PluginsRoute.kt` | **ZINDA** | `ui/navigation/DuaNavHost.kt:237` → `PluginsScreen` (declared `PluginsRoute.kt:15`) | Build fail. Plugins screen nav se hat jaayegi |
| `app/src/main/java/com/dua/ai/network/ChatStream.kt` | **ZINDA — sabse khatarnak** | `network/DuaApiClient.kt:195` → `ChatStreamParser()` (class declared `ChatStream.kt:39`) | Build fail. **Streaming chat poora band** — AI ka jawab word-by-word aana ruk jaayega |
| `app/src/main/java/com/dua/ai/ui/theme/MarkdownStyles.kt` | **ZINDA** | `ui/chat/MarkdownMessage.kt:4` (import) + `MarkdownMessage.kt:57`. `markdownBodyStyle` declared `MarkdownStyles.kt:11` | Build fail. Markdown wala message text render hona band |
| `app/src/main/java/com/dua/ai/ui/theme/ProjectTokens.kt` | **ZINDA** | poore app mein `ProjectTheme` ke **23** hits. Jaise `ui/projects/ProjectChatRoute.kt:7,26` aur `ui/projects/NewProject.kt:24,87` | Build fail. Saare Projects screens ka theme/colour gayab |
| `app/src/main/java/com/dua/ai/backup/drive/DriveBackupWorker.kt` | **ZINDA** | `DriveBackupScheduler` (same file, line 19) ise line 20 aur 26 par enqueue karta hai. `DriveBackupScheduler` `backup/drive/AutomaticDriveBackups.kt:27` se chalta hai | Build fail + **user ka automatic Drive backup band**. Yahan build pass bhi ho jaaye to bhi runtime par backup chupchaap toot sakta hai |

### `DriveBackupWorker` ke baare mein ek sudhaar

Pichhli audit mein likha tha ki yeh "manifest string se uthta hai". **Woh galat tha** — `app/` ke `*.xml` files mein `DriveBackupWorker` ka **koi zikr nahi**. Asli baat yeh hai:

1. Class ko seedha `DriveBackupScheduler` (same file) call karta hai — line 20, 26.
2. Us scheduler ko `AutomaticDriveBackups.kt:27` chalata hai.
3. Aur WorkManager apna pending kaam **apni database mein class naam ke saath** yaad rakhta hai, phir runtime par reflection se worker dobara banata hai. Isliye grep kabhi poora sach nahi batata.

---

## ❌ CORRECTION — "Ye teen pakka dead hain" wali baat GALAT thi

Pichhle pass mein likha tha ki ye teen files dead hain. **Woh galat tha.** Pass 2 mein poore repo ka symbol-level scan chalaya, aur teeno ke andar ke symbols **production code se use ho rahe hain**:

| File | Symbol | Kaun use karta hai (production) |
| --- | --- | --- |
| `ui/chat/TelegramInterpolators.kt` | `TelegramChatAnimator` | `ui/chat/ChatSwipeReply.kt:222` |
| | `TelegramCubicBezier` | `ui/components/TelegramThemeSwitch.kt:41` (import) + `:51` |
| | `TelegramDecelerate` | `ui/chat/ChatSwipeReply.kt:138`, `ui/chat/TelegramMessagePopup.kt:104`, `ui/chat/TelegramPageDown.kt:72,73` |
| `ui/chat/TelegramImageReveal.kt` | `TelegramImageCrossfade` | `ui/chat/ImagineMedia.kt:81` |
| | `imageReveal` (Modifier extension) | `ui/chat/ImagineMedia.kt:90` |
| `ui/history/TelegramDialogSwipe.kt` | `ChatUndoBar` | `ui/history/HistoryDrawer.kt:241` |
| | `DialogSwipeAction` | `ui/history/HistoryDrawer.kt:227,229` |
| | `DialogSwipeRow` | `ui/history/HistoryDrawer.kt:226` |
| | `SwipeDeleteIcon` | `ui/history/HistoryDrawer.kt:229` |
| | `SwipePinIcon` | `ui/history/HistoryDrawer.kt:227` |

**Galti ki wajah:** pehla scan **file ke naam** se grep kar raha tha (`TelegramInterpolators`), jo kabhi import nahi hota. Asli symbol ka naam alag hai (`TelegramChatAnimator`). Isliye "0 hits" mila — aur woh jhooth tha.

**Inhe delete MAT karo.** Teeno `THIRD_PARTY_NOTICES.md` ke Telegram section mein bhi hain, isliye delete karne par license trail bhi tootega.

---

## 🔍 Pass 2 — poore repo ka dead-code scan (20 Oct 2026)

**Result: 0 files dead mili. Is PR mein koi file move nahi ki gayi.**

### Method

1. `main` @ `c947d09` clone kiya (PR #303 merge ke baad ka HEAD).
2. `app/src/main/java/` ke **290** Kotlin/Java files se declared symbols nikale — `class`, `interface`, `object`, `typealias`, **extension functions** (`fun Modifier.foo`), aur top-level `val`/`var`.
3. Poore repo (sources + tests + XML + gradle + md) ka word index banaya, phir har symbol ke inbound references gine.
4. Saath mein **reachability BFS** — `MainActivity`, `DuaApp`, aur AndroidManifest ke components se shuru karke poora reference graph traverse kiya.

### Result

| Check | Number |
| --- | --- |
| Source files scanned | 290 |
| Files with **0 inbound refs** (kisi bhi file se) | **0** |
| Files with **0 inbound refs** (sirf production, tests hataake) | **0** |
| Files **unreachable** from production roots | **0** |

### Non-grep traps bhi check kiye

| Trap | Result |
| --- | --- |
| `AndroidManifest.xml` components | 5 app classes: `MainActivity`, `RememberThisShareTargetActivity`, `tasks.TaskReminderReceiver`, `voice.live.LiveVoiceService`, `widget.DuaToolbarWidgetReceiver` — sab reachable |
| `res/` XML | `res/xml/sharing_shortcuts.xml` → `com.dua.ai.MainActivity` (4 hits) |
| Reflection / `Class.forName` | **0** hits (sirf normal `::class.java` Kotlin) |
| Proguard | `app/proguard-rules.pro` — koi class-specific keep rule nahi |
| `build.gradle` sourceSets | koi custom `srcDir` nahi |
| `THIRD_PARTY_NOTICES.md` | 30 rows — koi bhi un files ka nahi jo is pass mein candidate bani |

### Ek blind spot jo pakda gaya

Pehla scan **extension functions miss kar raha tha** (`fun Modifier.homeBackdrop`, `fun NavGraphBuilder.settingsFeaturePages`). Isse 7 files galti se "unreachable" dikhi — `ui/chat/HomeBackdrop.kt`, `ui/chat/SwipeUpToOpen.kt`, `ui/chat/OgGenerationBackdrop.kt`, `ui/components/PressScale.kt`, `ui/navigation/ComingSoonRoutes.kt`, `ui/navigation/SettingsFeatureRoutes.kt`, `ui/og/BlobDrawable.java`. Regex theek karne ke baad **saari 7 reachable nikli** — sab zinda hain. Jaise `homeBackdrop` `ui/chat/ChatScreen.kt:371` se aur `swipeUpToOpen` `ChatScreen.kt:436` se call hote hain.

---

## 🚨 License warning — `third_party/androidify/`

Pichhli audit ne kaha tha "`third_party/androidify/` mein sirf ek LICENSE hai, delete kar sakte ho". **Yeh salah galat thi. Delete mat karo.**

| Sach | Detail |
| --- | --- |
| Folder mein kya hai | Sirf `third_party/androidify/LICENSE` — 1 file, koi code nahi. Yeh baat sahi thi |
| Par woh file kisi kaam ki hai | `THIRD_PARTY_NOTICES.md:12` usi file ko link karta hai: `License: Apache License, Version 2.0. Full text: third_party/androidify/LICENSE` |
| Aur bhi | `THIRD_PARTY_NOTICES.md` ke **Androidify** section mein **10 Dua files** ki entry hai. Poore notice file mein aise **30 rows** hain (Androidify, Telegram, Now in Android, SociaLite, Jetchat waghairah). Jaise `attachments/DroppedFiles.kt:3`, `diagnostics/DuaLog.kt:2`, `ondevice/GeminiNanoTextModel.kt:3`, `ui/chat/ChatDropTarget.kt:3` |
| Delete karne par | Apache-2.0 ka **attribution requirement toot jaayega** — code toh chalega, par license compliance par sawaal uthega. Yeh legal risk hai, build risk nahi |

**Sahi tareeka (agar kabhi hatana ho):** pehle `THIRD_PARTY_NOTICES.md` mein us license ka poora text ya authoritative URL (`https://www.apache.org/licenses/LICENSE-2.0`) daalo, phir folder delete karo.

---

## Kaise pakka pata karein ki file dead hai

Grep akela kaafi nahi. Yeh order follow karo:

1. **Baseline banao** — `./gradlew assembleDebug` chalao, pass hona chahiye.
2. **Grep se symbol dhoondo** — file ka nahi, **andar ke class/function ka** naam. File ka naam kabhi import nahi hota, isliye file naam se 0 hits normal hai.
3. **Wildcard import ka dhyaan rakho** — `import com.dua.ai.ui.theme.*` jaisi line 19 theme files ko "dead" dikha sakti hai. Isliye symbol-level check karo.
4. **Extension functions yaad rakho** — `fun Modifier.foo` aur `fun NavGraphBuilder.bar` ka naam file ke naam se alag hota hai. Inhe miss karna sabse aam galti hai (pass 2 mein yahi hui thi).
5. **File delete karo, phir build** — `./gradlew assembleDebug && ./gradlew lint`. Dono pass hone chahiye.
6. **Runtime par test karo** — build pass hona kaafi nahi. WorkManager, reflection, aur manifest-declared cheezein sirf app chala kar hi pakdi jaati hain. Backup, widget, notification wale hisse zaroor test karo.
7. **`THIRD_PARTY_NOTICES.md` check karo** — deleted file ka naam wahan hai kya? Hai to usi commit mein row hatao, warna license trail toot jaayega.
8. **Tab jaake delete commit karo** — aur PR mein likho ki build + app test dono pass hue.

---

## Is PR mein kya nahi kiya (pass 2)

Pass 2 mein koi file move ya delete **nahi** hui. `ChatViewModel.kt` todna, `ui/screens/` merge karna, `auth/` merge karna, `web/` rename karna — kuch bhi pass 2 mein nahi tha. Sirf yahi README update hui thi (pass 2 ka section + Telegram correction).

---

## 🔀 Pass 3 — history aur auth/account packages consolidate (20 Oct 2026)

**Base:** `main` @ `3456f37` (PR #304 merge ke baad).
**Is PR mein kuch delete nahi hua** — sirf files move hui hain, aur unke import paths update hue hain. `_to_delete/` folder mein is pass mein kuch nahi daala gaya.

### Kya move hua

| Purana path | Naya path | Kyun |
| --- | --- | --- |
| `app/src/main/java/com/dua/ai/auth/GoogleSignIn.kt` | `app/src/main/java/com/dua/ai/account/GoogleSignIn.kt` | `auth/` mein sirf 1 file thi. Login ka baaki saara kaam (`AccountApi`, `AccountRepository`, `AccountSessionStore`) `account/` mein hai — ek hi jagah hona chahiye |
| `app/src/test/java/com/dua/ai/auth/GoogleAccountHintTest.kt` | `app/src/test/java/com/dua/ai/account/GoogleAccountHintTest.kt` | Test apne subject ke saath hi jaata hai |
| `app/src/main/java/com/dua/ai/ui/history/HistoryGrouping.kt` | `app/src/main/java/com/dua/ai/history/HistoryGrouping.kt` | Yeh **pure logic** hai — drawer ka UI nahi. `Instant`, `ZoneId` par kaam karta hai, koi Compose import nahi |
| `app/src/main/java/com/dua/ai/ui/history/HistorySearch.kt` | `app/src/main/java/com/dua/ai/history/HistorySearch.kt` | Yeh bhi pure logic — `SavedChat` par search, koi Compose import nahi |
| `app/src/main/java/com/dua/ai/ui/history/SearchSnippet.kt` | `app/src/main/java/com/dua/ai/history/SearchSnippet.kt` | `searchSnippet` ko **`ui/chat/ConversationSearch.kt`** bhi use karta hai. Yaani `ui/history` mein rehna galat tha — woh `ui/chat` se import kar raha tha |
| `app/src/test/java/com/dua/ai/ui/history/HistoryGroupingTest.kt` | `app/src/test/java/com/dua/ai/history/HistoryGroupingTest.kt` | Test subject ke saath |
| `app/src/test/java/com/dua/ai/ui/history/HistorySearchTest.kt` | `app/src/test/java/com/dua/ai/history/HistorySearchTest.kt` | Test subject ke saath |
| `app/src/test/java/com/dua/ai/ui/history/RecentChatsTest.kt` | `app/src/test/java/com/dua/ai/history/RecentChatsTest.kt` | Test subject (`HistoryGrouping.recent`) ke saath |

**Total: 8 files move (3 main + 5 test).** Package declaration `com.dua.ai.ui.history` → `com.dua.ai.history`, aur `auth` → `account`.

### Import updates — 4 files mein 6 lines

| File | Kya badla |
| --- | --- |
| `ui/chat/ConversationSearch.kt` | `ui.history.SearchSnippet` → `history.SearchSnippet`, `ui.history.searchSnippet` → `history.searchSnippet` |
| `ui/history/HistoryDrawer.kt` | 2 naye import: `history.HistoryGrouping`, `history.HistorySearch` (pehle same package mein the, isliye import nahi tha) |
| `ui/auth/GoogleSignInButton.kt` | `auth.GoogleSignIn` / `auth.GoogleIdentity` → `account.GoogleSignIn` / `account.GoogleIdentity` |
| `test/ui/auth/GoogleSignInFlowTest.kt` | Waise hi 2 import |
| `viewmodel/ChatViewModel.kt` | **Chhoda nahi** — dekho neeche "Ek comment jo maine nahi badla" |

### Verify kaise kiya

| Check | Result |
| --- | --- |
| `grep -rn "com\.dua\.ai\.auth"` poore repo mein (incl. xml/md) | **0 hits** |
| Purane moved symbols ka leftover path (`com.dua.ai.ui.history.HistorySearch` etc.) | **0 hits** |
| `ui.history.HistoryDrawer` import (jo abhi bhi wahi hai — sahi hai) | 3 hits, **jaan-boojh kar chhoda** (neeche dekho) |
| Har moved file ka package line | 7 files = `com.dua.ai.history`, 1 file = `com.dua.ai.account` |
| Har consumer ke imports | Package se match — `ConversationSearch`/`HistoryDrawer` = `history.*`, `GoogleSignInButton` = `account.*` |
| `xml` / `proguard` / `build.gradle.kts` / `AndroidManifest.xml` mein in packages ka zikr | **0** — isliye koi XML path tootta nahi |
| `THIRD_PARTY_NOTICES.md` mein in 8 files ka row | **0** — isliye license trail ko chhune ki zaroorat nahi |
| Duplicate declarations (`object HistorySearch` etc.) | **1-1 hi** — dono jagah copy nahi bachi |

**Build nahi chala paaya — imaandaari se bata raha hoon.** Is sandbox mein Java aur Android SDK dono nahi hain (`java: command not found`, `ANDROID_HOME` khaali). Isliye `./gradlew assembleDebug` **verify nahi kiya**. Merge se pehle aapko chalana hoga. Jo cheez maine **kar** li woh hai compile-level path checking: har moved symbol ka declaration package + har consumer ka import match karta hai.

### Ek comment jo maine nahi badla (jaan-boojh kar)

`viewmodel/ChatViewModel.kt` ki line **581** mein ek KDoc comment hai:

```
/** The account picker needs an Activity, so the UI runs it ([com.dua.ai.auth.GoogleSignIn]) and reports here. */
```

Yeh ek **comment** hai — code nahi. Iska purana path `com.dua.ai.auth.GoogleSignIn` likha hai, jabki class ab `account` mein hai.

**Kyun nahi badla:** file **121 KB / 1,748 line** ki hai, aur change sirf ek comment ki. Poori file dobara likhna (121 KB) sirf ek comment ke liye theek trade-off nahi tha — review mein bhi 1-line diff ke bajaye 1,748-line file dikhti. **Yeh build ko nahi todta** (comment compile nahi hota). Koi bhi editor ise 2 second mein theek kar sakta hai — agla PR jisme `ChatViewModel.kt` waise bhi chhuna hai, usme yeh bhi saath ho jaayega.

### Left alone and why (jaan-boojh kar nahi chhua)

| Cheez | Kyun nahi chhui |
| --- | --- |
| `ui/history/HistoryDrawer.kt`, `DrawerBrandMark.kt`, `TelegramDialogSwipe.kt` | Yeh **asli UI** hain — Compose imports hain. `HistoryDrawer` chat screen ka drawer hai, `DrawerBrandMark` uska logo. Inhe `ui/history` mein hi rehna chahiye |
| `viewmodel/ChatHistory.kt` | Iska naam `history/` se milta hai par yeh **alag cheez** hai: `ChatHistory` feature class + `HistoryHost` interface, jo `ChatViewModel.kt:54,55` se banti hai. Ise move karna `ChatViewModel.kt` chhune ke barabar hai — woh alag PR ka kaam hai |
| `history/ChatHistory.kt` (`SavedChat`, `ChatHistoryCodec`) | Yeh canonical data layer hai. Sabse zyada import hota hai (**89 files**). Isko hilana = poore app ka blast radius |
| `ui/screens/` | Aapke nirdesh ke mutabiq chhoda |
| `web/`, `website/` | Aapke nirdesh ke mutabiq chhoda — rename nahi kiya |
| `ChatViewModel.kt` (file todna) | Alag PR ka kaam |

### Merge checklist

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```

Teeno pass hone chahiye. Uske baad app kholke check karo: **history drawer khulta hai**, **drawer mein search chalta hai** (`HistorySearch`), **Google sign-in button kaam karta hai** (`GoogleSignIn`), aur **chat ke andar ka search** (`ConversationSearch`). Yeh chaar jagah hi is move se touch hoti hain.

---

## 🔁 Pass 4 — `ui/screens/` ko feature packages mein todna (20 Oct 2026)

**Base:** `main` @ `9f98656` (PR #305 merge ke baad).
**Is PR mein kuch delete nahi hua** — sirf 9 files move hui hain aur unke import paths update hue hain. `_to_delete/` folder mein is pass mein kuch nahi daala gaya. **7 trap files ko haath nahi lagaya.**

### Kya move hua — 9 files

| Purana path | Naya path | Kyun |
| --- | --- | --- |
| `ui/screens/CloudSyncScreen.kt` | `ui/settings/CloudSyncScreen.kt` | Cloud sync ka **Settings → Cloud sync** page hai. `ui/settings/` mein `AccountContent`, `StorageScreen`, `HistoryDataScreen` pehle se hain — yeh bhi wahi kaam karta hai |
| `ui/screens/DataControlsScreen.kt` | `ui/settings/DataControlsScreen.kt` | **Settings → Data controls** page (stored files, chat count). Feature hub ka page nahi |
| `ui/screens/ImagineSettingsScreen.kt` | `ui/settings/ImagineSettingsScreen.kt` | Naam mein hi "Settings" hai. `DuaDestination.ImagineSettings` se khulta hai |
| `ui/screens/IntegrationSetupScreen.kt` | `ui/settings/IntegrationSetupScreen.kt` | **Settings → Feature setup** page (search, GitHub, voice providers). Sirf `DuaNavHost` se khulta hai |
| `ui/screens/ProfileScreen.kt` | `ui/settings/ProfileScreen.kt` | `ProfileScreen` pehle se **`ui/settings/AccountContent`** ko wrap karta hai — yeh account/settings ka page hai, feature screen nahi |
| `ui/screens/AnimatedVoiceCube.kt` | `ui/voice/AnimatedVoiceCube.kt` | Sirf `ui/voice/VoiceScreen.kt` use karta hai. Voice ka UI voice package mein rehna chahiye |
| `ui/screens/VoiceCubeGeometry.kt` | `ui/voice/VoiceCubeGeometry.kt` | `AnimatedVoiceCube` ka geometry helper — pure math, koi Compose UI nahi |
| `test/.../ui/screens/VoiceCubeGeometryTest.kt` | `test/.../ui/voice/VoiceCubeGeometryTest.kt` | Test apne subject ke saath |
| `ui/icons/DuaIcons.kt` | `ui/components/DuaIcons.kt` | `ui/icons/` mein **sirf 1 file** thi. `ui/components/` mein baaki shared UI bits (`Shimmer`, `PressScale`, `AccountAvatar`, `FeatureTopBar`) hain |

**Package changes:** `com.dua.ai.ui.screens` → `com.dua.ai.ui.settings` (5 files), `→ com.dua.ai.ui.voice` (3, test included), `com.dua.ai.ui.icons` → `com.dua.ai.ui.components` (1 file). Khaali `ui/icons/` dir hat gayi.

**`ui/screens/` ab 15 se 8 files par aa gaya:** `AssetFeatureRoutes.kt`, `FeatureScreens.kt`, `PluginsRoute.kt`, `AppPausedScreen.kt`, `ImagesScreen.kt`, `LibraryScreen.kt`, `PluginsScreen.kt`, `ComputerScreen.kt`.

### Import updates — 12 imports, 8 files (+3 fully-qualified uses)

| File | Kya badla |
| --- | --- |
| `ui/navigation/DuaNavHost.kt` | 4 imports (`CloudSyncScreen`, `DataControlsScreen`, `IntegrationSetupScreen`, `ProfileScreen`) + 1 fully-qualified `ImagineSettingsScreen` call |
| `ui/voice/VoiceScreen.kt` | `ui.screens.AnimatedVoiceCube` → `ui.voice.AnimatedVoiceCube` |
| `ui/screens/ComputerScreen.kt` | `ui.icons.DuaIcons` → `ui.components.DuaIcons` |
| `ui/settings/AccountContent.kt` | 2 same-package imports hata diye (`ProfileActions`, `ProfileUiState` ab same package mein hain) |
| `test/screenshots/ImagineScreenshotTest.kt` | `ui.screens.ImagineSettingsContent` → `ui.settings.*` |
| `test/screenshots/LoginScreenshotTest.kt` | 4 imports (`ProfileActions`, `ProfileContent`, `ProfileDialog`, `ProfileUiState`) |
| `test/ui/ApprovedNavigationUiTest.kt` | 2 fully-qualified uses (`ProfileContent`, `ProfileUiState`, `ProfileActions` → `ui.settings`) — `ui.settings.*` pehle se imported tha |
| `test/ui/pages/RemainingPagesTokensTest.kt` | `import com.dua.ai.ui.settings.*` add kiya (wildcard = covered) |
| `test/ui/settings/AccountReferenceUiTest.kt` | 3 imports hataye — `ui.settings.*` wildcard se already covered |
| `test/ui/settings/InnerSettingsRowsTest.kt` | `ui.screens.ImagineSettingsContent` → `ui.settings.*` |

### Verify kaise kiya

| Check | Result |
| --- | --- |
| Purane paths (`ui.icons`, `ui.screens.CloudSync/DataControls/ImagineSettings/IntegrationSetup/Profile*/AnimatedVoiceCube/VoiceCubeGeometry`) — `*.kt *.java *.xml *.pro *.kts *.md` mein | **0 hits** ✅ |
| `AndroidManifest.xml` / `res/` XML / `proguard-rules.pro` / `build.gradle.kts` mein in classes ka zikr | **0** |
| `THIRD_PARTY_NOTICES.md` mein in 9 files ka row | **0** — license trail chhua nahi |
| Extension function/property sweep (jaise pass 3 mein `HistoryDrawer` ka bug tha) | **0 misses** — in 9 files mein koi `fun X.y` / `val X.y` extension nahi hai; har consumer ka import ya wildcard match karta hai |
| Wildcard collision (ek file `ui.screens.*` + `ui.settings.*` dono import kare aur naam takra jaaye) | **0** — `VoiceOrb` `ui/voice` ke andar hi pehle se 2 overloads hai (pre-existing), yeh move usse touch nahi karta |

**Build nahi chala paaya — imaandaari se.** Is sandbox mein Java aur Android SDK dono nahi hain (`java: command not found`, `ANDROID_HOME` khaali). `./gradlew assembleDebug` **verify nahi kiya**. Merge se pehle chalana hoga.

### Left alone and why (jaan-boojh kar nahi chhua)

| Cheez | Kyun nahi chhui |
| --- | --- |
| `ui/screens/AssetFeatureRoutes.kt`, `FeatureScreens.kt`, `PluginsRoute.kt` | **Trap files** (upar table mein). `DuaNavHost.kt:227,231,237` aur `ChatScreen.kt:102` se zinda |
| `ui/screens/ComputerScreen.kt` | Abhi bhi `ui/screens` mein hai. "Computer" feature hub ka page hai (Home top bar se), settings page nahi. `ui/screens/ComputerScreen.kt` + 2 tests use karte hain |
| `ui/screens/AppPausedScreen.kt` | `ui/DuaApp.kt:52` se seedha fully-qualified call. Saath hi `THIRD_PARTY_NOTICES.md:25` mein **Androidify attribution row** hai (file header mein bhi Apache-2.0 notice) — move karne se notice path stale ho jaata. **Alag PR ka kaam** |
| `ui/screens/ImagesScreen.kt`, `LibraryScreen.kt`, `PluginsScreen.kt` | Yeh asli feature screens hain, settings nahi. Aur `AssetFeatureRoutes.kt` inke `Content` composables ko call karta hai — ek saath hilana zaroori hota, jo trap file ko chhune ke barabar hai |
| `ui/agent/AgentScreen.kt` (1-file package) | `ChatRoute.kt`, `ChatScreen.kt`, `ChatUiState.kt` aur `DuaNavHost.kt:248` use karte hain. Naam `chat` se milta hai par yeh **alag feature** hai (Research/GitHub/Briefing). Merge karna `ChatViewModel` PR ke saath karna behtar |
| `ui/onboarding/OnboardingScreen.kt` (1-file package) | `OnboardingScreen` full-screen flow hai, `OnboardingContent` shared UI. `ui/auth` ke saath merge karne par `ProfileScreen`/`EmailAuthScreen` ka blast radius badhta hai. Risk zyada, fayda kam |
| `ui/og/BlobDrawable.java` (1-file package) | **Telegram GPL-2.0 header** + `THIRD_PARTY_NOTICES.md:204-207` mein row. Move karne se license trail ka path stale ho jaata. `ui/chat/OgGenerationBackdrop.kt` use karta hai |
| `ui/auth/EmailAuthScreen.kt`, `GoogleSignInButton.kt` | 2 files hain, 1 nahi — "obvious single-file merge" nahi. Auth flow ka apna package theek hai |
| `web/`, `website/` | Aapke nirdesh ke mutabiq — rename nahi kiya |
| `ChatViewModel.kt` (todna) | Alag PR ka kaam |
| `viewmodel/ChatHistory.kt` | `ChatViewModel.kt:54,55` se banti hai — chhuna = `ChatViewModel` chhuna |

### Merge checklist

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```

Teeno pass hone chahiye. Uske baad app mein yeh 6 jagah check karo:

1. **Settings → Cloud sync** khulta hai (`CloudSyncScreen`)
2. **Settings → Data controls** khulta hai, stored files list dikhti hai (`StoredFilesSection`)
3. **Settings → Imagine** (imagine settings) khulta hai, provider/aspect badalte hain
4. **Settings → Feature setup** khulta hai (`IntegrationSetupScreen`)
5. **Profile / Account** screen khulti hai, log out dialog dikhta hai (`ProfileScreen` + `AccountContent`)
6. **Voice screen** — cube animation chalti hai (`AnimatedVoiceCube` + `VoiceCubeGeometry`)

Aur icons: koi bhi screen kholo — `DuaIcons.Sparkle` wala sparkle dikhna chahiye (Computer screen par bhi).

---

## 🔂 Pass 5 — `ui/auth`, `ui/onboarding`, `ui/og` merge + `AppPausedScreen` move (20 Oct 2026)

**Base:** `main` @ `4439a1a` (PR #306 merge ke baad).
**Is PR mein kuch delete nahi hua** — sirf files move hui hain aur unke package/import update hue hain. `_to_delete/` folder mein is pass mein kuch nahi daala gaya. **7 trap files ko haath nahi lagaya.**

### Kya move hua — 8 files (5 main + 3 test)

| Purana path | Naya path | Kyun |
| --- | --- | --- |
| `ui/auth/EmailAuthScreen.kt` | `ui/account/EmailAuthScreen.kt` | Pass 3 mein data side `auth/` → `account/` merge ho gaya tha, par **UI side ka `ui/auth/` reh gaya tha**. `ui/settings/AccountContent` + `ProfileScreen` already account ka UI hain — ab dono taraf ek hi naam |
| `ui/auth/GoogleSignInButton.kt` | `ui/account/GoogleSignInButton.kt` | `rememberGoogleSignIn` sirf account screens (`OnboardingScreen`, `ProfileScreen`) use karte hain. `account/GoogleSignIn.kt` (data) ke saath hi rehna chahiye |
| `ui/onboarding/OnboardingScreen.kt` | `ui/account/OnboardingScreen.kt` | **1-file package tha.** `OnboardingContent` ko `ui/settings/ProfileScreen.kt` aur `test/ui/settings/AccountReferenceUiTest.kt` call karte hain — yaani yeh already account screens ka hissa hai, sirf alag folder mein pada tha |
| `test/.../ui/auth/EmailAuthInteractionTest.kt` | `test/.../ui/account/EmailAuthInteractionTest.kt` | Test apne subject ke saath |
| `test/.../ui/auth/GoogleSignInFlowTest.kt` | `test/.../ui/account/GoogleSignInFlowTest.kt` | Test apne subject ke saath |
| `ui/og/BlobDrawable.java` | `ui/chat/BlobDrawable.java` | **1-file package tha** aur uska **ek hi consumer** `ui/chat/OgGenerationBackdrop.kt` hai (jo ab same package mein hai, isliye import hata diya). **Notice row same commit mein update ki** — neeche dekho |
| `ui/screens/AppPausedScreen.kt` | `ui/AppPausedScreen.kt` | **1-file page, ek hi consumer** `ui/DuaApp.kt` (jo `ui` package mein hai). Pass 4 mein "alag PR" likha tha — woh yahi PR hai |
| `test/.../ui/screens/AppPausedScreenTest.kt` | `test/.../ui/AppPausedScreenTest.kt` | Test subject ke saath |

**Package changes:** `com.dua.ai.ui.auth` → `com.dua.ai.ui.account` (2 files), `com.dua.ai.ui.onboarding` → `com.dua.ai.ui.account` (1 file), `com.dua.ai.ui.og` → `com.dua.ai.ui.chat` (1 file), `com.dua.ai.ui.screens` → `com.dua.ai.ui` (1 file + test).

**Khaali dirs hat gaye:** `ui/auth/` (main + test), `ui/onboarding/`, `ui/og/`.

**`ui/` ab:** `DuaApp.kt` + `AppPausedScreen.kt` root par, aur 11 subpackages (`account`, `agent`, `chat`, `components`, `history`, `navigation`, `projects`, `screens`, `settings`, `theme`, `voice`). `og`, `auth`, `onboarding` gaye.

### Import updates — 5 moves, 13 line changes, 10 files

| Move | Files touched | Lines |
| --- | --- | --- |
| `ui/auth` + `ui/onboarding` → `ui/account` | `DuaNavHost.kt` (2), `ProfileScreen.kt` (2), `OnboardingScreen.kt` (1 hataya — same package), `UiPreviewActivity.kt` (1), `LoginScreenshotTest.kt` (1), `RemainingPagesTokensTest.kt` (1), `AccountReferenceUiTest.kt` (1) | **9** |
| `ui/og` → `ui/chat` | `OgGenerationBackdrop.kt` (1 import hataya — same package) | **1** |
| `AppPausedScreen` → `ui/` | `DuaApp.kt` (1 fully-qualified → bare call), `AppPausedScreenTest.kt` (1 package line) | **2** |
| `docs/ui/ANDROID_UI.md` | Welcome row ka path | **1** |

### License trail — notice row same commit mein update

`THIRD_PARTY_NOTICES.md` mein **2 rows** update hui (koi row hatayi nahi gayi — attribution intact hai):

| Line (purana) | Purana path | Naya path |
| --- | --- | --- |
| 25 | `app/src/main/java/com/dua/ai/ui/screens/AppPausedScreen.kt` (Androidify / Apache-2.0) | `app/src/main/java/com/dua/ai/ui/AppPausedScreen.kt` |
| 204 | `app/src/main/java/com/dua/ai/ui/og/BlobDrawable.java` (Telegram / GPL-2.0-or-later) | `app/src/main/java/com/dua/ai/ui/chat/BlobDrawable.java` |

Dono files ke andar ka license header bhi waise hi hai — kuch hataya nahi gaya.

### Verify kaise kiya

| Check | Result |
| --- | --- |
| `com.dua.ai.ui.auth` (kt/java/xml/pro/kts/gradle) | **0 hits** ✅ |
| `com.dua.ai.ui.onboarding` | **0 hits** ✅ |
| `com.dua.ai.ui.og` | **0 hits** ✅ |
| Filesystem paths `com/dua/ai/ui/{auth,onboarding,og}` (xml/pro/kts) | **0 hits** ✅ |
| `ui.screens.AppPausedScreen` (koi bhi purana call) | **0 hits** ✅ |
| `AndroidManifest.xml` / `res/` XML / `proguard-rules.pro` / `build.gradle.kts` | **0** — koi component ya keep-rule in classes ko naam se nahi bulata |
| Extension function/property sweep (pass 3 ka `HistoryDrawer` bug, pass 4 ka `StoredFilesSection` bug) | **0 misses** — teeno moved files mein **koi `fun X.y` / `val X.y` extension nahi** hai; sweep ne 19 consumer-symbol pairs check kiye, sab import/wildcard/same-package se covered |
| `rememberGoogleSignIn` ke consumers | `ProfileScreen.kt` + `OnboardingScreen.kt` — dono same package, import hata diya |
| `OnboardingContent` ke consumers (7 files incl. `debug/`) | sab `com.dua.ai.ui.account.OnboardingContent` import karte hain |
| Duplicate declarations | **0** — har symbol ka 1 hi declaration |
| `_to_delete/README.md` purane sections | **chhue nahi** — sirf yeh naya section juda |

**Build nahi chala paaya — imaandaari se.** Is sandbox mein Java aur Android SDK dono nahi hain (`java: command not found`, `ANDROID_HOME` khaali, `/usr/lib/jvm` maujood nahi). `./gradlew assembleDebug` **verify nahi kiya**. Jo kar liya woh hai compile-level path checking + har moved symbol ka declaration/consumer import match.

### Left alone and why (jaan-boojh kar nahi chhua)

| Cheez | Kyun nahi chhui |
| --- | --- |
| `ui/screens/ComputerScreen.kt` | Abhi `ui/screens` mein hai. `ui/screens/FeatureScreens.kt` (trap) ke saath ek hi package share karta hai; akele hilane par trap file ka import todna padta. `DuaNavHost.kt` + 3 tests use karte hain |
| `ui/screens/ImagesScreen.kt`, `LibraryScreen.kt`, `PluginsScreen.kt` | Teeno `ui/screens/AssetFeatureRoutes.kt` (**trap**) se same-package call hote hain. **Yeh ek saath hi hil sakte hain**, aur trap file chhune ke bina nahi — isliye alag PR mein trap file ke saath |
| `ui/agent/AgentScreen.kt` (1-file package) | `ui/chat` ka natural sibling lagta hai par **`ChatRoute.kt`, `ChatScreen.kt`, `ChatUiState.kt` teeno se 7 imports** aate hain aur apne 2 dedicated tests (`AgentRouteTest`, `AgentScreenUiTest`) usi package mein hain. `ChatViewModel` split ke saath karna behtar — **risk zyada, fayda kam** |
| `web/`, `website/` | Aapke nirdesh ke mutabiq — rename nahi kiya |
| `ChatViewModel.kt` (todna) | Alag PR ka kaam. Comment `com.dua.ai.auth.GoogleSignIn` (line ~581) abhi bhi purana path kehta hai — comment compile nahi hota, 1,748-line file sirf uske liye dobara nahi likhi |
| 7 trap files | `_to_delete/README.md` ka pehla section — `AssetFeatureRoutes.kt`, `FeatureScreens.kt`, `PluginsRoute.kt`, `ChatStream.kt`, `MarkdownStyles.kt`, `ProjectTokens.kt`, `DriveBackupWorker.kt`. **Kisi ko touch nahi kiya** |

### Merge checklist

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```

Teeno pass hone chahiye. Uske baad app mein yeh 6 jagah check karo:

1. **Welcome / onboarding** screen — Dua logo, Google button, Skip (ab `ui/account/OnboardingScreen.kt`)
2. **Email sign-in** screen khulti hai (Account → Email) — `EmailAuthScreen`
3. **Account → Profile** — Google sign-in button kaam karta hai (`rememberGoogleSignIn`)
4. **Maintenance screen** — `DUA_MAINTENANCE=1` par "App paused" page + Try again (`AppPausedScreen`)
5. **Chat mein OG generation backdrop** — blob animation chalti hai (`BlobDrawable` ab `ui/chat`)
6. **Settings → Account** — logout dialog aur account rows (`ProfileScreen` + `AccountContent`)

---

## 🔃 Pass 6 — `ui/screens/` poora khatam, `ui/features/` bana (20 Oct 2026)

**Base:** `main` @ `49ce66d` (PR #307 merge ke baad).
**Is PR mein kuch delete nahi hua** — sirf 7 files move hui hain aur unke package/import paths update hue hain. `_to_delete/` folder mein is pass mein kuch nahi daala gaya.

**Sabse badi baat:** pichhle paanch pass se jo `ui/screens/` "chhoda hua" tha, woh **ab poora khatam ho gaya**. Us folder mein bache saare 7 files ek hi concern ke hain — **feature hub ke pages** (Images, Library, Plugins, Computer, Projects) — aur teeno trap files bhi wahin ki thin. Isliye poora folder ek naam ke saath `ui/features/` ban gaya, taaki aage koi "screens" naam ka confusion na rahe.

### Kya move hua — 7 files (saari `R` rename, koi `A`+`D` nahi)

| Purana path | Naya path | Kyun |
| --- | --- | --- |
| `ui/screens/AssetFeatureRoutes.kt` | `ui/features/AssetFeatureRoutes.kt` | **Trap file** (zinda). `DuaNavHost.kt:227,231` se chalti hai. Isme `ImagesScreen` + `LibraryScreen` declared hain |
| `ui/screens/FeatureScreens.kt` | `ui/features/FeatureScreens.kt` | **Trap file** (zinda). `ChatScreen.kt:102` par `ProjectsFeaturePage` import hota hai |
| `ui/screens/PluginsRoute.kt` | `ui/features/PluginsRoute.kt` | **Trap file** (zinda). `DuaNavHost.kt:237` se chalti hai. Isme `PluginsScreen` declared hai |
| `ui/screens/ImagesScreen.kt` | `ui/features/ImagesScreen.kt` | `ImagesContent` + `ImageStarterPrompts`. Sirf `AssetFeatureRoutes` (same package) aur 2 tests use karte hain |
| `ui/screens/LibraryScreen.kt` | `ui/features/LibraryScreen.kt` | `LibraryContent`. Sirf `AssetFeatureRoutes` (same package) aur 2 tests |
| `ui/screens/PluginsScreen.kt` | `ui/features/PluginsScreen.kt` | `PluginsContent`. Sirf `PluginsRoute` (same package) aur 2 tests |
| `ui/screens/ComputerScreen.kt` | `ui/features/ComputerScreen.kt` | Home top bar se khulne wala "Computer" placeholder page. `DuaNavHost.kt:243` + 3 tests |

**Package change:** `com.dua.ai.ui.screens` → `com.dua.ai.ui.features` (saat files).

**Khaali dir hat gaya:** `app/src/main/java/com/dua/ai/ui/screens/` — ab maujood nahi.

**`ui/` ab (13 entries):** `DuaApp.kt` + `AppPausedScreen.kt` root par, aur **11 subpackages** — `account`, `agent`, `chat`, `components`, `features`, `history`, `navigation`, `projects`, `settings`, `theme`, `voice`. (`screens` gaya.)

### Trap files hilaye — par kuch kamzor nahi hua

Yeh pass ka sabse nazuk hissa tha: teeno trap files **move** hui hain, delete nahi. `_to_delete/README.md` ka pehla section kehta hai "inhe delete MAT karo" — **delete kisi ko bhi nahi kiya**. Move karna safe tha kyunki:

1. Teenonu trap files ne **apni same-package calls** saath rakhi — `AssetFeatureRoutes` → `ImagesContent`/`LibraryContent`, `PluginsRoute` → `PluginsContent`. Saat files ek saath isi liye hili, taaki koi same-package reference na toote.
2. Bahar ke saare consumers ke import update hue (neeche table).
3. `THIRD_PARTY_NOTICES.md` mein in 7 files ka **koi row nahi hai** — verify kiya (`grep "ui/screens"` → 0 hits). Isliye license trail ko chhune ki zaroorat hi nahi padi.
4. In 7 files mein **koi license header bhi nahi** hai (Telegram/Androidify type) — `head -8` se check kiya.
5. Manifest / proguard / build.gradle.kts mein in classes ka **koi zikr nahi** — koi component ya keep-rule inhe naam se nahi bulata.
6. `Class.forName` / reflection — **0 hits**.

### Import updates — 13 lines, 7 files

| File | Kya badla |
| --- | --- |
| `ui/navigation/DuaNavHost.kt` | 4 fully-qualified calls: `ui.screens.ImagesScreen`, `.LibraryScreen`, `.PluginsScreen`, `.ComputerScreen` → `ui.features.*` |
| `ui/chat/ChatScreen.kt` | 1 import: `ui.screens.ProjectsFeaturePage` → `ui.features.ProjectsFeaturePage` |
| `test/screenshots/HomeScreenshotTest.kt` | 1 import (`ComputerScreen`) |
| `test/ui/voice/VoiceTokensTest.kt` | 1 import (`ComputerScreen`) |
| `test/ui/chat/FeatureIsolationUiTest.kt` | 4 imports (`ImagesContent`, `LibraryContent`, `PluginsContent`, `ProjectsFeaturePage`) |
| `test/ui/navigation/FeatureHubUiTest.kt` | 1 wildcard: `ui.screens.*` → `ui.features.*` |
| `test/ui/pages/RemainingPagesTokensTest.kt` | 1 wildcard: `ui.screens.*` → `ui.features.*` |

`git diff --shortstat` (README chhod kar) = **14 files changed, 20 insertions(+), 23 deletions(-)** — 7 rename + 7 consumers. README ke saath poora diff **15 files, +169 −23**.

### Verify — jo actually chalaya

| Check | Result |
| --- | --- |
| `com.dua.ai.ui.screens` — `*.kt *.java *.xml *.kts *.pro *.gradle` | **0 hits** ✅ |
| Filesystem path `com/dua/ai/ui/screens` | **0** (dir hi nahi raha) ✅ |
| `^package com.dua.ai.ui.screens` in moved files | **0** ✅ |
| Har moved symbol ka old-path grep (10 symbols) | **0 · 0 · 0 · 0 · 0 · 0 · 0 · 0 · 0 · 0** ✅ |
| Symbol sweep — har consumer ka package vs import (14 files) | **0 broken** — 8 `SAME-PKG ok` + 6 `RESOLVED` ✅ |
| Extension function/property declarations in moved files | **0** — koi `fun X.y` / `val X.y` nahi (isliye pass 3/4 wala pitfall yahan lagoo nahi hota) |
| Wildcard collision (`features.*` vs `theme.*`/`settings.*`/`chat.*`/`components.*`/`setup.*`/`library.*`/`imagine.*`/`account.*`/`model.*`) | **0** — ek bhi naam nahi takra raha ✅ |
| Duplicate declarations (10 symbols) | **1-1 hi** — koi purani copy nahi bachi ✅ |
| `THIRD_PARTY_NOTICES.md` mein in 7 files ka row | **0** ✅ |
| `docs/**/*.md` + `README.md` mein in 7 basenames ka zikr | **0** ✅ |
| `AndroidManifest.xml` / `res/` / `proguard-rules.pro` / `build.gradle.kts` | **0** ✅ |
| `_to_delete/README.md` ke purane sections | **chhue nahi** — sirf yeh naya section juda ✅ |

**Build nahi chala paaya — imaandaari se.** Is sandbox mein Java aur Android SDK dono nahi hain (`java: command not found`, `ANDROID_HOME` khaali). `./gradlew assembleDebug` **verify nahi kiya**. Jo kar liya woh hai compile-level path checking: har moved symbol ka declaration package + har consumer ka import/wildcard/fully-qualified match.

### Left alone and why (jaan-boojh kar nahi chhua)

| Cheez | Kyun nahi chhui |
| --- | --- |
| **`ui/agent/AgentScreen.kt`** (1-file package) | **Yahan `ui/chat` mein merge karna galat hota.** Dono packages ek doosre ko import karte hain — `ChatRoute.kt:30-32` → `ui.agent.AgentTask`/`GITHUB_AGENT_PROMPT`/`briefingPrompt`, `ChatScreen.kt:100-101` → `AgentContent`/`AgentModePill`, `ChatUiState.kt:157` → `AgentTask`; aur ulta `AgentScreen.kt:26-27` → `ui.chat.ChatFeature`/`ChatRoute`. Yaani `ui/agent` → `ui/chat` **aur** `ui/chat` → `ui/agent`. Merge karne par yeh ek hi package ban jaata — technically compile hota, par `AgentScreen` ka **`internal`** contract khatam ho jaata (`AgentContent`, `AgentModePill`, `briefingPrompt`, `GITHUB_AGENT_PROMPT` sab `internal`) aur 660-line `ChatScreen` ke saath ek hi package mein 4 agent symbols ghus jaate. Fayda sirf "ek folder kam" — risk asli. **ChatViewModel split ke saath karna behtar.** |
| `web/`, `website/` | Aapke nirdesh ke mutabiq — rename nahi kiya |
| `ChatViewModel.kt` (todna) | Aapke nirdesh ke mutabiq — sirf **plan** diya hai (neeche) |
| `diagnostics/DuaLog.kt`, `widget/DuaToolbarWidgetReceiver.kt` | Bache hue do 1-file top-level packages. `DuaLog` ko `DriveBackupWorker.kt:59`, `AutomaticCloudSync.kt:185,291`, `ChatViewModel.kt:1557,1682` fully-qualified use karte hain — `ui/` ke bahar rehna hi sahi hai (logging + widget app-wide concern hai, UI concern nahi). `widget/` manifest-declared receiver hai (`.widget.DuaToolbarWidgetReceiver`), move karne se manifest path update karna padta — fayda zero |
| Email/GitHub/Cloud backend | Is pass ka scope nahi tha |

### 📋 `ChatViewModel.kt` ka split plan (sirf plan — code nahi toda)

File: `viewmodel/ChatViewModel.kt` — **1,748 lines, 178 functions, 377 top-level members**.

#### Jo pehle se theek hai — isko dobara mat banao

Sabse important baat jo measurement se nikli: **file already tod ke rakhi hai**. `ChatHistory`, `ChatAgentControls`, `ChatUploads`, `ChatAccount`, `ChatDataControls`, `ChatProjects`, `ChatImagine` — saat features pehle se alag files mein hain (`viewmodel/Chat*.kt`, 164–315 lines each), aur `ChatViewModel` sirf `by lazy` delegates se unhe bandhta hai:

```kotlin
private val historyFeature:  ChatHistory        by lazy { ChatHistory(historyHost) }
private val agentFeature:    ChatAgentControls  by lazy { ChatAgentControls(agentHost) }
private val uploadsFeature:  ChatUploads        by lazy { ChatUploads(uploadsHost) }
private val accountFeature:  ChatAccount        by lazy { ChatAccount(accountHost) }
private val dataFeature:     ChatDataControls   by lazy { ChatDataControls(dataHost) }
private val projectsFeature: ChatProjects       by lazy { ChatProjects(projectsHost) }
private val imagineFeature:  ChatImagine        by lazy { ChatImagine(imagineHost) }
```

Har feature ke saath ek **`XxxHost` interface** hai jo `ChatViewModel` ko anonymous object se pass karta hai (state flows, `viewModelScope`, `api`, `account` etc.). Yeh **Facade pattern ka sahi implementation** hai, aur har host block 27–52 lines ka hai (kul **277 lines**) — wo peelne layak hai, par woh "split" nahi, "chhota cleanup" hai.

**Iska matlab:** file ki asli 1,472 lines ek hi cheez karti hain — **chat ki apni core loop** (message list, streaming, drafts, attachments, model/mode selection, TTS). Woh 7 features mein se kisi mein nahi hai kyunki woh sab ka base hai.

#### Baaki 1,472 lines ka breakdown

| Region | Lines | Lines count | Concern |
| --- | --- | --- | --- |
| 341–756 | 416 | Voice/TTS, settings/appearance, writing tools, memory notes |
| 757–1031 | 275 | **Reply loop** — `sendMessage` (136 lines), `cancelReply`, `collectReply` (35), `finalizeStreaming`, retry |
| 1032–1330 | 299 | Stored files / uploads UI glue — `uploadPicked` (29), `cancelUpload` (35), `discardUploads` (33) |
| 1331–1711 | 381 | Data & cloud sync, `startNewChat` (71), `retryResponse` (69), `deleteChat` (34), server-chat deletion |
| 1712–1749 | 38 | `imagineSettings` StateFlow |

Bade functions: `sendMessage` **136 lines** (1575–1710), `startNewChat` **71** (1503–1573), `retryResponse` **69** (1505–1573), `projectMemory` **39** (1243–1281), `deleteServerChat` **38** (1455–1492).

#### Order — sabse safe pehle

| Step | Kya nikalna hai | Naya ghar | Kyun safe | Risk |
| --- | --- | --- | --- | --- |
| **1** | **Voice/TTS block** — `replyTtsOptions`, `replyVoicePlan`, `generateReplyAudio`, `replyAudioCache`, `generatedAudioApi`, `liveStart`/`liveUsage`/`startLiveVoice`/`finishLiveVoice`/`loadLiveUsage` (est. 180–200 lines) | `viewmodel/ChatVoice.kt` + `ChatVoiceHost` | Sabse dekha-bhaala: **same pattern** jo 7 baar pehle se chalta hai. `voice/` package se hi baat karta hai, chat state par sirf padhta hai | **Low** — 4 public fun + 2 StateFlow |
| **2** | **Attachment / stored-files glue** — `uploadPicked`, `cancelUpload`, `discardUploads`, `flushPendingFileDeletes`, `attachmentImageBytes`, `_attachments` clear paths (est. 250–280 lines) | `viewmodel/ChatAttachments.kt` + host | `uploadsFeature` (`ChatUploads`) already maujood hai — yeh uska **UI-side glue** hai jo upar reh gaya | **Low-Med** — `_attachments` StateFlow shared hai |
| **3** | **Settings / appearance / writing** — `setPersonality`, `setAppearance`, `setAccent`, `saveAppearance`, `resetSettings`, `updateSettings`, `openWriting`/`runWriting`/`useWritingResult`/`askServerForWriting`/`closeWriting` (est. 200–230 lines) | `viewmodel/ChatSettings.kt` + host | `localSettings`/`appearanceStore` sirf padhe-jaate hain; `onDeviceAi` already inject-able | **Med** — `writingJob` lifecycle |
| **4** | **Memory notes** — `proposeMemoryNotes`, `acceptMemoryNote`, `suggestAgentMemory`, `dismissMemoryNote`, `confirmSharedMemory` (est. 60–80 lines) | `viewmodel/ChatMemory.kt` | Chhota, self-contained, sirf `memoryCandidates` StateFlow | **Low** (chhota) |
| **5** | **`sendMessage` ko todna** — 136 lines se `prepareSend` / `streamReply` / `persistResult` | same file, private funs | **Yeh split nahi, function-length fix hai** — build risk sabse kam, review sabse aasan | **Low** — par asli fayda bhi kam |
| **6** | **`startNewChat` / `retryResponse` / `deleteChat` / `deleteServerChat`** | `viewmodel/ChatLifecycle.kt` | **Sabse aakhir** — yeh chat ka dil hai, `sendMessage` aur history feature dono se bandha hua | **High** — yahan galti user-facing |

**Rukne ka point:** Step 3 ke baad file ~1,050–1,100 lines par aa jaayegi aur 10 features alag honge. **Step 4 ke baad ruk sakte hain.** Steps 5–6 ka fayda "line count" se zyada "readability" hai — unhe alag PR mein karein, aur pehle `sendMessage` ka test coverage dekhein.

**Golden rule (pass 3 se):** ek step = ek feature = ek commit. Tests pass → commit. Fail → revert, aage na badhein.

**Ek cheez jo is PR mein theek honi chahiye thi:** `ChatViewModel.kt` line ~581 ka comment abhi bhi `com.dua.ai.auth.GoogleSignIn` kehta hai (pass 3 se). Comment compile nahi hota, isliye 1,748-line file sirf uske liye dobara nahi likhi. **Step 1 karte waqt (jab file waise bhi khulegi) yeh saath theek kar lein.**

### Merge checklist

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```

Teeno pass hone chahiye. Uske baad app mein yeh **6 jagah** check karo — yeh exactly woh screens hain jo is pass mein hili hain:

1. **Home top bar → Computer** — "Coming soon" page khulti hai, sparkle icon dikhta hai (`ComputerScreen`)
2. **Chat → feature hub → Images** — prompt bubbles (`ImageStarterPrompts`), aspect chips, generate button (`ImagesContent`)
3. **Chat → feature hub → Library** — files list, filter, search, upload (`LibraryContent`)
4. **Chat → feature hub → Plugins** — search/GitHub toggles, provider rows (`PluginsContent`)
5. **Chat → Projects ka page** — chat ke andar se projects page khulta hai (`ProjectsFeaturePage`)
6. **Images / Library / Plugins ke back arrows** — `DuaNavHost` se `navigate` sahi jagah jaata hai (`AssetFeatureRoutes` + `PluginsRoute`)

---

## 🎙 Chat split — Step 1: Voice/TTS → `ChatVoice.kt` (20 Oct 2026)

**Base:** `main` @ `37d24ae` (PR #308 merge ke baad). **Kuch delete nahi hua** — sirf code move hua. **7 trap files ko haath nahi lagaya.**

Yeh `ChatViewModel.kt` split plan ka **Step 1** hai (upar Pass 6 section mein table). Golden rule follow hua: **ek step = ek feature = ek commit**.

### Line count — before → after

| File | Pehle | Ab | Farq |
| --- | --- | --- | --- |
| `viewmodel/ChatViewModel.kt` | **1,748** | **1,684** | **−64** |
| `viewmodel/ChatVoice.kt` | — | **136** (naya) | +136 |
| `viewmodel/` total | 3,507 | 3,579 | +72 (host + forwarders) |

`ChatViewModel` se **101 line nikli** (voice chunk) aur **37 line wapas aayi** (host object 19 + forwarders 11 + `_online`/`online`/`replyAudioCache` 5 + comments) — net **−64**.

### Kya nikaala — 10 public members + live session internals

| Kya | Purana ghar | Naya ghar |
| --- | --- | --- |
| `replyTtsOptions()` · `replyVoicePlan()` · `generateReplyAudio()` | `ChatViewModel` | `ChatVoice` |
| `liveVoiceAvailable()` · `liveOptions()` · `loadLiveUsage()` · `clearLiveError()` · `startLiveVoice()` | `ChatViewModel` | `ChatVoice` |
| `liveStart` · `liveUsage` (StateFlow) | `ChatViewModel` | `ChatVoice` |
| `_liveStart` · `_liveUsage` (private → `internal`) | `ChatViewModel` | `ChatVoice` |
| `generatedAudioApi` (private) | `ChatViewModel` | `ChatVoice` |
| `finishLiveVoice()` (private suspend) | `ChatViewModel` | `ChatVoice` |
| `LiveStartState` | `ChatViewModel` ka **nested** class | **top-level** class `ChatVoice.kt` mein |

**Pattern wahi hai jo pehle se 7 features mein hai** (`ChatHistory`, `ChatAgentControls`, `ChatUploads`, `ChatAccount`, `ChatDataControls`, `ChatProjects`, `ChatImagine`): ek `VoiceHost` interface + `ChatVoice` class `: VoiceHost by host` + `ChatViewModel` mein `by lazy` delegate.

```kotlin
private val voiceFeature: ChatVoice by lazy { ChatVoice(voiceHost) }
private val voiceHost: VoiceHost by lazy { object : VoiceHost { /* 17 members */ } }
```

`ChatVoice` object `voiceFeature` ke naam se hai — **`ChatVoice` class ka naam host property (`voice`) se collide nahi karta**, isliye koi naming clash nahi.

### Public API bilkul same — UI ko touch nahi karna pada

`ChatViewModel` par **10 forwarders** hain, wahi naam aur wahi signature:

```kotlin
fun replyTtsOptions(): com.dua.ai.voice.TtsOptions? = voiceFeature.replyTtsOptions()
fun replyVoicePlan(text: String): List<String> = voiceFeature.replyVoicePlan(text)
suspend fun generateReplyAudio(text: String): ByteArray = voiceFeature.generateReplyAudio(text)
val liveStart: StateFlow<LiveStartState> get() = voiceFeature.liveStart
val liveUsage: StateFlow<com.dua.ai.voice.live.LiveUsage?> get() = voiceFeature.liveUsage
fun liveVoiceAvailable(): Boolean = voiceFeature.liveVoiceAvailable()
fun liveOptions(): com.dua.ai.voice.live.LiveOptions? = voiceFeature.liveOptions()
fun loadLiveUsage() = voiceFeature.loadLiveUsage()
fun clearLiveError() = voiceFeature.clearLiveError()
fun startLiveVoice() = voiceFeature.startLiveVoice()
```

Isliye **`ui/` ke 4 consumer files chhue hi nahi** — `LiveVoiceScreen.kt`, `VoiceSettingsScreen.kt`, `ReplyVoice.kt`, `DuaNavHost.kt` sab `viewModel.<same name>` hi call karte hain.

### Jo `ChatViewModel` mein hi rehna chahiye tha

Yeh teen voice block ke aas-paas pade the par **voice ke nahi hain** — inhe chheda nahi:

| Member | Kyun ChatViewModel mein hi |
| --- | --- |
| `_online` / `online` | Network state — `setNetwork()`, `sendMessage` offline-retry, aur 3 UI files (`ChatRoute`, `ReplyVoice`, `VoiceModeScreen`) use karte hain |
| `replyAudioCache` | `HistoryHost` ka contract hai — `ChatHistory.clearAllChats()` ise `clear()` karta hai |
| `integrations` / `_switches` / `_projectId` / `localSettings` | Shared state, sirf voice ka nahi |

**`ChatVoice` inhe host ke through padhta hai** (`VoiceHost` mein declared, `ChatViewModel` implement karta hai) — koi copy nahi, koi duplicate state nahi.

### Verify — jo actually chalaya

| Check | Result |
| --- | --- |
| **Byte-compare** — purana chunk vs naya `ChatVoice` body | **sirf 4 line different, sab intentional** (`private`→`internal` ×2, `_projectId.value`→`projectId.value` ×2). Baaki **89 line byte-identical** ✅ |
| Moved block ke **17 class-level members** — sab present? | `generatedAudioApi`, `_liveStart`, `_liveUsage` ChatVoice mein; **14 ChatViewModel surface par** → **0 missing** ✅ |
| `VoiceHost` interface members | **17** — host object ke saare 17 overrides match ✅ |
| **Accidental override** (`ChatVoice` bhi declare kare + interface mein bhi ho) | **0** ✅ |
| Duplicate class-level declarations | `ChatVoice.kt` **0** · `ChatViewModel.kt` ke 2 (`refreshEngineCapabilities`, `storedFileUrl`) **pehle se the** — verify kiya `/tmp/CVM_before.kt` mein bhi 2-2 ✅ |
| Dangling internal refs (`_liveStart`/`_liveUsage`/`generatedAudioApi`/`finishLiveVoice`) ChatVoice ke bahar | **0** — bas do deliberate `voiceFeature._liveStart` reads (`liveProjectBusy`, init collector) ✅ |
| Unused imports in `ChatVoice.kt` | **0** — 9 imports, sab use hote hain ✅ |
| Brace balance dono files | **0 / 0** ✅ |
| Voice symbols ka repo-wide reach (main + test + androidTest) | 12 symbols, **0 break** — `replyTtsOptions` 5 · `liveStart` 14 · `liveUsage` 13 · `LiveStartState` 11 · `replyAudioCache` 8 · baaki 3–8 each ✅ |
| Stale comment `com.dua.ai.auth.GoogleSignIn` (line ~581) | **theek kiya** → `com.dua.ai.account.GoogleSignIn` (Pass 3 ka bacha hua) ✅ |
| `\u0000` cache-key separator | **byte-exact** preserve hua ✅ |
| 7 trap files | **koi touch nahi** ✅ |

### Aage kya — Step 2

Plan table ke mutabiq agla: **Attachment / stored-files glue → `ChatAttachments.kt`** (`uploadPicked`, `cancelUpload`, `discardUploads`, `flushPendingFileDeletes`, `attachmentImageBytes`, `_attachments` clear paths, ~250–280 line). `uploadsFeature` (`ChatUploads`) already maujood hai — yeh uska UI-side glue hai jo upar reh gaya.

Steps 3–6 (settings/writing, memory notes, `sendMessage` split, lifecycle) abhi baaki. **Step 4 ke baad rukne ka point** hai.

**Build yahan nahi chala** — sandbox mein Java/Android SDK nahi hai. Jo kiya woh byte-compare + interface/override/duplicate/import parity hai. Asli confirmation CI se aayegi.

### Merge checklist

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```

Teeno pass hone chahiye. Uske baad app mein yeh **5 jagah** — yeh exactly woh raaste hain jo voice code se guzarte hain:

1. **Reply ka AI voice** — koi reply suno (AI voice on) → audio chalta hai, replay cache se free
2. **Settings → Voice** — provider/voice/speed badlo, preview suno, live usage number dikhe
3. **Live voice screen** — mic permission → session start, minutes count, end par transcript chat mein save
4. **Voice mode (hands-free)** — orb tap → live voice available ho to LiveVoice par jaata hai, warna HandsFree
5. **Offline** — internet off karke AI voice → "AI voice setup is unavailable. Try device voice." wala sahi message
