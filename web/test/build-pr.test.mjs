// Phase 5C: Build mode pull requests with mocked GitHub (read + write tokens) and Gemini. Nothing touches the network.
import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { handleChat } from '../src/core/providers.mjs';
import { handleService } from '../src/server/service-handler.mjs';
import { buildConfiguration } from '../src/core/build-chat.mjs';
import { branchName, buildPrConfiguration, checkChanges, pathProblem, prLimits, proposeChanges, unifiedDiff, validatePrRequest, DIFF_MAX_CHARS } from '../src/core/build-pr.mjs';
import { ALLOWED_WRITE_REQUESTS, githubWrite, GitHubWriteError } from '../src/core/github-write.mjs';
import { filesEnv, router, authedChat } from './files-helpers.mjs';
import { readEvents } from './helpers.mjs';

const REPO = 'KRFbrothers/Dua-ai';
const GH = 'https://api.github.com';
const GEMINI = 'https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent';
const READ = 'fake-read-connector-secret-111';
const WRITE = 'fake-WRITE-token-secret-222';
const prEnv = { ...filesEnv, ENABLE_GITHUB_CONNECTOR: '1', GITHUB_CONNECTOR_TOKEN: READ, DUA_BUILD_PR: '1', GITHUB_BUILD_WRITE_TOKEN: WRITE };
const blobSha = text => createHash('sha1').update(`blob ${Buffer.byteLength(text)}\0`).update(text).digest('hex');
const treeSha = name => createHash('sha1').update('tree:' + name).digest('hex');
const HEAD = 'a'.repeat(40);
const README = '# Dua AI\n\nPersonal assistant.\n';
const SCRIPT = '#!/bin/sh\necho build\n';
const ID = '0f8fad5b-d9cb-469f-a165-70867728950e';
const BRANCH = 'dua/fix-readme-title-261008-134512';
const geminiReply = parts => Response.json({ candidates: [{ content: { role: 'model', parts }, finishReason: 'STOP' }], usageMetadata: { promptTokenCount: 10, candidatesTokenCount: 5 } });
const call = (name, args) => ({ functionCall: { name, args } });

/**
 * Stateful fake GitHub: read token for GET contents/tree, write token for the Git Data API and pulls.
 * Files: path -> {text, mode}; `link` is a symlink and `vendor/lib` a submodule.
 */
function github({ email, draftUnsupported = false, owner = 'KRFbrothers', refsRace = false, gemini = [] } = {}) {
  const files = { 'README.md': { text: README, mode: '100644' }, 'scripts/build.sh': { text: SCRIPT, mode: '100755' }, 'web/src/a.mjs': { text: 'export const a = 1;\n', mode: '100644' } };
  const special = { link: { type: 'blob', mode: '120000', sha: 'e'.repeat(40) }, 'vendor/lib': { type: 'commit', mode: '160000', sha: 'f'.repeat(40) } };
  const state = { refs: new Map(), commits: new Map(), pulls: [], blobs: [], trees: [], files, special };
  const listing = dir => {
    const prefix = dir ? dir + '/' : '';
    const entries = new Map();
    for (const [path, file] of Object.entries(files)) if (path.startsWith(prefix)) {
      const rest = path.slice(prefix.length); const name = rest.split('/')[0];
      entries.set(name, rest.includes('/') ? { path: name, type: 'tree', mode: '040000', sha: treeSha(prefix + name) } : { path: name, type: 'blob', mode: file.mode, sha: blobSha(file.text) });
    }
    for (const [path, item] of Object.entries(special)) if (path.startsWith(prefix)) {
      const rest = path.slice(prefix.length); const name = rest.split('/')[0];
      entries.set(name, rest.includes('/') ? { path: name, type: 'tree', mode: '040000', sha: treeSha(prefix + name) } : { path: name, ...item });
    }
    return { sha: treeSha(dir), truncated: false, tree: [...entries.values()] };
  };
  const dirs = () => new Set(['', ...[...Object.keys(files), ...Object.keys(special)].flatMap(path => path.split('/').slice(0, -1).map((_, i, all) => all.slice(0, i + 1).join('/')))]);
  const script = [...gemini];
  const fetcher = router([
    [url => url.startsWith(GH), (url, init, recorded) => {
      const path = url.slice(GH.length);
      const auth = recorded.headers.get('authorization');
      const method = init.method || 'GET';
      if (auth === 'Bearer ' + READ) {
        assert.equal(method, 'GET', 'the read token only GETs');
        if (path === `/repos/${REPO}`) return Response.json({ full_name: REPO, default_branch: 'main' });
        if (path === `/repos/${REPO}/git/trees/HEAD?recursive=1`) return Response.json({ truncated: false, tree: Object.entries(files).map(([p, f]) => ({ path: p, type: 'blob', size: f.text.length })) });
        if (path.startsWith(`/repos/${REPO}/contents/`)) {
          const name = decodeURIComponent(path.slice(`/repos/${REPO}/contents/`.length));
          const file = files[name];
          return file ? Response.json({ type: 'file', path: name, size: file.text.length, sha: blobSha(file.text), encoding: 'base64', content: Buffer.from(file.text).toString('base64') }) : new Response('{}', { status: 404 });
        }
        throw new Error('Unexpected read path ' + path);
      }
      assert.equal(auth, 'Bearer ' + WRITE, 'every other GitHub request uses the write token');
      assert.ok(['GET', 'POST'].includes(method), 'write client only GETs and POSTs');
      const api = `/repos/${REPO}`;
      const body = recorded.json;
      if (method === 'GET' && path === '/user') return Response.json({ login: owner });
      if (method === 'GET' && path === api) return Response.json({ full_name: REPO, default_branch: 'main', owner: { login: 'KRFbrothers' }, archived: false });
      if (method === 'GET' && path === `${api}/git/ref/heads/main`) return Response.json({ ref: 'refs/heads/main', object: { sha: HEAD, type: 'commit' } });
      if (method === 'GET' && path.startsWith(`${api}/git/ref/heads/`)) {
        const name = path.slice(`${api}/git/ref/heads/`.length).split('/').map(decodeURIComponent).join('/');
        const sha = state.refs.get(name);
        return sha ? Response.json({ ref: 'refs/heads/' + name, object: { sha, type: 'commit' } }) : new Response('{"message":"Not Found"}', { status: 404 });
      }
      if (method === 'GET' && path === `${api}/git/commits/${HEAD}`) return Response.json({ sha: HEAD, tree: { sha: treeSha('') }, message: 'Initial' });
      if (method === 'GET' && path.startsWith(`${api}/git/commits/`)) { const commit = state.commits.get(path.split('/').at(-1)); return commit ? Response.json(commit) : new Response('{}', { status: 404 }); }
      if (method === 'GET' && path.startsWith(`${api}/git/trees/`)) {
        const sha = path.split('/').at(-1);
        const dir = [...dirs()].find(d => treeSha(d) === sha);
        return dir === undefined ? new Response('{}', { status: 404 }) : Response.json(listing(dir));
      }
      if (method === 'GET' && path.startsWith(`${api}/pulls?head=`)) {
        const head = decodeURIComponent(new URL(GH + path).searchParams.get('head')).split(':')[1];
        return Response.json(state.pulls.filter(pr => pr.head.ref === head));
      }
      if (method === 'POST' && path === `${api}/git/blobs`) { state.blobs.push(Buffer.from(body.content, 'base64').toString('utf8')); return Response.json({ sha: blobSha(Buffer.from(body.content, 'base64').toString('utf8')) }, { status: 201 }); }
      if (method === 'POST' && path === `${api}/git/trees`) { state.trees.push(body); return Response.json({ sha: 'c'.repeat(40) }, { status: 201 }); }
      if (method === 'POST' && path === `${api}/git/commits`) { const sha = createHash('sha1').update(body.message).digest('hex'); state.commits.set(sha, { sha, message: body.message, tree: { sha: body.tree } }); return Response.json({ sha }, { status: 201 }); }
      if (method === 'POST' && path === `${api}/git/refs`) {
        const name = body.ref.slice('refs/heads/'.length);
        if (state.refs.has(name)) return Response.json({ message: 'Reference already exists' }, { status: 422 });
        state.refs.set(name, body.sha);
        if (refsRace) { state.refs.set(name, body.sha); return Response.json({ message: 'Reference already exists' }, { status: 422 }); }
        return Response.json({ ref: body.ref, object: { sha: body.sha } }, { status: 201 });
      }
      if (method === 'POST' && path === `${api}/pulls`) {
        if (state.pulls.some(pr => pr.head.ref === body.head)) return Response.json({ message: 'Validation Failed', errors: [{ message: 'A pull request already exists for KRFbrothers:' + body.head }] }, { status: 422 });
        if (draftUnsupported && body.draft) return Response.json({ message: 'Validation Failed', errors: [{ message: 'Draft pull requests are not supported in this repository.' }] }, { status: 422 });
        const pr = { number: 80 + state.pulls.length, html_url: `https://github.com/${REPO}/pull/${80 + state.pulls.length}`, draft: body.draft === true, state: 'open', head: { ref: body.head }, title: body.title, body: body.body, base: body.base };
        state.pulls.push(pr);
        return Response.json(pr, { status: 201 });
      }
      throw new Error(`Unexpected write request ${method} ${path}`);
    }],
    [url => url.startsWith(GEMINI), (url, init, recorded) => { const next = script.shift(); if (!next) throw new Error('Unexpected Gemini round'); return typeof next === 'function' ? next(recorded.json) : next; }]
  ], email ? { email } : undefined);
  fetcher.state = state;
  fetcher.gh = () => fetcher.calls.filter(c => c.url.startsWith(GH));
  fetcher.writes = () => fetcher.gh().filter(c => c.method !== 'GET');
  return fetcher;
}

const prRequest = (body, { token = 'user-token', origin } = {}) => new Request('https://dua.test/api/build/pr', { method: 'POST',
  headers: { 'content-type': 'application/json', ...(token ? { authorization: 'Bearer ' + token } : {}), ...(origin ? { origin } : {}) }, body: JSON.stringify(body) });
const approved = (extra = {}) => ({ repository: REPO, proposalId: ID, title: 'Fix README title', summary: 'Renames the title.\n\nTest: open the README.', branch: BRANCH,
  files: [{ path: 'README.md', action: 'update', content: '# Dua AI (personal)\n\nPersonal assistant.\n', baseSha: blobSha(README) }], ...extra });
/** Captures console output so tests can prove the write token is never logged. */
async function quietly(run) {
  const lines = [];
  const original = { log: console.log, warn: console.warn, error: console.error, info: console.info };
  for (const name of Object.keys(original)) console[name] = (...args) => lines.push(args.map(String).join(' '));
  try { return { result: await run(), lines }; } finally { Object.assign(console, original); }
}
/** POST /api/build/pr through the service router; every response body and log line is checked for both tokens. */
async function create(body, { env = prEnv, fetcher = github(), token } = {}) {
  const { result: response, lines } = await quietly(() => handleService(prRequest(body, { token }), { env, fetcher }));
  const text = await response.text();
  for (const secret of [WRITE, READ]) {
    assert.doesNotMatch(text, new RegExp(secret), 'tokens never appear in a response');
    assert.doesNotMatch(lines.join('\n'), new RegExp(secret), 'tokens never appear in logs');
  }
  return { status: response.status, body: JSON.parse(text), fetcher, lines };
}

test('disabled unless DUA_BUILD_PR=1 and the write token are set; config never leaks tokens', async () => {
  assert.equal(buildPrConfiguration(prEnv).enabled, true);
  assert.equal(buildPrConfiguration(prEnv).draft, true);
  assert.equal(buildPrConfiguration({ ...prEnv, DUA_BUILD_PR_DRAFT: '0' }).draft, false);
  assert.match(buildPrConfiguration({ ...prEnv, DUA_BUILD_PR: '' }).reason, /DUA_BUILD_PR=1/);
  assert.match(buildPrConfiguration({ ...prEnv, GITHUB_BUILD_WRITE_TOKEN: ' ' }).reason, /GITHUB_BUILD_WRITE_TOKEN.*Contents and Pull requests/);
  assert.match(buildPrConfiguration({ ...prEnv, DUA_BUILD: '0' }).reason, /DUA_BUILD=0/);
  assert.match(buildPrConfiguration({ ...prEnv, DUA_ALLOWED_EMAIL: '' }).reason, /owner-only/);
  assert.deepEqual(prLimits({}), { maxFiles: 20, fileMaxBytes: 200 * 1024, totalMaxBytes: 1024 * 1024 });
  assert.equal(buildConfiguration(prEnv, new Set(['Dua'])).tools.includes('propose_changes'), true);
  assert.equal(buildConfiguration({ ...prEnv, DUA_BUILD_PR: '0' }, new Set(['Dua'])).tools.includes('propose_changes'), false);
  const config = await (await handleChat(new Request('https://dua.test/api/config'), { env: { ...prEnv, DUA_BUILD_PR_REPOS: 'KRFbrothers/secret-repo' } })).json();
  assert.equal(config.build.pr.enabled, true);
  assert.equal(config.build.pr.restricted, true);
  assert.equal(config.build.readOnly, true, 'the model itself still never writes');
  const text = JSON.stringify(config);
  assert.doesNotMatch(text, new RegExp(WRITE));
  assert.doesNotMatch(text, /secret-repo/, 'the repository allowlist is not published');
  for (const env of [{ ...prEnv, DUA_BUILD_PR: '' }, { ...prEnv, GITHUB_BUILD_WRITE_TOKEN: '' }, { ...prEnv, DUA_BUILD: '0' }]) {
    const { status, body, fetcher } = await create(approved(), { env });
    assert.equal(status, 503);
    assert.ok(body.error);
    assert.equal(fetcher.calls.length, 0, 'nothing is called when the feature is off');
  }
});

test('the propose_changes tool only returns a proposal: no write token, no writes, then a forced text answer', async () => {
  const NEW = '# Dua AI (personal)\n\nPersonal assistant.\n';
  let declared, secondMode, toolResult;
  const fetcher = github({ gemini: [
    body => { declared = body.tools[0].functionDeclarations.map(f => f.name); assert.match(body.systemInstruction.parts.map(p => p.text).join('\n'), /only shows the owner a review card/); return geminiReply([call('read_file', { path: 'README.md' })]); },
    body => { assert.equal(body.contents[2].parts[0].functionResponse.response.sha, blobSha(README), 'read_file reports the blob sha'); return geminiReply([call('propose_changes', { title: 'Fix README title', summary: 'Rename.', changes: [{ path: 'README.md', action: 'update', content: NEW }, { path: 'docs/NEW.md', action: 'create', content: 'New\n' }] })]); },
    body => { secondMode = body.toolConfig.functionCallingConfig.mode; toolResult = body.contents.at(-1).parts[0].functionResponse.response; return geminiReply([{ text: 'Review the proposal and tap Create PR.' }]); }
  ] });
  const { result: response, lines } = await quietly(() => handleChat(authedChat({ model: 'Dua', mode: 'build', repository: REPO, messages: [{ role: 'user', text: 'Rename the README title.' }] }), { env: prEnv, fetcher }));
  const text = await response.text();
  const reply = JSON.parse(text);
  assert.deepEqual(declared, ['list_files', 'read_file', 'search_code', 'propose_changes']);
  assert.equal(secondMode, 'NONE', 'after a proposal the model answers in text');
  assert.equal(toolResult.ok, true);
  assert.match(toolResult.note, /Nothing was written/);
  const proposal = reply.proposal;
  assert.match(proposal.id, /^[0-9a-f-]{36}$/);
  assert.equal(proposal.repository, REPO);
  assert.equal(proposal.baseBranch, 'main');
  assert.match(proposal.branch, /^dua\/fix-readme-title-\d{6}-\d{6}$/);
  assert.deepEqual(proposal.files.map(f => [f.path, f.action, f.baseSha ?? null, f.additions, f.deletions]), [['README.md', 'update', blobSha(README), 1, 1], ['docs/NEW.md', 'create', null, 1, 0]]);
  assert.match(proposal.files[0].diff, /^--- a\/README.md\n\+\+\+ b\/README.md\n@@ -1,3 \+1,3 @@\n-# Dua AI\n\+# Dua AI \(personal\)\n \n Personal assistant\.$/);
  assert.match(proposal.files[1].diff, /^--- \/dev\/null\n\+\+\+ b\/docs\/NEW.md\n@@ -0,0 \+1,1 @@\n\+New$/);
  assert.equal(fetcher.writes().length, 0, 'the chat made no write request');
  assert.ok(fetcher.gh().every(c => c.headers.get('authorization') === 'Bearer ' + READ), 'the write token is never used by the chat');
  assert.ok(fetcher.calls.every(c => !String(c.body ?? '').includes(WRITE) && ![...c.headers.values()].some(v => v.includes(WRITE))));
  assert.doesNotMatch(text + lines.join('\n'), new RegExp(WRITE));
  // Streaming: the proposal event comes after the files-read event and before the text.
  const streamed = github({ gemini: [geminiReply([call('read_file', { path: 'README.md' })]), geminiReply([call('propose_changes', { title: 'T', summary: 'S', changes: [{ path: 'README.md', action: 'delete' }] })]), geminiReply([{ text: '' }])] });
  const events = await readEvents(await handleChat(authedChat({ model: 'Gemini', mode: 'build', repository: REPO, stream: true, messages: [{ role: 'user', text: 'Delete the README.' }] }), { env: prEnv, fetcher: streamed }));
  assert.deepEqual(events.map(e => e.type), ['build', 'build', 'build', 'build', 'proposal', 'text', 'done']);
  assert.equal(events[4].files[0].action, 'delete');
  assert.match(events[5].delta, /Review the changes below/, 'an empty answer after a proposal gets a default text');
  assert.equal(streamed.writes().length, 0);
});

test('proposal validation: read before update, no stale creates, truncated files, workflows, duplicates, caps', () => {
  const read = new Map([['README.md', { path: 'README.md', text: README, sha: blobSha(README), truncated: false }], ['big.txt', { path: 'big.txt', text: 'x', sha: 'b'.repeat(40), truncated: true }]]);
  const tree = { files: [{ path: 'README.md' }, { path: 'big.txt' }, { path: 'web/a.mjs' }] };
  const run = changes => proposeChanges({ title: 'T', summary: 'S', changes }, { repository: REPO, branch: 'main', read, tree, limits: prLimits({}) });
  assert.match(run([{ path: 'web/a.mjs', action: 'update', content: 'x' }]).response.error, /Read web\/a.mjs in full/);
  assert.match(run([{ path: 'README.md', action: 'create', content: 'x' }]).response.error, /already exists/);
  assert.match(run([{ path: 'big.txt', action: 'update', content: 'y' }]).response.error, /too large to read in full/);
  assert.match(run([{ path: 'README.md', action: 'update', content: README }]).response.error, /identical/);
  assert.match(run([{ path: '.github/workflows/ci.yml', action: 'create', content: 'on: push' }]).response.error, /workflows/);
  assert.match(run([{ path: 'a.md', action: 'create', content: 'x' }, { path: 'a.md', action: 'create', content: 'y' }]).response.error, /twice/);
  assert.match(run([{ path: 'a', action: 'create', content: 'x' }, { path: 'a/b', action: 'create', content: 'y' }]).response.error, /both as a file and as a folder/);
  assert.match(run([{ path: 'n.bin', action: 'create', content: 'a\u0000b' }]).response.error, /binary/);
  assert.match(run(Array.from({ length: 21 }, (_, i) => ({ path: `f${i}.md`, action: 'create', content: 'x' }))).response.error, /Too many files.*20/);
  assert.match(run([{ path: 'big.md', action: 'create', content: 'x'.repeat(201 * 1024) }]).response.error, /per-file limit is 200 KB/);
  assert.match(run([]).response.error, /at least one/);
  assert.equal(run([{ path: './new.md', action: 'create', content: 'x' }]).proposal.files[0].path, 'new.md');
  assert.equal(proposeChanges({ title: '  ', changes: [] }, { repository: REPO, branch: 'main', read, tree, limits: prLimits({}) }).response.error.startsWith('Give the pull request a short title.'), true);
});

test('create-pr is owner-only: anonymous, wrong account and foreign origins never reach GitHub', async () => {
  const anonymous = await create(approved(), { token: null });
  assert.equal(anonymous.status, 401);
  assert.match(anonymous.body.error, /Sign in from Profile to use Build pull requests/);
  assert.equal(anonymous.fetcher.gh().length, 0);
  const stranger = github({ email: 'someone@example.com' });
  const denied = await create(approved(), { fetcher: stranger });
  assert.equal(denied.status, 403);
  assert.match(denied.body.error, /Only the configured personal account/);
  assert.equal(stranger.gh().length, 0);
  const foreign = await handleService(prRequest(approved(), { origin: 'https://evil.test' }), { env: prEnv, fetcher: github() });
  assert.equal(foreign.status, 403);
  const get = await handleService(new Request('https://dua.test/api/build/pr'), { env: prEnv, fetcher: github() });
  assert.equal(get.status, 405);
});

test('blocked paths, symlinks and submodules are refused before anything is written', async () => {
  const bad = ['.github/workflows/ci.yml', '.GitHub/Workflows/x.yml', '.git/config', 'src/.git/hooks/pre-commit', '../outside.txt', 'a/../../b', '/etc/passwd', 'a//b', './a', 'C:/x', 'a\\b', '.gitmodules', ' README.md'];
  for (const path of bad) {
    assert.ok(pathProblem(path), path);
    const { status, fetcher } = await create(approved({ files: [{ path, action: 'create', content: 'x' }] }));
    assert.equal(status, 400, path);
    assert.equal(fetcher.gh().length, 0, path);
  }
  for (const [path, message] of [['link', /symlink/], ['link/inside.txt', /link is a symlink/], ['vendor/lib', /submodule/], ['vendor/lib/x.c', /submodule/], ['README.md/x', /README.md is a file/], ['web', /folder/]]) {
    const { status, body, fetcher } = await create(approved({ files: [{ path, action: 'create', content: 'x' }] }));
    assert.equal(status, 400, path);
    assert.match(body.error, message, path);
    assert.equal(fetcher.writes().length, 0, path);
  }
  assert.equal(pathProblem('docs/.github/workflows-notes.md'), null);
  assert.equal(pathProblem('web/src/core/build-pr.mjs'), null);
});

test('size caps (files, per-file, total, body) are enforced server-side and configurable', async () => {
  const many = await create(approved({ files: Array.from({ length: 21 }, (_, i) => ({ path: `f${i}.md`, action: 'create', content: 'x' })) }));
  assert.equal(many.status, 413);
  assert.match(many.body.error, /limit is 20/);
  const big = await create(approved({ files: [{ path: 'big.md', action: 'create', content: 'x'.repeat(200 * 1024 + 1) }] }));
  assert.equal(big.status, 413);
  const env = { ...prEnv, DUA_BUILD_PR_MAX_FILES: '2', DUA_BUILD_PR_FILE_MAX_KB: '4', DUA_BUILD_PR_MAX_KB: '6' };
  assert.equal((await create(approved({ files: [1, 2, 3].map(i => ({ path: `f${i}.md`, action: 'create', content: 'x' })) }), { env })).status, 413);
  const total = await create(approved({ files: [1, 2].map(i => ({ path: `f${i}.md`, action: 'create', content: 'é'.repeat(1600) })) }), { env });
  assert.equal(total.status, 413);
  assert.match(total.body.error, /limit is 6 KB/, 'sizes are UTF-8 bytes');
  const body = await create(approved({ summary: 'x', files: [{ path: 'a.md', action: 'create', content: 'x'.repeat(20 * 1024) }] }), { env });
  assert.equal(body.status, 413);
  for (const result of [many, big, total, body]) assert.equal(result.fetcher.gh().length, 0);
});

test('conflict detection: a file changed, deleted or created on main since the proposal is refused (409)', async () => {
  const changed = await create(approved({ files: [{ path: 'README.md', action: 'update', content: 'new\n', baseSha: blobSha('# Old README\n') }] }));
  assert.equal(changed.status, 409);
  assert.match(changed.body.error, /README.md changed on main since Dua read it.*regenerate/);
  const exists = await create(approved({ files: [{ path: 'web/src/a.mjs', action: 'create', content: 'x' }] }));
  assert.equal(exists.status, 409);
  assert.match(exists.body.error, /now exists on main/);
  const gone = await create(approved({ files: [{ path: 'docs/gone.md', action: 'delete', baseSha: 'b'.repeat(40) }] }));
  assert.equal(gone.status, 409);
  assert.match(gone.body.error, /no longer exists/);
  const missingSha = await create(approved({ files: [{ path: 'README.md', action: 'update', content: 'x' }] }));
  assert.equal(missingSha.status, 400);
  for (const result of [changed, exists, gone, missingSha]) assert.equal(result.fetcher.writes().length, 0);
});

test('success: blobs → tree → commit → new dua/* ref → draft PR with the Dua note; modes kept, deletes applied', async () => {
  const fetcher = github();
  const body = approved({ files: [
    { path: 'README.md', action: 'update', content: '# Dua AI (personal)\n', baseSha: blobSha(README) },
    { path: 'scripts/build.sh', action: 'update', content: SCRIPT + 'echo done\n', baseSha: blobSha(SCRIPT) },
    { path: 'web/src/a.mjs', action: 'delete', baseSha: blobSha('export const a = 1;\n') },
    { path: 'docs/new/guide.md', action: 'create', content: '# Guide\n' }
  ] });
  const result = await create(body, { fetcher });
  const lines = result.lines;
  const logs = lines.map(line => JSON.parse(line));
  assert.deepEqual(logs.map(row => [row.event, row.stage]).filter(([, stage]) => stage), [
    ['stage.started', 'pr_submission'], ['stage.finished', 'pr_submission']
  ]);
  assert.equal(logs.find(row => row.event === 'stage.finished').outcome, 'success');
  assert.equal(new Set(logs.map(row => row.requestId)).size, 1);
  assert.equal(result.status, 200, JSON.stringify(result.body));
  assert.deepEqual({ ...result.body, commit: undefined }, { ok: true, repository: REPO, branch: BRANCH, base: 'main', commit: undefined, url: `https://github.com/${REPO}/pull/80`, number: 80, draft: true, state: 'open', existing: false });
  const writes = fetcher.writes().map(c => c.url.slice(GH.length + `/repos/${REPO}`.length));
  assert.deepEqual(writes, ['/git/blobs', '/git/blobs', '/git/blobs', '/git/trees', '/git/commits', '/git/refs', '/pulls']);
  const [tree] = fetcher.state.trees;
  assert.equal(tree.base_tree, treeSha(''));
  assert.deepEqual(tree.tree.map(e => [e.path, e.mode, e.sha === null ? null : 'sha']), [['README.md', '100644', 'sha'], ['scripts/build.sh', '100755', 'sha'], ['web/src/a.mjs', '100644', null], ['docs/new/guide.md', '100644', 'sha']]);
  const commit = fetcher.writes().find(c => c.url.endsWith('/git/commits')).json;
  assert.deepEqual(commit.parents, [HEAD], 'the commit sits on top of the current default branch');
  assert.match(commit.message, /^Fix README title\n\nRenames the title\.[\s\S]*Dua-Proposal-Id: 0f8fad5b-d9cb-469f-a165-70867728950e$/);
  assert.deepEqual(fetcher.writes().find(c => c.url.endsWith('/git/refs')).json, { ref: 'refs/heads/' + BRANCH, sha: result.body.commit });
  const pr = fetcher.writes().find(c => c.url.endsWith('/pulls')).json;
  assert.equal(pr.head, BRANCH);
  assert.equal(pr.base, 'main');
  assert.equal(pr.draft, true);
  assert.match(pr.body, /Created by \*\*Dua\*\* \(Build mode\) from the owner's request/);
  assert.match(pr.body, /Dua never merges/);
  assert.match(pr.body, /`docs\/new\/guide.md` \(added\)/);
  assert.match(pr.body, /<!-- dua-proposal-id: 0f8fad5b/);
  assert.ok(!fetcher.calls.some(c => /\/merge|\/git\/refs\/heads/.test(c.url) && c.method !== 'GET'), 'no merge, no ref update');
  assert.ok(fetcher.writes().every(c => c.headers.get('authorization') === 'Bearer ' + WRITE));
  assert.doesNotMatch(lines.join('\n'), new RegExp(WRITE));
  // DUA_BUILD_PR_DRAFT=0 opens a normal pull request.
  const ready = await create(approved(), { env: { ...prEnv, DUA_BUILD_PR_DRAFT: '0' } });
  assert.equal(ready.body.draft, false);
  assert.equal(ready.fetcher.writes().find(c => c.url.endsWith('/pulls')).json.draft, false);
  // Repositories without draft support get a normal pull request and a notice.
  const fallback = await create(approved(), { fetcher: github({ draftUnsupported: true }) });
  assert.equal(fallback.status, 200);
  assert.equal(fallback.body.draft, false);
  assert.match(fallback.body.notice, /Draft pull requests are not available/);
});

test('branch naming: dua/<slug>-<yymmdd-hhmmss>, never the default branch or another prefix', async () => {
  const at = Date.UTC(2026, 9, 8, 13, 45, 12);
  assert.equal(branchName('Fix README title!', at), BRANCH);
  assert.equal(branchName('Ünïcode & émoji 🎉 only', at), 'dua/unicode-emoji-only-261008-134512');
  assert.equal(branchName('!!!', at), 'dua/changes-261008-134512');
  assert.ok(branchName('a'.repeat(200), at).length <= 4 + 40 + 14);
  for (const branch of ['main', 'feature/x', 'dua/', 'dua/../main', 'dua/UPPER', 'dua/a b', 'refs/heads/dua/x', 'dua/x/y']) {
    const { status, body, fetcher } = await create(approved({ branch }));
    assert.equal(status, 400, branch);
    assert.match(body.error, /new dua\/\.\.\. branch/, branch);
    assert.equal(fetcher.gh().length, 0);
  }
});

test('idempotent: a second tap returns the same pull request; a foreign branch with the same name is refused', async () => {
  const fetcher = github();
  const first = await create(approved(), { fetcher });
  const writesAfterFirst = fetcher.writes().length;
  const second = await create(approved(), { fetcher });
  assert.equal(second.status, 200);
  assert.equal(second.body.url, first.body.url);
  assert.equal(second.body.existing, true);
  assert.equal(fetcher.writes().length, writesAfterFirst, 'no second commit, ref or pull request');
  assert.equal(fetcher.state.pulls.length, 1);
  // Branch made, pull request not (earlier attempt failed in between): the retry only opens the pull request.
  const half = github();
  half.state.refs.set(BRANCH, (() => { const sha = createHash('sha1').update('m').digest('hex'); half.state.commits.set(sha, { sha, message: `x\n\nDua-Proposal-Id: ${ID}` }); return sha; })());
  const resumed = await create(approved(), { fetcher: half });
  assert.equal(resumed.status, 200);
  assert.deepEqual(half.writes().map(c => c.url.split('/').at(-1)), ['pulls']);
  // Someone else's branch with this name.
  const taken = github();
  taken.state.refs.set(BRANCH, (() => { const sha = 'd'.repeat(40); taken.state.commits.set(sha, { sha, message: 'Unrelated work' }); return sha; })());
  const refused = await create(approved(), { fetcher: taken });
  assert.equal(refused.status, 409);
  const refusalLogs = refused.lines.map(line => JSON.parse(line));
  assert.equal(refusalLogs.find(row => row.event === 'stage.finished').outcome, 'error');
  assert.equal(refusalLogs.find(row => row.event === 'stage.finished').status, 409);
  assert.equal(refusalLogs.at(-1).status, 409);
  assert.match(refused.body.error, /already exists and was not made for this proposal/);
  assert.equal(taken.writes().length, 0);
  // A double tap racing to create the ref: the loser resumes instead of failing.
  const race = await create(approved(), { fetcher: github({ refsRace: true }) });
  assert.equal(race.status, 200);
  assert.equal(race.fetcher.state.pulls.length, 1);
});

test('repository allowlist: DUA_BUILD_PR_REPOS, or only repositories owned by the write token account', async () => {
  const listed = await create(approved(), { env: { ...prEnv, DUA_BUILD_PR_REPOS: 'KRFbrothers/notes, KRFbrothers/other' } });
  assert.equal(listed.status, 403);
  assert.match(listed.body.error, /DUA_BUILD_PR_REPOS/);
  assert.equal(listed.fetcher.gh().length, 0);
  const allowed = await create(approved(), { env: { ...prEnv, DUA_BUILD_PR_REPOS: 'krfbrothers/dua-ai' } });
  assert.equal(allowed.status, 200);
  assert.ok(!allowed.fetcher.gh().some(c => c.url === GH + '/user'), 'with an allowlist the token owner is not needed');
  const foreign = await create(approved(), { fetcher: github({ owner: 'someone-else' }) });
  assert.equal(foreign.status, 403);
  assert.match(foreign.body.error, /owned by the write token's account/);
  assert.equal(foreign.fetcher.writes().length, 0);
  // The model is not offered the tool for repositories outside the allowlist.
  const chat = github({ gemini: [body => { assert.deepEqual(body.tools[0].functionDeclarations.map(f => f.name), ['list_files', 'read_file', 'search_code']); return geminiReply([{ text: 'ok' }]); }] });
  await handleChat(authedChat({ model: 'Dua', mode: 'build', repository: REPO, messages: [{ role: 'user', text: 'hi' }] }), { env: { ...prEnv, DUA_BUILD_PR_REPOS: 'KRFbrothers/notes' }, fetcher: chat });
});

test('write client: GET/POST allowlist only (no PATCH/PUT/DELETE), dua/* refs and heads only, safe errors', async () => {
  let fetched = 0;
  const fetcher = async () => { fetched += 1; return Response.json({}); };
  const env = { GITHUB_BUILD_WRITE_TOKEN: WRITE };
  const refused = [['PATCH', `/repos/${REPO}/git/refs/heads/main`, { sha: HEAD, force: true }], ['PUT', `/repos/${REPO}/pulls/1/merge`, {}], ['DELETE', `/repos/${REPO}/git/refs/heads/dua/x`],
    ['POST', `/repos/${REPO}/merges`, {}], ['POST', `/repos/${REPO}/git/refs`, { ref: 'refs/heads/main', sha: HEAD }], ['POST', `/repos/${REPO}/git/refs`, { ref: 'refs/heads/dua/x', sha: HEAD, force: true }],
    ['POST', `/repos/${REPO}/git/refs`, { ref: 'refs/heads/dua/../main', sha: HEAD }], ['POST', `/repos/${REPO}/pulls`, { head: 'main', base: 'dev' }], ['GET', '/user/repos'], ['POST', '/user/repos', {}],
    ['POST', `/repos/${REPO}/contents/x`, {}], ['GET', `/repos/${REPO}/git/trees/HEAD?recursive=1`]];
  for (const [method, path, body] of refused) await assert.rejects(githubWrite(method, path, { env, fetcher, body }), error => error instanceof GitHubWriteError && error.status === 400, `${method} ${path}`);
  assert.equal(fetched, 0);
  assert.ok(ALLOWED_WRITE_REQUESTS.every(([method]) => method === 'GET' || method === 'POST'));
  const source = readFileSync(new URL('../src/core/github-write.mjs', import.meta.url), 'utf8') + readFileSync(new URL('../src/core/build-pr.mjs', import.meta.url), 'utf8');
  assert.doesNotMatch(source, /'(PATCH|PUT|DELETE)'/, 'no update, merge or delete requests exist');
  assert.doesNotMatch(readFileSync(new URL('../src/core/build-chat.mjs', import.meta.url), 'utf8'), /github-write|githubWrite|createPullRequest|GITHUB_BUILD_WRITE_TOKEN/, 'the model loop cannot reach the write client');
  await assert.rejects(githubWrite('GET', '/user', { env: {}, fetcher }), error => error.status === 503);
  const failing = status => async () => new Response(JSON.stringify({ message: `Bad credentials ${WRITE}` }), { status });
  for (const [status, expected] of [[401, 502], [403, 403], [404, 404], [422, 422], [500, 502]]) {
    await assert.rejects(githubWrite('GET', '/user', { env, fetcher: failing(status) }), error => error.status === expected && !error.message.includes(WRITE) && !error.message.includes('Bad credentials'));
  }
  const { status, body } = await create(approved(), { fetcher: router([[url => url.startsWith(GH), () => new Response(JSON.stringify({ message: `token ${WRITE} revoked` }), { status: 401 })]]) });
  assert.equal(status, 502);
  assert.match(body.error, /write token .* invalid or expired/);
});

test('request validation and the unified diff used by the review card', () => {
  const limits = prLimits({});
  assert.throws(() => validatePrRequest({ ...approved(), extra: 1 }, limits), /Invalid pull request data.*regenerate.*review/);
  assert.throws(() => validatePrRequest(approved({ proposalId: 'x' }), limits), /proposal id/);
  assert.throws(() => validatePrRequest(approved({ title: 'x'.repeat(121) }), limits), /1 to 120/);
  assert.throws(() => validatePrRequest(approved({ repository: '../x' }), limits), /owner\/name/);
  assert.throws(() => checkChanges([{ path: 'a', action: 'delete', content: 'x' }], limits), /no content/);
  assert.throws(() => checkChanges([{ path: 'a', action: 'rename' }], limits), /create, update or delete/);
  assert.equal(validatePrRequest(approved({ title: '  Edited\ttitle  ' }), limits).title, 'Edited title');
  const middle = unifiedDiff('a.txt', 'one\ntwo\nthree\nfour\nfive\nsix\nseven\neight\nnine\nten\n', 'one\ntwo\nthree\nfour\nFIVE\nsix\nseven\neight\nnine\nten\neleven\n');
  assert.equal(middle.diff, '--- a/a.txt\n+++ b/a.txt\n@@ -2,9 +2,10 @@\n two\n three\n four\n-five\n+FIVE\n six\n seven\n eight\n nine\n ten\n+eleven');
  assert.equal(middle.additions, 2);
  assert.equal(middle.deletions, 1);
  const apart = unifiedDiff('b', Array.from({ length: 30 }, (_, i) => `l${i}`).join('\n'), Array.from({ length: 30 }, (_, i) => i === 2 || i === 25 ? `L${i}` : `l${i}`).join('\n'));
  assert.equal((apart.diff.match(/^@@/gm) || []).length, 2, 'distant changes get separate hunks');
  assert.equal(unifiedDiff('c', 'x\n', null).diff, '--- a/c\n+++ /dev/null\n@@ -1,1 +0,0 @@\n-x');
  const huge = unifiedDiff('d', null, 'line\n'.repeat(20000));
  assert.equal(huge.truncated, true);
  assert.ok(huge.diff.length <= DIFF_MAX_CHARS + 200);
  assert.equal(huge.additions, 20000);
});

test('PR failure recovery reaches the owner without exposing write-token or upstream details', async () => {
  for (const [upstream, expected, hint] of [
    [401, 502, /Replace it.*redeploy.*same proposal/],
    [403, 403, /GITHUB_BUILD_WRITE_TOKEN.*If rate-limited.*reset/],
    [404, 404, /selected-repository access for GITHUB_BUILD_WRITE_TOKEN/],
    [409, 409, /regenerate the proposal/],
    [422, 422, /Review the proposal.*same proposal/],
    [429, 429, /Wait.*limit to reset.*same proposal/],
    [500, 502, /retry the same proposal.*existing branch or pull request/]
  ]) {
    const fetcher = router([[url => url.startsWith(GH), () => Response.json({ message: `raw upstream ${WRITE}` }, { status: upstream })]]);
    const result = await create(approved(), { fetcher });
    assert.equal(result.status, expected);
    assert.match(result.body.error, hint);
    assert.doesNotMatch(JSON.stringify(result.body), /raw upstream/);
    const calls = fetcher.calls.filter(call => call.url.startsWith(GH));
    assert.equal(calls.length, 1, 'no automatic retries or writes after a failed repository read');
    assert.equal(calls[0].method, 'GET');
  }
  const missing = await create(approved(), { env: { ...prEnv, GITHUB_BUILD_WRITE_TOKEN: '' } });
  assert.equal(missing.status, 503);
  assert.match(missing.body.error, /GITHUB_BUILD_WRITE_TOKEN.*selected repositories/);
  assert.equal(missing.fetcher.gh().length, 0);
  const network = await create(approved(), { fetcher: router([[url => url.startsWith(GH), () => { throw new Error(`network ${WRITE}`); }]]) });
  assert.equal(network.status, 502);
  assert.match(network.body.error, /retry the same proposal.*existing branch or pull request/);
});

test('unsafe PR paths offer a valid example without reflecting the rejected value', async () => {
  const result = await create(approved({ files: [{ path: '../private-value', action: 'create', content: 'x' }] }));
  assert.equal(result.status, 400);
  assert.match(result.body.error, /relative to the repository root.*README.md/);
  assert.doesNotMatch(result.body.error, /private-value/);
  assert.equal(result.fetcher.gh().length, 0);
});
