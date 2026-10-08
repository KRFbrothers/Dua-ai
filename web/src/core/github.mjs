// Read-only GitHub REST client for the personal connector and Build mode (Phase 5B).
// The token (GITHUB_CONNECTOR_TOKEN) stays on this server. Every request is a GET to an allowlisted
// api.github.com path, and nothing in this module can send any other method: Dua never writes to GitHub.
const API = 'https://api.github.com';
/** owner/name, the same shape the repository list returns. */
export const REPOSITORY = /^[A-Za-z0-9_-]{1,100}\/[A-Za-z0-9_.-]{1,100}$/;
const repoPath = '/repos/[A-Za-z0-9_-]{1,100}/[A-Za-z0-9_.-]{1,100}';
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
  if (typeof path !== 'string' || !ALLOWED_GITHUB_PATHS.some(pattern => pattern.test(path))) throw new GitHubError('This GitHub request is not allowed.', 400);
  if (!githubReady(env)) throw new GitHubError('GitHub connector setup is not enabled.', 503);
  const response = await fetcher(API + path, {
    method: 'GET', redirect: 'error', signal: AbortSignal.any([signal || new AbortController().signal, AbortSignal.timeout(15000)]),
    headers: { Authorization: 'Bearer ' + githubToken(env), Accept: 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'Dua-Personal-Connector' }
  });
  if (!response.ok) {
    await response.body?.cancel();
    if (response.status === 404) throw new GitHubError('Not found in this repository, or the connector token cannot read it.', 404);
    if (response.status === 403 || response.status === 429) throw new GitHubError('GitHub access or rate limit prevented this request. Retry in a minute.', 429);
    if (response.status === 401) throw new GitHubError('The GitHub connector token is invalid or expired.', 502);
    throw new GitHubError('GitHub connector request failed.', 502);
  }
  try { return JSON.parse(new TextDecoder().decode(await boundedBytes(response, maxBytes))); }
  catch (error) { throw new GitHubError(error?.message === 'Response too large' ? 'GitHub response is too large.' : 'GitHub returned an invalid response.', 502); }
}

/** Repository-relative file path: no leading slash, no `..`, no control characters. Returns the encoded URL path or null. */
export function encodeRepoPath(value) {
  if (typeof value !== 'string') return null;
  const path = value.trim().replace(/^\.\//, '');
  if (!path || path.length > 400 || path.startsWith('/') || path.includes('\\') || /[\u0000-\u001f\u007f]/.test(path)) return null;
  const segments = path.split('/');
  if (segments.some(segment => !segment || segment === '.' || segment === '..')) return null;
  return segments.map(segment => encodeURIComponent(segment)).join('/');
}
const repoUrl = repository => {
  if (typeof repository !== 'string' || !REPOSITORY.test(repository)) throw new GitHubError('Choose a repository as owner/name.', 400);
  return '/repos/' + repository;
};

/** The owner's 30 most recently updated repositories (affiliation=owner). */
export async function listRepositories(options) {
  const repositories = await githubGet('/user/repos?per_page=30&sort=updated&affiliation=owner', options);
  if (!Array.isArray(repositories) || repositories.length > 30) throw new GitHubError('GitHub returned an invalid response.', 502);
  return repositories.map(repo => {
    if (!repo || !REPOSITORY.test(repo.full_name || '')) throw new GitHubError('GitHub returned an invalid response.', 502);
    return { name: repo.full_name.split('/')[1], fullName: repo.full_name, private: repo.private === true, description: typeof repo.description === 'string' ? repo.description.slice(0, 240) : '', url: 'https://github.com/' + repo.full_name };
  });
}

/** {fullName, defaultBranch, private, description} */
export async function repositoryInfo(repository, options) {
  const repo = await githubGet(repoUrl(repository), { ...options, maxBytes: 256 * 1024 });
  if (!repo || !REPOSITORY.test(repo.full_name || '')) throw new GitHubError('GitHub returned an invalid response.', 502);
  const branch = typeof repo.default_branch === 'string' && /^[A-Za-z0-9._/-]{1,200}$/.test(repo.default_branch) ? repo.default_branch : 'HEAD';
  return { fullName: repo.full_name, defaultBranch: branch, private: repo.private === true, description: typeof repo.description === 'string' ? repo.description.slice(0, 240) : '' };
}

/**
 * File tree of the default branch (`git/trees/HEAD?recursive=1`), trimmed: blobs only, at most `maxEntries`
 * paths (GitHub itself stops at 100,000 entries / 7 MB and sets `truncated`). Returns {files:[{path,size}], total, truncated}.
 */
export async function repositoryTree(repository, { maxEntries = 5000, ...options } = {}) {
  const tree = await githubGet(repoUrl(repository) + '/git/trees/HEAD?recursive=1', { ...options, maxBytes: 8 * 1024 * 1024 });
  if (!tree || !Array.isArray(tree.tree)) throw new GitHubError('GitHub returned an invalid response.', 502);
  const blobs = tree.tree.filter(item => item?.type === 'blob' && typeof item.path === 'string' && item.path.length <= 400);
  return { files: blobs.slice(0, maxEntries).map(item => ({ path: item.path, size: Number.isSafeInteger(item.size) ? item.size : 0 })), total: blobs.length,
    truncated: tree.truncated === true || blobs.length > maxEntries };
}

/**
 * One text file from the default branch (`contents/{path}`), cut to `maxBytes` characters.
 * Returns {path, size, text, truncated}. Directories, binaries and files over GitHub's 1 MB contents limit are refused.
 */
export async function repositoryFile(repository, path, { maxBytes = 100 * 1024, ...options } = {}) {
  const encoded = encodeRepoPath(path);
  if (!encoded) throw new GitHubError('Use a file path relative to the repository root.', 400);
  const file = await githubGet(repoUrl(repository) + '/contents/' + encoded, { ...options, maxBytes: 3 * 1024 * 1024 });
  if (Array.isArray(file) || file?.type !== 'file') throw new GitHubError('That path is a folder, not a file.', 400);
  const size = Number.isSafeInteger(file.size) ? file.size : 0;
  if (file.encoding !== 'base64' || typeof file.content !== 'string') throw new GitHubError(size > 1024 * 1024 ? 'That file is over 1 MB, too large to read here.' : 'That file cannot be read as text.', 413);
  const bytes = Buffer.from(file.content.replace(/\s+/g, ''), 'base64');
  if (bytes.subarray(0, 8000).includes(0)) throw new GitHubError('That file is binary, not text.', 415);
  const text = new TextDecoder('utf-8').decode(bytes).replace(/^\uFEFF/, '');
  const truncated = text.length > maxBytes;
  return { path: typeof file.path === 'string' ? file.path : path, size, text: truncated ? text.slice(0, maxBytes) : text, truncated };
}

/** GitHub code search inside one repository (default branch only; GitHub allows about 10 searches a minute). */
export async function searchRepository(repository, query, options) {
  if (typeof repository !== 'string' || !REPOSITORY.test(repository)) throw new GitHubError('Choose a repository as owner/name.', 400);
  const text = typeof query === 'string' ? query.replace(/[\u0000-\u001f\u007f]/g, ' ').replace(/\b(?:repo|org|user):\S*/gi, ' ').replace(/\s+/g, ' ').trim().slice(0, 200) : '';
  if (!text) throw new GitHubError('Enter something to search for.', 400);
  const q = encodeURIComponent(`${text} repo:${repository}`).replace(/[!'()*]/g, c => '%' + c.charCodeAt(0).toString(16).toUpperCase());
  const result = await githubGet(`/search/code?q=${q}&per_page=20`, { ...options, maxBytes: 2 * 1024 * 1024 });
  const items = Array.isArray(result?.items) ? result.items : [];
  return { total: Number.isSafeInteger(result?.total_count) ? result.total_count : items.length, results: items.slice(0, 20).filter(item => typeof item?.path === 'string').map(item => ({ path: item.path })) };
}
