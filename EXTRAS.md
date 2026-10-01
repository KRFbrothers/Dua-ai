# Extras

## Suggested animations
- Splash: `animateFloatAsState` scale 0.92→1 + alpha 0→1, 600ms
- Onboarding dots: `animateDpAsState` 8→24 width
- Auth errors: `AnimatedVisibility` + `slideInVertically`
- Navigation: fade 220ms + 8–16dp horizontal slide
- Habit ring: `animateFloatAsState` sweep
- Note “Saved”: fade after 600ms debounce (`LaunchedEffect`)
- Lists: `Modifier.animateItem()` on Lazy items when you wire real data

APIs: `animateFloatAsState`, `animateDpAsState`, `AnimatedVisibility`, NavHost `enterTransition`.

## Accessibility per screen
- Splash: logo `contentDescription = "Nova logo"`
- Onboarding: pager “page X of 3”; Skip is a real button
- Login/Signup: password toggle announced Show/Hide
- All icon-only buttons: contentDescription set
- Checkboxes and switches use default semantics
- Touch: buttons 52dp, nav items default 48+, chips 36+ (label still tappable)
- Contrast: navy #1F3A5F on white, paper #F6F4EF with ink #1A1C1E (both AA+)
- Dark: #A8C5E2 on #0E2136, mist #E6E2DC on #0F1216

## Performance
- Lists use `LazyColumn` / `LazyVerticalStaggeredGrid` + `key`
- Sample data is in `remember` / `rememberSaveable` — replace with ViewModel before shipping
- Do not put heavy work in `@Composable` bodies
- Keep `useDynamicColor = false` until brand QA is done
- Avoid nested scrollables (Home is a single vertical scroll)
- FAB + bottom bar: content has 96dp bottom padding so last row is not hidden

## Future
- Real auth (email link + Google Identity)
- Room or DataStore for tasks/notes/habits
- Widget for today’s top task
- Biometric lock that actually gates the nav graph
- Manrope font files under `res/font`
- Shared element from note card → detail (Compose SharedTransitionLayout)
- Empty-state illustrations instead of text-only
- Sync later, offline first

## Honest limits of this kit
- No backend. Login only checks “not empty / 6 chars”.
- Google button routes to Home so you can preview the main app.
- Dates, reminders, pin, delete, search are UI-complete, not persisted.
- Theme segmented control does not yet rewrite `NovaTheme` (needs a CompositionLocal).
- This folder is source to copy into Android Studio. It is not a full Gradle project.
