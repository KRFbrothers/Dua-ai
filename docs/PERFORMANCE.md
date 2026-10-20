# Performance: Baseline Profile and startup benchmark

Dua ships a **Baseline Profile** so Android pre-compiles the code used at startup and in the chat
screen, which makes the first launch and first scrolls smoother.

There are two profiles:

| File | What it is |
| --- | --- |
| `app/src/main/baseline-prof.txt` | Hand-written fallback. Always used. Do not delete it. |
| `app/src/release/generated/baselineProfiles/` | The real, generated profile. Appears after you run the generator below and commit it. |

Both are merged into the release APK automatically.

## The `:benchmark` module

`benchmark/` is a **test-only** module. Nothing in it is packaged in the Dua app and it adds no runtime
library. It contains:

- `BaselineProfileGenerator`: records these journeys: cold start, open the history drawer, open a
  chat, scroll, type and send a message. Sending happens with **airplane mode on**, so the
  generator never talks to the Dua servers (it just exercises the offline path). Airplane mode is
  switched off again at the end.
- `StartupBenchmark`: measures cold start time with no compilation, with the Baseline Profile and
  fully compiled.

## How Fareed generates the real profile

This **must run on a real Android phone** (Android 9 / API 28 or newer; Android 13+ recommended,
no root needed). **Google Cloud Shell cannot do this**: it has no phone or emulator attached and
can't reach a phone over adb. GitHub Actions can't do it either, and we have no workflow for it. Use a computer
(Windows, Mac or Linux) with Android Studio or the Android SDK installed.

1. On the phone: Settings → About phone → tap **Build number** 7 times → Developer options →
   turn on **USB debugging** (or **Wireless debugging**).
2. Connect the phone:
   - USB: plug in the cable and accept the "Allow USB debugging?" prompt.
   - Wireless: Developer options → Wireless debugging → *Pair device with pairing code*, then on
     the computer: `adb pair IP:PAIR_PORT` (enter the code), then `adb connect IP:PORT`.
3. Check it shows up: `adb devices` should list the phone as `device`.
4. **Important:** this build has the same package name as your personal Dua APK but a different
   signing key, so Android won't install it over your APK. Uninstall Dua first (its chats on the
   phone are deleted), or use a spare phone. Reinstall your personal APK afterwards.
5. Unlock the phone and keep the screen on. In the repo folder run:

   ```bash
   ./gradlew :app:generateBaselineProfile
   ```

   It installs a special `benchmarkRelease` build of Dua (signed with the debug key), runs the
   journeys a few times and
   writes the profile to `app/src/release/generated/baselineProfiles/baseline-prof.txt`.
6. Commit that file in a PR. The next personal APK build includes it.

To measure startup (optional):

```bash
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest
```

Results are printed and saved under `benchmark/build/outputs/`. Compare `startupNoCompilation`
vs `startupBaselineProfile` (`timeToInitialDisplayMs`).

## Notes

- Normal builds and CI never run the generator (`automaticGenerationDuringBuild = false`), so
  `assembleDebug`, unit tests and the personal APK workflow are unaffected.
- If onboarding or sign-in screens change, the journeys may need new selectors (texts like
  "Skip for now", "Open chat history", "Ask Dua AI…", "Send message").
- Adapted from Now in Android's `benchmarks/` module (Apache 2.0, see `THIRD_PARTY_NOTICES.md`).
