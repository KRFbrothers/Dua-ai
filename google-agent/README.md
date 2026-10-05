# Dua coding coordinator + independent reviewer — Google ADK

This is a separate developer assistant, not the Android app or Dua's chat backend.
It uses Google ADK 2.11.0 and Gemini on Vertex AI. Vercel and Supabase remain in place.
Google Cloud billing/model access, GitHub permissions and a runtime are required;
creating the code does not activate a deployed agent. Agent Platform Studio cannot
automatically edit this repository without these custom tools.

## What is ready

- Read the fixed repository `KRFbrothers/Dua-ai` at a pinned commit.
- Store up to 12 bounded source files in an immutable proposal and show a focused diff.
- Run a separate Reviewer with structured findings, no write tools and no user approval power.
- Create a new `dua-agent/…` branch and **draft** PR only after ready review and
  framework confirmation of the matching proposal hash/diff; never write main or merge.
- Dispatch the existing `checks.yml` workflow and read results for the exact commit.
- No shell, APK packaging, deployment, deletion, dependency/workflow editing or secret tools.
- Per-invocation model/tool/token scheduling budgets shared with the Reviewer.
- 35 mocked/security/schema/orchestration tests pass, including real ADK child-agent
  execution, shared usage, confirmation rejection/approval and duplicate-write guards.
- Agent tests have a separate credential-free GitHub Actions workflow.

The original single-agent preview has produced live Gemini replies and GitHub reads
in the owner's session. The upgraded two-agent flow has not yet been verified with
live credentials or the phone UI. Authentication currently needs refresh in that
session. Managed deployment remains unverified. Confirmations are an experimental ADK
feature; if your client does not support them, writes must remain blocked.
This is the first multi-agent milestone: Coordinator + Reviewer, run sequentially.
Android/Web specialist workers, parallel file ownership, a separate validation agent
and automated failing-log diagnosis remain future work. No automatic self-upgrades.

## Update the existing phone workspace after merging

Stop your current server with Ctrl+C. Keep `dua_coder/.env` private and unchanged.
In the owner's existing Cloud Shell checkout, run each command separately:

```sh
cd ~/Dua-agent-setup
git fetch https://github.com/KRFbrothers/Dua-ai.git main
git merge --ff-only FETCH_HEAD
cd google-agent
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python -m unittest discover -s tests -v
```

The fetch may prompt for GitHub username/token; finish those prompts before pasting
the next command. If merge refuses because of local changes/divergence, stop and
inspect it; do not delete the checkout or force-reset to make it pass.

Copy the current Cloud Shell preview URL, remove `/dev-ui/` and any query string,
and choose its matching port. Start using your actual origin, for example:

```sh
.venv/bin/python start_agent.py --port 8081 --preview-origin https://8081-YOUR-PREVIEW-HOST.cloudshell.dev
```

This is a placeholder: replace the origin with your own exact preview address.
The launcher keeps loopback binding, loads the ignored settings file, checks Google
credential refresh and adds that literal origin to ADK. It does not grant IAM or
disable origin protection. Port-in-use errors mean an existing server must be stopped
or another matching port/origin chosen. Existing sessions may contain old tool calls;
start **New Session** after an upgrade.

## Phone setup: first test through Google Cloud Shell

Cloud Shell runs commands on Google's computer; no local terminal installation is
needed. On a phone, Chrome's Desktop site mode may be necessary.

Your selected project ID is **`global-healer-m6shk`**; `.env.example` already
contains it. Its billing, enabled APIs and IAM have not been checked from this session.

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
   .venv/bin/python -m pip install -r requirements.txt
   cp .env.example dua_coder/.env
   ```

7. In Cloud Shell Editor open `google-agent/dua_coder/.env`. Replace
   the project value if you choose another project, and enter the GitHub token in
   `DUA_GITHUB_TOKEN`. Leave it in this ignored private file, never commit it.
   The GitHub runtime tools use it; it is not provided to Gemini as a tool argument.
8. Authenticate application-default credentials if needed:

   ```sh
   gcloud auth application-default login
   ```

   Follow the browser login instructions. If prompted for a quota project, set it
   with `gcloud auth application-default set-quota-project YOUR_PROJECT_ID`.
   If Cloud Shell says credentials are already available but refresh fails with
   metadata/service-account errors, choose **Y** to finish explicit application-default
   browser login. Do not publish its verification code. Then set the quota project.
   Your identity needs access to use Vertex AI in that project; a 403 is an IAM/API
   configuration issue, not a reason to put credentials in code.
9. Check the starter, then start the private development UI:

   ```sh
   .venv/bin/python -m unittest discover -s tests -v
   .venv/bin/python start_agent.py --port 8081 --preview-origin https://8081-YOUR-PREVIEW-HOST.cloudshell.dev
   ```

   Replace that preview placeholder with your actual origin. You can open Cloud Shell
   Web Preview on 8081 first to copy its address, then start the server and refresh.
10. Click Cloud Shell **Web Preview → Change port → 8081 → Preview**. Select `dua_coder`.
    Keep the preview restricted to your Google account. ADK's development server
    has no built-in authentication; do not publish it on an unauthenticated endpoint.
11. First message: **"Read README and list improvements. Do not change files."**
    Check that only your repository is read. Then request one small source change.
    The coordinator prepares a stored proposal and asks the independent Reviewer to
    check it. Inspect its findings and the focused diff. Review the `publish_proposal`
    hash/diff arguments in the confirmation panel.
    Approve only the intended patch. A text message saying "approved" is not a
    replacement for ADK's confirmation response. If no approval UI appears, stop
    and keep the agent read-only until the client supports confirmation.
12. Approve a test dispatch only if no suitable PR-triggered run exists; open the PR/Actions links on GitHub.
    CI runs web tests and Android compilation/unit tests; it does not build an APK.
    Inspect the diff and passing checks, mark the draft ready and merge yourself.

Cloud Shell preview is temporary and may stop when your session ends. Model calls
use billable Vertex AI quotas; GitHub Actions uses your account's runner quotas.
The callbacks schedule at most 15 model calls and 40 tool calls per invocation,
including the reviewer, and stop scheduling after 60,000 reported tokens or 10 minutes.
These are not hard monetary limits: an in-flight call can exceed the token threshold,
unreported/failed calls are not fully accounted, and the deadline is checked before
new work rather than cancelling all active requests. Model output is capped at 8192
tokens, provider HTTP attempts at two and reviewer attempts at two per proposal.
New messages start a new invocation budget. No daily spending cap is implemented.
Close the server with
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
- Proposals expire after one hour; prepare a fresh pinned proposal rather than
  approving old content. At most four proposals are stored per session; split tasks.
- Review failure/blocking finding: publishing is blocked; at most two review attempts.
- A proposal's deterministic branch protects against repeated writes across sessions.
  A `write_unknown` result requires manual GitHub inspection; it is not retried.
- PR creation failed after branch creation: use the returned compare link rather
  than repeating the write and creating another branch.
- Tests pending/failed: never claim passed. Inspect GitHub Actions and the exact SHA.
- Instructions cannot guarantee generated code is correct. Human review matters;
  source can invoke external services, and the token permissions are broader than
  the tool's code restrictions. Protect main and never expose this developer agent
  to ordinary Dua chat users.
- Main branch protection is a separate GitHub plan/settings concern. The code has
  no main-push/merge tools, but its runtime token permissions are broader than those
  tool restrictions. No new scopes or third-party MCP server are required here.
