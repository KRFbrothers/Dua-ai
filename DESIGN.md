# Nova — Complete UI Design System
App: Nova | Type: Productivity + Lifestyle | Platform: Android + Jetpack Compose + Material 3
Package: com.nova.app | Target: 18–35, mobile-first, global

## 1. DESIGN OVERVIEW

### App concept
Nova is a calm daily OS: tasks, notes, habits, and a quiet profile. Not a loud “hustle” app. Premium paper + ink feel. One primary action per screen.

### Design principles
1. Calm first — warm paper backgrounds, not cold gray.
2. One focus — every screen has one clear next action.
3. 8dp grid — 8 / 16 / 24 / 32 only.
4. Soft elevation — no harsh drop shadows.
5. Dark mode is a first-class theme, not an invert.
6. Touch targets 48dp+. Contrast AA+.

### UX flow (text)
```
Splash (1.6s)
  → first launch? Onboarding (3 pages) → Login
  → returning? Login (or skip if session exists) → Home

Login ↔ Signup
Login → Forgot Password → Login

Main (BottomNav, 4 tabs)
  Home ─────── Habits Tracker
  Tasks ────── Add / Edit Task
  Notes ────── Note Detail
  Profile ──── Settings
```

Back stack
- Auth screens: back to previous auth screen; from Login back exits or stays.
- Detail screens (Edit Task, Note Detail, Habits, Settings): pop to parent tab.
- Bottom tabs: save state per tab, do not stack tabs on each other.

---

## 2. DESIGN SYSTEM

### Color palette (HEX)

| Token            | Light     | Dark      | Use |
|------------------|-----------|-----------|-----|
| Primary          | #1F3A5F   | #A8C5E2   | Buttons, selected tab, key icons |
| OnPrimary        | #FFFFFF   | #0E2136   | Text on primary |
| PrimaryContainer | #D6E4F0   | #2A4668   | Soft chips, selected cards |
| OnPrimaryContainer | #0E2136 | #D6E4F0 | Text on container |
| Secondary        | #C9842A   | #E8B86D   | Accents, streaks, FAB highlight |
| OnSecondary      | #FFFFFF   | #3B2708   | Text on secondary |
| SecondaryContainer | #FBE8C8 | #5C3D12 | Habit filled rings, gold chips |
| Tertiary         | #3D6B5A   | #A8CDB8   | Habits success, done tasks |
| Background       | #F6F4EF   | #0F1216   | Screen canvas |
| OnBackground     | #1A1C1E   | #E6E2DC   | Primary text |
| Surface          | #FFFBFC   | #161B22   | Cards, sheets, bars |
| OnSurface        | #1A1C1E   | #E6E2DC   | Card text |
| SurfaceVariant   | #E8E4DC   | #2A3038   | Input fill, dividers-lite |
| OnSurfaceVariant | #43474E   | #C3C7CE   | Secondary text |
| Outline          | #73777F   | #8B9198   | Borders |
| Error            | #B3261E   | #F2B8B5   | Errors |
| OnError          | #FFFFFF   | #601410   | Text on error |
| InverseSurface   | #2F3133   | #E6E2DC   | Snackbars |
| Scrim            | #000000   | #000000   | 32% overlay |

Brand extras (not M3 tokens, used in splash/onboarding)
- NovaInk #0F1216
- NovaGold #C9842A
- NovaPaper #F6F4EF

### Typography
Family: **Manrope** (Google Fonts). Fallback: `FontFamily.SansSerif`.

| Role            | Size | Line | Weight |
|-----------------|------|------|--------|
| Display Large   | 57   | 64   | Bold 700 |
| Display Medium  | 45   | 52   | Bold 700 |
| Display Small   | 36   | 44   | Bold 700 |
| Headline Large  | 32   | 40   | SemiBold 600 |
| Headline Medium | 28   | 36   | SemiBold 600 |
| Headline Small  | 24   | 32   | SemiBold 600 |
| Title Large     | 22   | 28   | SemiBold 600 |
| Title Medium    | 16   | 24   | SemiBold 600 |
| Title Small     | 14   | 20   | Medium 500 |
| Body Large      | 16   | 24   | Regular 400 |
| Body Medium     | 14   | 20   | Regular 400 |
| Body Small      | 12   | 16   | Regular 400 |
| Label Large     | 14   | 20   | Medium 500 |
| Label Medium    | 12   | 16   | Medium 500 |
| Label Small     | 11   | 16   | Medium 500 |

Letter spacing: Display −0.25, Titles 0, Labels +0.1.

### Spacing
4 unused. Use 8, 16, 24, 32, 48.
- Screen horizontal padding: 24
- Section gap: 24
- Card inner padding: 16
- Between stacked fields: 16
- Icon-to-text: 8
- Bottom nav content padding: 80 (clear FAB / nav)

### Shapes
- ExtraSmall 8dp — buttons, inputs
- Small 12dp — cards, list rows
- Medium 16dp — dialogs
- Large 24dp — pills, FABs, sheets
- ExtraLarge 32dp — splash mark container

### Elevation
- Resting card: 1dp + 4% black (light) / 8% white (dark)
- Raised (pressed / FAB): 3dp
- No 8dp+ slabs.

### Component styles
- Primary button: filled, height 52, radius 8, label Label Large
- Secondary button: outlined, same height, outline 1dp
- Text button: no fill, 48 min height
- Input: height 56, radius 8, SurfaceVariant fill, no heavy underline
- Card: Surface, radius 12, 1dp elevation
- Chip / pill: radius 24, height 36
- FAB: 56, radius 16, Primary
- Top bar: Surface, no elevation, 64 height
- Bottom nav: Surface, 80 height including inset, indicator PrimaryContainer pill

### Icons
Material Icons Extended.
Common: AutoAwesome (Nova mark), Home, CheckCircle, StickyNote2, Person, Settings, Add, ArrowBack, Visibility, VisibilityOff, Email, Lock, DarkMode, Notifications, Logout, LocalFireDepartment, Edit, Delete, Search, MoreVert, CalendarMonth, Flag, Repeat.

---

## 3. SCREEN-BY-SCREEN DESIGN

### 1. Splash
Purpose: brand beat, then route.
Layout: full Background. Center 88dp circle (PrimaryContainer) with AutoAwesome 40dp Primary. 16 below: wordmark “Nova” Headline Medium. 8 below: “Shine a little every day.” Body Small OnSurfaceVariant.
States: only loading (timer). After 1600ms navigate.
Anim: logo scale 0.92→1.0 + fade 0→1, 600ms EaseOut.
A11y: contentDescription “Nova logo”.

### 2. Onboarding (3 pages)
Purpose: promise, then Login.
Pages:
1. Focus — “One list. Clear head.” Tasks illustration (icon stack).
2. Remember — “Notes that stay quiet until you need them.”
3. Grow — “Habits that compound.” CTA “Get started”.
Layout: status bar, skip TextButton top-end 24 pad. Page content 24 pad. Horizontal pager. Dots 8 gap, active pill 24×8 Secondary, inactive 8×8 Outline. Bottom button 24 pad, 24 above nav inset.
States: page 1–3. Last page button label changes “Get started”.
Anim: pager crossfade + dots width animate.
A11y: page “1 of 3”, skip announced.

### 3. Login
Purpose: email + password → Home.
Layout top→bottom:
- 48 top spacer
- “Welcome back” Headline Small
- “Sign in to Nova” Body Medium, 8 gap
- 32 gap
- Email field
- 16 gap Password field (trailing visibility)
- 8 gap Forgot? aligned end
- 24 gap Primary “Sign in” full width
- 16 gap outlined “Continue with Google”
- 24 gap “New here? Create account” center
States: idle, loading (button spinner, fields disabled), error (banner + field supporting text), success (navigate Home).
Anim: error banner slide from top 200ms.
A11y: password hide/show described.

### 4. Signup
Same scaffold as Login.
Fields: Name, Email, Password, Confirm password.
Primary “Create account”.
Footer “Already have an account? Sign in”.
States same. Password rules shown as Body Small when focused.

### 5. Forgot Password
Headline “Reset password”. Body “We’ll email a reset link.”
Email field + Primary “Send link”.
Success state: replace form with check icon + “Check your inbox” + TextButton Back to sign in.
Error: invalid email supporting text.

### 6. Home / Dashboard
Purpose: today snapshot.
Top: “Good evening,” Label Large + “Fareed” Headline Small (sample name). Trailing settings-less; avatar 40dp opens Profile tab.
24 gap. 3 metric cards in a row (Tasks left, Habit streak, Notes count). Card 12 radius, 16 pad, number Headline Small, label Label Small.
24 gap. Section “Today” Title Medium + “See all” TextButton → Tasks.
Today task rows (max 3): checkbox 24, title Body Large, time Label Small.
24 gap. Section “Habits” + “Open” → Habits.
2 habit rings in a row.
24 gap. “Quick note” outlined field fake → Notes.
Empty: illustration + “Nothing due. Nice.”
Loading: 3 shimmer cards.
Error: retry text button.
Anim: list items fade+slide 40dp, staggered 40ms.

### 7. Task List
Top bar title “Tasks”. Actions: search, filter chip row (All / Today / Done).
FAB + bottom right “Add task”.
List: cards 12 radius, 16 vertical pad, checkbox, title, due chip, flag.
Empty: “No tasks yet” + Primary “Add your first task”.
Loading: 6 placeholders.
Error: inline retry.
Swipe: end-to-start delete with confirm snackbar Undo.

### 8. Add / Edit Task
Top bar: Close X, title “New task” or “Edit task”, trailing Save TextButton.
Fields: Title (required), Notes multiline 4 lines, Due date picker row, Priority chips Low/Med/High, Remind switch.
Delete text button only in edit mode (error color).
States: validation on save (title blank → supporting error). Saving spinner on Save.
Keyboard: title focused on enter.

### 9. Notes List
Top “Notes”. Search field 16 below app bar.
Masonry-ish 2-col cards: title Title Small, preview Body Small 2 lines, date Label Small.
FAB “New note”.
Empty / loading / error same pattern.
Tap → Note Detail.

### 10. Note Detail
Top: Back, pin toggle, more (delete).
Title field (Headline Small, no border).
Body field fills rest (Body Large).
Auto-save label “Saved” Label Small fade after 600ms debounce.
Empty title placeholder “Untitled”.

### 11. Habits Tracker
Top “Habits”.
Week strip: 7 circles, today outlined Secondary.
List: habit row = 48 ring (progress), name, “4/7 this week” Label Small, check button 48.
FAB “New habit” → simple dialog: name + days chips.
Empty: “Start a tiny habit.”
Anim: ring sweep when checked.

### 12. Profile
Avatar 88, name Headline Small, email Body Small.
Stats row: 12 tasks, 4 notes, 3 habits.
Rows: Account, Notifications, Appearance, About, Sign out (error color).
Appearance row opens Settings (or inline theme).

### 13. Settings
Top “Settings”.
Groups:
- Appearance: Light / Dark / System segmented
- Notifications: Daily reminder switch + time
- Privacy: lock with biometrics switch (visual only)
- About: version 1.0.0
Back to Profile.

### 14. Bottom Navigation
Tabs: Home, Tasks, Notes, Profile.
Selected: Primary icon + PrimaryContainer pill indicator.
Unselected: OnSurfaceVariant.
Habits and Settings are NOT tabs.
FAB only on Tasks and Notes.

---

## 4–6. CODE + RESOURCES
See `/home/workdir/artifacts/nova/` source tree.
BOM: androidx.compose:compose-bom:2026.09.00 (Material3 1.4.0).
