# Drawer/profile and project cleanup

Audit date: 2026-10-19. Scope: tracked files, Android source callers, assets and declared dependencies. No backend, authentication, backup format or website behavior changes.

## Current drawer

New chat is the fixed left footer icon; the real account avatar is the fixed right icon. Profile closes the drawer and opens the existing Settings handler, including for guests and while New chat is disabled. No name label, PRO badge or separate gear is added. The shared avatar, existing theme, Home account menu and navigation are reused.

## Removed unused code and runtimes

- Removed unreferenced `DuaHapticManager`, `DuaMotionConfig` and their Compose factories. Current preference-aware ChatActions haptics, stream throttling, motion presets and live reduced-motion observation remain active.
- Removed the obsolete helper-only motion test class; behavioral motion, streaming, predictive Back and UI coverage remain.
- Removed unused Rive, Lottie Compose and Core Performance dependency/catalog entries. The app had no production runtime imports or calls for them. No replacement runtime or fabricated shader/device-tier behavior was added.
- Removed unused drawer typography aliases and footer spacing token left over from the old labeled account footer.

## Duplicate files retained intentionally

A SHA-256 comparison of 711 tracked files found two identical pairs: `ic_launcher.xml` / `ic_launcher_round.xml`, and `.node-version` / `.nvmrc`. These are separate Android launcher and Node-tool entry points, so deleting one would remove a supported consumer. Functional backup code, tests, Android backup rules and the logo-copy script are not temporary duplicates and remain.

## Verification

The full default Android unit/Compose UI suite and debug APK assembly validate the cleanup together with the footer change. Native drawer captures cover light/dark and normal/2× text. Physical-device frame timing, keyboard animation, battery cost and release APK/AAB size are outside these checks.

- Full `:app:testDebugUnitTest :app:assembleDebug -Pdua.ui-record --offline` run: 1,371 tests across 181 classes, zero failures/errors/skips. The five removed tests exercised deleted helpers or token constants; behavioral motion tests remain.
- The universal debug APK decreased from 67,205,692 to 33,694,207 bytes (33,511,485 bytes, about 49.9%). This is a local debug-build comparison, not a release, download-size, frame-rate or battery measurement. Rive native libraries are absent from the resulting APK.
- The APK DEX files contain no supplied-demo `com/example/geminiapp` package.
- Current native light/dark drawer captures, including 2× text, show the fixed footer clear of scrollable history.

After syncing main’s three Windows-compatibility test fixes, 55 affected Home/drawer/storage/preferences tests passed across 5 classes with zero failures/errors/skips; debug APK assembly passed again. `git diff --check` passed.
