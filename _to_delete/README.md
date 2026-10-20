# Yeh files delete karne se pehle padho

Ye folder abhi **khaali** hai — sirf yahi README hai. Yahan aage woh files aayengi jo delete ke liye review mein hain (path structure same rahega, taaki `git diff` mein saaf dikhe).

Sab line numbers `main` branch ke commit **`e6fac6e`** par **20 Oct 2026** ko fresh grep se liye gaye hain. Purane numbers copy nahi kiye.

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

## ✅ Ye teen pakka dead hain

Inke object ka naam poore `.kt` codebase mein **kahin nahi** aata (sirf apni hi file ke andar):

| File path | Proof |
| --- | --- |
| `app/src/main/java/com/dua/ai/ui/chat/TelegramInterpolators.kt` | `TelegramInterpolators` → apni file ke bahar **0 hits** |
| `app/src/main/java/com/dua/ai/ui/chat/TelegramImageReveal.kt` | `TelegramImageReveal` → apni file ke bahar **0 hits** |
| `app/src/main/java/com/dua/ai/ui/history/TelegramDialogSwipe.kt` | `TelegramDialogSwipe` → apni file ke bahar **0 hits** |

### ⚠️ Par ek problem hai — ye teen notices mein bhi hain

Teeno files `THIRD_PARTY_NOTICES.md` ke **Telegram for Android** section mein row ke saath listed hain (us section mein total **7 rows**). Aur teeno ke header mein likha hai:

> Adapted from Telegram ... Original: Telegram for Android, Copyright Nikolai Kudashov, 2013-2018 ... licensed under GNU GPL v. 2 or later

Matlab: code dead hai, par **attribution dead nahi hai**. Delete karo to notice file mein dangling row reh jaayega — jo license hygiene kharab karta hai.

**Sahi tareeka:** delete wale commit mein hi `THIRD_PARTY_NOTICES.md` ki woh 3 rows bhi hatao. Pehle us file ka header dekho jo inhe adapt kiya tha.

Phir bhi **seedha delete mat karo** — neeche wala process follow karo. Grep se "0 hits" milna proof nahi hai (neeche wajah likhi hai).

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
4. **File delete karo, phir build** — `./gradlew assembleDebug && ./gradlew lint`. Dono pass hone chahiye.
5. **Runtime par test karo** — build pass hona kaafi nahi. WorkManager, reflection, aur manifest-declared cheezein sirf app chala kar hi pakdi jaati hain. Backup, widget, notification wale hisse zaroor test karo.
6. **`THIRD_PARTY_NOTICES.md` check karo** — deleted file ka naam wahan hai kya? Hai to usi commit mein row hatao, warna license trail toot jaayega.
7. **Tab jaake delete commit karo** — aur PR mein likho ki build + app test dono pass hue.

---

## Is PR mein kya nahi kiya

Koi file move ya delete **nahi** hui. `ChatViewModel.kt` todna, `ui/screens/` merge karna, `auth/` merge karna, `web/` rename karna — kuch bhi is PR mein nahi hai. Sirf yahi README add hui hai.
