// GitHub client for Build mode pull requests (Phase 5C). It is ONLY used by the owner-approved
// POST /api/build/pr route (build-pr.mjs), never by the chat or the model's tool loop.
// It authenticates with its own token, GITHUB_BUILD_WRITE_TOKEN (fine-grained, selected repositories, Contents and
// Pull requests read/write), separate from the read-only GITHUB_CONNECTOR_TOKEN.
// The allowlist below is the complete list of requests it can make. It has GET and POST only:
// no PATCH (so no force-push or ref moves), no PUT (no merge) and no DELETE (no branch deletion).
// New refs must be refs/heads/dua/*, and pull requests can only come from a dua/* branch.
import { boundedBytes, GITHUB_REPOSITORY_HINT } from './github.mjs';
import { validRepository, validGitHubApiPath, repositoryWritePathIssue, GITHUB_REPOSITORY_API_PATTERN as repo } from './github-validation.mjs';

const API = 'https://api.github.com';
const sha = '[0-9a-f]{40}(?:[0-9a-f]{24})?';
const branchPath = '[A-Za-z0-9._%-]{1,250}(?:/[A-Za-z0-9._%-]{1,250}){0,9}';
/** [method, pattern]: every request this client can make. */
export const ALLOWED_WRITE_REQUESTS = Object.freeze([
  ['GET', /^\/user$/],
  ['GET', new RegExp(`^${repo}$`)],
  ['GET', new RegExp(`^${repo}/git/ref/heads/${branchPath}$`)],
  ['GET', new RegExp(`^${repo}/git/commits/${sha}$`)],
  ['GET', new RegExp(`^${repo}/git/trees/${sha}$`)],
  ['GET', new RegExp(`^${repo}/pulls\\?head=[A-Za-z0-9_-]{1,100}%3Adua%2F[A-Za-z0-9._%-]{1,200}&state=all&per_page=5$`)],
  ['POST', new RegExp(`^${repo}/git/blobs$`)],
  ['POST', new RegExp(`^${repo}/git/trees$`)],
  ['POST', new RegExp(`^${repo}/git/commits$`)],
  ['POST', new RegExp(`^${repo}/git/refs$`)],
  ['POST', new RegExp(`^${repo}/pulls$`)]
]);

export class GitHubWriteError extends Error {
  constructor(message, status = 502, detail = '') { super(message); this.status = status; this.detail = detail; }
}
export const writeToken = env => typeof env?.GITHUB_BUILD_WRITE_TOKEN === 'string' ? env.GITHUB_BUILD_WRITE_TOKEN.trim() : '';

const MESSAGES = {
  401: ['The GitHub write token (GITHUB_BUILD_WRITE_TOKEN) is invalid or expired. Replace it in backend hosting settings, then redeploy and retry the same proposal.', 502],
  403: ['The GitHub write token was refused. Check selected-repository access and Contents and Pull requests: Read and write permissions for GITHUB_BUILD_WRITE_TOKEN. If rate-limited, wait for the GitHub limit to reset before retrying the same proposal.', 403],
  404: ['Repository, branch or file not found, or the GitHub write token cannot access this repository. Check the repository and selected-repository access for GITHUB_BUILD_WRITE_TOKEN.', 404],
  409: ['GitHub reported a conflict (the repository may be empty or just changed). Ask Dua to regenerate the proposal.', 409],
  422: ['GitHub rejected this change. Review the proposal and repository pull request settings before retrying the same proposal.', 422],
  429: ['GitHub rate limit reached. Wait for the GitHub limit to reset before retrying the same proposal.', 429]
};

/**
 * One allowlisted request with the write token. Returns parsed JSON (bounded). On failure throws GitHubWriteError
 * with an allowlisted message; GitHub's own error text is only kept in `detail` (short, never returned to the app
 * or logged) so callers can tell e.g. "Reference already exists" apart.
 */
export async function githubWrite(method, path, { env, fetcher = fetch, signal, body, maxBytes = 2 * 1024 * 1024, notFound } = {}) {
  if (!validGitHubApiPath(path) || !ALLOWED_WRITE_REQUESTS.some(([allowed, pattern]) => allowed === method && pattern.test(path))) throw new GitHubWriteError('This GitHub request is not allowed.', 400);
  if (method === 'POST' && path.endsWith('/git/trees') &&
      !(Array.isArray(body?.tree) && body.tree.length > 0 && body.tree.every(entry => repositoryWritePathIssue(entry?.path) === null && entry.type === 'blob' && ['100644', '100755'].includes(entry.mode))))
    throw new GitHubWriteError('Git tree changes must use allowed file paths and regular files.', 400);
  if (method === 'POST' && path.endsWith('/git/refs') && !(typeof body?.ref === 'string' && /^refs\/heads\/dua\/[A-Za-z0-9._/-]{1,200}$/.test(body.ref) && !body.ref.includes('..') && Object.keys(body).every(key => key === 'ref' || key === 'sha')))
    throw new GitHubWriteError('Dua can only create new dua/* branches.', 400);
  if (method === 'POST' && path.endsWith('/pulls') && !(typeof body?.head === 'string' && body.head.startsWith('dua/')))
    throw new GitHubWriteError('Pull requests can only come from a dua/* branch.', 400);
  const token = writeToken(env);
  if (!token) throw new GitHubWriteError('Build pull requests are not configured. Set GITHUB_BUILD_WRITE_TOKEN in backend hosting settings, then redeploy.', 503);
  const response = await fetcher(API + path, {
    method, redirect: 'error', signal: AbortSignal.any([signal || new AbortController().signal, AbortSignal.timeout(20000)]),
    headers: { Authorization: 'Bearer ' + token, Accept: 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'Dua-Build-PR',
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
    ...(body === undefined ? {} : { body: JSON.stringify(body) })
  });
  if (!response.ok) {
    let detail = '';
    try { const data = JSON.parse(new TextDecoder().decode(await boundedBytes(response, 64 * 1024))); detail = [data?.message, ...(Array.isArray(data?.errors) ? data.errors.map(e => e?.message || e?.code) : [])].filter(v => typeof v === 'string').join(' ').slice(0, 300); }
    catch { await response.body?.cancel().catch(() => {}); }
    if (response.status === 404 && notFound !== undefined) return notFound;
    const [message, status] = MESSAGES[response.status] || ['GitHub request failed. Check GitHub service availability, then retry the same proposal so Dua can find any existing branch or pull request.', 502];
    throw new GitHubWriteError(message, status, detail);
  }
  try { return JSON.parse(new TextDecoder().decode(await boundedBytes(response, maxBytes))); }
  catch (error) { throw new GitHubWriteError(error?.message === 'Response too large' ? 'GitHub response is too large. Ask Dua for a smaller change; check GitHub for an existing branch or pull request before starting over.' : 'GitHub returned an invalid response. Retry the same proposal so Dua can find any existing branch or pull request.', 502); }
}

export const repoApi = repository => {
  if (!validRepository(repository)) throw new GitHubWriteError(GITHUB_REPOSITORY_HINT, 400);
  return '/repos/' + repository;
};
export const encodeBranch = branch => branch.split('/').map(encodeURIComponent).join('/');
