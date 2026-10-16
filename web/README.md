# Node API on Cloud Run (migration step 1)

This runs the existing `web/` Node API as a separate **dua-api** service in
**us-central1**, next to the Python `dua-ai-engine`. It does not
switch Android URLs or move Python/Vertex features into Node.

## Build and run

Use Node 24. Local development remains `npm ci && npm start` on
`127.0.0.1:3000` (or your `PORT`); `npm start` loads ignored `.env` settings.
From `web/`, build and smoke-test the production container:

```sh
docker build -t dua-api .
docker run --rm -p 8080:8080 dua-api
# In another terminal:
curl --fail-with-body http://localhost:8080/api/config
curl --fail-with-body http://localhost:8080/api/setup
```

The image uses Node 24, locked production dependencies and the non-root `node`
user. It starts `server.mjs` directly, without reading `.env`. `NODE_ENV=production`
or Cloud Run's `K_SERVICE` selects `0.0.0.0` and default port `8080`; `PORT` overrides
the port. Both Docker and Cloud Build contexts allow only runtime inputs, keeping
`.env`, credentials, tests and local dependencies out of uploads/images.

## Deploy (Google Cloud Console + Cloud Shell)

1. Select your Google Cloud project with billing enabled. Run the deployment as
   its Owner (or someone with equivalent API, IAM, build and deployment permissions).
2. In **Security → Secret Manager → Create secret**, create the following names
   and paste their existing backend values into the secret value boxes:

   | Secret name | Value / when needed |
   | --- | --- |
   | `SUPABASE_URL` | Required: HTTPS project root, without `/rest/v1/`. |
   | `SUPABASE_ANON_KEY` | Required: same public anon/publishable credential used by the Node backend. |
   | `DUA_ALLOWED_EMAIL` | Required: your allowed owner's email. |
   | `GEMINI_API_KEY` + `GEMINI_MODEL` | For Gemini chat: your API key and an available model ID. Alternatively create the key/model pair for the provider you use from `.env.example`. Python's Vertex credentials are not a Node provider API key. |
   | `SUPABASE_SERVICE_ROLE_KEY` | Optional admin credential: account deletion and durable daily limits. |
   | `DUA_FILES_BUCKET` + `DUA_FILES` | For existing stored files: private bucket name and `1`, **after** the Storage migration/bucket from [SUPABASE.md](../SUPABASE.md) is ready. Files default off in this deployment. |

   Other active or commented `NAME=value` settings in [.env.example](.env.example),
   such as provider keys/models, `BRAVE_SEARCH_API_KEY`, voice settings and GitHub
   tokens/flags, can be created with the **same secret name as the variable**.
   Only existing matching secrets are attached; unrelated secrets are ignored.
   Never upload `.env`, provider keys or a service-account JSON file into this repo.
3. Open **Cloud Shell**, get this PR's merged code (or check out its branch to test),
   open the repository folder, and run:

   ```sh
   export PROJECT_ID=your-google-cloud-project-id
   bash web/deploy-cloudrun.sh
   ```

   The script enables required APIs, creates `dua-api` (runtime) and `dua-api-build`
   service accounts, grants the build account `roles/cloudbuild.builds.builder`,
   grants the runtime account access to each selected secret only, creates the
   `dua-api` Docker repository, builds the image, and deploys it. No manual Docker
   installation, environment-value copying into commands, or Python deployment is needed.
   Build logs use Cloud Logging. The runtime account receives no project-wide admin role.
4. Copy the **dua-api URL** printed at the end and test it:

   ```sh
   DUA_API_URL=https://YOUR-DUA-API-SERVICE-URL
   curl --fail-with-body "$DUA_API_URL/api/config"
   curl --fail-with-body "$DUA_API_URL/api/setup"
   ```

   Both should return JSON with HTTP 200. `/api/setup` should show
   `account.requireLogin: true`, `account.ownerRestricted: true`, and the providers
   you configured. These are configuration checks, not proof of a live authenticated
   AI reply. The root `/` intentionally returns 404. Chat/account/file operations keep
   their existing application authentication; public Cloud Run invocation allows
   clients to reach that authentication layer without a Google IAM identity token.

Cloud Run uses a **900-second** request timeout and **zero minimum instances**
(scale to zero; cold starts are expected). The script sets
`DUA_CHAT_DEADLINE_SECONDS=780`, within the existing 10–790 range and above the
local default of 280. To choose another value, prefix the deploy command with
`DUA_CHAT_DEADLINE_SECONDS=600`. `PORT`, `DUA_REQUIRE_LOGIN` and the deadline are
deployment controls, not Secret Manager imports; login is always required here.

Re-run the script after code or secret changes. Secret versions are pinned per
deployment; creating a new version alone does not update an existing revision.
Redeployment replaces **dua-api's** environment and secret bindings, so remove an
optional secret from Secret Manager (or disable its flag) before redeploying to stop
using it. Optional file/GitHub/write/generated-voice flags default off unless their
named secret explicitly enables them. Existing Supabase migrations and provider
accounts are reused; this script does not create or migrate application data.

### Android feature hubs

The native Library/Images screens use the additive owner-only `GET /api/files/list` route for private cloud inventory. It returns bounded file metadata without signed links; existing `/api/files/url` resolves a selected file on demand. Redeploy this Node service after merging the feature hubs; no new secrets or migration are required. Older deployments keep chat/project references usable and the app shows a cloud-inventory retry/update notice. See [feature hub behavior and validation](../docs/FEATURE_HUBS.md).
