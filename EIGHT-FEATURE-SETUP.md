# Eight feature groups: setup and activation

The code prepares all eight groups, with an explicit status screen on web and Android. It does not enable a paid provider, create keys, change your Vercel variables, or execute an account deletion. Existing Gemini and Supabase settings keep working.

Open **Settings → Capabilities / Feature setup** after deploying. The page reports configuration, rather than claiming that an untested external service is working. The production backend is `https://dua-cyan.vercel.app`; Android uses the same backend after rebuilding.

| Group | Setup included | Activate | Still outside this setup |
| --- | --- | --- | --- |
| AI providers | Gemini plus text adapters for ChatGPT, Claude, Grok and Kimi; configured model picker on both clients | Add that provider's API key and model ID, redeploy | Live provider verification; new providers' attachments |
| Web search | Explicit Brave queries and source links on both clients | Add `BRAVE_SEARCH_API_KEY`, redeploy, submit Search | Automatic search during AI chat and webpage analysis |
| Automatic sync | Web foreground opt-in snapshot coordinator; native policy helpers and manual snapshots | Sign in, review existing data, enable automatic sync on the website | Native automatic scheduler and background/realtime sync |
| Memory | Local proposals from explicit remember/yaad rakho statements, individual Save/Dismiss on both clients | Enable proposals on web or choose Suggest memory notes on Android; approve notes | Unprompted model extraction, embeddings and automatic fact learning |
| Connectors/plugins | Allowlisted read-only GitHub repository listing on both clients | Configure token, owner email and enable flag; sign in | OAuth connectors, arbitrary plugins or write tools |
| Computer/screen/video | Browser local camera and desktop screen previews with explicit permission and Stop | Open Feature setup → Camera and screen preview | Native capture, video analysis, remote computer control |
| Advanced voice | Device speech remains; optional explicit generated audio on web through ElevenLabs | Set voice key/ID, enable flag and owner email; sign in | Native generated audio UI, full duplex, automatic interruption, background voice |
| Account/protection | Email recovery OTP, password update, Google web PKCE, explicit account deletion, optional owner/login policy and daily request quota | See account steps below | Native Google callback/session handoff, currency spending cap |

## Deployment

1. Merge the reply-icon PR first, then the setup PR. The setup branch starts from the reply-icon branch.
2. Let the GitHub-connected Vercel project deploy. Use the production domain, rather than a protected preview URL.
3. Check `/api/setup` or the app's Feature setup screen. This response never contains API keys or the allowed owner's email.
4. Install a newly built Android APK for native changes. Updating the website does not replace an installed APK.

The new setup endpoints use the Vercel/local Node handler. The legacy Netlify functions retain their earlier Gemini-only behavior; use the Vercel production project for these integrations. There are nine Vercel functions, within the current Hobby project's function-count limit.

## Start with the existing services

You already created the Supabase cloud table, configured Gmail SMTP, signed in and uploaded a manual snapshot. No additional service is needed for web automatic sync, reviewed local memory proposals, or email recovery.

- **Automatic sync:** Cloud sync → enable on this device. Consent is bound to the signed-in account. It uploads saved chats, drafts and memory notes; file/audio bytes are excluded. Switching accounts, deletions, conflicting edits or failed storage pause it. Use the manual cloud actions to inspect data before choosing Resume. It runs while a browser page is visible, rather than after the app closes. See [AUTOMATION-SETUP.md](AUTOMATION-SETUP.md).
- **Memory:** Memory → enable suggestions → Find suggestions. Only explicit remember statements create proposals. Save each note you want. Separately enable “Use local memory in replies” if notes should be sent with prompts. On Android, Feature setup → Suggest memory notes scans the current conversation.
- **Recovery:** Supabase Authentication → Emails → Reset Password template must include `{{ .Token }}`. In Dua Profile enter the account email, request a reset email, then enter its code and choose a new password. The existing SMTP is reused; no password or OTP belongs in GitHub.
- **Personal access:** After sign-in is working, optionally set `DUA_REQUIRE_LOGIN=1` and `DUA_ALLOWED_EMAIL` to your own login email. Redeploy. Local history remains available when online access is denied.

## Optional server variables

Use **Vercel project → Environment Variables**. Add only the variables for a feature you choose; preserve existing working values. Production variables require a new deployment. The checked-in [web/.env.example](web/.env.example) contains names and empty placeholders, never working secrets.

| Feature | Variables |
| --- | --- |
| ChatGPT | `OPENAI_API_KEY`, `OPENAI_MODEL` |
| Claude | `ANTHROPIC_API_KEY`, `ANTHROPIC_MODEL` |
| Grok | `GROK_API_KEY`, `GROK_MODEL` |
| Kimi | `KIMI_API_KEY`, `KIMI_MODEL` |
| Search | `BRAVE_SEARCH_API_KEY` |
| Google web sign-in | `SUPABASE_AUTH_REDIRECT_URL=https://dua-cyan.vercel.app/api/account/oauth/callback`; enable Google in Supabase and configure its Google callback |
| Account deletion | Server-only `SUPABASE_SERVICE_ROLE_KEY` |
| Daily request limit | `DUA_DAILY_REQUEST_LIMIT` (e.g. `50`), server-only `SUPABASE_SERVICE_ROLE_KEY`; run the usage SQL below |
| GitHub repository list | `ENABLE_GITHUB_CONNECTOR=1`, `GITHUB_CONNECTOR_TOKEN`, `DUA_ALLOWED_EMAIL` |
| Generated audio | `ENABLE_GENERATED_VOICE=1`, `ELEVENLABS_API_KEY`, `ELEVENLABS_VOICE_ID`, `DUA_ALLOWED_EMAIL` |

Provider model values must be API model IDs available to your account. Keep API secrets in server settings; never put them in browser code, Android, screenshots or chat. Consumer chat subscriptions are separate from API access. No new paid provider or key was provisioned; OpenAI key setup remains deferred as requested.

GitHub and generated audio stay inaccessible until an allowed personal owner is configured and signed in. Revoke a connector by disabling its flag and revoking the token at GitHub. See [PROVIDERS.md](PROVIDERS.md), [ACCOUNT-SETUP.md](ACCOUNT-SETUP.md) and [ADVANCED-SETUP.md](ADVANCED-SETUP.md) for service-specific steps.

## Optional database migration

The original `202610040001_dua_cloud.sql` is already applied in your project. Automatic web snapshots and recovery do not need another table. If you choose a durable daily request quota, run [supabase/migrations/202610040002_dua_usage.sql](supabase/migrations/202610040002_dua_usage.sql) in Supabase SQL Editor, then add the quota variables above and redeploy. This migration creates usage rows with account ownership and a server-only atomic request reservation.

The quota counts attempts shared by chat, search and optional online integrations. Failed providers consume attempts. It is a request count, rather than a token or currency budget. Provider-side spending controls remain separate. Account deletion requires typing **DELETE MY ACCOUNT** and binds the request to the account shown; it deletes cloud account data while keeping local chats and notes.

## Validation

Provider, search, auth, quotas, sync and media tests use mocked external services. No live email, external provider, connector, billing or deletion request was sent during development. Verify one optional service at a time after activation. Physical Android speech, permissions, installed voices, session recovery and deployment behavior still need device verification.
