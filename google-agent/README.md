# Dua GitHub coding agent — Google ADK

This is a separate developer assistant, not the Android app or Dua's chat backend.
It uses Google ADK 2.11.0 and Gemini on Vertex AI. Vercel and Supabase remain in place.
Google Cloud billing/model access, GitHub permissions and a runtime are required;
creating the code does not activate a deployed agent. Agent Platform Studio cannot
automatically edit this repository without these custom tools.

## What is ready

- Read the fixed repository `KRFbrothers/Dua-ai` at a pinned commit.
- Propose up to 12 bounded source files, with explicit ADK tool confirmation.
- Create a new `dua-agent/…` branch and **draft** PR; never write main or merge.
- Dispatch the existing `checks.yml` workflow and read results for the exact commit.
- No shell, APK packaging, deployment, deletion, dependency/workflow editing or secret tools.
- 13 mocked/security/schema tests pass, including actual ADK approve/reject handling.

Not yet verified: a live Gemini call, GitHub token access, Cloud project IAM, managed
deployment and the phone confirmation UI. Confirmations are an experimental ADK
feature; if your client does not support them, writes must remain blocked.
This is a bounded starter, not a complete autonomous replacement for Codex.

## Phone setup: first test through Google Cloud Shell

Cloud Shell runs commands on Google's computer; no local terminal installation is
needed. On a phone, Chrome's Desktop site mode may be necessary.

1. Merge this setup PR into main. In Google Cloud console choose your billing-linked
   project. Copy its **Project ID**, not the display name.
2. Create a small billing budget/alerts under Billing. Alerts do not stop spending.
3. Enable the **Vertex AI API** in APIs & Services. Select a Gemini model available
   to your project; the example `gemini-2.5-flash` is a configurable starting value,
   not a promise of availability in every project/location.
4. On GitHub open Settings → Developer settings → Personal access tokens →
   Fine-grained tokens. Create a short-lived token for **only Dua-ai**, with
   Contents read/write, Pull requests read/write and Actions read/write.
   Do not grant Administration or Workflows. These permissions are still powerful;
   enforce main branch protection separately. Use a dedicated GitHub App later.
   Never paste the token into chat, an issue, source code or a command URL.
5. Click Google Cloud's **Activate Cloud Shell** (`>_`). Run this one line:

   ```sh
   git clone https://github.com/KRFbrothers/Dua-ai.git
   ```

   For the private repository, Git prompts for your GitHub username and the token
   as the password. Password input is hidden. Do not enable plaintext credential storage.
6. Run these lines:

   ```sh
   cd Dua-ai/google-agent
   python3 -m venv .venv
   source .venv/bin/activate
   pip install -r requirements.txt
   cp .env.example dua_coder/.env
   ```

7. In Cloud Shell Editor open `google-agent/dua_coder/.env`. Replace
   `your-project-id` with your actual project ID and enter the GitHub token in
   `DUA_GITHUB_TOKEN`. Leave it in this ignored private file, never commit it.
   The GitHub runtime tools use it; it is not provided to Gemini as a tool argument.
8. Authenticate application-default credentials if needed:

   ```sh
   gcloud auth application-default login
   ```

   Follow the browser login instructions. If prompted for a quota project, set it
   with `gcloud auth application-default set-quota-project YOUR_PROJECT_ID`.
   Your identity needs access to use Vertex AI in that project; a 403 is an IAM/API
   configuration issue, not a reason to put credentials in code.
9. Check the starter, then start the private development UI:

   ```sh
   python -m unittest discover -s tests -v
   adk web --host 127.0.0.1 --port 8080 --max_llm_calls 15 .
   ```

10. Click Cloud Shell **Web Preview → Preview on port 8080**. Select `dua_coder`.
    Keep the preview restricted to your Google account. ADK's development server
    has no built-in authentication; do not publish it on an unauthenticated endpoint.
11. First message: **"Read README and list improvements. Do not change files."**
    Check that only your repository is read. Then request one small source change.
    Review the complete `create_draft_pr` arguments in the confirmation panel.
    Approve only the intended patch. A text message saying "approved" is not a
    replacement for ADK's confirmation response. If no approval UI appears, stop
    and keep the agent read-only until the client supports confirmation.
12. Approve a test dispatch if needed; open the PR/Actions links on GitHub.
    CI runs web tests and Android compilation/unit tests; it does not build an APK.
    Inspect the diff and passing checks, mark the draft ready and merge yourself.

Cloud Shell preview is temporary and may stop when your session ends. Model calls
use billable Vertex AI quotas; GitHub Actions uses your account's runner quotas.
The 15-call per-run limit is not a daily monetary limit. Close the server with
Ctrl+C when finished. Do not deploy the whole Android repository with Buildpacks.

## Agent Platform managed runtime — after the private preview works

Google ADK supports `adk deploy agent_engine`. This is the separate Python
`dua_coder` agent, not `app/` or `web/`. A managed deployment adds runtime cost and
does not by itself provide a phone chat UI supporting tool confirmations.

Before deployment:

1. Choose a supported regional Agent Engine location. Global model routing in the
   preview is not a deployment region; verify the selected model works regionally.
2. Enable the platform/build APIs required by the current Agent Engine deployment
   wizard/CLI. Use the console's documented IAM roles; do not grant Owner merely
   to resolve an error.
3. Create a dedicated runtime service account with Vertex AI usage permission.
4. In **Secret Manager**, create `dua-github-token` containing the GitHub token.
   Grant that runtime service account **Secret Manager Secret Accessor on this
   secret only**. The operator also needs permission to deploy using that identity.
5. Remove the raw token from `dua_coder/.env` and set the reference instead:

   ```text
   DUA_GITHUB_TOKEN_SECRET=projects/YOUR_PROJECT_ID/secrets/dua-github-token/versions/latest
   ```

   Leave `DUA_GITHUB_TOKEN` blank. Runtime code resolves the secret using its Google
   identity; no service-account key JSON is needed. Ensure the deployed environment
   includes the reference and model/project settings, not the raw GitHub token.
6. Review `adk deploy agent_engine --help` and a runtime config with the dedicated
   service account before invoking it. From `google-agent/`, the deployment target is
   `dua_coder`, for example:

   ```sh
   adk deploy agent_engine --project=YOUR_PROJECT_ID --region=YOUR_SUPPORTED_REGION --display_name=dua-coder dua_coder
   ```

   This creates billable resources. The command is a deployment entry point, not
   a fully verified deployment for your account; IAM/runtime settings must be
   resolved first. Keep invocation private and verify the attached service identity.
7. Use a client that round-trips ADK confirmation events. A managed agent resource
   alone is not a public chat app. Until that interface is verified, use the private
   Cloud Shell preview for approved writes; do not disable confirmations as a shortcut.

Reference: [Google ADK documentation](https://google.github.io/adk-docs/), especially
function tool confirmation and Agent Engine deployment. Cloud console labels can
change. The repository includes no automatic cloud deployment or API key creation.

## Errors and limitations

- GitHub 401/403: check token expiry, selected repo and permissions privately.
- Vertex 403: check project, billing, API enablement and identity access.
- Model 404: choose an available Gemini model via `DUA_AGENT_MODEL`.
- Main changed: re-read and approve a new patch; no force push.
- PR creation failed after branch creation: use the returned compare link rather
  than repeating the write and creating another branch.
- Tests pending/failed: never claim passed. Inspect GitHub Actions and the exact SHA.
- Instructions cannot guarantee generated code is correct. Human review matters;
  source can invoke external services, and the token permissions are broader than
  the tool's code restrictions. Protect main and never expose this developer agent
  to ordinary Dua chat users.
