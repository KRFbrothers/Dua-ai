// Read-only GitHub REST client for the personal connector and Build mode (Phase 5B).
// The token (GITHUB_CONNECTOR_TOKEN) stays on this server. Every request is a GET to an allowlisted
// api.github.com path, and nothing in this module can send any other method: Dua never writes to GitHub.
import { validRepository, validGitHubApiPath, validRepositorySearchQuery, normalizeRepositoryReadPath, MAX_REPOSITORY_PATH_CHARS, GITHUB_REPOSITORY_API_PATTERN as repoPath } from './github-validation.mjs';
export { REPOSITORY } from './github-validation.mjs';
const API = 'https://api.github.com';
/** The only requests this client can make. Anything else is refused before reaching the network. */
export const ALLOWED_GITHUB_PATHS = Object.freeze([
  /^\/user\/repos\?per_page=30&sort=updated&affiliation=owner$/,
  new RegExp(`^${repoPath}$`),
  new RegExp(`^${repoPath}/git/trees/HEAD\\?recursive=1$`),
  new RegExp(`^${repoPath}/contents/[A-Za-z0-9%._~!$'()*+,;=:@-][A-Za-z0-9%._~!$'()*+,;=:@/-]{0,1199}$`),
  /^\/search\/code\?q=[A-Za-z0-9%._~+-]{1,600}&per_page=20$/
]);

export class GitHubError extends Error {
  constructor(message, status = 502) { super(message); this.status = status; }
}
export const githubToken = env => typeof env.GITHUB_CONNECTOR_TOKEN === 'string' ? env.GITHUB_CONNECTOR_TOKEN.trim() : '';
export const githubReady = (env = {}) => env.ENABLE_GITHUB_CONNECTOR === '1' && Boolean(githubToken(env));
// Shared recovery copy only; repository/path validation and access rules stay at their existing call sites.
export const GITHUB_SETUP_HINT = 'GitHub connector setup is not enabled. Set ENABLE_GITHUB_CONNECTOR=1 and a read-only GITHUB_CONNECTOR_TOKEN in backend hosting settings, then redeploy.';
export const GITHUB_REPOSITORY_HINT = 'Use owner/name (for example owner/repo), not a GitHub URL.';
export const GITHUB_PATH_HINT = 'Use a file path relative to the repository root, such as README.md. Remove leading slashes, backslashes and empty, . or .. path segments.';

export async function boundedBytes(response, limit) {
  const declared = Number(response.headers.get('content-length'));
  if (declared > limit) { await response.body?.cancel(); throw Error('Response too large'); }
  if (!response.body) return new Uint8Array();
  const reader = response.body.getReader();
  let size = 0;
  const chunks = [];
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > limit) { await reader.cancel(); throw Error('Response too large'); }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
  return bytes;
}

/** GET an allowlisted path and parse bounded JSON. Errors are allowlisted messages; GitHub's text never leaves the server. */
export async function githubGet(path, { env, fetcher = fetch, signal, maxBytes = 1024 * 1024 } = {}) {
  if (!validGitHubApiPath(path) || !ALLOWED_GITHUB_PATHS.some(pattern => pattern.test(path)) ||
      (path.startsWith('/search/code?') && !validRepositorySearchQuery(new URLSearchParams(path.split('?')[1]).get('q'))))
    throw new GitHubError('This GitHub request is not allowed.', 400);
  if (!githubReady(env)) throw new GitHubError(GITHUB_SETUP_HINT, 503);
  const response = await fetcher(API + path, {
    method: 'GET', redirect: 'error', signal: AbortSignal.any([signal || new AbortController().signal, AbortSignal.timeout(15000)]),
    headers: { Authorization: 'Bearer ' + githubToken(env), Accept: 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'Dua-Personal-Connector' }
  });
  if (!response.ok) {
    await response.body?.cancel();
    if (response.status === 404) throw new GitHubError('Not found in this repository, or the connector token cannot read it. Check the repository name/file path and the selected repositories for GITHUB_CONNECTOR_TOKEN.', 404);
    if (response.status === 403) throw new GitHubError('GitHub refused connector access. Check the selected repository and Contents: Read-only permission for GITHUB_CONNECTOR_TOKEN. If rate-limited, wait for the GitHub limit to reset.', 429);
    if (response.status === 429) throw new GitHubError('GitHub rate limit reached. Wait for the GitHub limit to reset before retrying this read.', 429);
    if (response.status === 401) throw new GitHubError('The GitHub connector token is invalid or expired. Replace GITHUB_CONNECTOR_TOKEN in backend hosting settings, then redeploy and retry.', 502);
    throw new GitHubError('GitHub connector request failed. Check GitHub service availability, then retry this repository read.', 502);
  }
  try { return JSON.parse(new TextDecoder().decode(await boundedBytes(response, maxBytes))); }
  catch (error) { throw new GitHubError(error?.message === 'Response too large' ? 'GitHub response is too large. Choose a smaller repository or file.' : 'GitHub returned an invalid response. Retry this repository read; if it persists, check GitHub service availability.', 502); }
}

/** Repository-relative file path: no leading slash, no `..`, no control characters. Returns the encoded URL path or null. */
export function encodeRepoPath(value) {
  const path = normalizeRepositoryReadPath(value);
  return path === null ? null : path.split('/').map(segment => encodeURIComponent(segment)).join('/');
}
const repoUrl = repository => {
  if (!validRepository(repository)) throw new GitHubError(GITHUB_REPOSITORY_HINT, 400);
  return '/repos/' + repository;
};

/** The owner's 30 most recently updated repositories (affiliation=owner). */
export async function listRepositories(options) {
  const repositories = await githubGet('/user/repos?per_page=30&sort=updated&affiliation=owner', options);
  if (!Array.isArray(repositories) || repositories.length > 30) throw new GitHubError('GitHub returned an invalid response. Retry this repository read; if it persists, check GitHub service availability.', 502);
  return repositories.map(repo => {
    if (!repo || !validRepository(repo.full_name)) throw new GitHubError('GitHub returned an invalid response. Retry this repository read; if it persists, check GitHub service availability.', 502);
    return { name: repo.full_name.split('/')[1], fullName: repo.full_name, private: repo.private === true, description: typeof repo.description === 'string' ? repo.description.slice(0, 240) : '', url: 'https://github.com/' + repo.full_name };
  });
}

/** {fullName, defaultBranch, private, description} */
export async function repositoryInfo(repository, options) {
  const repo = await githubGet(repoUrl(repository), { ...options, maxBytes: 256 * 1024 });
  if (!repo || !validRepository(repo.full_name)) throw new GitHubError('GitHub returned an invalid response. Retry this repository read; if it persists, check GitHub service availability.', 502);
  const branch = typeof repo.default_branch === 'string' && /^[A-Za-z0-9._/-]{1,200}$/.test(repo.default_branch) ? repo.default_branch : 'HEAD';
  return { fullName: repo.full_name, defaultBranch: branch, private: repo.private === true, description: typeof repo.description === 'string' ? repo.description.slice(0, 240) : '' };
}

/**
 * File tree of the default branch (`git/trees/HEAD?recursive=1`), trimmed: blobs only, at most `maxEntries`
 * paths (GitHub itself stops at 100,000 entries / 7 MB and sets `truncated`). Returns {files:[{path,size}], total, truncated}.
 */
export async function repositoryTree(repository, { maxEntries = 5000, ...options } = {}) {
  const tree = await githubGet(repoUrl(repository) + '/git/trees/HEAD?recursive=1', { ...options, maxBytes: 8 * 1024 * 1024 });
  if (!tree || !Array.isArray(tree.tree)) throw new GitHubError('GitHub returned an invalid response. Retry this repository read; if it persists, check GitHub service availability.', 502);
  const blobs = tree.tree.filter(item => item?.type === 'blob' && typeof item.path === 'string' && item.path.length <= MAX_REPOSITORY_PATH_CHARS);
  return { files: blobs.slice(0, maxEntries).map(item => ({ path: item.path, size: Number.isSafeInteger(item.size) ? item.size : 0 })), total: blobs.length,
    truncated: tree.truncated === true || blobs.length > maxEntries };
}

/**
 * One text file from the default branch (`contents/{path}`), cut to `maxBytes` characters.
 * Returns {path, size, sha, text, truncated}; `sha` is the blob sha Build pull requests use to detect stale proposals.
 * Directories, binaries and files over GitHub's 1 MB contents limit are refused.
 */
export async function repositoryFile(repository, path, { maxBytes = 100 * 1024, ...options } = {}) {
  const encoded = encodeRepoPath(path);
  if (!encoded) throw new GitHubError(GITHUB_PATH_HINT, 400);
  const file = await githubGet(repoUrl(repository) + '/contents/' + encoded, { ...options, maxBytes: 3 * 1024 * 1024 });
  if (Array.isArray(file) || file?.type !== 'file') throw new GitHubError('That path is a folder, not a file. Choose a text file inside it.', 400);
  const size = Number.isSafeInteger(file.size) ? file.size : 0;
  if (file.encoding !== 'base64' || typeof file.content !== 'string') throw new GitHubError(size > 1024 * 1024 ? 'That file is over 1 MB, too large to read here. Choose a smaller text file.' : 'That file cannot be read as text. Choose a UTF-8 text or source-code file.', 413);
  const bytes = Buffer.from(file.content.replace(/\s+/g, ''), 'base64');
  if (bytes.subarray(0, 8000).includes(0)) throw new GitHubError('That file is binary, not text. Choose a UTF-8 text or source-code file.', 415);
  const text = new TextDecoder('utf-8').decode(bytes).replace(/^\uFEFF/, '');
  const truncated = text.length > maxBytes;
  const sha = typeof file.sha === 'string' && /^[0-9a-f]{40}(?:[0-9a-f]{24})?$/.test(file.sha) ? file.sha : null;
  return { path: typeof file.path === 'string' ? file.path : path, size, sha, text: truncated ? text.slice(0, maxBytes) : text, truncated };
}

/** GitHub code search inside one repository (default branch only; GitHub allows about 10 searches a minute). */
export async function searchRepository(repository, query, options) {
  if (!validRepository(repository)) throw new GitHubError(GITHUB_REPOSITORY_HINT, 400);
  const text = typeof query === 'string' ? query.replace(/[\u0000-\u001f\u007f]/g, ' ').replace(/\b(?:repo|org|user):\S*/gi, ' ').replace(/\s+/g, ' ').trim().slice(0, 200) : '';
  if (!text) throw new GitHubError('Enter something to search for.', 400);
  if (!validRepositorySearchQuery(`${text} repo:${repository}`)) throw new GitHubError('Use plain search terms without quotes, parentheses, backslashes or OR/NOT operators.', 400);
  const q = encodeURIComponent(`${text} repo:${repository}`).replace(/[!'()*]/g, c => '%' + c.charCodeAt(0).toString(16).toUpperCase());
  const result = await githubGet(`/search/code?q=${q}&per_page=20`, { ...options, maxBytes: 2 * 1024 * 1024 });
  const items = Array.isArray(result?.items) ? result.items : [];
  if (items.some(item => !validRepository(item?.repository?.full_name) || item.repository.full_name.toLowerCase() !== repository.toLowerCase() || typeof item.path !== 'string' || normalizeRepositoryReadPath(item.path) !== item.path))
    throw new GitHubError('GitHub returned search results outside the selected repository or with invalid paths.', 502);
  return { total: Number.isSafeInteger(result?.total_count) ? result.total_count : items.length, results: items.slice(0, 20).filter(item => typeof item?.path === 'string').map(item => ({ path: item.path })) };
}
