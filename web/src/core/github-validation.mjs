// Pure repository/path policy for the Vercel connector, Build flows and saved metadata.
// No credentials, HTTP, error messages or filesystem access belong in this module.
export const REPOSITORY_NAME_PATTERN = '[A-Za-z0-9_-]{1,100}/[A-Za-z0-9_.-]{1,100}';
export const GITHUB_REPOSITORY_API_PATTERN = '/repos/' + REPOSITORY_NAME_PATTERN;
export const REPOSITORY = new RegExp('^' + REPOSITORY_NAME_PATTERN + '$');
export const MAX_REPOSITORY_PATH_CHARS = 400;

export const validRepository = value => typeof value === 'string' && REPOSITORY.test(value);

// A GitHub-relative path, not a local filesystem path. Caller-specific normalization
// and strict stored/write policies are applied by the public helpers below.
function validPathSegments(value) {
  return typeof value === 'string' && value.length > 0 && value.length <= MAX_REPOSITORY_PATH_CHARS &&
    !value.startsWith('/') && !/[\\\x00-\x1f\x7f]/.test(value) &&
    !value.split('/').some(part => !part || part === '.' || part === '..');
}

/** Read compatibility: trim and strip one leading ./ before validating/encoding. */
export function normalizeRepositoryReadPath(value) {
  if (typeof value !== 'string') return null;
  const path = value.trim().replace(/^\.\//, '');
  return validPathSegments(path) ? path : null;
}

/** Stored metadata / approved writes must already be canonical, with no drive prefix. */
export function validRepositoryPath(value) {
  return validPathSegments(value) && value === value.trim() && !/^[A-Za-z]:/.test(value);
}

/** Additional write protection, shared by live PR validation and saved proposals. */
export function repositoryWritePathIssue(value) {
  if (!validRepositoryPath(value)) return 'invalid';
  const lower = value.toLowerCase();
  if (value.split('/').some(part => part.toLowerCase() === '.git')) return 'git';
  if (lower === '.gitmodules') return 'submodule';
  if (lower.startsWith('.github/workflows/')) return 'workflow';
  return null;
}
