# Account recovery, Google login and personal access setup

Existing email login and manual cloud snapshots keep working with `SUPABASE_URL` and the public `SUPABASE_ANON_KEY`. New setup is optional. No real account, email or deletion was performed during implementation; endpoint tests use mocks.

## Password recovery (web and Android)

1. Keep the Supabase Email provider enabled. Use the custom SMTP settings already configured for this project.
2. In **Authentication → Emails → Templates → Reset Password**, include the numeric recovery token in the email body: `Your Dua recovery code is {{ .Token }}`. The default template may contain only a link; this app's recovery screen asks for the code.
3. Keep the minimum password length at least 8. OTP expiry and length use the project's Supabase settings; the server accepts numeric codes from 6 to 10 digits.
4. Request a recovery email, verify its code, then enter a new password. Verification creates a session for that account. It does not upload local chats or memories.

Server endpoints:

| Endpoint | JSON body | Result |
| --- | --- | --- |
| `POST /api/account/reset-password` | `{ "email": "you@example.com" }` | Generic delivery notice; no account-existence detail |
| `POST /api/account/verify-recovery` | `{ "email": "you@example.com", "token": "123456" }` | Verified account and session |
| `PUT /api/account/password` | `{ "password": "new password", "currentPassword": "optional current password" }` | Updates the authenticated user's password |

Web sessions stay in secure HttpOnly cookies. Android uses the existing native Bearer session contract. Supabase may require fresh recovery verification or the current password depending on its configured password-change policy. Updating a password does not promise revocation of every existing device session.

## Google login (web)

1. Create a Google OAuth web application in Google Cloud, enable its consent screen, and add your Gmail account as a test user if the application remains in testing.
2. In **Supabase → Authentication → Sign In / Providers → Google**, enable Google and enter its client ID and client secret directly there.
3. Register the **Supabase provider callback URL** shown on that page in Google Cloud's authorized redirect URIs. This is Supabase's `/auth/v1/callback` URL, rather than the app callback below.
4. In Supabase **URL Configuration**, set the site's URL to `https://dua-cyan.vercel.app` and allow `https://dua-cyan.vercel.app/api/account/oauth/callback**` as an app redirect. The callback uses a random `flow` query parameter. Keep the allowlist limited to this callback path.
5. Add Vercel production environment variable `SUPABASE_AUTH_REDIRECT_URL=https://dua-cyan.vercel.app/api/account/oauth/callback` and redeploy.

`POST /api/account/google` returns an authorization URL and stores a temporary PKCE verifier in a secure HttpOnly cookie. The browser opens the URL; `GET /api/account/oauth/callback` validates the bound flow and exchanges the code on the server. It returns to `/profile.html`. Client-supplied redirects are ignored, and the configured HTTPS callback must match the request's origin, so preview deployments do not use the production callback accidentally.

Native Google login needs a registered Android callback and a secure native session handoff. That handoff is not enabled by this setup. Android can open web Profile for browser Google login; it still signs into its native session through email/password or recovery.

## Permanent account deletion (optional)

The UI requires typing **DELETE MY ACCOUNT**. The server verifies the current account with the public Supabase API, then deletes that same user through Supabase's admin endpoint. A supplied user ID is never accepted. Deleting the account also removes cloud snapshots and request-usage rows through database foreign-key cascades. Local chats and memories remain on the device; use local data controls separately if desired.

To enable deletion, place `SUPABASE_SERVICE_ROLE_KEY` in Vercel's **server-only** environment settings and redeploy. Use the project's Supabase secret/admin key, rather than its anon/publishable key. This key is powerful: never put it in HTML, Android BuildConfig, `NEXT_PUBLIC_` or `VITE_` variables, screenshots, repository files or chat. The application uses it only for the explicit deletion endpoint and optional request-budget RPC; cloud snapshot routes continue using the signed-in user's token and row-level security.

`DELETE /api/account/delete` takes `{ "confirmation": "DELETE MY ACCOUNT", "expectedAccountId": "the account id shown in Profile" }`. The displayed account binding is required; an account switch returns HTTP 409 before deletion. It stays unavailable when the admin key is absent. `GET /api/account/features` reports which optional account features are configured without returning credentials.

## Restrict online AI to your personal account (optional)

Add these server environment variables and redeploy:

```dotenv
DUA_REQUIRE_LOGIN=true
DUA_ALLOWED_EMAIL=your-own-email@example.com
```

Both are optional and disabled by default. `DUA_REQUIRE_LOGIN` accepts `true` or `1` to enable it, and `false` or `0` to disable it. An allowed email implies login is required. The server verifies the account through Supabase before accepting online AI requests; an email in the client payload is not trusted. Local history remains available even when login or an online request fails. Do not set these restrictions until your login works.

The backend calls `guardChatAccess(request, { env, fetcher })` before invoking a model. A successful result may include refreshed HttpOnly cookies; the router forwards its `headers` with the model response. Denials return the guard's `response` without calling a provider.

## Durable daily request budget (optional)

1. Run `supabase/migrations/202610040002_dua_usage.sql` in the project's Supabase SQL editor.
2. Add a server-only `SUPABASE_SERVICE_ROLE_KEY` as described above.
3. Set `DUA_DAILY_REQUEST_LIMIT` to a whole number from 1 to 100000, for example `50`, then redeploy. This setting implies login is required.

The database atomically reserves one request attempt per accepted online chat request, per account, per UTC day. Failed provider calls still consume an attempt. Counts survive deployments and concurrent requests. The function is executable only by the trusted server's service role; the client cannot choose a higher limit. Requests fail closed when configured budget storage is unavailable. Old usage rows for the account are pruned after 30 days. The `X-Dua-Requests-Remaining` header reports remaining attempts; an exhausted budget returns HTTP 429 and `Retry-After`.

This is a **request-count limit**, not a token, cost or billing cap. A single request can still include several provider or tool operations. Configure provider spending limits separately if supported, and keep personal model use within that provider's free quota.

## After changing deployment environment variables

Redeploy the production project. Confirm email sign-in first, then test recovery with your own account, and only enable an owner restriction or daily request budget after account verification works. Google requires its external provider configuration; deletion and the optional usage migration remain disabled until their server setup exists.
