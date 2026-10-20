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
