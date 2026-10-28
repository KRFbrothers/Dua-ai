# Android reference and acceptance handoff

The owner supplied a Gemini Android recording and approved the in-place Dua UI
replacement. Implementation uses the existing Android app, original Dua artwork,
state/actions and backend capabilities. There is one chat renderer and composer.

The recording is 159.858356 seconds, 720 × 1584 pixels, with SHA-256
`de2f456b7b1f93e2a5e1c3576303e1525a6131b3325bb35443bfb2651cae603b`.
It was sampled at 40 overview and 17 additional detailed timestamps. Raw media,
private account content and its download/share link are kept outside versioned
source. Do not turn reference account details or files into product fixtures.

See [observations](GEMINI_ANDROID_OBSERVATIONS.md),
[approved scope](../../SPEC-gemini-android-ui.md),
[current ownership](LEGACY_CHAT_SHELL.md), and
[verification results](../../tasks/todo.md).

## Device acceptance

The cloud recording and local Compose renders do not establish phone behavior.
Use the existing Android Studio project (`apps/android`) and the owner's device
for final checks:

1. Home Chat and Task; starter draft/send; keyboard, multiline text and Back.
2. Drawer More/search, selected chat, pin, rename and delete confirmation.
3. Photos, camera and files: cancellation, permission denial, upload and removal.
4. Dictation and voice: mic permission, mute, interrupt, close/background cleanup,
   photo selection and return to chat for documents.
5. Profile/settings and Library: actual account, saved preferences, loading,
   refresh failure, local documents and authenticated media opening.
6. Narrow/landscape layouts, large text, both themes, reduced motion and TalkBack.

Use real Dua capabilities. Screen sharing is unavailable in the current voice
mode; existing Coming soon destinations remain explicit placeholders. Dark
appearance was not supplied as reference, so it is checked for usability rather
than claimed to match unseen reference screens. No backend deploy or data
migration is required for this UI.

## Optional REA capture on Windows

REA can capture the phone connected to the Windows laptop. It does not connect
that phone to this cloud workspace. In Android Studio's PowerShell terminal:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
$env:REA_ADB_PATH = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
codex.cmd
```

Replace the SDK path if customized. The phone must report `device`; accept its
USB debugging prompt if it reports `unauthorized`. Reuse the installed REA
registration and manually open screens when collecting evidence. Its documented
ADB integration captures screenshots; it does not establish arbitrary tap/type/
swipe control or the success of unobserved feature actions.

REA documentation: [device analysis](https://github.com/KRFbrothers/rea/blob/main/docs/adb-device-analysis.md)
and [Android analysis](https://github.com/KRFbrothers/rea/blob/main/docs/android-analysis.md).
