# Google sign-in returns to the login screen

Reported on 2026-10-19: selecting a Google account returns to the same login screen without completing sign-in or showing an error.

## Findings and change

The app's explicit Continue with Google buttons used `GetGoogleIdOption`, the dismissible bottom-sheet flow, and only tried `GetSignInWithGoogleOption` after NoCredentialException. Provider cancellation was cleared with a null error, which can produce the reported silent return. Both onboarding and Account share this path.

Use `GetSignInWithGoogleOption` directly for user-tapped Google buttons, as specified in [Android's implementation guide](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation). This flow also supports adding or reauthenticating an account. Show an incomplete-sign-in/retry message for provider cancellation. A genuine coroutine cancellation when leaving the screen still resets progress and propagates cancellation.

The request still uses a fresh cryptographic nonce, sends its SHA-256 hash to Google and passes the raw nonce with the ID token to the existing backend. Parsing, remembered-account matching, Supabase token verification, owner restrictions, encrypted session persistence and navigation after a verified session remain active. No client ID, signing key, redirect URL, Supabase setting or server authorization rule is changed.

## Diagnostic limits

Read-only Supabase Auth logs had no events in the 2026-10-19 05:30–06:27 UTC window. The wider 24-hour window contained successful token/user requests and no recorded Auth rejection. These observations do not prove what happened on the phone: an unavailable Google credential, a request failing before Supabase, a different installed build/backend, or an Android OAuth signing mismatch could also prevent an exchange.

The installed build number was not supplied. A physical-device retry with the correctly signed personal APK is required to confirm the reported attempt completes. If it still returns, the newly visible message distinguishes an incomplete provider flow from a backend rejection. Check the Android OAuth package/certificate and web client audience against SIGNING.md if configuration fails; do not replace the permanent key or disable nonce/owner checks.

## Verification

Regression coverage exercises the real Google credential request shape and parsing through an injected platform boundary: explicit button option, fresh hashed/raw nonce contract, one identity handoff, visible provider cancellation, missing credentials without a second picker, remembered-account mismatch, screen cancellation propagation, unexpected credentials and missing configuration. Existing account/session and UI tests cover backend exchange, persistence, owner consistency, navigation and error presentation.

Supabase's current [Google sign-in guide](https://supabase.com/docs/guides/auth/social-login/auth-google) and changelog were reviewed. No relevant current native ID-token contract change was found. No live sign-in was attempted on the user's behalf and no credential values were read or logged.

Validated on 2026-10-19: the full default Android suite passed 1,382 tests across 183 classes, with zero failures/errors/skips, including all six new Google flow cases. `:app:assembleDebug` and `git diff --check` passed. A genuine Google/Play-services exchange on the physical phone remains unverified.
