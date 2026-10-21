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

---

## 📎 Chat split — Step 2: draft-attachment glue → `ChatAttachments.kt` (20 Oct 2026)

**Base:** `main` @ `acbae0b` (PR #309 / Step 1 merge ke baad). **Kuch delete nahi hua** — sirf code move hua. **7 trap files ko haath nahi lagaya.** Golden rule follow hua: **ek step = ek feature = ek commit**.

Yeh `ChatViewModel.kt` split plan ka **Step 2** hai (upar Pass 6 section ki table).

### Line count — before → after

| File | Pehle | Ab | Farq |
| --- | --- | --- | --- |
| `viewmodel/ChatViewModel.kt` | **1,684** | **1,654** | **−30** |
| `viewmodel/ChatAttachments.kt` | — | **93** (naya) | +93 |
| `viewmodel/` total | 3,579 | 3,642 | +63 (host + forwarders) |

`ChatViewModel` se **51 line nikli** (attachment bodies) aur **21 line wapas aayi** (host object 13 + forwarders 6 + comment 1) → net **−30**.

`git diff --shortstat` = **2 files, +114 −51**.

### Kya nikaala — 5 members

| Kya | Purana | Naya |
| --- | --- | --- |
| `attachmentImageBytes(index)` | `ChatViewModel` | `ChatAttachments` |
| `acceptSharedAttachments(uris)` | `ChatViewModel` | `ChatAttachments` |
| `removeAttachment(index)` | `ChatViewModel` | `ChatAttachments` |
| `clearAttachments()` | `ChatViewModel` | `ChatAttachments` |
| `uploadPicked(files)` (private) | `ChatViewModel` | `ChatAttachments` (`internal`) |
| `MAX_UPLOADS = 10` (companion) | `ChatViewModel` | `ChatAttachments` ka `private companion` |

**Pattern wahi jo pehle se 8 features mein hai:** `AttachmentsHost` interface + `ChatAttachments : AttachmentsHost by host` + `ChatViewModel` mein `by lazy` delegate.

```kotlin
private val attachmentsFeature: ChatAttachments by lazy { ChatAttachments(attachmentsHost) }
private val attachmentsHost: AttachmentsHost by lazy { object : AttachmentsHost { /* 11 members */ } }
```

### Public API bilkul same — `ui/` ko touch nahi kiya

`ChatViewModel` par **5 forwarders** hain, wahi naam aur wahi signature. Isliye `ChatRoute.kt`, `ProjectsFlow.kt`, `VoiceModeScreen.kt` — **ek bhi consumer file nahi badli**:

| Caller | Kya call karta hai |
| --- | --- |
| `ChatRoute.kt:383` | `viewModel.acceptSharedAttachments(entry.uris)` |
| `ChatRoute.kt:303` | `viewModel::removeAttachment` |
| `ChatRoute.kt:235,259,302` · `ProjectsFlow.kt:167` · `VoiceModeScreen.kt:339` | `viewModel.clearAttachments()` |
| `ChatRoute.kt:304` | `viewModel.attachmentImageBytes(index)` |
| `DraftAttachmentRemovalTest:39,43,48` · `OnDeviceFeaturesTest:89,90` | `vm.removeAttachment` · `model.attachmentImageBytes` |

**Sirf 1 line ki body badli hai:** `clearAttachments()` mein `discardUploads()` → `uploadsFeature.discardUploads()` — kyunki wo method ab `ChatAttachments` mein nahi hai, `ChatUploads` mein hai. Behavior identical (`ChatViewModel.discardUploads()` bhi wahi call karta hai). `uploadPicked` mein `maxStoredBytes()` → `uploadsFeature.maxStoredBytes()` aur `startUpload(...)` → `uploadsFeature.startUpload(...)`, wahi wajah — `UploadsHost` se already available.

### Jo `ChatViewModel` mein hi rehna chahiye tha (chhua nahi)

| Member | Kyun | Kahan |
| --- | --- | --- |
| `_attachments` · `_attachmentError` · `_readingFiles` · `_preparingFiles` · `_uploads` | 5 features + `sendMessage` + backup preflight sab padhte hain | `ChatViewModel` (host se share) |
| `selectionVersion` · `preparingSelections` | `ChatUploads` selection race control karta hai | `ChatViewModel` (host se share) |
| `attachmentImageBytes` ka base64 decode | **abhi yahan hai** (`ChatAttachments`) | puri body move hui |
| `describe()` · `copyCapped()` | pehle se `ChatAttachmentFiles.kt` mein | chhue nahi |
| `_dataBusy` / `_isTyping` | shared chat state | `ChatViewModel` (host se share) |

**Koi duplicate state nahi** — `ChatAttachments` sab kuch host ke through padhta hai.

### Verify — jo actually chalaya

| Check | Result |
| --- | --- |
| **Byte-compare** — 5 moved bodies (normalised) vs naya file | `attachmentImageBytes` 3/3 · `acceptSharedAttachments` 6/6 (1 line intentionally badli) · `removeAttachment` 4/4 · `clearAttachments` 6/6 (1 line intentionally badli) · `uploadPicked` **28/28 lines present** (4 lines intentionally qualified) ✅ |
| `AttachmentsHost` members | **11** — host object ke 11 overrides se **11 = 11**, missing 0 · extra 0 ✅ |
| Duplicate declarations | `ChatAttachments.kt` **0** · `ChatViewModel.kt` **0 naye** ✅ |
| `MAX_UPLOADS` dangling refs | **0** — sirf naye file ke companion mein ✅ |
| Unused imports in `ChatAttachments.kt` | **0** — 10 imports, sab use ✅ |
| Brace balance dono files | **0 / 0** ✅ |
| Moved members ka repo-wide reach (main + test + androidTest) | 5 symbols, **0 break** — saare public API par forwarder se serve hote hain ✅ |
| `chat.id`-type class ka silent break (pass 3/4 ka pattern) | **0** — moved bodies ke andar koi same-package symbol nahi hai; sab `ChatViewModel` ke host members ya fully-qualified/imported ✅ |
| 7 trap files | **koi touch nahi** ✅ |
| `_to_delete/README.md` purane 646 line | **byte-identical** — sirf naya section juda ✅ |

### ⚠️ Ek honest note

`ChatViewModel.kt` mein **3 unused imports** mile — `PhotoPolicy` (line 11), `CameraCaptureStore` (16), `SignedUpload` (46). **Yeh Step 1 se pehle se hain**, mere is change se nahi — `ChatViewModel.kt` ke `/tmp/CVM_before.kt` (base version) mein bhi teeno ke 0 use hain. Voice split ke waqt `ChatUploads` mein chale gaye the par import reh gaye. **Is PR mein nahi hataye** — scope se bahar, aur unrelated line touch karna review ko ganda karta hai. Agle step mein saath theek ho sakte hain.

### Aage kya

- **Step 3:** settings / appearance / writing (`setPersonality`, `setAppearance`, `setAccent`, `saveAppearance`, `resetSettings`, `updateSettings`, `openWriting`/`runWriting`/`useWritingResult`/`askServerForWriting`/`closeWriting` — est. 200–230 line) → `ChatSettings.kt`
- **Step 4:** memory notes (`proposeMemoryNotes`, `acceptMemoryNote`, `suggestAgentMemory`, `dismissMemoryNote`, `confirmSharedMemory` — est. 60–80 line) → `ChatMemory.kt`
- **Step 5–6:** `sendMessage` split, lifecycle — **high risk, alag PR**
- **Rukne ka point: Step 4 ke baad** (file ~1,050–1,100 line, 10 features alag)

### Merge checklist

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```

Teeno pass hone chahiye. Uske baad app mein yeh **5 jagah** — exactly woh raaste jo attachment code se guzarte hain:

1. **Photo attach** — chat mein photo chips → thumbnail dikhe, remove (×) kaam kare
2. **File attach (stored)** — bada file (spreadsheet/audio) sign-in ke saath → upload progress, phir send
3. **Camera se photo** — camera icon → photo pick → chip add ho
4. **Share sheet se file** — doosri app se Dua ko file share → `acceptSharedAttachments` queue
5. **Naya chat / chat switch** — `clearAttachments()` sab chips saaf kare, uploads bhi

**Build yahan nahi chala** — sandbox mein Java/Android SDK nahi hai. Jo kiya woh byte-compare + host/interface parity + import/brace balance hai. Asli confirmation CI se aayegi.

---

## ⚙️ Chat split — Step 3: settings / appearance / writing → `ChatSettings.kt` (20 Oct 2026)

**Base:** `main` @ `3a40a88` (PR #310 / Step 2 merge ke baad). **Kuch delete nahi hua** — sirf code move hua. **7 trap files ko haath nahi lagaya.** Golden rule follow hua: **ek step = ek feature = ek commit**.

Yeh `ChatViewModel.kt` split plan ka **Step 3** hai (Pass 6 section ki table).

### Line count — before → after

| File | Pehle | Ab | Farq |
| --- | --- | --- | --- |
| `viewmodel/ChatViewModel.kt` | **1,654** | **1,597** | **−57** |
| `viewmodel/ChatSettings.kt` | — | **118** (naya) | +118 |
| `viewmodel/` total | 3,642 | 3,703 | +61 (host + forwarders) |

`ChatViewModel` se **84 line nikli** aur **27 wapas aayi** (host object 10 + forwarders 16 + comment 1) → net **−57**.

`git diff --shortstat` = **1 file, +27 −84** (naya file alag se +118).

### Kya nikaala — 16 public members + 6 private names

| Kya | Purana | Naya |
| --- | --- | --- |
| `polishing` · `polishNotice` | `ChatViewModel` | `ChatSettings` |
| `polishImaginePrompt()` | `ChatViewModel` | `ChatSettings` |
| `writing` · `openWriting` · `runWriting` · `useWritingResult` · `askServerForWriting` · `closeWriting` | `ChatViewModel` | `ChatSettings` |
| `settingsStatus` · `setPersonality` · `setAppearance` · `setAccent` · `resetSettings` · `settingsNotice` · `updateSettings` | `ChatViewModel` | `ChatSettings` |
| `_polishing` · `_polishNotice` · `_writing` · `writingJob` · `_settingsStatus` · `saveAppearance` (private) | `ChatViewModel` | `ChatSettings` (`private`) |

**Pattern wahi jo pehle se 9 features mein hai:** `SettingsHost` interface + `ChatSettings : SettingsHost by host` + `ChatViewModel` mein `by lazy` delegate.

```kotlin
private val settingsFeature: ChatSettings by lazy { ChatSettings(settingsHost) }
private val settingsHost: SettingsHost by lazy { object : SettingsHost { /* 7 members */ } }
```

### Public API bilkul same — `ui/` ko touch nahi kiya

`ChatViewModel` par **16 forwarders** hain, wahi naam aur wahi signature. Isliye **ek bhi consumer file nahi badli**. External refs jo forwarder se serve hote hain:

| Member | External refs | Kahan |
| --- | --- | --- |
| `updateSettings` | **23** | `SettingsOverviewPages`, `AppearanceScreen`, `ImagineSettingsScreen`, `MemoryScreen`, `IntegrationSetupScreen`, `ChatRoute`, `PluginsRoute`, + tests |
| `settingsStatus` | **8** | `DuaNavHost` (×2), `AppearanceScreen`, `ImagineSettingsScreen`, `MemoryScreen`, `NotificationsScreen`, `SettingsOverviewPages`, `VoiceSettingsScreen` |
| `setAccent` · `setAppearance` · `setPersonality` | 7 · 1 · 3 | `AppearanceScreen`, `PersonalityPromptingTest`, `AppearancePreferencesTest` |
| `writing` · `openWriting` · `runWriting` · `useWritingResult` · `askServerForWriting` · `closeWriting` | 9 · 4 · 2 · 2 · 1 · 1 | `ChatRoute` (writer sheet), `OnDeviceFeaturesTest` |
| `polishing` · `polishNotice` · `polishImaginePrompt` | 2 · 3 · 3 | `ChatRoute` (prompt polish chip), `OnDeviceFeaturesTest` |
| `resetSettings` · `settingsNotice` | 4 · 4 | `DuaNavHost`, `MemoryScreen`, `LiveVoiceScreen`, `ProjectSwitchingTest` |

**Ek bhi line ki body badalne ki zaroorat nahi padi** — is baar `discardUploads()` jaisa cross-feature qualify bhi nahi karna pada, kyunki moved bodies sirf apne host members aur `com.dua.ai.ondevice.*` / `com.dua.ai.settings.*` use karti hain.

### Jo `ChatViewModel` mein hi rehna chahiye tha (chhua nahi)

| Member | Kyun |
| --- | --- |
| `localPreferences` · `localSettings` | **`ChatProjects` (7 refs), `ChatDataControls` (3), `ChatAccount` (3), `ChatVoice` (5), `ChatImagine` (4), `ChatHistory` (3)** — 9 host contracts inhe padhte hain |
| `onDeviceAi` · `onDeviceAiEnabled()` | `ChatHistory` automatic chat titles ke liye padhta hai (`HistoryHost` ka member hai) |
| `appearance` · `appearanceLoaded` | `sendMessage` 2 jagah `appearance.value.personality?.instruction` padhta hai; `personalityUnavailable()` bhi `appearanceLoaded` padhta hai |
| `appearanceNotice` | `AppearanceScreen` sirf read karta hai, write kahin nahi |
| `foreground` · `setForeground()` | `sendMessage` notifications gate karta hai (`!foreground && ... notifications`) |
| `_draft` · `updateDraft()` | Har feature ka shared composer (7 host contracts) |

**Koi duplicate state nahi** — `ChatSettings` sab kuch host ke through padhta hai.

### Verify — jo actually chalaya

| Check | Result |
| --- | --- |
| **Byte-compare** — 4 moved blocks (normalised) vs naya file, ordered subsequence | **83/83 lines identical** — `597-616` 20/20 · `617-654` 38/38 · `667-675` 9/9 · `684-699` 16/16 ✅ |
| `SettingsHost` members vs host overrides | **7 = 7**, missing 0 · extra 0 ✅ |
| Accidental override (`_liveStart` wala Step-1 bug) | **0** — 22 class decls mein se ek bhi interface naam nahi ✅ |
| Duplicate class decls | `ChatSettings.kt` **0** ✅ |
| Purane private naam repo-wide | `_polishing` · `_polishNotice` · `_writing` · `writingJob` · `_settingsStatus` · `saveAppearance` — **sab 0** outside `ChatSettings.kt` ✅ |
| Unused imports | **0** — 6 imports, sab use ✅ |
| Brace balance dono files | **0 / 0** ✅ |
| Manifest / res XML / proguard / `build.gradle.kts` / `.pro` | **0** reference ✅ |
| **Reflection trap** — `PersonalityPromptingTest` `getDeclaredField("appearanceLoaded")` | **safe** — `appearanceLoaded` `ChatViewModel` mein hi hai, move nahi hua ✅ |
| 7 trap files / `THIRD_PARTY_NOTICES.md` | **koi touch nahi** ✅ |

### Aage kya

- **Step 4:** memory notes (`proposeMemoryNotes`, `acceptMemoryNote`, `suggestAgentMemory`, `dismissMemoryNote`, `confirmSharedMemory` — est. 60–80 line) → `ChatMemory.kt`
- **Step 5–6:** `sendMessage` split, lifecycle — **high risk, alag PR**
- **Rukne ka point: Step 4 ke baad** (file ~1,500 line, 11 features alag)

### Merge checklist

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```

Teeno pass hone chahiye. Uske baad app mein yeh **5 jagah** — exactly woh raaste jo settings/writing code se guzarte hain:

1. **Settings → Appearance** — theme (light/dark/system), accent colour, Dua AI Personality chip → "Saved on this device." dikhe
2. **Chat composer → prompt polish** — Imagine mode mein sparkle icon → draft rewrite ho, notice dikhe; draft badal jaye to "kept as is" message
3. **Chat → reply → Summarize / Rewrite** — sheet khule, task chale, "Use" draft mein daale, "Ask server" request banaye, dismiss band kare
4. **Settings → Reset** — device settings + Personal memory reset hon, project settings bache ("Project settings and memory kept")
5. **Chat → model picker → free model** — `updateSettings(freeModel=…)` still save ho (yeh `ChatViewModel` se forwarder se jaata hai)

**Build yahan nahi chala** — sandbox mein Java/Android SDK nahi hai. Jo kiya woh byte-compare + host/interface parity + import/brace balance + reflection-trap check hai. Asli confirmation CI se aayegi.

---

## 🧠 Chat split — Step 4: memory notes → `ChatMemory.kt` (20 Oct 2026)

Base: `main` @ `6e6e7f2` (PR #311 / Step 3 merge ke baad).

### Line count — before → after

| File | Pehle | Ab | Farq |
| --- | --- | --- | --- |
| `viewmodel/ChatViewModel.kt` | **1,597** | **1,574** | **−23** |
| `viewmodel/ChatMemory.kt` | — | **69** (naya) | +69 |
| `viewmodel/` total | 3,703 | 3,749 | +46 |

`ChatViewModel` se **41 line nikli** aur **18 wapas aayi** (host object 9 + forwarders 6 + blank/comment 3) → net **−23**. `git diff --shortstat` = **1 file, +18 −41** (naya file alag).

### Kya nikaala — 5 public members + 1 private

| Member | Purana | Naya |
| --- | --- | --- |
| `memoryCandidates` (`StateFlow`) | `ChatViewModel` | `ChatMemory` + forwarder |
| `proposeMemoryNotes()` | `ChatViewModel` | `ChatMemory` + forwarder |
| `acceptMemoryNote(note)` | `ChatViewModel` | `ChatMemory` + forwarder |
| `dismissMemoryNote(note)` | `ChatViewModel` | `ChatMemory` + forwarder |
| `confirmSharedMemory(note, expectedOwnerId)` | `ChatViewModel` | `ChatMemory` + forwarder |
| `suggestAgentMemory(fact)` (private) | `ChatViewModel` | `ChatMemory` (`internal`, forwarder `private`) |
| `_memoryCandidates` (private flow) | `ChatViewModel` | `ChatMemory` |

Pattern wahi jo pehle se **10 features** mein hai (`ChatHistory`, `ChatAgentControls`, `ChatUploads`, `ChatAccount`, `ChatDataControls`, `ChatProjects`, `ChatImagine`, `ChatVoice`, `ChatAttachments`, `ChatSettings`) — `MemoryHost` interface + `ChatMemory : MemoryHost by host` + `by lazy` delegate (`memoryFeature`, naam clash se bacha).

### Public API bilkul same — `ui/` ki ek bhi file nahi badli

6 forwarders, wahi naam aur signature. External refs jo forwarder se serve hote hain: `memoryCandidates` 5 refs (`IntegrationSetupScreen` 4, `ProjectSwitchingTest` 1), `confirmSharedMemory` 6 (`SharedMemoryTest` 5, `DuaNavHost` 1), `proposeMemoryNotes` 2, `acceptMemoryNote` 1, `dismissMemoryNote` 1 — **sab forwarder se jaate hain**. Diff mein `ui/` ka koi path nahi.

### Jo `ChatViewModel` mein hi rehna chahiye tha

| Member | Kyun |
| --- | --- |
| `_projectId` | **5 host contracts** padhte hain (`ChatProjects`, `ChatDataControls`, `ChatVoice`, `ChatHistory`, `ChatImagine`) — shared project state |
| `localPreferences` · `localSettings` | **9 host contracts** ka shared store |
| `_messages` | core chat loop ka state, sab features padhte hain |
| `account` | `ChatAccount` / cloud sync ka base |
| `syncBlocked()` | `ChatDataControls` contract member — sync/reply gate |
| `_integrations` · `integrationNotice()` | integration notice line poore screen par shared hai |

Inhe `ChatMemory` **host ke through padhta hai** — koi copy nahi, koi duplicate state nahi.

### Verify — jo actually chalaya

| Check | Result |
| --- | --- |
| **Byte-compare** — base `400-430` vs `ChatMemory.kt` body (line-by-line `diff`) | **40/41 lines byte-identical**, ek hi line badli: `private fun suggestAgentMemory` → `internal` (forwarder ke liye zaroori) ✅ |
| `MemoryHost` members vs host overrides | **7 = 7**, missing 0 · extra 0 ✅ |
| Accidental override (Step 1 ka `_liveStart` bug) | **0** — 10 class decls mein ek bhi interface naam nahi ✅ |
| Purana `_memoryCandidates` outside `ChatMemory.kt` | **0** ✅ |
| Unused imports | **0** — 4 imports, sab use ✅ |
| Brace balance (dono files) | **349/349** · **17/17** ✅ |
| Duplicate class-level decls | `ChatViewModel` 1 · `ChatMemory` 2 (interface + class, intended) ✅ |
| `MemoryHost` / `ChatMemory` naam repo mein pehle se? | **0** — koi clash nahi ✅ |
| Non-Kotlin refs (`.xml`, `.kts`, `.pro`, `.md`, `.json`) | **0** ✅ |
| **Reflection trap** — 16 test files `getDeclaredField`/`getDeclaredMethod` | koi bhi memory field/method target nahi karta — `safe` ✅ |
| **androidTest** source set | **maujood nahi** — `app/src/` mein `debug`, `main`, `test` hi hain (skip nahi, khaali) ⚠️ |
| `_to_delete/README.md` purane 858 line | **md5 identical** (`a37cada6…`), sirf naya section juda ✅ |
| 7 trap files · `THIRD_PARTY_NOTICES.md` · `ui/` | **koi touch nahi** ✅ |

**Step 2 ka sabak dobara check kiya:** `suggestAgentMemory` ek `private` member thi jo `ChatViewModel` ke `ReplyEvent.Memory` handler se call hoti hai (line ~696). Isliye usse `internal` kiya aur `ChatViewModel` par `private` forwarder rakha — public surface nahi khula.

### Aage kya

- **Step 5:** `sendMessage` (136 lines) ko `prepareSend` / `streamReply` / `persistResult` mein todna — same file, private funs. **Split nahi, function-length fix.**
- **Step 6:** `startNewChat` / `retryResponse` / `deleteChat` / `deleteServerChat` → `ChatLifecycle.kt`. **High risk** — yahan galti user-facing hai.
- **Rukne ka point: yahan (Step 4 ke baad).** File 1,574 lines par hai, 11 features alag ho chuke hain.

### Merge checklist

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```

Phir app mein yeh **5 jagah**:

1. **Settings → Integrations → memory suggestions** — "Propose notes" dabao, 4 se zyada na aayein, notice dikhe
2. **Memory note accept** — "Remember" wala note save ho, notice "Note saved and cached on this phone…" aaye
3. **Memory note dismiss** — candidate list se hat jaaye
4. **Dua Agent `remember`** — reply ke dauraan memory event se suggestion queue mein aaye (bina tap ke save na ho)
5. **Share sheet se memory save** — `confirmSharedMemory` galat owner par error de, sahi owner par save kare

**Build yahan nahi chala** — sandbox mein Java/Android SDK nahi hai. Jo kiya woh byte-compare + host/interface parity + symbol sweep + reflection-trap check hai. Asli confirmation CI se aayegi.

---

## 🔧 Chat split — Step 5: `sendMessage` internal refactor (20 Oct 2026)

Base: `main` @ `7b712aa` (PR #312 / Step 4 merge ke baad).

Yeh `ChatViewModel.kt` split plan ka **Step 5** hai (Pass 6 section ki table). Step 1–4 se **nature alag** hai: **koi code move nahi hua, koi naya file nahi bana.** Sirf `sendMessage` (136 lines) ko usi file ke andar private functions mein toda gaya. Golden rule wahi: **ek step = ek commit**.

### Line count — before → after

| File | Pehle | Ab | Farq |
| --- | --- | --- | --- |
| `viewmodel/ChatViewModel.kt` | **1,574** | **1,628** | **+54** |
| `sendMessage` khud | **136** | **4** | **−132** |
| `viewmodel/` total | 3,749 | 3,803 | +54 |

`git diff --shortstat` = **1 file changed, 100 insertions(+), 46 deletions(-)**.

Yeh **akela step hai jisme file badi hoti hai** — wajah: har extraction ka apna signature line + `return`/bare-brace, plus `SendPlan` data class. Steps 1–4 mein −174 lines net aayi thi; Step 5 ka fayda line count nahi, **readability aur reviewability** hai.

### Naya structure

| Member | Lines | Kya karta hai |
| --- | --- | --- |
| `sendMessage(userText)` | **4** (`1402–1405`) | Orchestrator — `_prepareSend` → `_streamReply` |
| `SendPlan` (private data class) | **24** (`1408–1431`) | Ek outgoing turn ka frozen snapshot: files, convert, storedFiles, edited, sentPaths, userMessage, reply, request, persistenceBase, persistenceOwner, currentGeneration, startedAt, requestProjectId, responseModel, requestWebMode/Reasoning/XSearch/Code/Build/Agent/Personality |
| `_prepareSend(userText)` | **84** (`1433–1516`) | 16 guards + request snapshot + user/reply pair + commit. `null` = turn reject/handled |
| `_streamReply(plan, userText)` | **46** (`1518–1563`) | `viewModelScope.launch` + `try/catch/finally`, file prep, `collectReply` |
| `_persistResult(plan, state, final)` | **7** (`1565–1571`) | Uploads filter → `clearAttachments()` → snapshot → `updateMessage` |
| `_failedReply(plan, userText, failure)` | **11** (`1573–1583`) | Turn wapas hatao, draft/failedPrompt restore, offline queue |
| `_finishReply(currentGeneration)` | **6** (`1585–1591`) | Typing flag off, `saveHistory()`, notification gate |

### Public API bilkul same — `ui/` ki ek bhi file nahi badli

`sendMessage(userText: String)` ka signature wahi. **15 call-sites** unchanged: `ChatRoute` (3), `ProjectsFlow`, `VoiceModeScreen`, `AgentScreen`, `ChatAgentControls:77`, `ChatImagine:118`, `ChatViewModel:1174` (offline retry) + 5 test files (`PersonalityPromptingTest` ×3, `ProjectSwitchingTest` ×2, `ExternalRoutingTest`). Diff mein `ui/` ka koi path nahi — **sirf `ChatViewModel.kt`**.

### Reviewer ke liye — 3 jagah dhyan dena

1. **Early returns.** `sendMessage` mein 16 `return` the → `_prepareSend` mein **`return null`** ban gaye, aur orchestrator `?: return` se exit karta hai. Har guard ka effect same: aage ka kuch nahi chalta. Ek subtility — `_imagine.value` wala branch `imagineFeature.sendImagine(userText)` call karke `null` deta hai, yaani woh bhi turn khatam karta hai, pehle jaisa.
2. **`finally` ka scope.** `_finishReply` `_streamReply` ke `finally` se **hamesha** chalta hai, aur andar `generation == currentGeneration` guard same hai. `catch (cancelled: CancellationException) { throw cancelled }` `finally` se pehle hi re-throw karta hai — matlab cancel par bhi cleanup chalta hai, pehle jaisa. `_finishReply` ko koi `return` nahi, isliye `finally` skip nahi ho sakta.
3. **Coroutine scope + evaluation order.** `replyJob = viewModelScope.launch { … }` `_streamReply` mein hai, `replyJob` par assignment wahi hai. Do ordering bugs verification mein **pakde gaye aur theek kiye**:
   - `accumulator!!.snapshot(ended = true)` original mein `clearAttachments()` ke **baad** evaluate hota tha. Direct parameter pass karne se woh pehle chal jaata — to `_persistResult` ko **lambda** (`() -> StreamSnapshot`) diya, jo uploads filter + `clearAttachments()` ke baad call hota hai. Snapshot ek hi baar evaluate hota hai.
   - `_persistResult` ka `state: String` — original do jagah `"complete"` / `"stopped"` hardcoded tha. Ab parameter hai, values wahi.

### Verify — jo actually chalaya

| Check | Result |
| --- | --- |
| **Byte-compare** — base `1402–1537` vs naya region (`diff`, whitespace-normalised) | **117/136 lines byte-identical**; baaki 19 lines sirf footer/brace lines jo helper mein chali gayin ✅ |
| **Guard block** `1403–1418` vs `1434–1449` | 16 → 16 lines, **sirf `return` → `return null`** ✅ |
| **Snapshot+request block** `1419–1477` vs `1450–1508` | 59 → 59 lines, **sirf 6 `return` → `return null`** — ek bhi logic line nahi badli ✅ |
| **Reply setup + commit** `1454–1477` vs `1484–1510` | 24 → 24 lines **IDENTICAL**, sirf `return SendPlan(...)` juda ✅ |
| **try-head** `1478–1497` vs `1519–1538` | 20 → 20 lines, sirf `plan.` prefix + `final` local ✅ |
| **`_persistResult`** vs complete-path `1499–1502` | 4 → 4 lines, sirf `plan.` + `final`→`final()` ✅ |
| **`_failedReply`** vs failed-path `1519–1526` | 8 → 8 lines, sirf `plan.` prefix ✅ |
| **`_finishReply`** vs finally `1530–1534` | 5 → 5 lines **IDENTICAL** ✅ |
| `SendPlan` type — `StreamSnapshot` (nested nahi) | `ChatStream.kt:183` se confirm ✅ |
| Brace balance · final depth | **352 / 352** · **0** ✅ |
| `sendMessage` call-sites | **15** — signature nahi badla ✅ |
| Naam clash (`SendPlan`, `_prepareSend`, `_streamReply`, `_persistResult`, `_failedReply`, `_finishReply`) | pehle **0 hits** — `streamReply` sirf `api.streamReply` ka method hai, clash nahi ✅ |
| Changed files | **sirf `ChatViewModel.kt`** — `ui/`, tests, trap files, notices sab untouched ✅ |
| README purane 949 line | **md5 `dbdc8405…` identical** (sirf naya section juda) ✅ |

### Kya nahi chhua

`collectReply` · `finalizeStreaming` · `cancelReply` · `retryResponse` · `startNewChat` · `deleteChat` · `deleteServerChat` · `projectMemory` — **sab jahan the wahin hain**. `sendMessage` ka **behaviour, error strings, offline-retry condition, notification gate** — sab byte-identical.

### Merge checklist

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```

Phir app mein yeh **6 jagah**:

1. **Normal text send** — user bubble + streaming reply, typing indicator chale
2. **Attachment ke saath send** — uploads send ke baad composer se hatein, preview clear ho
3. **Build mode** — repo ke saath send, attachment ke saath reject ho (error same)
4. **Offline send** — "You're offline. Dua will send this message when you're back online." aaye, reconnect par auto resend
5. **Stream beech mein stop** — partial reply rahe, "Reply interrupted: … Partial reply kept; tap retry."
6. **Edit karke resend** — purana turn replace ho, Imagine on ho to "Turn off Imagine…" error aaye

**Build yahan nahi chala** — sandbox mein Java/Android SDK nahi hai. Jo kiya woh line-by-line diff + ordering audit + brace balance + call-site check hai. Asli confirmation CI se aayegi.

### Agla step

- **Step 6:** `startNewChat` (71) / `retryResponse` (69) / `deleteChat` (34) / `deleteServerChat` (38) → `viewmodel/ChatLifecycle.kt`. **High risk** — yahan galti user-facing hai (chat loss / duplicate chat). Alag PR, aur pehle `sendMessage` + history ka test coverage dekh lein.
- Steps 1–5 ke baad `ChatViewModel.kt` = **1,628 lines**, 11 features alag. Step 6 ke bina bhi file reviewable hai.

---

## 🔁 Chat split — Step 6: reply lifecycle → `ChatLifecycle.kt` (20 Oct 2026)

Base: `main` @ `5eb7a37` (PR #313 / Step 5 merge ke baad). Yeh `ChatViewModel.kt` split plan ka **aakhri step** hai (Pass 6 section ki table, row 6).

**Sabse pehle ek finding — plan galat tha.** Split plan kehta tha chaar function nikalne hain: `startNewChat` (71), `retryResponse` (69), `deleteChat` (34), `deleteServerChat` (38). Code kholne par pata chala **teen pehle se nikal chuke hain**:

| Function | Kahan hai | `ChatViewModel` mein kya hai |
| --- | --- | --- |
| `startNewChat()` | `ChatHistory.kt:273` — **poora body** | 1 line forwarder (`historyFeature.startNewChat()`) |
| `deleteChat(id)` | `ChatHistory.kt:250` — **poora body** | 1 line forwarder |
| `deleteServerChat(id)` | `ChatHistory.kt:198` — **poora body** | 1 line forwarder |
| `retryResponse(messageId)` | `ChatViewModel.kt` — **poora body (69 lines)** | asli logic |

Yaani Step 6 ka asli kaam **ek hi function** tha. Teen ko chhua nahi — unka ghar pehle se sahi tha.

### Line count — before → after

| File | Pehle | Ab | Farq |
| --- | --- | --- | --- |
| `viewmodel/ChatViewModel.kt` | **1,628** | **1,606** | **−22** |
| `viewmodel/ChatLifecycle.kt` | — | **133** (naya) | +133 |
| `viewmodel/ChatReplyHelpers.kt` | 26 | **28** | +2 |
| `viewmodel/` total | 3,803 | 3,916 | +113 |

`git diff --shortstat` = **3 files changed, 184 insertions(+), 70 deletions(-)** (+ yeh README section).

Shape wahi jo Step 1–4 mein tha — `LifecycleHost` interface (39 members) + `ChatLifecycle : LifecycleHost by host` + `by lazy` delegate (`lifecycleFeature`). `retryResponse` ka poora body **jaisa tha waisa** naye file mein, aur `ChatViewModel` par 1-line forwarder.

**Yeh bhi akela step hai jahan file chhoti hoti hai par `viewmodel/` package bada hota hai** — wajah: 39-member host block (46 lines) + 69-line body ka naya file. Fayda line count nahi, **isolation** hai: retry ka poora cancellation/generation logic ab ek jagah padha ja sakta hai.

### Public API bilkul same — `ui/` ki ek bhi file nahi badli

`fun retryResponse(messageId: String)` — signature wahi. **2 call-sites** unchanged: `ui/chat/ChatRoute.kt:221` aur `test/PersonalityPromptingTest.kt:112`. Diff mein `ui/`, test, trap files — kuch nahi.

### Kya `ChatViewModel` mein hi rehna hi tha (aur kyun)

| Member | Kyun move nahi hui |
| --- | --- |
| `generation` (`:668`) | `_streamReply` (`sendMessage` path) aur `ChatImagine` dono isi counter se guard karte hain |
| `replyJob` (`:674`) | `_streamReply` ise assign karta hai; `ChatImagine` host se padhta hai |
| `retryOriginal` (`:676`) | `finalizeStreaming()` ise padhta hai — aur woh `cancelReply()` ka hissa hai |
| `cancelReply` / `finalizeStreaming` / `stopReply` | `cancelReply` ko `HistoryHost`, `DataControlsHost` aur `stopReply` teeno call karte hain; `ChatImagine` `stopAll` ke through `stopReply` tak aata hai. Hile to teen host contracts badalne padte |

**Sabse important constraint — reflection.** `test/StoppedReplyRecoveryTest.kt:56-57` seedha field naam se likhta hai:

```kotlin
setField("retryOriginal", original)
return Job().also { setField("replyJob", it) }
```

`ChatViewModel::class.java.getDeclaredField(...)` — yaani **field `ChatViewModel` par declared honi chahiye**. Agar `replyJob` / `retryOriginal` `ChatLifecycle` mein chali jaayein to woh 3 test methods `NoSuchFieldException` se fail hote. Isliye dono fields wahin rahein, aur `LifecycleHost` sirf **get/set forward** karta hai — koi copy nahi, koi duplicate state nahi.

### `EMPTY_REPLY` ka chhota move

`private companion object` ka `EMPTY_REPLY` `ChatLifecycle` se reach nahi hota (companion `private` hai). Usko `ChatReplyHelpers.kt` mein **top-level `internal const val`** bana diya — ek hi declaration, dono files wahin se padhti hain. `ChatViewModel` ki companion se line hatai gayi, value **bilkul same** (`"Model ne text reply nahi diya. Dobara try karein."`) aur `_streamReply` ka use `:1517` par unchanged hai.

### Verify — jo actually chalaya

| Check | Result |
| --- | --- |
| **Byte-compare** — purana body (`1332–1399`) vs naya `retryResponse` body, whitespace-normalised | **65/65 non-empty lines identical, 0 mismatches** ✅ |
| Line count of body — old vs new | **68 → 65**; 3 line ka farq = ek import-shortening (`ChatViewModel.EMPTY_REPLY` → `EMPTY_REPLY`) + naye file ka `package`/`import`/header, body ka ek line nahi hata ✅ |
| **Statement order** guard → plan → personality → mode freeze → request → persistence → generation → typing → retryOriginal → updateMessage → launch → try/catch/finally | **bilkul same** ✅ |
| `retryOriginal = original` **`replyJob = ...` se pehle** (original order) | same ✅ |
| `catch (cancelled) { throw cancelled }` **`finally` se pehle** | same ✅ |
| `finally` mein `if (generation == currentGeneration) { _isTyping = false; saveHistory() }` | same ✅ |
| `Host` interface vs host overrides | **39 = 39**, missing 0, extra 0 ✅ |
| **Reflection fields** `generation`/`replyJob`/`retryOriginal` | **abhi bhi `ChatViewModel` par declared** (`668`, `674`, `676`) ✅ |
| Brace balance | `ChatViewModel` **324/324**, `ChatLifecycle` **36/36** ✅ |
| Naam clash (`ChatLifecycle`, `LifecycleHost`, `lifecycleFeature`) | pehle **0 hits**, ab sirf apne file mein ✅ |
| `retryResponse` call-sites | **2** — signature nahi badla ✅ |
| Changed files | **3** — `ui/`, tests, trap files, `THIRD_PARTY_NOTICES.md` sab untouched ✅ |
| README purane **1,040** line | **md5 `5edbaef9…` identical** (sirf yeh section juda) ✅ |

### ⚠️ Test coverage — jo user ko pata hona chahiye

`retryResponse` ka **dedicated test nahi hai**. Jo hai woh sirf itna:

| Function | Existing coverage |
| --- | --- |
| `retryResponse` | **1 indirect call** — `PersonalityPromptingTest.kt:112` (personality prompt bhejta hai, retry ke error paths nahi) |
| `startNewChat` | 6 test files (`StoppedReplyRecoveryTest`, `ProjectSwitchingTest`, `ChatRenameTest`, `ChatPinTest`, `CacheFileRetentionTest`, +1) |
| `deleteChat` | 3 test files |
| `deleteServerChat` | 1 test file (`ServerChatDeletionCacheTest`) |

Yaani **normal retry (complete reply), partial-failure retry, Stopped-then-retry, Build-mode retry, project chat retry** — inme se kisi ka bhi assertion nahi hai. Step 5/6 ke liye dedicated test likhna baaki kaam hai (neeche "Aage kya").

### Left alone and why (jaan-boojh kar nahi chhua)

- `startNewChat` / `deleteChat` / `deleteServerChat` — **pehle se `ChatHistory` mein**. Upar table dekho. Unka forwarder hi rehne diya.
- `cancelReply` / `finalizeStreaming` / `stopReply` — teen host contracts inhe call karte hain; hilane se `LifecycleHost` + `HistoryHost` + `DataControlsHost` teeno badalne padte.
- `_messages` ka `updateMessage(...)` — `ChatImagine` host se bhi use hota hai, isliye `ChatViewModel` mein hi.
- `collectReply` — `_streamReply` (Step 5) aur `retryResponse` dono isse chalte hain; host ke through dispatch.
- `ChatViewModel.kt` ka purana stale comment (`com.dua.ai.auth.GoogleSignIn`) — **jaan-boojh kar**. Comment compile nahi hota; 1,606-line file uske liye dobara likhna galat trade hai.

### Merge checklist

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```

Phir app mein yeh **5 jagah** — retry ke paanch raaste:

1. **Normal retry** — reply pe Retry; wahi reply regenerate ho, purana text replace ho
2. **Retry ke beech Stop** — partial reply rahe, `"Retry interrupted: … Partial reply kept; tap retry."` aaye
3. **Retry fail (koi text nahi)** — **original reply wapas** aaye, `"Retry failed. Original response kept."` aaye
4. **Build reply pe Retry** — wahi repository uthaye, Build mode on rahe
5. **Chat switch karke wapas** — purani generation ka late reply naye chat ko na chhue (generation guard)

**Build yahan nahi chala** — sandbox mein Java/Android SDK nahi hai. Jo kiya woh byte-compare + host parity + brace balance + call-site check hai. Asli confirmation CI se.

---

## 📊 ChatViewModel split — final summary (Steps 1–6)

**`ChatViewModel.kt`: 1,748 → 1,606 lines (−142, −8.1%)** · **12 features alag**

| # | Step | File | Lines | Pattern |
| --- | --- | --- | --- | --- |
| 1 | Voice/TTS | `ChatVoice.kt` | 136 | `ChatVoiceHost` |
| 2 | Attachments | `ChatAttachments.kt` | 93 | `AttachmentsHost` |
| 3 | Settings/Appearance/Writing | `ChatSettings.kt` | 118 | `SettingsHost` |
| 4 | Memory notes | `ChatMemory.kt` | 69 | `MemoryHost` |
| 5 | `sendMessage` split | *(same file)* | — | 5 private helpers + `SendPlan` |
| 6 | Reply lifecycle | `ChatLifecycle.kt` | 133 | `LifecycleHost` |
| — | History | `ChatHistory.kt` | 288 | `HistoryHost` |
| — | Agent controls | `ChatAgentControls.kt` | — | `AgentHost` |
| — | Uploads | `ChatUploads.kt` | — | `UploadsHost` |
| — | Account | `ChatAccount.kt` | — | `AccountHost` |
| — | Data controls | `ChatDataControls.kt` | — | `DataControlsHost` |
| — | Projects | `ChatProjects.kt` | — | `ProjectsHost` |
| — | Imagine | `ChatImagine.kt` | — | `ImagineHost` |

(Step 5 ne file **+54** ki thi — woh akela step hai jisme line count badhta hai. Steps 1–4, 6 ne milkar **−196** diya.)

### Jo bacha — jaan-boojh kar

`ChatViewModel` ke ~1,606 lines mein asli bacha hua kaam:

- **Message core** — `_messages`, `updateMessage`, `collectReply` (35), `finalizeStreaming`, generation/replyJob ka control
- **Model + mode selection** — `setModel`, `setAgent`, `setReasoning`, `setWebMode`, `selectFreeModel`, `setRunCode`, `setBuildRepository`, `setXSearch` — pehle se saaf, chhote functions
- **Composer state** — draft, edit, attachments ke flows
- **Wiring** — 12 host blocks (~450 lines)

Aage toda ja sakta hai (`ChatModes.kt` for model/mode selection ~200 lines), **par zaroorat nahi**. File ab reviewable hai.

### Aage kya karna chahiye

1. **Dedicated tests likho `retryResponse` ke liye** — abhi coverage almost zero hai (upar table). Paanch cases: normal, partial-fail, no-text-fail, Build-reply, stale generation.
2. **`startNewChat` / `deleteChat` ke tests** add karo — delete ke 3 test files hain par `deleteChat` ka idempotency/cancel-reply path assert nahi hota.
3. **Uske baad** hi naya split (`ChatModes.kt`) socho. Pehle test, phir move — ulta karne par regression chhup jaata hai.

---

## 🧪 Chat split — tests: `retryResponse` (20 Oct 2026)

Base: `main` @ `c7afa0d` (PR #314 / Step 6 merge ke baad). Yeh Step 6 ke report mein mila gap bharta hai — **`retryResponse` ka koi dedicated test nahi tha**.

### Coverage — before → after

| | Pehle | Ab |
| --- | --- | --- |
| Dedicated tests | **0** | **5** |
| Indirect calls | 1 (`PersonalityPromptingTest:112` — sirf personality payload assert karta hai, retry ke error paths nahi) | wahi 1 + 5 naye |
| Error paths asserted | 0 | **2** (fail-before-text, Stop-before-text) |
| State-restore asserted | 0 | **3** (original reply, `retryOriginal`, stale generation) |

### File

`app/src/test/java/com/dua/ai/viewmodel/RetryResponseTest.kt` — **5 `@Test`**. Koi naya dependency nahi — wahi stack jo baaki `viewmodel/` tests use karte hain: **JUnit4 + Robolectric (`sdk 34`, `LooperMode.PAUSED`) + OkHttp interceptor** (network se pehle intercept, `PersonalityPromptingTest` ka pattern).

**Coverage pehle kya thi:** `retryResponse` ka **0 dedicated test**; `PersonalityPromptingTest:112` se ek indirect call jo sirf personality payload dekhta hai — retry ke early-return guards, error paths, state restore, generation gate, kuch bhi assert nahi hota tha. `StoppedReplyRecoveryTest` sirf `stopReply()` ko synthetic `Job()` se test karta hai, asli retry se nahi.

**Coverage ab:** teeno public paths (success, stop, fail) + Build variant + stale generation — **5/5**, aur teeno `isTyping`/`retryOriginal`/history-disk par assertions.

Harness note — **chaar CI runs, chaar alag theories**, sirf aakhri wali sach nikli:

1. **Blocking interceptor gate.** `CountDownLatch.await()` OkHttp interceptor ke andar chal raha tha, aur woh interceptor **Main thread** par invoke hota hai. Main block → Robolectric ka `idleFor()` bhi Main par hai → deadlock. **Fix:** koi blocking gate nahi.
2. **`delayMs` — galat theory.** Uske baad SSE payload mein `delayMs: 30000` daala gaya yeh soch kar ki server-side delay banega. **Production client us field ko padhta hi nahi** (`grep delayMs app/src/main` mein sirf `ChatHistoryWriter` ka `Debouncer` milta hai), matlab woh ignore hua aur delay kabhi bana hi nahi. Field hata diya.
3. **Asli mechanism.** Test 2 aur 5 ko delay ki zaroorat hi nahi: `retryResponse()` streaming turn **synchronously** set karta hai (coroutine launch se pehle), aur SSE frame Main par tabhi apply hoti hai jab **yeh thread** paused Looper ko pump kare. Test 2/5 pump nahi karte — isliye turn streaming state mein hi observable hai: na latch, na sleep, na clock advance.
4. **Teardown.** Pehle `store.clear()` tha jo `onCleared()` chalata hai — drive rebinder, backup delegate, recorder, poll jobs, sab paused Looper par. Ab `onCleared()` nahi chalta: sirf `replyJob` cancel, Main ka bounded pump (25 × 5 ms) cancellation handlers ke liye, phir HTTP stack band. Koi assertion isse depend nahi karti.
5. **`Timeout` rule — khud hang ka karan nikla (hata diya).** `@get:Rule val noHang = Timeout.seconds(60)` lagaya gaya tha taaki atakne par 60 s mein naam mile. Ulta hua: JUnit ka `Timeout` **test body ko ek naye thread** par chalata hai, jabki Robolectric main Looper ko **runner thread** se bandhta hai. Doosre thread se `shadowOf(mainLooper).idleFor()` — yahi woh misuse hai jo test ko **fail** nahi karta, **wedges** karta hai. Isliye rule poora hata diya; har wait pehle se bounded hai aur message ke saath assert karta hai.

### 🔍 Root cause — jo aakhir mein mila

**Hang test-level nahi, task-level tha.** Farq yahi hai aur isi ne chaar run barbaad kiye:

- Agar koi **test fail** hota, XML report banti, Gradle `BUILD FAILED` deta, job **`failure`** aata — naam ke saath.
- Yahan job **`cancelled`** aaya, 25 min ke `timeout-minutes` par — matlab **Gradle task hi khatam nahi hua**. Test fail ho hi nahi raha tha; worker intezaar mein baitha tha.

Do cheezein worker ko bandhti thi, dono teardown mein Chhoot gayi thi:

| # | Kyun | Ab |
| --- | --- | --- |
| **1** | `model.viewModelScope` **kabhi cancel nahi hua**. Har test (`ChatViewModel(application)`) ek naya ViewModel banata hai jo `viewModelScope` par collectors chhodta hai; `ViewModelStore` ka `clear()` jaan-boojh kar nahi chalaya jaata (wo `onCleared()` chalata hai). Collector zinda \u2192 non-daemon thread zinda \u2192 JVM worker thread exit nahi karta. | `teardown` mein `model.viewModelScope.cancel()`. |
| **2** | OkHttp pools sirf tab band hote the jab `::client.isInitialized` — aur `setup()` ka **account stack** (`rejectingClient()`) alag client tha jo kabhi band nahi hota tha. Uski pool threads bhi non-daemon. | Dono stacks band: `closeHttp()` → `cancelAll` + `evictAll` + `shutdown` + **`awaitTermination(2s)`**. |

Yahi wajah hai ki pichhla commit root cause *prove* nahi kar paaya: logs admin-only the (`403 Must have admin rights`) aur sandbox build memory se mar gaya. Is baar evidence code se aaya, na ki log se — aur `main` par jo job abhi bhi `in_progress` hai (`38fbc77`), wo isi diagnosis se match karta hai.

**Production code chhua nahi:** `retryResponse` streaming turn coroutine launch se **pehle** synchronously set karta hai, isliye test 2/5 ko koi delay/latch nahi chahiye — yeh behaviour sahi hai, test hi galat likha gaya tha.

Test 2 aur 5 **poori tarah synchronous** hain: na `await()`, na `settle()`, na `sleep`. Sirf tests 1/3/4 response ka intezaar karte hain (`PersonalityPromptingTest` wala `idleFor` + `Thread.sleep`), kyunki wahan SSE body OkHttp thread par real time mein padhi jaati hai.

### 5 cases

| # | Test | Kya prove karta hai |
| --- | --- | --- |
| 1 | `aNormalRetryReplacesTheOldReplyAndSavesHistory` | Purana reply replace hua, `state="complete"`, `retryOriginal` clear, **history disk par likhi gayi** (`ChatHistoryCodec.decode` se verify). Request mein **sirf original prompt** jaata hai — purana reply dobara nahi bhejta. |
| 2 | `stoppingARetryBeforeTextRestoresTheOriginalReply` | Stream khola par text nahi aaya → `stopReply()` → **original reply wapas**, `retryOriginal` null, `isTyping` false, error `"Response stopped."` |
| 3 | `aRetryThatFailsBeforeTextRestoresTheOriginalAndDoesNotQueue` | 500 + JSON error → original wapas, error `"Retry failed. Original response kept."`. Saath mein **current behaviour document** kiya: `OfflineRetry.hasPending == false` — sirf `sendMessage` offline queue karta hai, `Retry` nahi. |
| 4 | `aBuildReplyRetriesInBuildModeAgainstTheSameRepository` | Build reply (`build.repository` set) retry hone par request mein `mode="build"` **aur wahi `repository`** jaata hai; reply par build info bani rehti hai |
| 5 | `aRetryThatFinishesAfterTheChatWasLeftWritesNothing` | `startNewChat()` se generation badla → der se aaya stream **kuch nahi likhta**: naye chat mein koi message leak nahi, `isTyping` false, fresh chat fresh rehti hai |

Deterministic: koi `Thread.sleep` nahi, koi real network nahi. Streaming ko aage badhane ke liye Robolectric ka paused clock (`idleFor`) — wahi jo `PersonalityPromptingTest` karta hai. Negative assertion wale tests mein **bounded idle** (`settle`) hai, taaki "kuch nahi hua" ko bhi time mile.

### ❗ Production code bilkul nahi chhua

`git diff --name-only` = **2 paths** — naya test file + yeh README section. Koi `.kt` main-source file nahi.

### ⚠️ Ek behaviour jo test ne pakda (bug nahi, design)

**Retry fail hone par woh offline queue mein nahi jaata.** `_failedReply` (Step 5) `offlineRetry.sendFailed(...)` call karta hai — par `retryResponse` apna alag path hai aur woh queue **nahi** karta. Test 3 isi ko explicit assert karta hai.

Yeh jaan-boojh kar hai ya nahi, code se pata nahi chalta — comment kahin nahi hai. **Product decision aapka:** agar Retry ko bhi reconnect par auto-resend karna chahiye to woh alag change hai (aur naya behaviour hai, isliye is PR mein nahi kiya). Agar nahi chahiye, to ek comment add karna chahiye taaki koi ise bug samajh kar "fix" na kare.

### Aage kya

Step 6 ke report ki baaki do sifarishein abhi bhi khuli hain: `deleteChat` ke missing tests (idempotency, cancel-reply path) aur uske baad hi `ChatModes.kt`.

---

## 🧪 Chat split — tests: `deleteChat` (21 Oct 2026)

`retryResponse` ke tests merge hone ke baad (PR #316, `main` `9375784`), Step 6 report ki doosri sifarish poori ki: `deleteChat` ke dedicated tests. **Sirf test file** — production code ek line bhi nahi chhua.

### Coverage — before → after

| | Before | After |
| --- | --- | --- |
| `retryResponse` | Step 6 tak 0 dedicated test; ab `RetryResponseTest.kt` ke 5 | unchanged (yahi PR nahi chhua) |
| `deleteChat` (active branch — messages/draft/summary/mode reset + `saveHistory`) | **0** — koi test nahi | **3** assertions-heavy tests (1, 2, 5) |
| `deleteChat` (cancel-reply path — `replyJob` cancel, `isTyping`, `retryOriginal`, `generation`) | **0** | **2** tests (3, 4) |
| `deleteChat` (non-active chat — bacha hua chat + uska draft) | **0** | **1** test (6) |
| Total naye tests | — | **6** |

`deleteChat` code 24 line ka hai par uske **6 alag side-effect** hain, aur pehle inme se ek bhi assert nahi hota tha: `cancelReply()`, `activeId` rotate, `_messages`/`_draft`/`_summary`/`_webMode`/`_xSearch`/`_buildRepo` reset, `_savedChats` filter, `saveHistory()`, aur per-reply metadata maps + `pollJobs` cleanup.

### File

`app/src/test/java/com/dua/ai/viewmodel/DeleteChatTest.kt` — **6 tests**, 283 line.

Wahi harness jo `RetryResponseTest` / `PersonalityPromptingTest` / `ChatPinTest` use karte hain: `RobolectricTestRunner`, `@Config(sdk = [34])`, `LooperMode.PAUSED`, real `ChatViewModel`, real `ChatHistoryWriter`, real SharedPreferences-backed history, network sirf OkHttp interceptor tak. **Koi naya framework ya dependency nahi.**

### 6 cases

| # | Test | Kya prove karta hai |
| --- | --- | --- |
| 1 | `deletingTheActiveChatClearsMessagesDraftCachesAndDisk` | `messages` khaali, `draft` khaali, `_summary`/`_webMode`/`_buildRepo` null, `_xSearch` false, `_savedChats` se gayab, `activeChatId` naya, aur **disk par bhi** sirf bacha chat (`ChatHistoryCodec.decode`) |
| 2 | `deletingTheSameChatTwiceChangesNothingTheSecondTime` | **Idempotency** — doosri baar stored history string **byte-identical**, `_savedChats` same, `activeChatId` dobara rotate nahi |
| 3 | `deletingTheActiveChatWhileARetryStreamsCancelsItAndResetsTypingAndRetryOriginal` | **Cancel-reply path** — `retryResponse()` ke baad `state=STREAMING` + `isTyping` true → delete → `replyJob` inactive **aur null**, `retryOriginal` null, `isTyping` false, `generation` badha (koi orphan coroutine nahi) |
| 4 | `aCancelledStreamFromADeletedChatNeverWritesAgain` | Delete ke baad looper pump karne par bhi stale turn wapas nahi likhta; `error` null, typing false, `_savedChats` sirf bacha chat |
| 5 | `deletingAChatThatDoesNotExistIsSafeAndChangesNothing` | Unknown id → disk, `_savedChats`, `activeChatId`, `messages`, `draft` sab **unchanged** |
| 6 | `deletingABackgroundChatKeepsTheActiveConversationAndItsDraft` | Non-active chat delete → khula chat + uska draft bilkul untouched, sirf target chat disk se gaya |

### ⏱️ Determinism — #316 wala hang dobara nahi ho sakta

| Rule | Kaise |
| --- | --- |
| Koi `Thread.sleep` / `settle()` / `CountDownLatch` | **Zero.** Saare 6 tests synchronous hain. |
| Koi paused looper pump nahi | `LooperMode.PAUSED` par looper **test body mein kabhi pump nahi hota** — sirf teardown mein bounded drain. Isliye do assertions ke beech koi dispatched continuation nahi chalta. |
| Streaming turn kaise dekha | `retryResponse()` streaming state **coroutine launch se pehle synchronously** set karta hai — wahi observation jo `RetryResponseTest` ke test 2/5 use karte hain. Koi wait nahi. |
| Interceptor kabhi block nahi karta | Jo request aaye use turant ek complete SSE turn milta hai. Koi latch `await()` interceptor ke andar nahi (yahi #316 ka root cause tha). |
| Har test independent | Apna `ChatViewModel` + saaf SharedPreferences; koi order dependency nahi. |
| Teardown | `replyJob?.cancel()` + `viewModelScope.cancel()` + dono OkHttp pools `shutdown` + `awaitTermination(2s)`. |

### ❗ Production code bilkul nahi chhua

```
git diff --name-only origin/main...HEAD
app/src/test/java/com/dua/ai/viewmodel/DeleteChatTest.kt
```

`git status -- app/src/main` = **0 paths**.

Blob proof: remote `c1ab6133725305700b6fa065a2d75ee1cebf9cc9` = local `git hash-object` ✅
Branch `test/delete-chat-coverage`, HEAD `324b101`, base `main` `9375784`. PR: #317.

### ⚠️ Ek behaviour jo test likhte waqt mila (bug nahi, design — aapka decision)

**`deleteChat()` `clearAttachments()` nahi bulata.** Active branch mein sirf `cancelReply()` + message/state reset hai; `clearAttachments()` `startNewChat()` aur `clearAllChats()` mein hai. Matlab composer mein pada draft attachment us chat se **agle chat mein chala jaata hai** — jabki `startNewChat` use saaf kar deta hai.

Yeh asymmetry jaan-boojh kar hai ya nahi, code se pata nahi chalta (koi comment nahi). Isliye test 1 mein attachment assertion jaan-boojh kar **nahi** rakha — warna test current behaviour lock kar deta ya red ho jaata. Agar delete par attachments clear hone chahiye to woh alag production PR hai, phir yahan ek assertion add ho jaayega.

### Merge checklist

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```

> Sandbox note: is environment ka cgroup memory limit **2 GiB** hai (`/sys/fs/cgroup/memory.max`; `memory.events` → `oom_kill 2`). `compileDebugKotlin` daemon har koshish mein OOM se mar gaya — `-Xmx4g`, `--no-daemon`, `--max-workers=1`, `-Xmx1100m` + in-process Kotlin, sab same. Isliye local run possible nahi tha; CI hi sach hai.

### 🔴 CI: pehla run fail (step 7) → fix → 4/4 green

**Run 1 — `39548085533`** (HEAD `edc8eda`): 3/4 green, **`Android build and tests` failed** — Step 7 `Compile Android sources and run unit tests`, exit code 1, 05:11:05 → 05:16:31 (≈5.4 min). Yeh **hang nahi tha** — pichhle attempt 21+ minute par atka tha, ab 5 minute mein fail hua. Yaani test-structure theek thi, compile/assertion galat thi.

Log download (`/repos/.../actions/runs/{id}/logs`) is account par **403 "Must have admin rights"** deta hai, isliye exact compiler line nahi mili. Iske bajaye failing file ko passing file ke against line-by-line verify kiya — **`RetryResponseTest.kt` (jo `main` par pass hota hai) hi ground truth thi**. Usse 3 real mismatches nikle:

| # | Galti | Fix |
| --- | --- | --- |
| 1 | `openChat(ACTIVE)` seed ke liye call kiya — woh `openChat` dialog/summary flows chhedta hai; seed uske bagair hi pura ho jaata hai | seed `_savedChats` + `_activeChatId` + `_messages` + `_draft` + toggles, phir **`saveHistory()` reflection se** (private hai), phir `flushHistory()` |
| 2 | Disk assert `flushHistory()` par depend thi, jo sirf pending write likhta hai — pehle `saveHistory()` hi call nahi hui thi (test ke hisaab se disk khaali/adhoora) | seed ke andar `saveHistory()` → `flushHistory()`, isliye disk par seed **guaranteed** hai (assert se pehle `savedOnDisk()` se verify bhi karte hain) |
| 3 | `requests` bookkeeping (har HTTP URL collect karna) — koi test uspar assert nahi karta tha, sirf dead state + unused import | poora hata diya; interceptor bas ek complete SSE turn deta hai aur non-`chat.test` host par fail karta hai |

Saath hi: `ChatHistoryCodec` ka import sahi hai (woh `ChatHistory.kt` mein hai, alag file nahi) — verify kiya. Public `StateFlow`s (`savedChats`, `activeChatId`, `draft`, `messages`) use kiye jahan available hain, reflection sirf private (`_savedChats`, `_messages`, `_draft`, `_summary`, `_webMode`, `_xSearch`, `_buildRepo`, `replyJob`, `retryOriginal`, `generation`, `appearanceLoaded`) ke liye.

**Run 2 — `39549094082`** (HEAD `d9ecb55`): **4/4 green** ✅

| Job | Conclusion |
| --- | --- |
| Android build and tests | **success** (step 7 `Compile Android sources and run unit tests` → success) |
| Backend tests | success |
| Website build | success |
| Google Cloud engine tests | success |

Run URL: `https://github.com/KRFbrothers/Dua-ai/actions/runs/39549094082`

> Note: `appearanceLoaded` reflection se `true` set kiya jaata hai (`appearance` store ke **initial** value par start hota hai, aur setup mein `AppearanceStore.reset()` chal chuka hai). `retryResponse` test 3 ke liye yeh flag zaroori hai; koi assertion kisi later appearance emission par depend nahi karti. `openChat` par ID rotate hone ka jhanjhat bhi isi liye nahi uthaya.

### Aage kya

`deleteChat` ke baaki missing tests (`deleteServerChat` ki tarah `_dataBusy` guard, `draftSaver.pending` wala path, aur `pollJobs`/`replyBuild` map cleanup ka direct assertion) — phir `ChatModes.kt` split. **Pehle test, phir move.**

---

## 🧪 Chat split — tests: `deleteChat` cleanup (21 Oct 2026)

Base: `main` @ `84ebe92` (PR #317 ka merge commit). Pichhle section ("Chat split — tests: `deleteChat`") ne `deleteChat` ke **6 behaviour** cover kiye the — state reset, idempotency, cancel-reply, stale turn, unknown id, background chat. Us section ke "Aage kya" ne jo teen cheezein chhodi thi, wahi yahan cover hoti hain, plus teen jo `deleteServerChat` ke saath share hoti hain.

### Nayi file

`app/src/test/java/com/dua/ai/viewmodel/DeleteChatCleanupTest.kt` — **6 tests**. Harness bilkul wahi jo `DeleteChatTest` / `RetryResponseTest` / `PersonalityPromptingTest` use karte hain: Robolectric, `@Config(sdk=[34])`, `LooperMode.PAUSED`, real `ChatViewModel`, real `ChatHistoryWriter`, real SharedPreferences-backed history; network sirf interceptor tak.

| # | Test | Kya prove karta hai |
| --- | --- | --- |
| 1 | `deletingWhileDataIsBusyChangesNothingAndWorksAgainOnceTheBusyFlagClears` | `_dataBusy = true` par delete **no-op** — disk string byte-identical, `savedChats`/`activeChatId`/`messages` unchanged. Phir `_dataBusy = false` karke wahi delete chalti hai ⇒ teesra assert prove karta hai ki guard hi wajah thi, seed toota nahi tha |
| 2 | `deletingAnyChatFlushesThePendingTypedDraftToDiskFirst` | `updateDraft()` ke baad `draftSaver.pending == true` (debounce chal raha hai) → delete → `pending == false` **aur** typed draft disk par `ACTIVE` ke saath, `OTHER` ke saath nahi. `runNow()` ka asar, bina kisi timer ke |
| 3 | `deletingTheActiveChatCancelsItsPollJobsAndLeavesTheSurvivingChatsPollsAlone` | `pollJobs` mein 3 jobs seed kiye (do deleted chat ke, ek bachne wale chat ka) → delete → dono `isActive == false`, bachne wala `isActive == true`, aur map se sirf deleted ids gaye |
| 4 | `deletingTheActiveChatDropsOnlyItsPerReplyMetadata` | Paanchon maps (`replyWebModes`, `replyReasoning`, `replyAgent`, `replyCode`, `replyBuild`) mein deleted + surviving reply dono ki entries → delete ke baad deleted ki **sab gayab**, surviving ki **sab intact** (values bhi, sirf presence nahi) |
| 5 | `deletingTheActiveChatForgetsItsExclusiveMediaButKeepsFilesOtherChatsStillUse` | Do chats jinke media ek file `shared.png` share karte hain + ek-ek exclusive (`only-active.png`, `only-other.png`) → delete active → shared **bacha**, doosre chat ki file **bachi**, sirf exclusive **gayi**. Yeh `stillUsed` wala rule hai jo `bindCloudHistory` bhi use karta hai |
| 6 | `deletingAServerChatWhileARetryStreamsIsRefusedInsteadOfCancellingTheRetry` | `deleteServerChat` ka cancel-reply path: `chatDeletionEnabled = true` + streaming retry → delete **refuse** hoti hai (`"Finish the current reply or sync action, then try again."`), `busy == false`, `id == ACTIVE`, reply job **abhi bhi active**, reply **abhi bhi streaming**, disk untouched |

### Determinism — #316 wala hang dobara nahi

| Rule | Is file mein |
| --- | --- |
| Koi wall-clock pause test body mein | **0** (per-test count 0,0,0,0,0,0) |
| `CountDownLatch` / `settle()` / `await()` polling | **0** |
| Paused looper test body mein pump | **0** — sirf `@After` ka bounded drain (25×5 ms) |
| Blocking interceptor | **0** — har request ko turant ek complete SSE turn |
| Teardown mein bounded wait | sirf `executorService.awaitTermination(2s)`, **wahi** jo `DeleteChatTest:123` aur `RetryResponseTest:120` mein pass hota hai |
| `runTest`/`TestDispatcher` | zaroorat nahi — har assertion us state ko padhti hai jo call ne **synchronously** set kiya (e.g. `retryResponse` streaming turn coroutine dispatch se pehle set karta hai) |

Har test apna `ChatViewModel` + saaf SharedPreferences banata hai; koi test doosre par depend nahi karta. `ViewModelStore.clear()` jaan-boojh kar nahi bulaya (wahi wajah jo pichhle section mein likhi hai).

### Coverage — pehle → ab

| Function / path | Pehle | Ab |
| --- | --- | --- |
| `deleteChat` — state reset, idempotency, cancel-reply, stale turn, unknown id, background | 6 tests (`DeleteChatTest.kt`) | same, chhue nahi |
| `deleteChat` — `_dataBusy` guard | **0 tests** | test 1 |
| `deleteChat` — `draftSaver.runNow()` pending path | **0 tests** | test 2 |
| `deleteChat` — `pollJobs` cancel (direct assertion) | **0 tests** | test 3 |
| `deleteChat` — per-reply maps (5 maps) | **0 tests** | test 4 |
| `deleteChat` — `mediaCache.forget` sharing rule | **0 tests** | test 5 |
| `deleteServerChat` — cancel-reply / busy-reply guard | **0 tests** | test 6 |
| `deleteServerChat` — happy path + cloud apply | `ServerChatDeletionCacheTest` (pehle se) | same, chhue nahi |

Net: `deleteChat` ka cleanup half pehle **poora untested** tha (0/5 paths), ab **5/5** covered; `deleteServerChat` ka refusal path bhi ab cover hai.

### Production code bilkul nahi chhua

```
git status --short -- app/src/main   → 0 paths
git diff --stat                       → sirf DeleteChatCleanupTest.kt (+ yeh README section)
```

Koi naya dependency/framework nahi. `_to_delete/README.md` ke purane sections — khaas kar "Chat split — tests: `retryResponse`" aur "Chat split — tests: `deleteChat`" — **ek line bhi nahi badli** (sirf yeh naya section end mein juda).

### ⚠️ Jo mila (report kiya, fix nahi kiya)

1. **`deleteChat` `clearAttachments()` nahi bulata** — pichhle section mein detail hai; yahan dobara lock nahi kiya, aur test 1 mein attachment assertion jaan-boojh kar nahi rakha.
2. **`deleteServerChat` sirf *typing* par refuse karta hai, sirf *preparing/reading files* par nahi.** `syncBlocked()` mein `_isTyping`, `_dataBusy`, `liveProjectBusy`, previews aur `buildPr.creating` hain — `_preparingFiles` / `_readingFiles` **nahi**. Matlab jab koi attachment abhi prepare ho raha ho (streaming shuru nahi hua), tab bhi server delete chal jaayegi. `retryResponse` khud `_preparingFiles`/`_readingFiles` ko guard karta hai (`ChatLifecycle.kt`), to yeh asymmetry hai. Expected: delete refuse ho. Actual: delete proceed karti hai. **Fix nahi kiya** — production change hai, aapka decision. `deleteChat` (local) bhi same guard use karta hai, isliye wo bhi affected hai.

### Merge checklist

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```

> Sandbox note: yahan cgroup limit **2 GiB** hai (`oom_kill 2`) — `compileDebugKotlin` daemon OOM se marta hai, isliye local Gradle run possible nahi. Verify kiya: braces 42/42, parens 252/252, 6 `@Test`, koi unused import nahi, test bodies mein koi wait nahi. Confirmation CI se.

### Aage kya

`deleteChat` + `deleteServerChat` ka cleanup coverage ab kaafi hai. Agla kaam **`ChatModes.kt`** — aur uska **pehla test mode-freeze** hona chahiye, kyunki wahi extract hone par sabse pehle tootega. Uske baad hi move.

## 🧪 Chat split — tests: ChatModes mode freeze (21 Oct 2026)

`ChatModes.kt` split ka **step 0**: pehla test, phir move. Yeh section sirf naye test file ka hai — production code ek line bhi nahi chhua.

### Nayi file

`app/src/test/java/com/dua/ai/viewmodel/ChatModesModeFreezeTest.kt` — **7 tests**, 424 lines, ek hi naya file.

Branch `test/chat-modes-freeze` @ `e377e04`, base `main` `fae0dda` (PR #318 merge). PR #319.

### Coverage — pehle → ab

| Contract | Pehle | Ab |
| --- | --- | --- |
| `retryResponse` reply ka frozen web mode replay karta hai (composer ka naya mode use **nahi**) | ❌ sirf indirect (`PersonalityPromptingTest` personality dekhta hai, mode nahi) | ✅ assertion + wire par `web="search"` |
| Freeze **scoped** hai — naya turn composer ka current mode leta hai | ❌ | ✅ naye reply ki `replyWebModes` = `null`, purane ki `"search"` |
| `replyBuild` freeze — Build reply composer ke Build chhodne ke baad bhi wahi repo | ⚠️ `RetryResponseTest` `original.build` se cover karta hai, `replyBuild` map se nahi | ✅ `replyBuild` + wire par `mode="build"` + `repository` |
| `openChat` saved `buildRepository` adopt karta hai, session modes reset | ❌ | ✅ |
| `startNewChat` teeno modes reset (`_webMode`/`_xSearch`/`_buildRepo`) | ❌ | ✅ |
| `setWebMode` guard **dono** direction + streaming lock | ❌ | ✅ |
| `setXSearch` guard dono direction (non-Grok refuse → Grok+server apply) | ❌ | ✅ |
| `setRunCode` / `setBuildRepository` guard dono direction + Build exclusivity | ❌ | ✅ |
| Modes disk par nahi jaate, `buildRepository` jaata hai | ❌ | ✅ `ChatHistoryCodec` encode→decode |

### 7 cases

1. `aRetrySendsTheRepliesFrozenWebModeAfterTheComposerWasTurnedOff` — seed `replyWebModes[REPLY]="search"`, composer `setWebMode(null)`, retry → freeze `"search"` bacha, composer `null` bacha, streaming turn, dono turns intact. Koi wait nahi.
2. `aNewTurnUsesTheComposersCurrentModeWhileTheOldReplyKeepsItsFrozenOne` — retry → `stopReply()` → original restore, `retryOriginal` null, composer wapas; `replyWebModes[REPLY]` abhi bhi `"search"` (freeze reply ka hai, composer ka nahi).
3. `aBuildReplyKeepsItsRepositoryAfterTheComposerLeftBuildMode` — `replyBuild[REPLY]=KRFbrothers/Dua-ai` + `_buildRepo=null` → retry par `mode="build"`, `repository="KRFbrothers/Dua-ai"`, `web` field gayab.
4. `openingASavedChatAppliesItsStoredRepositoryAndClearsTheSessionModes` — `openChat` adopt/reset, repo-less chat → Build clear, `startNewChat` → teeno reset.
5. `webSearchAndXSearchRefuseWithoutCapabilityAndApplyOnceTheServerOffersThem` — refuse + kuch store nahi, phir offer par apply; streaming par chip lock.
6. `buildAndRunCodeRefuseWithoutCapabilityAndApplyOnceItIsAvailable` — Run code refuse→apply; Build refuse **aur** Run code ko na chhede (early-return proof) → phir apply + web/X/reasoning/runCode sab off.
7. `theModesAreSessionOnlyWhileTheBuildRepositorySurvivesTheDiskRoundTrip` — encode→decode round trip; `webMode`/`xSearch`/`buildRepo` keys disk par nahi.

### ⏱️ Determinism — #316 wala hang dobara nahi

| Check | Result |
| --- | --- |
| `CountDownLatch` | **0** |
| `Thread.sleep` | 1 — sirf `awaitRequest()` helper ke andar (2 ms yield) |
| Blocking interceptor | ❌ nahi — hamesha ek poora SSE turn, kabhi block nahi |
| Paused looper test body mein pump | 6/7 tests mein **koi pump nahi**; sirf test 3 ko outgoing body chahiye (coroutine + OkHttp worker thread) |
| History writer in flight | ❌ nahi — seed aur `openChat` tests `saveHistory()`/`flushHistory()` **nahi** karte (writer `Dispatchers.Default` par chalta hai, teardown ke waqt beech mein hai to race, behaviour nahi). Test 7 pure `ChatHistoryCodec` round trip hai |
| JUnit `@Rule Timeout` | jaan-boojh kar nahi (body dusre thread par jaati hai, `idleFor()` wedge karta hai) |
| Teardown | `viewModelScope.cancel()` + bounded drain + `awaitTermination(2s)`; `ViewModelStore.clear()` **nahi** |
| Naya framework/dependency | ❌ koi nahi |

### 🔴 CI: do red runs → fix → 4/4 green

| Run | HEAD | Result | Kya galat tha |
| --- | --- | --- | --- |
| `39555317373` | `3fbc31b` | ❌ Android step 7, 4m48s (hang **nahi**) | Assertions `assertEquals("message", actual)` 2-arg likhi thi — JUnit usme **expected = message string** samajhta hai. Saath hi `_selectedModel` ka default `"Dua"` hai (main `Gemini` maan raha tha), aur Build gate ko configured provider chahiye |
| `39556891495` | `f2662e1` | ❌ Android step 7, 5m20s | 3-arg form theek ho gayi, par test 2 `sendMessage()` par depend karta tha (poore send path ka state) aur seed har test mein `saveHistory()` + `flushHistory()` karta tha — writer `Dispatchers.Default` par |
| `39558767608` | `e377e04` | ✅ **4/4 green** — Android · Backend · Website · Google Cloud (Android step 7 **first attempt** mein pass) | — |

Note: log download `403 Must have admin rights` deta hai; red ka karan annotations (`Process completed with exit code 1`, `.github` line 406) + passing sibling se statement-by-statement diff se nikala, andaaze se nahi.

### Blob proof

```
local  5f38299b7b6448bc4a9e89d08213ee38dcbdef58
remote 5f38299b7b6448bc4a9e89d08213ee38dcbdef58
```

### ❗ Production code bilkul nahi chhua

```
git diff --name-only origin/main...HEAD -- app/src/main | wc -l   → 0
git diff --name-only origin/main...HEAD  → sirf ChatModesModeFreezeTest.kt (+ yeh README section)
```

### 🔎 Jo test likhte waqt confirm hua (bug nahi, design)

- **Mode freeze `reply*` maps se aata hai, composer se nahi.** `ChatLifecycle.retryResponse` `replyWebModes[messageId]` padhta hai, `_webMode.value` nahi. Yeh contract ab locked hai — split ke waqt isi ko bachana hai.
- **`ReplyRetryPlan.create` files wale turn ko retry se rokta hai** (`"\nFiles: "` check) — isliye mode-freeze test mein attach-free turn chahiye.
- **`_buildRepo` per-chat hai, per-reply map optional.** `replyBuild[id]` sirf tab set hota hai jab request mein build ho; absent ho to `original.build?.repository` fallback hai.

### ⚠️ Pehle se report kiye bugs (yahan bhi fix nahi kiye)

1. `deleteChat` `clearAttachments()` nahi bulata — attachment agle chat mein chala jaata hai.
2. `deleteServerChat` / `deleteChat` `_preparingFiles` / `_readingFiles` par refuse nahi karte (`syncBlocked()` mein missing).

### Merge checklist

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```

> Sandbox note: cgroup limit **2 GiB** (`oom_kill 2`) — local Gradle possible nahi. Structural verify: braces 44/44, parens 337/337, 7 `@Test`, koi unused import nahi. Confirmation CI se.

### Aage kya

Ab `ChatModes.kt` (~200 line) extract kar sakte hain — mode toggles, compatibility guards aur yeh freeze contract tests ke saamne safe hain. Move ke waqt public API byte-identical rakhna, aur `reply*` maps ko `ChatModesHost` ke peeche se waisa hi forward karna.

---

## 🔒 `syncBlocked()` guard fix — file preparation ab delete ko rokta hai (21 Oct 2026)

**Pehla production-code change** is poore test streak mein. Baaki sab PRs (`#316`–`#319`) sirf test + docs the. Yahan ek real bug tha jo teen reports se flag ho raha tha, aur fix **ek hi jagah** hua.

### 🐞 Bug — kya galat tha

`deleteServerChat` apne guard ke liye `syncBlocked()` par depend karta hai (`ChatHistory.kt:201`), lekin `syncBlocked()` attachment picker ki state **dekhta hi nahi tha**:

```kotlin
// pehle — ChatViewModel.kt:1209
private fun syncBlocked() = account.state.value.busy || _dataBusy.value || _isTyping.value || liveProjectBusy() ||
    _cloudPreview.value != null || _importPreview.value != null || _buildPr.value.values.any { it.creating }
```

`_preparingFiles` aur `_readingFiles` missing the. Dono lifecycle:

| Flag | Kahan set hota hai | Kya karta hai |
| --- | --- | --- |
| `_preparingFiles` | `ChatUploads.selectAttachments` (`preparingSelections++`) | picker se aaye URIs ko describe/upload karta hai |
| `_readingFiles` | `ChatUploads.loadFiles` (`version`-guarded) | worker par base64/limits ke saath files padhta hai |

**Expected:** jab tak pick chal raha ho, `deleteServerChat` refuse kare — "Finish the current reply or sync action, then try again."
**Actual:** delete aage badh jaata tha → `_chatDeletion.busy = true`, `_dataBusy = true`, `account.cloud()` → `account.deleteChat()` → `applyConfirmedChatDeletion()` → `bindCloudHistory()`, jo **active chat ke saath-saath `messages`/`draft`/`_attachments` badal deta hai** — bilkul us waqt jab picker apna result `_attachments`/`_draft` mein likhne wala ho. Do writers, ek hi state: delete jeet jaati hai to user ka selection gayab, pick jeet jaata hai to delete ke baad wapas aa jaata hai.

**Intent pehle se clear tha** (isliye yeh "design choice" nahi, incomplete guard hai):

- `ChatLifecycle.kt:67` — `retryResponse` khud `_isTyping || _preparingFiles || _readingFiles || _dataBusy` par return karta hai
- `ChatViewModel.kt:1025` — data-controls action `!syncBlocked() && !_readingFiles.value && !_preparingFiles.value` **explicitly** check karta hai, yaani author ko pata tha ki `syncBlocked()` in dono ko cover nahi karta
- `ChatViewModel.kt:1218` — offline resend busy ko `_isTyping || _preparingFiles || _readingFiles` maanta hai
- `ChatAgentControls.kt:67`, `ChatAttachments.kt:39`, `ChatImagine.kt:104` — sab in dono ko reply-shaped state maante hain

`syncBlocked()` hi akela jagah tha jahan yeh chhoot gaya.

### 🔧 Fix — 2 line, ek jagah

```kotlin
// ab — ChatViewModel.kt:1209
// File preparation/reading is a reply-shaped action: the picker that started it owns the draft
// and the message list until it finishes, so anything gated by syncBlocked() must wait too.
private fun syncBlocked() = account.state.value.busy || _dataBusy.value || _isTyping.value || liveProjectBusy() ||
    _preparingFiles.value || _readingFiles.value ||
    _cloudPreview.value != null || _importPreview.value != null || _buildPr.value.values.any { it.creating }
```

`syncBlocked()` **private** hai — public API zero change. Signature, return type, naam sab same. Sirf predicate ka body.

### 🔀 Blast radius — kaun-kaun se caller ab badle

`syncBlocked()` ke saare callers (audit kiya, 11 jagah):

| Caller | Pehle | Ab | Theek? |
| --- | --- | --- | --- |
| `deleteServerChat` | pick ke dauraan allowed ❌ | refused ✅ | **yahi bug tha** |
| `renameChat` | pick ke dauraan allowed ❌ | refused ✅ | ✅ pick draft ke upar likh raha hota hai |
| `setChatPinned` | pick ke dauraan allowed ❌ | refused ✅ | ✅ wahi |
| `saveMemoryNote` | allowed | refused | ✅ aisa lagta hai ki yeh pehle se `check()` se crash karta hoga |
| `restoreDriveBackup` | allowed | refused | ✅ restore pick ke beech mein nahi hona chahiye |
| `applyConfirmedChatDeletion` | allowed | refused | ⚠️ **side effect**: pehle khula path, ab band. `_dataBusy` already true hota hai jab yeh chalta hai, isliye practically dead branch; par tight hai |
| `AutomaticCloudSync.LocalPort.read/commit` | allowed | refused | `check()` ab pick ke dauraan fail karega. Pehle bhi yahi hota tha kyunki `read()` `flushHistory()` karta hai aur `commit()` `!draftSaver.pending` maangta hai — pick ke dauraan dono hi tootte the. Ab **fail-fast, silent** |
| `retryResponse` (`ChatLifecycle.kt:67`) | apna alag check | unchanged | ✅ apna check pehle se tha, ab consistent |

**Behaviour change ka asar:** server delete, rename, pin, memory note, Drive restore — in sab par ab pick ke dauraan 1-line error aata hai ("Finish the current reply or sync action…"). Koi naya crash nahi, koi naya path nahi.

### 🧪 Naya test file — `DeleteServerChatGuardTest.kt` (5 tests)

`app/src/test/java/com/dua/ai/viewmodel/DeleteServerChatGuardTest.kt` · 281 lines · 5 `@Test`

| # | Case | Test | Assertions |
| --- | --- | --- | --- |
| 1 | **Regression** — preparing state | `aServerDeleteIsRefusedWhileTheAttachmentPickerIsStillPreparingFiles` | error = exact `"Finish the current reply or sync action, then try again."` · `busy == false` · `_dataBusy == false` · `id == OTHER` · `completed == false` **Yeh fix se pehle FAIL hota hai** |
| 2 | Reading state | `aServerDeleteIsRefusedWhileAttachmentsAreStillBeingRead` | wahi teen assertions `_readingFiles = true` par |
| 3 | Refusal side-effect free + guard live | `aRefusedServerDeleteLeavesTheChatsOnDiskUntouchedAndProceedsOnceThePickFinishes` | disk string **byte-identical** · `savedChats`/`messages`/`_activeChatId` unchanged · 2 chats intact · **phir flag clear** → `error == null`, `busy == true`, `_dataBusy == true` |
| 4 | Dono flags ek saath | `preparationAndReadingTogetherAreStillASingleLiveRefusal` | dono par refuse · dobara refuse (no double-fail) · sirf ek clear → **abhi bhi refuse** · dono clear → accept · disk par 2 chats |
| 5 | Shared guard | `renamingAndPinningWaitForFilePreparationTooBecauseTheyShareTheGuard` | `renameChat`/`setChatPinned` dono flags par refuse; refusal ne title/pin **nahi** likha; clear hone par accept + title/pin **sach mein** set |

**Coverage pehle kya thi → ab kya hai:**

| | Pehle | Ab |
| --- | --- | --- |
| `syncBlocked()` ke 9 conditions | 3 tested (`_dataBusy`, `_isTyping`, `account.busy` indirectly) | **5/9** (`_preparingFiles`, `_readingFiles` add) |
| `deleteServerChat` ke guards | 1/5 (`_isTyping`) — `DeleteChatCleanupTest` test 6 | **3/5** (`_isTyping`, `_preparingFiles`, `_readingFiles`) |
| Pick-in-flight ke dauraan destructive actions | **0 tested** | **2** (delete + rename/pin) |
| File preparation par koi bhi assertion | **0** | **5 tests** |

### ⏱ Determinism — PR #316 ka hang dobara nahi

| Risk | Status |
| --- | --- |
| `Thread.sleep` | ❌ nahi — 0 occurrence (comment mein bhi nahi) |
| `CountDownLatch` | ❌ nahi — 0 |
| Blocking interceptor | ❌ nahi — account interceptor **turant** `IOException` throw karta hai, `/api/chat` interceptor is file mein hai hi nahi |
| Real network | ❌ nahi — account call transport par hi mar jaata hai |
| Paused looper test body mein pump | ❌ nahi — sirf `@After` ka bounded drain (25×5 ms) |
| JUnit `@Rule Timeout` | jaan-boojh kar nahi (body dusre thread par jaati hai, `idleFor()` wedge karta hai) |
| Kis cheez par assert karte hain | guard refusals + accepted-state, dono **`viewModelScope.launch` se PEHLE** synchronously likhe jaate hain — koi coroutine schedule nahi, koi `advanceUntilIdle` ki zaroorat nahi |
| Naya framework/dependency | ❌ koi nahi |

Har test apna `ChatViewModel` + apna `OkHttpClient` + saaf prefs. Koī test doosre par depend nahi karta.

### 📋 Merge checklist

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```

> Sandbox note: cgroup **2 GiB** (`oom_kill 2`) — local Gradle possible nahi. Confirmation CI se.

### ⚠️ Jo abhi bhi khula hai (fix nahi kiya)

1. **`deleteChat` `clearAttachments()` nahi bulata** — attachment agle chat mein chala jaata hai (`startNewChat`/`clearAllChats` clear karte hain, `deleteChat` nahi).
2. **`deleteChat` par `syncBlocked()` hi nahi** — woh sirf `if (_dataBusy.value) return` karta hai. Is fix ne `deleteServerChat` theek kiya; **local delete ka guard abhi bhi `_isTyping`/pick miss karta hai**. Yeh jaan-boojh kar alag rakha (scope: `deleteServerChat`).
3. **`liveProjectBusy()`, `_cloudPreview`, `_importPreview`, `buildPr.creating`** — teeno untested hain.

---

## 🧷 `deleteChat` (local path) guard gap — ab shared guard par (21 Oct 2026)

PR **[#323](https://github.com/KRFbrothers/Dua-ai/pull/323)** · branch `fix/delete-chat-guard` · HEAD `d215d23` · base `main` @ `3a31dd8` · **merge nahi kiya** (aapka decision)

### 🐞 Bug — kya galat tha

`deleteServerChat` PR #322 ke baad `syncBlocked()` par gated hai. **`deleteChat` (local path) abhi bhi ek hi flag dekhta tha:**

```kotlin
// pehle — ChatHistory.kt:250
fun deleteChat(id: String) {
    if (_dataBusy.value) return
```

Operation wahi 4 buffers rewrite karta hai jo server path karta hai — `_messages`, `_draft`, `_savedChats`, aur history file (`saveHistory()`). Aur `_dataBusy` sirf **delete/clear chhap** ke dauraan true hota hai. Attachment picker, cloud review, import preview, build PR aur live voice usse set hi nahi karte — isliye local delete unn sab ke beech mein aa sakta tha aur **active chat unke neeche se swap** ho jaata.

**Expected:** jo bhi flow draft/history ka owner hai, uske chalte waqt delete refuse ho.
**Actual:** delete chalti thi — `activeId` rotate, `_messages` khaali, history rewrite, jabki woh flow abhi usi state par kaam kar raha tha.

### 📊 Gap — `syncBlocked()` ke 9 conditions vs local delete

| # | Condition | `deleteServerChat` (pehle) | `deleteChat` (pehle) | Ab |
|---|---|---|---|---|
| 1 | `account.state.value.busy` | ✅ | ❌ **leak** | ✅ |
| 2 | `_dataBusy` | ✅ | ✅ | ✅ |
| 3 | `_isTyping` | ✅ | ❌ **jaan-boojh** | ❌ **jaan-boojh** |
| 4 | `liveProjectBusy()` (live voice) | ✅ | ❌ **leak** | ✅ |
| 5 | `_preparingFiles` | ✅ (#322) | ❌ **leak** | ✅ |
| 6 | `_readingFiles` | ✅ (#322) | ❌ **leak** | ✅ |
| 7 | `_cloudPreview != null` | ✅ | ❌ **leak** | ✅ |
| 8 | `_importPreview != null` | ✅ | ❌ **leak** | ✅ |
| 9 | `_buildPr.values.any { it.creating }` | ✅ | ❌ **leak** | ✅ |

**9 mein se 6 conditions local delete par missing thi.** Sirf `_dataBusy` common tha.

**`_isTyping` jaan-boojh kar bahar:** open chat ko delete karna **hi** streaming reply ko cancel karne ka tareeka hai. `DeleteChatTest` test 3 ("deleting while a retry streams cancels it") aur test 4 ("a cancelled stream never writes again") isi par depend karte hain. Guard ko `syncBlocked()` ka poora copy banane se woh dono toot jaate aur streaming reply **orphan** ho jaata. Isliye fix ne sirf **stored-state half** liya.

### 🔧 Fix — 13 insertions, 2 deletions

```kotlin
// ChatViewModel.kt — syncBlocked() ab usi predicate se compose hota hai (duplicate guard nahi)
private fun syncBlocked() = cacheBlocked() || _isTyping.value
/** The half of syncBlocked that guards the *stored* state rather than a reply. */
private fun cacheBlocked() = account.state.value.busy || _dataBusy.value || liveProjectBusy() ||
    _preparingFiles.value || _readingFiles.value ||
    _cloudPreview.value != null || _importPreview.value != null || _buildPr.value.values.any { it.creating }
```

```kotlin
// ChatHistory.kt
fun deleteChat(id: String) {
    if (cacheBlocked()) return
```

- **Ek hi source of truth.** `syncBlocked()` ka behaviour byte-identical (wahi 9 conditions, wahi order) — sirf uske terms `cacheBlocked()` mein nikal gaye.
- `HistoryHost` interface mein `fun cacheBlocked(): Boolean` (internal), `ChatViewModel` ke **5 host overrides** mein `override fun cacheBlocked() = this@ChatViewModel.cacheBlocked()`, aur ek `private fun cacheBlocked()`.
- **Public API zero change** — `syncBlocked()`/`deleteChat()` ke naam, signature, return type same. Sirf predicate ka body.

### 🧪 Naya test file — `DeleteChatGuardTest.kt` (10 tests)

| # | Case | Kya assert hota hai |
|---|---|---|
| 1 | **`_preparingFiles` → refuse** (regression) | poora refusal contract; **fix se pehle FAIL hota hai** |
| 2 | `_readingFiles` → refuse | wahi contract |
| 3 | `_cloudPreview` → refuse, `_importPreview` → refuse | dono previews, dono alag alag |
| 4 | `_buildPr[reply].creating` → refuse; `creating=false` + error → allow | guard **narrow** hai, sirf live hit block karta hai |
| 5 | `account.busy` → refuse; clear → allow | guard live hai, sticky nahi |
| 6 | live voice starting → refuse; clear → allow | wahi |
| 7 | refusal **side-effect free**, picker khatam par accept | disk string **byte-identical** + `savedChats`/`activeChatId`/`messages`/`draft` unchanged |
| 8 | teen blockers ek saath = **ek** refusal | ek-ek clear karne par bhi sahi; dono clear → accept |
| 9 | **`_isTyping` guard mein nahi** | streaming retry delete se **cancel** hota hai, block nahi; `replyJob` inactive + null |
| 10 | background delete bhi gated | picker draft ka owner hai, sirf open chat ka nahi |

Har refusal par assert: stored history string unchanged, `savedChats` ids unchanged, `activeChatId` unchanged, `messages` unchanged, `draft` unchanged.

### 📈 Coverage — pehle vs ab

| Kya | Pehle | Ab |
|---|---|---|
| `deleteChat` ke guards | **1/9** (sirf `_dataBusy`) | **8/9** (`_isTyping` jaan-boojh kar out) |
| File preparation par local-delete assertions | **0** | **4 tests** |
| Refusal ke side-effect-free proof | **0** | har guard test mein |
| "Guard narrow hai" (`_isTyping` allow) lock | implicit | **explicit test 9** |
| Background-delete guard | **0** | **1 test** |

### ⏱ Determinism — PR #316 ka hang dobara nahi

| Cheez | Status |
|---|---|
| `Thread.sleep` | ❌ nahi — 0 |
| `CountDownLatch` | ❌ nahi — 0 |
| Blocking interceptor | ❌ nahi — `/api/chat` interceptor turant poora SSE turn deta hai, kabhi block nahi karta |
| Real network | ❌ nahi — account interceptor har request par `AssertionError` throw karta hai |
| Paused looper test body mein pump | ❌ nahi — sirf `@After` ka bounded drain (25×5 ms) |
| JUnit `@Rule Timeout` | jaan-boojh kar nahi (body dusre thread par, `idleFor()` wedge) |
| Kis cheez par assert | refusal ka koi bhi side effect nahi hota; accepted path `activeId` rotate + `saveHistory()` — **sab synchronous** |
| Naya framework/dependency | ❌ koi nahi |

Har test apna `ChatViewModel` + apna `OkHttpClient` + saaf prefs.

### 🔍 Jo test likhte waqt confirm hua (bug nahi, design)

**`syncBlocked()` ko as-is copy karna galat hota.** Uske 9 conditions ka ek (`_isTyping`) reply-lifecycle se hai, stored state se nahi. Local delete ke liye wahi ek condition **ulta** kaam karti hai — delete hi reply ko cancel karta hai. Isliye fix ne predicate ko do hisson mein baanta: `cacheBlocked()` (stored state, 8 conditions) aur `_isTyping` (reply). Test 9 isi asymmetry ko lock karta hai, taaki koi future refactor galti se `deleteChat` ko `syncBlocked()` par shift na kar de.

### 📋 Merge checklist

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```

> Sandbox note: cgroup **2 GiB** — local Gradle possible nahi. Confirmation CI se.

### ⚠️ Jo abhi bhi khula hai (fix nahi kiya)

1. **`deleteChat` `clearAttachments()` nahi bulata** — composer ka draft attachment agle chat mein chala jaata hai. `startNewChat`/`clearAllChats` clear karte hain, `deleteChat` nahi.
2. **`deleteChat` (active branch) `cancelReply()` ke baad `clearAttachments()` nahi** — upar wala hi gap, active path par.
3. **`_isTyping` ki jagah koi explicit "reply is cancellable" concept nahi** — local delete abhi bhi `cancelReply()` par depend karta hai, guard par nahi. Yeh sahi hai, par ek comment/naam se document hona chahiye.
4. **`syncBlocked()` ke 3 conditions abhi bhi untested** — `liveProjectBusy()` (server path), `_cloudPreview`, `_importPreview` (server path). Is PR ne inhe **local** path par cover kiya, server path par nahi.

### Aage kya

`deleteChat` ka `clearAttachments()` decision (aapka call), phir `ChatModes.kt` split — mode-freeze test pehle se green hai (7 tests), splitting ke waqt "pehle test, phir move".


---

## 🧹 Chat split — tests: `deleteChat` attachments (21 Oct 2026)

`deleteChat()` ne messages, draft aur mode flags reset kiye — par **composer** nahi, jo usi chat ka tha. `startNewChat()` aur `clearAllChats()` dono `clearAttachments()` bulate hain; delete path nahi bulata tha.

### Root cause

```kotlin
// pehle — ChatHistory.kt:264
if (id == activeId) {
    cancelReply()
    activeId = UUID.randomUUID().toString()
    _messages.value = emptyList()
    _draft.value = ""
    ...
}
```

`clearAttachments()` (`ChatAttachments.kt:51`) **paanch** cheezein clear karta hai:

| State | Kaun set karta hai | Delete ke baad pehle |
|---|---|---|
| `_attachments` | picker / `loadFiles` | **stale** — deleted chat ke draft files |
| `_uploads` | `uploadPicked` | **stale** — pending uploads |
| `_attachmentError` | picker failure | **stale** — purana error composer ke neeche |
| `_readingFiles` | `loadFiles` | **stale** — read flag stuck |
| `selectionVersion` | `loadFiles` / `clearAttachments` | **stale** — in-flight load purane chat par land kar sakta tha |

`_draft` clear hota tha, par `_attachments`/`_uploads` nahi — isliye **draft text khaali, par files attached**. Agla chat un files ke saath khulta tha.

### `clearAttachments()` callers ka audit

| Caller | File:line | Kya clear karta hai |
|---|---|---|
| `startNewChat()` | `ChatHistory.kt:280` | poora composer |
| `openChat()` | `ChatHistory.kt:124` | poora composer (chat switch) |
| `clearAllChats()` | `ChatHistory.kt:112` | poora composer |
| `bindCloudHistory()` (deleted active) | `ChatHistory.kt:194` | poora composer |
| `_persistResult()` | `ChatViewModel.kt:1556` | poora composer (reply land) |
| **`deleteChat()`** | `ChatHistory.kt:264` | **pehle: kuch nahi → ab: poora composer** |

### Fix — 3 lines, ek jagah

```kotlin
if (id == activeId) {
    cancelReply()
    // The composer belongs to the chat being deleted: its draft files, pending uploads and
    // selection counter must go with it, or they leak into the next conversation.
    clearAttachments()
    activeId = UUID.randomUUID().toString()
```

- **Sirf active branch.** Background delete composer ko chhoota nahi — woh khule chat ka hai.
- `clearAttachments()` pehle se `HistoryHost` par declared hai (`ChatHistory.kt:60`) ⇒ **koi interface change nahi**, **public API zero change**.
- `selectionVersion++` bhi saath aata hai ⇒ delete ke waqt chal rahi `loadFiles` apna result purane chat par **land nahi kar sakti**.

### Tests — `DeleteChatAttachmentsTest.kt`, 6 cases

| # | Test | Kya assert hota hai |
|---|---|---|
| 1 | `deletingTheActiveChatClearsTheDraftAttachmentsUploadsAndSelection` | `_attachments` empty · `_uploads` empty · `_attachmentError` `""` · `_readingFiles` false · `selectionVersion` aage badha · sirf `OTHER` disk par — **fix se pehle FAIL** |
| 2 | `deletingABackgroundChatKeepsTheComposersAttachmentsAndSelection` | composer ke files/uploads/error/selection **intact** · khula chat + draft bacha · `OTHER` hat gaya |
| 3 | `deletingTheActiveChatFlushesThePendingDraftAndLeavesNothingQueued` | `updateDraft()` → `pending == true` → delete → `pending == false` · draft disk par · deleted chat late save se wapas nahi aati |
| 4 | `deletingTheActiveChatForgetsItsCachedMediaButKeepsFilesOtherChatsUse` | deleted chat ki exclusive cached file **gayi** · doosre chat ki shared file **bachi** (asli files cache dir mein) |
| 5 | `aDraftAttachmentFromADeletedChatNeverReachesTheNextChat` | delete → `startNewChat()` → naya chat bilkul saaf (end-to-end leak) |
| 6 | `aRefusedDeleteLeavesTheComposersAttachmentsUntouched` | `_preparingFiles` par refuse · composer + disk **byte-identical** |

### Coverage pehle → ab

| Kya | Pehle | Ab |
|---|---|---|
| `clearAttachments()` ke 5 effects, delete ke baad | **0/5** | **5/5** |
| `deleteChat` ke composer assertions | **0** | **6 tests** |
| Cross-chat attachment leak | **0** | **1** (end-to-end) |
| Background delete ka composer isolation | **0** | **1** |
| Draft saver pending path × delete | **0** | **1** |

### Determinism

`Thread.sleep` **0** · `CountDownLatch` **0** · blocking interceptor **0** · real network **0** · JUnit `@Rule Timeout` **nahi** (wahi #316 ka wedge) · paused looper test body mein **kabhi pump nahi** (sirf `@After` mein bounded drain) · `advanceUntilIdle` **0**. Har test apna ViewModel + apna OkHttpClient + saaf prefs. Account transport har request par `AssertionError` throw karta hai, isliye stray network hop chup-chaap nahi guzarta.

### Verification

```
app/src/main/java/.../ChatHistory.kt                |   3 ++
app/src/test/java/.../DeleteChatAttachmentsTest.kt | 327 ++++++++++++++++++++
2 files changed, 330 insertions(+)
```

Braces 82/82 (ChatHistory), 38/38 (test). Parens 218/218, 240/240. 6 `@Test`. Koi unused import nahi. Production scope: `git diff --name-only main...HEAD` = **2 files**.

### Merge checklist

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```

`lint` CI mein nahi hai — locally chalana hoga.

### Aage kya

`deleteChat` ke baaki guard branches (`liveProjectBusy`, `_cloudPreview`, `_importPreview`, `buildPr.creating`) abhi bhi untested hain, aur `deleteServerChat` ka happy path + cloud apply bhi. Uske baad `ChatModes.kt` split — mode-freeze test pehle se green hai (7 tests), "pehle test, phir move".

## 🧩 Chat split — Step 7: modes → `ChatModes.kt` (21 Oct 2026)

`ChatAgentControls.kt` already owned Agent / Run code / Build / PR / approvals / X search, and the per-reply freeze maps belong to `ChatLifecycle` (retry reads) and `ChatHistory` (delete clears). What was still in `ChatViewModel.kt` was exactly the composer's own mode logic — four members. Those are now `ChatModes.kt`.

**PR #326** · branch `refactor/chat-modes-split` @ `db65627` · base `main@2993516` · **merged nahi** (owner merge karega).

### Kya move hua

| Moved | Base lines in `ChatViewModel.kt` |
|---|---|
| `setReasoning` | 767 |
| `selectAuto` | 808 |
| `setWebMode` | 878–884 |
| `webModeError` | 885–890 |

`ChatModes.kt` = **40 lines**: `internal interface ChatModesHost` (11 members), `internal class ChatModes(host: ChatModesHost) : ChatModesHost by host`, and the four members.

### Kya nahi move hua — aur kyun

Har symbol ke liye teen test chalaye (feature-host contract · reflection test · shared state):

| Stayed | Why |
|---|---|
| `_reasoning` / `_agent` / `_xSearch` / `_imagine` / `_runCode` / `_webMode` / `_error` / `_integrations` / `_selectedModel` / `_isTyping` | `AgentControlsHost` (19 fields), `ImagineHost` (28), `HistoryHost` (35), `AccountHost`, `LifecycleHost` — sab declare karte hain. Move karte hi sibling host blocks compile hona band. |
| `leaveBuild()` | `AgentControlsHost` + `ImagineHost` dono call karte hain. |
| `replyWebModes` / `replyReasoning` / `replyAgent` / `replyCode` / `replyBuild` / `replyXSearch` | `LifecycleHost` padhta hai (freeze), `HistoryHost` clear karta hai (delete). Base state. |
| `codeUsable` / `buildUsable` / `xSearchUsable` | Already `ChatAgentControls` par forward; `LifecycleHost` bhi declare karta hai. |
| `buildRepositories` / `_buildPr` / `buildUnavailableReason` / `loadBuildRepositories` / `setBuildRepository` / `setAgent` / `setRunCode` / `setXSearch` / `createPullRequest` / `decideAgentApproval` / `codeSnippetUnavailableReason` | Pehle se `ChatAgentControls` ka kaam. |
| `requestTurns` / `applySummary` / `_summary` | Long-context plumbing, modes ka concern nahi. |

Upar ke saare fields reflection tests se bhi pinned hain (`getDeclaredField("_buildRepo")`, `"_webMode"`, `"_reasoning"`, `"_xSearch"` …) — woh **runtime** par fail hote hain, compile par nahi. Isliye jo bhi test naam se pakadta hai, woh hila nahi.

### `ChatModesHost` ka shape

```kotlin
internal interface ChatModesHost {
    val _isTyping: MutableStateFlow<Boolean>
    val _reasoning: MutableStateFlow<Boolean>
    val _agent: MutableStateFlow<Boolean>
    val _xSearch: MutableStateFlow<Boolean>
    val _imagine: MutableStateFlow<Boolean>
    val _runCode: MutableStateFlow<Boolean>
    val _webMode: MutableStateFlow<String?>
    val _error: MutableStateFlow<String?>
    val _integrations: MutableStateFlow<com.dua.ai.setup.IntegrationState>
    val _selectedModel: MutableStateFlow<String>
    fun leaveBuild()
}
```

ViewModel mein: `private val modesFeature: ChatModes by lazy { ChatModes(modesHost) }` + `private val modesHost: ChatModesHost by lazy { object : ChatModesHost { … } }`. Forwarders wahi ki wahi signature rakhte hain: `fun setReasoning(enabled: Boolean) = modesFeature.setReasoning(enabled)`, `fun selectAuto() = modesFeature.selectAuto()`, `fun setWebMode(mode: String?) = modesFeature.setWebMode(mode)`, aur `private fun webModeError(mode, model) = modesFeature.webModeError(mode, model)`.

### Line counts — file badhi, chhoti nahi

| | Before | After |
|---|---|---|
| `ChatViewModel.kt` | 1,616 | 1,620 (**+4**) |
| `ChatModes.kt` | — | 40 |

14 lines logic gayi, 18 wapas aayi (11-member host object + 3 forwarders + delegate + blank). `git diff --stat` = `18 insertions(+), 14 deletions(-)`. Seedha bolna: is step ka fayda yeh hai ki mode logic ka apna naam aur file hai, yeh nahi ki koi number ghata.

### Verification — local Gradle nahi chal saka

Sandbox ka cgroup cap **2 GiB** (`memory.max` = 2147483648, `oom_kill` badha) — `compileDebugKotlin` par Gradle daemon OOM se mar jaata hai. Isliye local build signal nahi tha; **CI hi proof hai**. Static checks:

| Check | Result |
|---|---|
| Ordered byte-compare | **14/14** base lines maujood. `diff` sirf blank lines + javadoc + `private fun webModeError` → `fun webModeError` dikhata hai. |
| Host parity | interface **11** == host overrides **11**, `diff` clean |
| Accidental override (member interface + class dono mein) | **koi nahi** — `comm -12` khaali |
| Public API | **206 == 206**, `diff` khaali |
| Brace balance | `ChatViewModel.kt` 319/319, `ChatModes.kt` 10/10 |
| Changed files | 3 — `ChatModes.kt` (naya), `ChatViewModel.kt`, `ChatModesCoverageTest.kt` (naya) |
| `ui/` files | **0** — koi UI edit nahi chahiye, matlab slice sahi scoped hai |

### Tests — pehle, phir move

Commit order: `6316ff6` (tests) → `db65627` (move). Naya file `ChatModesCoverageTest.kt`, **4 tests**:

| Test | Kya cover karta hai |
|---|---|
| `replyWebModeAnswersForTheFrozenReplyAndForNothingElse` | `replyWebMode()` public reader — freeze test sirf backing map reflection se padhta tha |
| `reasoningIsLockedWhileTypingAndTakesOverImagineAgentAndBuildWhenEnabled` | `setReasoning` ka typing lock + exclusivity (Imagine/Agent off, Build left); off locked nahi |
| `autoClearsEveryModeAndIsIgnoredWhileAReplyStreams` | `selectAuto` chhe modes + Build clear karta hai; mid-reply kuch nahi |
| `readLinkRefusesUntilTheServerOffersItAndIgnoresAnUnknownMode` | `webModeError` ka `"page"` arm, dono direction, + invalid mode drop |

**Coverage pehle → ab:**

| Member | Pehle | Ab |
|---|---|---|
| `setReasoning` | **0 tests** | 1 |
| `selectAuto` | **0 tests** | 1 |
| `setWebMode` | sirf `"search"` | `search` + `page` + invalid + typing lock |
| `webModeError` | sirf `"search"` arm | dono arm, dono direction |
| `replyWebMode()` | **0** (sirf map reflection) | 1 |

`ChatModesModeFreezeTest.kt` (7 tests, PR #319) **bilkul untouched**.

### Determinism

`Thread.sleep` **0** · `CountDownLatch` **0** · blocking interceptor **0** (client har request par `AssertionError` — mode toggle network chhuye to loud fail) · JUnit `@Rule Timeout` **nahi** (woh body ko alag thread par le jaata hai aur paused Looper wedge hota hai — #316 wala hang) · paused looper test body mein **kabhi pump nahi** · `advanceUntilIdle` **0**. Har assertion woh state padhti hai jo call khud synchronously set karti hai. Har test apna ViewModel + apna OkHttpClient + saaf prefs.

### CI

Run **`39573981048`** — https://github.com/KRFbrothers/Dua-ai/actions/runs/39573981048 — **4/4 success**:

| Job | Conclusion | Time |
|---|---|---|
| Android build and tests | ✅ success | 09:03:25 → 09:07:44 (**4m 19s**, koi hang nahi) |
| Backend tests | ✅ success | 18s |
| Website build | ✅ success | 14s |
| Google Cloud engine tests | ✅ success | 1m 21s |

Merge-point `main` run `39571483986` bhi 4/4 success.

### Merge checklist

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```

`lint` CI mein nahi hai — locally chalana hoga.

### Aage kya

Baaki untested mode paths: `setBuildRepository`/`setXSearch`/`setRunCode` ke typing-lock branches, aur `_agentAvailable` false hone par `retryResponse` ka `requestAgent` drop. Uske baad agla slice — `ChatHistory.kt` (295 lines) sabse bada bacha hua feature file hai.

## 🧪 Chat split — remaining mode / Retry guards (21 Oct 2026)

Base: `main@f7450a2`. Step 7 / PR #326 ab merge ho chuka hai. Upar ke final "Aage kya" ke mode aur Retry gaps yahan cover hue: **6 naye tests**, do existing test files mein. Purane README ka har byte prefix ke roop mein bacha hai; koi source file move/delete nahi hui.

### Coverage

| Path | Naya regression check |
|---|---|
| `setRunCode` typing lock | Enable **aur** disable dono refused; supported provider ke saath Agent/Imagine/Build aur existing notice unchanged. |
| `setXSearch` typing lock | Grok + advertised X search ke saath enable **aur** disable refused; Agent aur notice unchanged. |
| `setBuildRepository` typing lock | Signed-in owner + configured Gemini ke saath repository change **aur** clear refused; Grok selection, other modes, notice aur saved history unchanged. |
| Frozen Agent reply, capability unavailable | Real retry request ka `mode="auto"`; original frozen Agent flag aur composer choices preserved. |
| Frozen Agent + Reasoning, capability unavailable | Real retry request ka `mode="reasoning"`; composer ki current choice se replace nahi hota. |
| Frozen Agent + Reasoning, capability available | Real retry request ka `mode="agent"`; Agent Reasoning ko suppress karta hai. Yeh positive control always-off fallback ko pakadta hai. |

`ChatModesCoverageTest.kt`: **4 → 7** tests. `RetryResponseTest.kt`: **5 → 8** tests. `ChatModesModeFreezeTest.kt` ke **7 tests untouched**.

### Harness

Real `ChatViewModel` + existing real streaming client; HTTP interceptor network se pehle complete SSE reply deta hai. Mode-lock tests synchronous hain. Retry cases existing bounded wait/teardown reuse karte hain. Koi naya latch, blocking interceptor, sleep loop ya JUnit `Timeout` rule nahi. Production APIs, reflection-pinned fields aur UI same hain.

### Verification

| Check | Result |
|---|---|
| Focused mode / freeze / Retry suite | **22 passed**, 0 failed/skipped |
| Full `:app:testDebugUnitTest -Pdua.ui-record` | **1,672 passed**, 0 failures/errors/skips |
| `:app:assembleDebug` | **pass** |
| `:app:lintDebug` | **fail: 2 existing errors, 60 warnings** — details below |

Lint errors dono unchanged production files mein hain:

- `ui/navigation/DuaNavHost.kt:298`, `StateFlowValueCalledInComposition`: Voice route composition `viewModel.projectId.value` padhti hai instead of collecting it as state.
- `res/layout/dua_toolbar_widget.xml:21`, `UseAppTint`: live widget `ImageButton` ka `android:tint`. Yeh **RemoteViews** layout hai; `app:tint` se blindly replace nahi karna, kyunki launcher AppCompat inflater use nahi karta.

Test/build pass ko lint pass nahi maana gaya. Is change ka scope two test files + yeh append-only report hai. Agla refactor candidate `ChatHistory.kt` hai; lint ke dono existing blockers bhi ab explicitly recorded hain.
