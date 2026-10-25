import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { validRepository, validRepositoryPath, normalizeRepositoryReadPath, repositoryWritePathIssue } from '../src/core/github-validation.mjs';
import { encodeRepoPath, repositoryInfo, repositoryFile, listRepositories, githubGet, searchRepository, GitHubError } from '../src/core/github.mjs';
import { repoApi, githubWrite, GitHubWriteError } from '../src/core/github-write.mjs';
import { validateChat } from '../src/core/providers.mjs';
import { checkChanges, pathProblem, prLimits, proposeChanges, validatePrRequest } from '../src/core/build-pr.mjs';
import { validateCloudSnapshot } from '../src/core/cloud-snapshot.mjs';

const fixture = JSON.parse(readFileSync(new URL('../../../app/src/test/resources/cloud-snapshot-v1.json', import.meta.url), 'utf8'));
const snapshotPath = (path, kind) => {
  const snapshot = structuredClone(fixture);
  const message = snapshot.chats[0].messages.find(item => item[kind]);
  if (kind === 'build') message.build.files = [{ path }];
  else message.proposal.files[0].path = path;
  return snapshot;
};

test('repository policy is used by chat, read and write entry points without coercing input types', async () => {
  let calls = 0;
  const fetcher = async () => { calls++; throw Error('Unexpected network call'); };
  for (const name of ['owner/repo', 'Owner-1/repo_name.2', 'a'.repeat(100) + '/' + 'b'.repeat(100)]) {
    assert.equal(validRepository(name), true);
    assert.equal(repoApi(name), '/repos/' + name);
    assert.equal(validateChat({ model: 'Dua', mode: 'build', repository: name, messages: [{ role: 'user', text: 'hi' }] }), null);
  }
  for (const name of ['', null, undefined, 1, {}, ['owner/repo'], 'owner', 'owner/repo/extra', 'https://github.com/owner/repo', ' owner/repo', 'owner/repo ', 'owner\\repo', 'a'.repeat(101) + '/repo', 'owner/' + 'b'.repeat(101)]) {
    assert.equal(validRepository(name), false, String(name));
    assert.throws(() => repoApi(name), error => error instanceof GitHubWriteError && error.status === 400);
    await assert.rejects(repositoryInfo(name, { fetcher }), error => error instanceof GitHubError && error.status === 400);
    assert.equal(validateChat({ model: 'Dua', mode: 'build', repository: name, messages: [{ role: 'user', text: 'hi' }] })[1], 400);
    assert.throws(() => validatePrRequest({ repository: name }, prLimits({})), /owner\/name/);
  }
  assert.equal(calls, 0);
});

test('read normalization and URL escaping preserve literals while canonical metadata/writes stay strict', () => {
  for (const [input, normalized, encoded] of [
    [' README.md ', 'README.md', 'README.md'],
    ['./docs/a b#d.md', 'docs/a b#d.md', 'docs/a%20b%23d.md'],
    ['docs/%2e%2e/file', 'docs/%2e%2e/file', 'docs/%252e%252e/file'],
    ['C:/literal.txt', 'C:/literal.txt', 'C%3A/literal.txt']
  ]) {
    assert.equal(normalizeRepositoryReadPath(input), normalized);
    assert.equal(encodeRepoPath(input), encoded);
  }
  for (const path of [' README.md ', './README.md', 'C:/literal.txt']) {
    assert.equal(validRepositoryPath(path), false);
    assert.equal(repositoryWritePathIssue(path), 'invalid');
    assert.throws(() => validateCloudSnapshot(snapshotPath(path, 'build')), /Invalid cloud snapshot/);
    assert.throws(() => checkChanges([{ path, action: 'create', content: 'x' }], prLimits({})), /relative to the repository root/);
  }
  for (const path of ['README.md', 'docs/a b#d.md', 'src/日本語.kt', 'a'.repeat(400)]) {
    assert.equal(validRepositoryPath(path), true);
    assert.equal(repositoryWritePathIssue(path), null);
    assert.equal(pathProblem(path), null);
    assert.doesNotThrow(() => validateCloudSnapshot(snapshotPath(path, 'build')));
    assert.doesNotThrow(() => validateCloudSnapshot(snapshotPath(path, 'proposal')));
    assert.doesNotThrow(() => checkChanges([{ path, action: 'create', content: 'x' }], prLimits({})));
  }
});

test('unsafe segments, controls and overlong paths are rejected before a read or accepted proposal', async () => {
  const paths = ['', null, undefined, 1, {}, [], '../x', 'docs/../x', '/etc/passwd', 'a//b', 'a/./b', 'a\\b', 'a'.repeat(401),
    ...Array.from({ length: 32 }, (_, code) => 'a' + String.fromCharCode(code) + '/b'), 'a\x7f/b'];
  let calls = 0;
  const fetcher = async () => { calls++; throw Error('Unexpected network call'); };
  for (const path of paths) {
    assert.equal(encodeRepoPath(path), null, String(path));
    assert.equal(validRepositoryPath(path), false);
    assert.equal(repositoryWritePathIssue(path), 'invalid');
    await assert.rejects(repositoryFile('owner/repo', path, { fetcher }), error => error instanceof GitHubError && error.status === 400);
    assert.throws(() => validateCloudSnapshot(snapshotPath(path, 'build')), /Invalid cloud snapshot/);
    assert.throws(() => validateCloudSnapshot(snapshotPath(path, 'proposal')), /Invalid cloud snapshot/);
  }
  assert.equal(calls, 0);
});

test('protected paths remain readable metadata but are blocked in both live and saved proposals', () => {
  for (const [path, issue] of [['.git/config', 'git'], ['src/.GiT/hooks/run', 'git'], ['.GitModules', 'submodule'], ['.GitHub/Workflows/ci.yml', 'workflow']]) {
    assert.equal(validRepositoryPath(path), true);
    assert.notEqual(encodeRepoPath(path), null);
    assert.equal(repositoryWritePathIssue(path), issue);
    assert.notEqual(pathProblem(path), null);
    assert.throws(() => checkChanges([{ path, action: 'create', content: 'x' }], prLimits({})));
    assert.throws(() => validateCloudSnapshot(snapshotPath(path, 'proposal')), /Invalid cloud snapshot/);
    assert.doesNotThrow(() => validateCloudSnapshot(snapshotPath(path, 'build')));
  }
  assert.equal(repositoryWritePathIssue('docs/.github/workflows-notes.md'), null);
});

test('malformed GitHub repository names fail as safe upstream errors, not coerced values', async () => {
  for (const name of [['owner/repo'], { toString: 'owner/repo' }, 12]) {
    await assert.rejects(listRepositories({ env: { ENABLE_GITHUB_CONNECTOR: '1', GITHUB_CONNECTOR_TOKEN: 'fake-token' }, fetcher: async () => Response.json([{ full_name: name }]) }), error => error instanceof GitHubError && error.status === 502);
  }
});

const boundaryEnv = { ENABLE_GITHUB_CONNECTOR: '1', GITHUB_CONNECTOR_TOKEN: 'fake-read', GITHUB_BUILD_WRITE_TOKEN: 'fake-write' };
const noNetwork = async () => { assert.fail('Rejected input must not reach GitHub'); };

test('repository terminators and controls are rejected at all entry points', async () => {
  for (const name of ['owner/.', 'owner/..', 'owner/repo\n', 'owner/repo\r', 'owner/repo\t']) {
    assert.equal(validRepository(name), false);
    assert.throws(() => repoApi(name), /owner\/name/);
    await assert.rejects(repositoryInfo(name, { env: boundaryEnv, fetcher: noNetwork }), { status: 400 });
    assert.equal(validateChat({ model: 'Dua', mode: 'build', repository: name, messages: [{ role: 'user', text: 'hi' }] })[1], 400);
  }
  for (const path of ['README.md\n', '\tREADME.md', 'README.md\r', 'src/\ud800.kt']) {
    assert.equal(encodeRepoPath(path), null);
    await assert.rejects(repositoryFile('owner/repo', path, { env: boundaryEnv, fetcher: noNetwork }), { status: 400 });
    const result = proposeChanges({ title: 'Change', changes: [{ path, action: 'create', content: 'x' }] }, { repository: 'owner/repo', branch: 'main', read: new Map(), tree: { files: [] }, limits: prLimits({}) });
    assert.equal(result.proposal, undefined);
    assert.match(result.response.error, /relative to the repository root/);
  }
});

test('raw and encoded URL normalization cannot bypass read/write allowlists', async () => {
  for (const path of [
    '/repos/owner/repo\n', '/repos/owner/../repo', '/repos/owner/.',
    '/repos/owner/repo/contents/../private', '/repos/owner/repo/contents/%2e%2e/private',
    '/repos/owner/repo/contents/a%2Fb', '/repos/owner/repo/contents/a%5cb',
    '/repos/owner/repo/contents/a%00b', '/repos/owner/repo/contents/%zz',
    '/repos/owner/repo/contents/a\n', '/repos/owner/repo/contents/a#fragment'
  ]) {
    await assert.rejects(githubGet(path, { env: boundaryEnv, fetcher: noNetwork }), { status: 400 }, path);
  }
  for (const path of ['/repos/owner/repo/git/blobs\n', '/repos/owner/../git/blobs']) {
    await assert.rejects(githubWrite('POST', path, { env: boundaryEnv, fetcher: noNetwork, body: {} }), { status: 400 });
  }
  for (const branch of ['..', '%2e%2e', 'a%2fb', 'a%5cb', 'a%0ab']) {
    await assert.rejects(githubWrite('GET', '/repos/owner/repo/git/ref/heads/' + branch, { env: boundaryEnv, fetcher: noNetwork }), { status: 400 });
  }
  const calls = [];
  await repositoryFile('owner/repo', 'docs/%2e%2e/a b.md', { env: boundaryEnv, fetcher: async url => {
    calls.push(url);
    return Response.json({ type: 'file', encoding: 'base64', content: 'b2s=' });
  } });
  assert.deepEqual(calls, ['https://api.github.com/repos/owner/repo/contents/docs/%252e%252e/a%20b.md']);
});

test('low-level git tree writes cannot bypass protected paths or create symlinks/submodules', async () => {
  for (const path of ['.github', '.GitHub/Workflows', '.github/workflows/ci.yml', '.GitHub/Workflows/ci.yml', 'nested/.GiT/config', '.GitModules', 'a/../b', 'a//b', 'a\n']) {
    assert.notEqual(repositoryWritePathIssue(path), null);
    assert.notEqual(pathProblem(path), null);
    assert.throws(() => validateCloudSnapshot(snapshotPath(path, 'proposal')), /Invalid cloud snapshot/);
    await assert.rejects(githubWrite('POST', '/repos/owner/repo/git/trees', { env: boundaryEnv, fetcher: noNetwork, body: { tree: [{ path, type: 'blob', mode: '100644', sha: 'abc' }] } }), { status: 400 });
  }
  for (const [type, mode] of [['blob', '120000'], ['commit', '160000'], ['tree', '040000']]) {
    await assert.rejects(githubWrite('POST', '/repos/owner/repo/git/trees', { env: boundaryEnv, fetcher: noNetwork, body: { tree: [{ path: 'safe', type, mode, sha: 'abc' }] } }), { status: 400 });
  }
  let writes = 0;
  for (const mode of ['100644', '100755']) {
    await githubWrite('POST', '/repos/owner/repo/git/trees', { env: boundaryEnv, fetcher: async (_url, options) => {
      assert.equal(options.method, 'POST');
      assert.equal(options.redirect, 'error');
      writes++;
      return Response.json({ sha: 'tree' });
    }, body: { tree: [{ path: 'src/file.kt', type: 'blob', mode, sha: null }] } });
  }
  assert.equal(writes, 2);
});

test('search is scoped before HTTP and rejects mismatched/malformed repository results', async () => {
  for (const q of ['term', 'term repo:owner/repo repo:other/repo', 'term repo:owner/repo org:other', 'term OR repo:owner/repo', '"term repo:owner/repo', 'term -repo:owner/repo', '(term) repo:owner/repo']) {
    await assert.rejects(githubGet('/search/code?q=' + encodeURIComponent(q) + '&per_page=20', { env: boundaryEnv, fetcher: noNetwork }), { status: 400 });
  }
  for (const query of ['term OR other', '"unterminated', 'term NOT other', '(term)', 'term\\other']) {
    await assert.rejects(searchRepository('owner/repo', query, { env: boundaryEnv, fetcher: noNetwork }), { status: 400 });
  }
  for (const item of [null, { path: 'x' }, { path: 'x', repository: { full_name: 'other/repo' } }, { path: '../x', repository: { full_name: 'owner/repo' } }, { path: null, repository: { full_name: 'owner/repo' } }]) {
    await assert.rejects(searchRepository('owner/repo', 'term', { env: boundaryEnv, fetcher: async () => Response.json({ items: [item] }) }), { status: 502 });
  }
  const result = await searchRepository('owner/repo', 'term repo:other/repo org:other user:other', { env: boundaryEnv, fetcher: async url => {
    assert.equal(new URL(url).searchParams.get('q'), 'term repo:owner/repo');
    return Response.json({ items: Array.from({ length: 25 }, (_, i) => ({ path: `file${i}.kt`, repository: { full_name: 'OWNER/REPO' } })) });
  } });
  assert.equal(result.results.length, 20);
});
