# GitHub connector and Build recovery

This guide covers the Node API's (dua-api) read-only connector and owner-confirmed Build PR flow. The [Cloud Run coding agent](../google-cloud-backend/README.md#coding-agent-setup-phase-2-optional) has a separate token, configuration and implementation. See [architecture boundaries](ARCHITECTURE.md#github-permission-boundaries) before changing either flow.

## Common failures

| Failure / example message | Recovery |
| --- | --- |
| `Use owner/name (for example owner/repo), not a GitHub URL.` | Select the repository or enter its two-part name, e.g. `owner/repo`. Do not paste `https://github.com/owner/repo` or a file URL into the repository field. |
| `Use a file path relative to the repository root, such as README.md.` | Choose an actual file from the repository tree. Use `src/example.kt` rather than an absolute/device path; remove empty or `.` / `..` segments and backslashes. Protected `.git`, `.gitmodules` and workflow files must be removed from a write proposal. |
| `GitHub connector setup is not enabled.` | In the backend hosting environment, set `ENABLE_GITHUB_CONNECTOR=1` and `GITHUB_CONNECTOR_TOKEN`, then redeploy. The token needs selected-repository access and read-only Contents/Metadata. Never use the write token as a replacement for the read token. |
| `The GitHub connector token is invalid or expired.` | Replace `GITHUB_CONNECTOR_TOKEN` in hosting settings and redeploy. This is the backend's GitHub credential, not the owner's Dua/Supabase login; signing out of the app does not replace it. |
| `GitHub refused connector access.` / `Not found in this repository...` | Check the repository name/file path and token's selected repositories, permissions and organization approval requirements. A private repository can appear missing when the token cannot access it. If GitHub rate-limited the request, wait for its limit to reset. |
| `GitHub rate limit reached.` | Wait for the GitHub limit to reset, then retry. There is no guaranteed one-minute reset, automatic retry or automatic permission expansion. |
| `GitHub response is too large.` / `That file is over 1 MB...` | Use a smaller repository/file for reads. Ask for a smaller change if the PR response/change is too large. Check GitHub for an already-created branch/PR before starting a new proposal. |
| `GitHub returned an invalid response.` / connection failure | Check the connection and GitHub service availability. Retry a repository read; for PR creation, retry the **same proposal** so the backend can find an existing branch/PR. Raw GitHub bodies and token values are not included in public error messages. |
| Build pull requests need `GITHUB_BUILD_WRITE_TOKEN` | Configure the separate fine-grained write token for selected repositories with Contents and Pull requests set to Read and write. Enable `DUA_BUILD_PR=1` only when you intend to allow the owner-confirmed write flow; complete owner Auth setup and redeploy. See [Build PR setup](../PROVIDERS.md#build-mode-pull-requests-after-approval-phase-5c). |
| `The GitHub write token ... is invalid or expired.` / `The GitHub write token was refused.` | Replace or correct `GITHUB_BUILD_WRITE_TOKEN` in hosting settings, redeploy, then retry the same proposal. Check selected-repository access and the two write permissions; if rate-limited, wait for reset. Do not grant Workflows/Admin permissions to bypass blocked file paths. |
| `The proposal is out of date: ask Dua to regenerate it.` | The proposal's saved file version no longer matches the default branch. Ask for a fresh proposal and review its diff again before confirming. An unrelated existing branch also requires a fresh proposal; do not overwrite it. |
| `GitHub refused to open the pull request.` | Check repository PR settings, then retry the same proposal to reuse its branch. A failed request may have created a commit/branch already. Do not assume that an error means nothing was written. |

For a local Node server, update the ignored `web/.env` and restart `npm start` instead of redeploying. Start from [web/.env.example](../web/.env.example) without overwriting an existing local file. Never paste tokens into chat, screenshots, source or test fixtures.

## Maintainer notes

Messages follow **what failed + the next recovery step**. The implementation keeps the existing public status mappings and authentication/validation boundaries; a message is not a new machine-readable error code. In particular, the existing read client maps GitHub 403 and 429 to its rate/access error status, and callers retain their existing HTTP mappings.

Recovery copy lives in [github.mjs](../web/src/core/github.mjs), [github-write.mjs](../web/src/core/github-write.mjs) and [build-pr.mjs](../web/src/core/build-pr.mjs). Shared repository/path/setup hints also reach the [connector route](../web/src/core/advanced-integrations.mjs) and [Build request validation](../web/src/core/providers.mjs). Request method/path allowlists, owner checks, limits, file-version checks, branch prefixes and confirmation behavior are unchanged.

Mocked tests in [build-chat.test.mjs](../web/test/build-chat.test.mjs) and [build-pr.test.mjs](../web/test/build-pr.test.mjs) cover setup/input guidance, HTTP failures, hidden upstream details, unchanged status mappings and no extra retries/writes. Run from `web/`:

```sh
node --test test/build-chat.test.mjs test/build-pr.test.mjs
npm run test:ci
```
