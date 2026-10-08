// Build mode pull requests (Phase 5C). Two halves that never meet:
//  1. The model's `propose_changes` tool (called from the Build loop in build-chat.mjs) only VALIDATES a proposal
//     and returns it to the app. It has no token and no network access; nothing in it can write anywhere.
//  2. POST /api/build/pr (handleBuildPr) runs only when the owner taps Create PR and confirms in the app. It is
//     owner-only (Supabase sign-in + DUA_ALLOWED_EMAIL), re-checks every rule, refuses stale proposals, and uses the
//     separate GITHUB_BUILD_WRITE_TOKEN through github-write.mjs (GET/POST allowlist: no force-push, merge or delete).
import { randomUUID } from 'node:crypto';
import { envInteger } from './adapters.mjs';
import { readJsonLimited } from './limited-json.mjs';
import { REPOSITORY } from './github.mjs';
import { encodeBranch, githubWrite, GitHubWriteError, repoApi, writeToken } from './github-write.mjs';
import { resolveOwner, StorageError } from './storage.mjs';

const noStore = { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' };
const json = (value, status = 200) => Response.json(value, { status, headers: noStore });
const off = value => String(value ?? '').trim() === '0';
export const DIFF_MAX_CHARS = 60000;
const MAX_TREE_FETCHES = 60;
const MAX_TITLE = 120;
const MAX_SUMMARY = 4000;
export const PROPOSAL_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
export const PR_BRANCH = /^dua\/[a-z0-9](?:[a-z0-9-]{0,70}[a-z0-9])?$/;
const SHA = /^[0-9a-f]{40}(?:[0-9a-f]{24})?$/;
const CONTROL = /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/;

export class BuildPrError extends Error {
  constructor(message, status = 400) { super(message); this.status = status; }
}

/** Caps for one proposal / pull request (optional env vars). */
export function prLimits(env = {}) {
  return {
    maxFiles: envInteger(env, 'DUA_BUILD_PR_MAX_FILES', 20, 1, 50),
    fileMaxBytes: envInteger(env, 'DUA_BUILD_PR_FILE_MAX_KB', 200, 1, 1000) * 1024,
    totalMaxBytes: envInteger(env, 'DUA_BUILD_PR_MAX_KB', 1024, 1, 3000) * 1024
  };
}
/** DUA_BUILD_PR_REPOS: comma-separated owner/repo (case-insensitive), or null when unset (then: repositories owned by the write token's account). */
export function prRepositories(env = {}) {
  const list = String(env.DUA_BUILD_PR_REPOS ?? '').split(',').map(item => item.trim()).filter(Boolean);
  return list.length ? list.filter(item => REPOSITORY.test(item)).map(item => item.toLowerCase()) : null;
}
export const prDraft = (env = {}) => !off(env.DUA_BUILD_PR_DRAFT);

/** Public status for /api/config (`build.pr`): never the token or the repository list, always a reason when off. */
export function buildPrConfiguration(env = {}) {
  const reason = off(env.DUA_BUILD) ? 'Build mode is turned off on this server (DUA_BUILD=0).'
    : String(env.DUA_BUILD_PR ?? '').trim() !== '1' ? 'Pull requests from Build mode are off. To allow them, set DUA_BUILD_PR=1 and GITHUB_BUILD_WRITE_TOKEN in hosting settings.'
    : !writeToken(env) ? 'Build pull requests need GITHUB_BUILD_WRITE_TOKEN: a fine-grained token for selected repositories with Contents and Pull requests set to Read and write.'
    : !String(env.DUA_ALLOWED_EMAIL || '').trim() ? 'Build pull requests are owner-only: set DUA_ALLOWED_EMAIL to your personal account in hosting settings.'
    : !String(env.SUPABASE_URL || '').trim() || !String(env.SUPABASE_ANON_KEY || '').trim() ? 'Build pull requests need sign-in: set SUPABASE_URL and SUPABASE_ANON_KEY in hosting settings.'
    : null;
  const { maxFiles, fileMaxBytes, totalMaxBytes } = prLimits(env);
  return { enabled: !reason, ...(reason ? { reason } : {}), approval: 'owner', draft: prDraft(env), restricted: prRepositories(env) !== null,
    maxFiles, fileMaxKb: fileMaxBytes / 1024, totalMaxKb: totalMaxBytes / 1024 };
}
/** Whether the model may propose changes for this repository (the create route re-checks, including the token owner). */
export function prAllowedFor(env, repository) {
  if (!buildPrConfiguration(env).enabled || typeof repository !== 'string') return false;
  const list = prRepositories(env);
  return !list || list.includes(repository.toLowerCase());
}

/** Why a repository path cannot be changed, or null. Blocks workflows, .git, .gitmodules, absolute and `..` paths. */
export function pathProblem(path) {
  if (typeof path !== 'string' || !path || path.length > 400) return 'Use a file path relative to the repository root.';
  if (path !== path.trim() || path.startsWith('/') || path.includes('\\') || /^[A-Za-z]:/.test(path) || CONTROL.test(path) || /[\r\n\t]/.test(path)) return `"${path.slice(0, 80)}" is not a safe repository path.`;
  const segments = path.split('/');
  if (segments.some(segment => !segment || segment === '.' || segment === '..')) return `"${path.slice(0, 80)}" is not a safe repository path (empty, "." or ".." segment).`;
  const lower = path.toLowerCase();
  if (segments.some(segment => segment.toLowerCase() === '.git')) return 'Files inside .git cannot be changed.';
  if (lower === '.gitmodules') return 'Submodules (.gitmodules) cannot be changed from Dua.';
  if (lower.startsWith('.github/workflows/')) return 'GitHub Actions workflows (.github/workflows) cannot be changed from Dua.';
  return null;
}
const bytes = text => Buffer.byteLength(text, 'utf8');

/**
 * Validates a list of changes [{path, action: create|update|delete, content?, baseSha?}] against the path rules and caps.
 * `requireSha`: update/delete must carry the blob sha they were based on (the create route). Returns normalized changes.
 */
export function checkChanges(changes, limits, { requireSha = false } = {}) {
  if (!Array.isArray(changes) || !changes.length) throw new BuildPrError('A proposal needs at least one file change.');
  if (changes.length > limits.maxFiles) throw new BuildPrError(`Too many files in one proposal (${changes.length}; the limit is ${limits.maxFiles}).`, 413);
  const seen = new Set();
  let total = 0;
  const normalized = changes.map(change => {
    if (!change || typeof change !== 'object' || Array.isArray(change)) throw new BuildPrError('Invalid file change.');
    const allowed = requireSha ? ['path', 'action', 'content', 'baseSha'] : ['path', 'action', 'content'];
    if (Object.keys(change).some(key => !allowed.includes(key))) throw new BuildPrError('Invalid file change.');
    const { path, action, content } = change;
    const problem = pathProblem(path);
    if (problem) throw new BuildPrError(problem);
    if (!['create', 'update', 'delete'].includes(action)) throw new BuildPrError(`Choose create, update or delete for ${path}.`);
    if (seen.has(path)) throw new BuildPrError(`${path} appears twice in the proposal.`);
    seen.add(path);
    if (action === 'delete') { if (content !== undefined && content !== null) throw new BuildPrError(`A deleted file has no content (${path}).`); }
    else {
      if (typeof content !== 'string') throw new BuildPrError(`Give the complete new content for ${path}.`);
      if (content.includes('\u0000')) throw new BuildPrError(`${path} looks binary. Only text files can be proposed.`, 415);
      const size = bytes(content);
      if (size > limits.fileMaxBytes) throw new BuildPrError(`${path} is ${Math.ceil(size / 1024)} KB; the per-file limit is ${limits.fileMaxBytes / 1024} KB.`, 413);
      total += size;
    }
    if (requireSha && action !== 'create' && !(typeof change.baseSha === 'string' && SHA.test(change.baseSha))) throw new BuildPrError(`${path} is missing the version it was based on. Ask Dua to regenerate the proposal.`);
    if (requireSha && action === 'create' && change.baseSha !== undefined && change.baseSha !== null) throw new BuildPrError('Invalid file change.');
    return { path, action, ...(action === 'delete' ? {} : { content }), ...(requireSha && action !== 'create' ? { baseSha: change.baseSha } : {}) };
  });
  if (total > limits.totalMaxBytes) throw new BuildPrError(`The proposal is ${Math.ceil(total / 1024)} KB in total; the limit is ${limits.totalMaxBytes / 1024} KB.`, 413);
  const paths = [...seen];
  const nested = paths.find(path => paths.some(other => other !== path && other.startsWith(path + '/')));
  if (nested) throw new BuildPrError(`${nested} is used both as a file and as a folder.`);
  return normalized;
}

const cleanText = (value, max) => typeof value === 'string' ? value.replace(/\r\n?/g, '\n').replace(CONTROL, '').trim().slice(0, max) : '';
export function slugify(title) {
  const slug = title.normalize('NFKD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 40).replace(/-+$/, '');
  return slug || 'changes';
}
/** dua/<slug>-<yymmdd-hhmmss UTC>: always a new dua/* branch, never the default branch. */
export function branchName(title, now = Date.now()) {
  const d = new Date(now);
  const two = n => String(n).padStart(2, '0');
  return `dua/${slugify(title)}-${two(d.getUTCFullYear() % 100)}${two(d.getUTCMonth() + 1)}${two(d.getUTCDate())}-${two(d.getUTCHours())}${two(d.getUTCMinutes())}${two(d.getUTCSeconds())}`;
}

// ---------------------------------------------------------------- unified diff (Myers) for the review card
const lines = text => { if (!text) return []; const all = text.split('\n'); if (all.at(-1) === '') all.pop(); return all; };
function myers(a, b, maxD = 2000) {
  const n = a.length, m = b.length, max = n + m, offset = max + 1;
  const v = new Int32Array(2 * max + 3);
  const trace = [];
  let found = -1;
  for (let d = 0; d <= Math.min(max, maxD); d += 1) {
    trace.push(v.slice(offset - d - 1, offset + d + 2));
    for (let k = -d; k <= d; k += 2) {
      let x = k === -d || (k !== d && v[offset + k - 1] < v[offset + k + 1]) ? v[offset + k + 1] : v[offset + k - 1] + 1;
      let y = x - k;
      while (x < n && y < m && a[x] === b[y]) { x += 1; y += 1; }
      v[offset + k] = x;
      if (x >= n && y >= m) { found = d; break; }
    }
    if (found >= 0) break;
  }
  if (found < 0) return null;
  const ops = [];
  let x = n, y = m;
  for (let d = found; d >= 0; d -= 1) {
    const row = trace[d];
    const at = k => row[k + d + 1];
    const k = x - y;
    const prevK = k === -d || (k !== d && at(k - 1) < at(k + 1)) ? k + 1 : k - 1;
    const prevX = d === 0 ? 0 : at(prevK);
    const prevY = prevX - prevK;
    while (x > prevX && y > prevY) { ops.push([' ', a[x - 1]]); x -= 1; y -= 1; }
    if (d > 0) { if (x === prevX) ops.push(['+', b[prevY]]); else ops.push(['-', a[prevX]]); }
    x = prevX; y = prevY;
  }
  return ops.reverse();
}
/** {diff, additions, deletions, truncated}: unified diff with 3 lines of context, cut at DIFF_MAX_CHARS. */
export function unifiedDiff(path, before, after, { context = 3 } = {}) {
  const a = lines(before ?? ''), b = lines(after ?? '');
  let start = 0;
  while (start < a.length && start < b.length && a[start] === b[start]) start += 1;
  let endA = a.length, endB = b.length;
  while (endA > start && endB > start && a[endA - 1] === b[endB - 1]) { endA -= 1; endB -= 1; }
  const midA = a.slice(start, endA), midB = b.slice(start, endB);
  const middle = (midA.length + midB.length <= 40000 && myers(midA, midB)) || [...midA.map(line => ['-', line]), ...midB.map(line => ['+', line])];
  const ops = [...a.slice(0, start).map(line => [' ', line]), ...middle, ...a.slice(endA).map(line => [' ', line])];
  const oldNo = [], newNo = [];
  let o = 1, w = 1;
  for (const [t] of ops) { oldNo.push(o); newNo.push(w); if (t !== '+') o += 1; if (t !== '-') w += 1; }
  const out = [before === null ? '--- /dev/null' : `--- a/${path}`, after === null ? '+++ /dev/null' : `+++ b/${path}`];
  let index = 0;
  while (index < ops.length) {
    while (index < ops.length && ops[index][0] === ' ') index += 1;
    if (index >= ops.length) break;
    const from = Math.max(0, index - context);
    let end = index, j = index;
    while (j < ops.length) {
      if (ops[j][0] !== ' ') { end = j + 1; j += 1; continue; }
      let k = j;
      while (k < ops.length && ops[k][0] === ' ') k += 1;
      if (k >= ops.length || k - j > 2 * context) break;
      j = k;
    }
    const stop = Math.min(ops.length, end + context);
    const slice = ops.slice(from, stop);
    const oldLen = slice.filter(([t]) => t !== '+').length, newLen = slice.filter(([t]) => t !== '-').length;
    out.push(`@@ -${oldLen ? oldNo[from] : oldNo[from] - 1},${oldLen} +${newLen ? newNo[from] : newNo[from] - 1},${newLen} @@`, ...slice.map(([t, line]) => t + line));
    index = stop;
  }
  const additions = ops.filter(([t]) => t === '+').length, deletions = ops.filter(([t]) => t === '-').length;
  let diff = out.join('\n');
  let truncated = false;
  if (diff.length > DIFF_MAX_CHARS) {
    const cut = diff.lastIndexOf('\n', DIFF_MAX_CHARS);
    const rest = diff.slice(cut + 1).split('\n').length;
    diff = diff.slice(0, cut) + `\n… diff cut here (${rest} more lines). The pull request will contain the full change.`;
    truncated = true;
  }
  return { diff, additions, deletions, ...(truncated ? { truncated: true } : {}) };
}

// ---------------------------------------------------------------- 1. the model's tool: proposal only, no writes
export const PROPOSE_DECLARATION = Object.freeze({
  name: 'propose_changes',
  description: 'Propose file changes for the owner to review as a pull request. This does NOT write anything: the app shows the proposal and only the owner can approve it and create the pull request. Give the complete new content of every created or updated file. Update or delete only files you read in full with read_file in this reply.',
  parameters: { type: 'OBJECT', properties: {
    title: { type: 'STRING', description: 'Short pull request title, imperative, at most 120 characters.' },
    summary: { type: 'STRING', description: 'What changes and why, and how to test it. A few lines of markdown.' },
    changes: { type: 'ARRAY', description: 'The file changes.', items: { type: 'OBJECT', properties: {
      path: { type: 'STRING', description: 'File path relative to the repository root.' },
      action: { type: 'STRING', enum: ['create', 'update', 'delete'], description: 'create a new file, update (replace) an existing file, or delete it.' },
      content: { type: 'STRING', description: 'Complete new file content for create or update (never placeholders). Omit for delete.' }
    }, required: ['path', 'action'] } }
  }, required: ['title', 'summary', 'changes'] }
});

/**
 * Runs the model's propose_changes call: validation only. `read` maps paths read in this reply to {path, text, sha, truncated};
 * `tree` is the default branch file list. Returns {proposal, response} or {response: {error}} for the model.
 * The proposal is returned to the app for review; nothing here touches GitHub.
 */
export function proposeChanges(args, { repository, branch, read, tree, limits, now = Date.now, id = randomUUID }) {
  try {
    const title = cleanText(args?.title, MAX_TITLE).replace(/\s+/g, ' ');
    if (!title) throw new BuildPrError('Give the pull request a short title.');
    const summary = cleanText(args?.summary, MAX_SUMMARY);
    const raw = Array.isArray(args?.changes) ? args.changes.map(change => change && typeof change === 'object' ? { path: typeof change.path === 'string' ? change.path.trim().replace(/^\.\//, '') : change.path, action: change.action,
      ...(change.content !== undefined && change.content !== null && change.action !== 'delete' ? { content: change.content } : {}) } : change) : args?.changes;
    const changes = checkChanges(raw, limits);
    const known = new Set(tree.files.map(file => file.path));
    const files = changes.map(change => {
      if (change.action === 'create') {
        if (known.has(change.path)) throw new BuildPrError(`${change.path} already exists. Read it with read_file and propose an update instead.`);
        return { path: change.path, action: 'create', content: change.content, ...unifiedDiff(change.path, null, change.content) };
      }
      const original = read.get(change.path);
      if (!original) throw new BuildPrError(`Read ${change.path} in full with read_file in this reply before proposing to ${change.action} it.`);
      if (original.truncated) throw new BuildPrError(`${change.path} is too large to read in full, so it cannot be changed from Dua.`);
      if (!original.sha) throw new BuildPrError(`The version of ${change.path} is unknown. Read it again with read_file.`);
      if (change.action === 'update' && change.content === original.text) throw new BuildPrError(`The new content of ${change.path} is identical to the current file.`);
      return { path: change.path, action: change.action, ...(change.action === 'update' ? { content: change.content } : {}), baseSha: original.sha,
        ...unifiedDiff(change.path, original.text, change.action === 'update' ? change.content : null) };
    });
    const createdAt = now();
    const proposal = { id: id(), repository, baseBranch: branch, branch: branchName(title, createdAt), title, summary, createdAt: new Date(createdAt).toISOString(), files };
    return { proposal, response: { ok: true, files: files.length, note: 'The proposal is shown to the owner as a review card. Nothing was written to GitHub. Do not call propose_changes again. Briefly explain the change and ask the owner to review it and tap Create PR if it looks right. Do not repeat the full diff.' } };
  } catch (error) {
    if (error instanceof BuildPrError) return { response: { error: error.message + ' Nothing was proposed.' } };
    throw error;
  }
}

// ---------------------------------------------------------------- 2. owner-approved create route
const REQUEST_KEYS = ['repository', 'proposalId', 'title', 'summary', 'branch', 'files'];
/** Validates the create-pr body (the proposal the owner approved, possibly with an edited title). */
export function validatePrRequest(body, limits) {
  if (!body || typeof body !== 'object' || Array.isArray(body) || Object.keys(body).some(key => !REQUEST_KEYS.includes(key))) throw new BuildPrError('Invalid request.');
  if (typeof body.repository !== 'string' || !REPOSITORY.test(body.repository)) throw new BuildPrError('Choose a repository as owner/name.');
  if (typeof body.proposalId !== 'string' || !PROPOSAL_ID.test(body.proposalId)) throw new BuildPrError('Invalid proposal id. Ask Dua to regenerate the proposal.');
  const title = cleanText(body.title, MAX_TITLE + 1).replace(/\s+/g, ' ');
  if (!title || title.length > MAX_TITLE) throw new BuildPrError(`Use a title of 1 to ${MAX_TITLE} characters.`);
  if (body.summary !== undefined && (typeof body.summary !== 'string' || body.summary.length > MAX_SUMMARY)) throw new BuildPrError('The summary is too long.');
  if (typeof body.branch !== 'string' || !PR_BRANCH.test(body.branch)) throw new BuildPrError('The branch must be a new dua/... branch (lowercase letters, digits and dashes).');
  return { repository: body.repository, proposalId: body.proposalId, title, summary: cleanText(body.summary, MAX_SUMMARY), branch: body.branch, files: checkChanges(body.files, limits, { requireSha: true }) };
}

const ACTION_WORDS = { create: 'added', update: 'changed', delete: 'deleted' };
export function prBody({ summary, files, proposalId }) {
  return [summary || 'Changes proposed by Dua.', '', '---',
    'Created by **Dua** (Build mode) from the owner\'s request, after the owner reviewed this proposal in the Dua app and tapped Create PR.',
    'Dua never merges: review the changes and merge them yourself if they look right.', '',
    '**Files**', ...files.map(file => `- \`${file.path}\` (${ACTION_WORDS[file.action]})`), '', `<!-- dua-proposal-id: ${proposalId} -->`].join('\n');
}
const commitMessage = ({ title, summary, proposalId }) => [title, '', ...(summary ? [summary, ''] : []), 'Created by Dua (Build mode) after the owner approved this proposal in the app.', '', `Dua-Proposal-Id: ${proposalId}`].join('\n');
const prUrl = (pr, repository) => typeof pr?.html_url === 'string' && pr.html_url.toLowerCase().startsWith(`https://github.com/${repository.toLowerCase()}/pull/`) && Number.isSafeInteger(pr.number) ? pr.html_url : null;

/** Looks up blob sha and mode of each path on the base tree (non-recursive tree walk, so large repositories work). Refuses symlinks, submodules and file/folder clashes. */
async function currentEntries(paths, rootTree, call, api) {
  const listings = new Map();
  let fetches = 0;
  const listing = treeSha => {
    if (!listings.has(treeSha)) {
      if ((fetches += 1) > MAX_TREE_FETCHES) throw new BuildPrError('The proposal touches too many folders to check. Split it into smaller proposals.', 413);
      listings.set(treeSha, call('GET', `${api}/git/trees/${treeSha}`, { maxBytes: 8 * 1024 * 1024 }).then(tree => {
        if (!tree || !Array.isArray(tree.tree)) throw new GitHubWriteError('GitHub returned an invalid response.', 502);
        return { truncated: tree.truncated === true, entries: new Map(tree.tree.filter(item => typeof item?.path === 'string').map(item => [item.path, { type: item.type, mode: item.mode, sha: item.sha }])) };
      }));
    }
    return listings.get(treeSha);
  };
  const kind = entry => entry.mode === '120000' ? 'a symlink' : entry.mode === '160000' || entry.type === 'commit' ? 'a submodule' : entry.type === 'tree' ? 'a folder' : 'a file';
  const result = new Map();
  for (const path of paths) {
    const segments = path.split('/');
    let current = await listing(rootTree);
    let entry = null;
    for (const [index, segment] of segments.entries()) {
      entry = current.entries.get(segment) || null;
      const last = index === segments.length - 1;
      if (!entry) { if (current.truncated) throw new BuildPrError(`GitHub could not list the folder of ${path}, so the change cannot be checked safely.`, 422); break; }
      if (!last) {
        if (entry.type !== 'tree') throw new BuildPrError(`${segments.slice(0, index + 1).join('/')} is ${kind(entry)}, so ${path} cannot be written.`);
        current = await listing(entry.sha);
        entry = null;
      } else if (entry.type !== 'blob' || !['100644', '100755'].includes(entry.mode)) throw new BuildPrError(`${path} is ${kind(entry)} on the default branch. Dua only changes regular files.`);
    }
    result.set(path, entry && SHA.test(entry.sha || '') ? { sha: entry.sha, mode: entry.mode } : null);
  }
  return result;
}

/**
 * Creates branch + commit + pull request for an approved, validated request. Idempotent per proposal: the branch name
 * is fixed by the proposal, so a second tap finds the branch (its commit carries Dua-Proposal-Id) and returns the
 * existing pull request instead of opening another.
 */
export async function createPullRequest(request, { env, fetcher = fetch, signal }) {
  const call = (method, path, options = {}) => githubWrite(method, path, { env, fetcher, signal, ...options });
  const api = repoApi(request.repository);
  const repo = await call('GET', api, { maxBytes: 512 * 1024 });
  if (!repo || typeof repo.full_name !== 'string' || !REPOSITORY.test(repo.full_name) || typeof repo.default_branch !== 'string' || !/^[A-Za-z0-9._/-]{1,200}$/.test(repo.default_branch)) throw new GitHubWriteError('GitHub returned an invalid response.', 502);
  if (repo.archived === true) throw new BuildPrError('This repository is archived, so Dua cannot open a pull request.', 409);
  const repository = repo.full_name;
  const list = prRepositories(env);
  if (list && !list.includes(repository.toLowerCase())) throw new BuildPrError('This repository is not in DUA_BUILD_PR_REPOS, so Dua cannot open pull requests in it.', 403);
  if (!list) {
    const user = await call('GET', '/user', { maxBytes: 256 * 1024 });
    if (typeof user?.login !== 'string' || user.login.toLowerCase() !== String(repo.owner?.login || repository.split('/')[0]).toLowerCase())
      throw new BuildPrError('Without DUA_BUILD_PR_REPOS, Dua only opens pull requests in repositories owned by the write token\'s account. Add this repository to DUA_BUILD_PR_REPOS to allow it.', 403);
  }
  const base = repo.default_branch;
  if (request.branch === base) throw new BuildPrError('Dua never commits to the default branch.', 400);
  const owner = repository.split('/')[0];
  const draft = prDraft(env);
  const findPr = async () => {
    const prs = await call('GET', `${api}/pulls?head=${encodeURIComponent(owner)}%3A${encodeURIComponent(request.branch)}&state=all&per_page=5`, { maxBytes: 2 * 1024 * 1024 });
    const pr = Array.isArray(prs) ? prs.find(item => item?.head?.ref === request.branch && prUrl(item, repository)) : null;
    return pr ? { url: pr.html_url, number: pr.number, draft: pr.draft === true, state: pr.merged_at ? 'merged' : pr.state === 'closed' ? 'closed' : 'open' } : null;
  };
  const openPr = async () => {
    const body = { title: request.title, head: request.branch, base, body: prBody(request), draft };
    let pr;
    try { pr = await call('POST', `${api}/pulls`, { body }); }
    catch (error) {
      if (!(error instanceof GitHubWriteError) || error.status !== 422) throw error;
      if (/already exists/i.test(error.detail)) { const existing = await findPr(); if (existing) return { ...existing, existing: true }; }
      // Draft pull requests are not available on some plans (e.g. private repositories on GitHub Free): open a normal one.
      if (!draft || !/draft/i.test(error.detail)) throw new GitHubWriteError('GitHub refused to open the pull request.', 422);
      pr = await call('POST', `${api}/pulls`, { body: { ...body, draft: false } });
      if (!prUrl(pr, repository)) throw new GitHubWriteError('GitHub returned an invalid response.', 502);
      return { url: pr.html_url, number: pr.number, draft: false, state: 'open', existing: false, notice: 'Draft pull requests are not available for this repository, so a normal (ready for review) pull request was opened.' };
    }
    if (!prUrl(pr, repository)) throw new GitHubWriteError('GitHub returned an invalid response.', 502);
    return { url: pr.html_url, number: pr.number, draft: pr.draft === true, state: 'open', existing: false };
  };
  /** The branch exists already: ours (same proposal id) → reuse it; otherwise refuse. */
  const resume = async ref => {
    const commitSha = ref?.object?.sha;
    const commit = typeof commitSha === 'string' && SHA.test(commitSha) ? await call('GET', `${api}/git/commits/${commitSha}`, { maxBytes: 512 * 1024 }) : null;
    if (typeof commit?.message !== 'string' || !commit.message.includes(`Dua-Proposal-Id: ${request.proposalId}`))
      throw new BuildPrError(`Branch ${request.branch} already exists and was not made for this proposal. Ask Dua to regenerate the proposal.`, 409);
    return { ...(await findPr() ?? await openPr()), existing: true };
  };
  const refPath = `${api}/git/ref/heads/${encodeBranch(request.branch)}`;
  const existingRef = await call('GET', refPath, { notFound: null, maxBytes: 256 * 1024 });
  if (existingRef) return { repository, branch: request.branch, base, ...(await resume(existingRef)) };

  const head = await call('GET', `${api}/git/ref/heads/${encodeBranch(base)}`, { maxBytes: 256 * 1024 });
  const headSha = head?.object?.sha;
  if (typeof headSha !== 'string' || !SHA.test(headSha)) throw new GitHubWriteError('GitHub returned an invalid response.', 502);
  const headCommit = await call('GET', `${api}/git/commits/${headSha}`, { maxBytes: 512 * 1024 });
  const rootTree = headCommit?.tree?.sha;
  if (typeof rootTree !== 'string' || !SHA.test(rootTree)) throw new GitHubWriteError('GitHub returned an invalid response.', 502);
  // Conflict check: every file must still be exactly the version the proposal was based on.
  const current = await currentEntries(request.files.map(file => file.path), rootTree, call, api);
  for (const file of request.files) {
    const now = current.get(file.path);
    if (file.action === 'create' && now) throw new BuildPrError(`${file.path} now exists on ${base}. The proposal is out of date: ask Dua to regenerate it.`, 409);
    if (file.action !== 'create' && !now) throw new BuildPrError(`${file.path} no longer exists on ${base}. The proposal is out of date: ask Dua to regenerate it.`, 409);
    if (file.action !== 'create' && now.sha !== file.baseSha) throw new BuildPrError(`${file.path} changed on ${base} since Dua read it. The proposal is out of date: ask Dua to regenerate it.`, 409);
  }
  const entries = [];
  for (const file of request.files) {
    if (file.action === 'delete') { entries.push({ path: file.path, mode: current.get(file.path).mode, type: 'blob', sha: null }); continue; }
    const blob = await call('POST', `${api}/git/blobs`, { body: { content: Buffer.from(file.content, 'utf8').toString('base64'), encoding: 'base64' }, maxBytes: 64 * 1024 });
    if (typeof blob?.sha !== 'string' || !SHA.test(blob.sha)) throw new GitHubWriteError('GitHub returned an invalid response.', 502);
    entries.push({ path: file.path, mode: current.get(file.path)?.mode || '100644', type: 'blob', sha: blob.sha });
  }
  const tree = await call('POST', `${api}/git/trees`, { body: { base_tree: rootTree, tree: entries }, maxBytes: 8 * 1024 * 1024 });
  if (typeof tree?.sha !== 'string' || !SHA.test(tree.sha)) throw new GitHubWriteError('GitHub returned an invalid response.', 502);
  const commit = await call('POST', `${api}/git/commits`, { body: { message: commitMessage(request), tree: tree.sha, parents: [headSha] }, maxBytes: 512 * 1024 });
  if (typeof commit?.sha !== 'string' || !SHA.test(commit.sha)) throw new GitHubWriteError('GitHub returned an invalid response.', 502);
  try { await call('POST', `${api}/git/refs`, { body: { ref: `refs/heads/${request.branch}`, sha: commit.sha }, maxBytes: 256 * 1024 }); }
  catch (error) {
    // A double tap raced us to the branch: return that attempt's pull request instead of a second one.
    if (error instanceof GitHubWriteError && error.status === 422 && /already exists/i.test(error.detail)) {
      const ref = await call('GET', refPath, { maxBytes: 256 * 1024 });
      return { repository, branch: request.branch, base, ...(await resume(ref)) };
    }
    throw error;
  }
  return { repository, branch: request.branch, base, commit: commit.sha, ...(await openPr()) };
}

/** POST /api/build/pr: the only route that writes to GitHub. Runs after the owner's Create PR tap + confirmation. */
export async function handleBuildPr(request, { env = {}, fetcher = fetch } = {}) {
  const url = new URL(request.url);
  if (url.pathname !== '/api/build/pr') return json({ error: 'Not found.' }, 404);
  if (request.method !== 'POST') return json({ error: 'Method not allowed.' }, 405);
  if (request.headers.get('origin') && request.headers.get('origin') !== url.origin) return json({ error: 'Request origin is not allowed.' }, 403);
  const config = buildPrConfiguration(env);
  if (!config.enabled) return json({ error: config.reason }, 503);
  if (!(request.headers.get('content-type') || '').startsWith('application/json')) return json({ error: 'Use JSON.' }, 415);
  try { await resolveOwner(request, { env, fetcher, feature: 'Build pull requests' }); }
  catch (error) {
    if (request.signal.aborted) return json({ error: 'Request stopped.' }, 504);
    return json({ error: error instanceof StorageError ? error.message : 'Account verification is unavailable. Retry.' }, error instanceof StorageError ? error.status : 503);
  }
  const limits = prLimits(env);
  let pr;
  try {
    let body;
    // JSON escaping can roughly double code; Vercel caps request bodies at 4.5 MB.
    try { body = await readJsonLimited(request, Math.min(4 * 1024 * 1024, limits.totalMaxBytes * 2 + 256 * 1024)); }
    catch (error) { throw new BuildPrError(error?.status === 413 ? 'The proposal is too large to send.' : 'Invalid request.', error?.status === 413 ? 413 : 400); }
    const approved = validatePrRequest(body, limits);
    const list = prRepositories(env);
    if (list && !list.includes(approved.repository.toLowerCase())) throw new BuildPrError('This repository is not in DUA_BUILD_PR_REPOS, so Dua cannot open pull requests in it.', 403);
    pr = await createPullRequest(approved, { env, fetcher, signal: request.signal });
  } catch (error) {
    if (error instanceof BuildPrError || error instanceof GitHubWriteError) {
      console.warn('[dua-build-pr] refused', error.status);
      return json({ error: error.message }, error.status);
    }
    if (error?.name === 'AbortError' || error?.name === 'TimeoutError' || request.signal.aborted) return json({ error: 'GitHub request stopped or timed out. Check GitHub before retrying: the pull request may already exist (a retry will not open a second one).' }, 504);
    console.error('[dua-build-pr] failed', error?.name);
    return json({ error: 'Creating the pull request failed. Retry.' }, 502);
  }
  return json({ ok: true, ...pr });
}
