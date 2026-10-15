import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { validRepository, validRepositoryPath, normalizeRepositoryReadPath, repositoryWritePathIssue } from '../src/core/github-validation.mjs';
import { encodeRepoPath, repositoryInfo, repositoryFile, listRepositories, GitHubError } from '../src/core/github.mjs';
import { repoApi, GitHubWriteError } from '../src/core/github-write.mjs';
import { validateChat } from '../src/core/providers.mjs';
import { checkChanges, pathProblem, prLimits, validatePrRequest } from '../src/core/build-pr.mjs';
import { validateCloudSnapshot } from '../src/core/cloud-snapshot.mjs';

const fixture = JSON.parse(readFileSync(new URL('../../app/src/test/resources/cloud-snapshot-v1.json', import.meta.url), 'utf8'));
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
