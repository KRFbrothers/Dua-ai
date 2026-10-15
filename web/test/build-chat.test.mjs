// Phase 5B: read-only, owner-only Build mode with mocked GitHub and Gemini. Nothing touches the network.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { handleChat, validateChat } from '../src/core/providers.mjs';
import { handleService } from '../src/server/service-handler.mjs';
import { advancedConfiguration } from '../src/core/advanced-integrations.mjs';
import { buildConfiguration, buildLimits, runBuildLoop, prepareRepository, FUNCTION_DECLARATIONS, treeListing } from '../src/core/build-chat.mjs';
import { githubGet, encodeRepoPath, ALLOWED_GITHUB_PATHS, GitHubError } from '../src/core/github.mjs';
import { formatEvent } from '../src/core/sse.mjs';
import { filesEnv, router, authedChat } from './files-helpers.mjs';
import { readEvents } from './helpers.mjs';

const REPO = 'KRFbrothers/Dua-ai';
const GH = 'https://api.github.com';
const GEMINI = 'https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent';
const TOKEN = 'ghp_connector_secret_test';
const buildEnv = { ...filesEnv, ENABLE_GITHUB_CONNECTOR: '1', GITHUB_CONNECTOR_TOKEN: TOKEN };
const b64 = text => Buffer.from(text).toString('base64');
const README = '# Dua AI\n\nPersonal assistant.\n';
const DIFF_REPLY = 'Change the title:\n\n```diff\n--- a/README.md\n+++ b/README.md\n@@ -1,3 +1,3 @@\n-# Dua AI\n+# Dua AI (personal)\n \n Personal assistant.\n```\n';
const FILES = { 'README.md': README, 'web/src/core/providers.mjs': 'export const x = 1;\n'.repeat(50), 'big.txt': 'a'.repeat(300 * 1024) };
const ask = (extra = {}) => ({ model: 'Dua', mode: 'build', repository: REPO, messages: [{ role: 'user', text: 'Rename the README title.' }], ...extra });
const call = (name, args, extra = {}) => ({ functionCall: { name, args }, ...extra });
const geminiReply = parts => Response.json({ candidates: [{ content: { role: 'model', parts }, finishReason: 'STOP' }], usageMetadata: { promptTokenCount: 10, candidatesTokenCount: 5 } });

/** GitHub fake (GET only; anything else fails the test) plus a scripted Gemini. */
function world({ gemini = [], files = FILES, email, tree } = {}) {
  const script = [...gemini];
  const fetcher = router([
    [url => url.startsWith(GH), (url, init) => {
      assert.equal(init.method, 'GET', 'GitHub requests must be GET');
      assert.equal(init.body, undefined);
      const path = url.slice(GH.length);
      if (path === `/repos/${REPO}`) return Response.json({ full_name: REPO, default_branch: 'main', private: false, description: 'Dua' });
      if (path === `/repos/${REPO}/git/trees/HEAD?recursive=1`) return Response.json(tree || { sha: 't', truncated: false, tree: [{ path: 'web', type: 'tree' }, ...Object.entries(files).map(([p, text]) => ({ path: p, type: 'blob', size: text.length }))] });
      if (path.startsWith(`/repos/${REPO}/contents/`)) {
        const name = decodeURIComponent(path.slice(`/repos/${REPO}/contents/`.length));
        if (name === 'huge.bin') return Response.json({ type: 'file', path: name, size: 2 * 1024 * 1024, encoding: 'none', content: '' });
        if (name === 'logo.png') return Response.json({ type: 'file', path: name, size: 4, encoding: 'base64', content: Buffer.from([0x89, 0x50, 0, 0x47]).toString('base64') });
        if (name === 'web') return Response.json([{ path: 'web/src', type: 'dir' }]);
        return files[name] === undefined ? new Response('{"message":"Not Found"}', { status: 404 }) : Response.json({ type: 'file', path: name, size: files[name].length, encoding: 'base64', content: b64(files[name]) });
      }
      if (path.startsWith('/search/code?')) return Response.json({ total_count: 1, items: [{ path: 'README.md', html_url: 'x', repository: { full_name: REPO } }] });
      if (path.startsWith('/repos/') && path.endsWith('/Dua-ai')) return new Response('{}', { status: 404 });
      throw new Error('Unexpected GitHub path ' + path);
    }],
    [url => url.startsWith(GEMINI), (url, init, recorded) => {
      const next = script.shift();
      if (!next) throw new Error('Unexpected extra Gemini round');
      return typeof next === 'function' ? next(recorded.json) : next;
    }]
  ], email ? { email } : undefined);
  fetcher.github = () => fetcher.calls.filter(c => c.url.startsWith(GH));
  fetcher.gemini = () => fetcher.calls.filter(c => c.url.startsWith(GEMINI));
  return fetcher;
}
const allGet = fetcher => fetcher.github().every(c => c.method === 'GET' && c.body === undefined);

test('Build config explains every missing piece and never leaks the token', async () => {
  const on = new Set(['Dua', 'Gemini']);
  assert.equal(buildConfiguration(buildEnv, on).enabled, true);
  assert.match(buildConfiguration({ ...buildEnv, DUA_BUILD: '0' }, on).reason, /DUA_BUILD=0/);
  assert.match(buildConfiguration({ ...buildEnv, ENABLE_GITHUB_CONNECTOR: '' }, on).reason, /ENABLE_GITHUB_CONNECTOR=1/);
  assert.match(buildConfiguration({ ...buildEnv, GITHUB_CONNECTOR_TOKEN: ' ' }, on).reason, /GITHUB_CONNECTOR_TOKEN/);
  assert.match(buildConfiguration({ ...buildEnv, DUA_ALLOWED_EMAIL: '' }, on).reason, /owner-only.*DUA_ALLOWED_EMAIL/);
  assert.match(buildConfiguration({ ...buildEnv, SUPABASE_URL: '' }, on).reason, /SUPABASE_URL/);
  assert.match(buildConfiguration(buildEnv, new Set()).reason, /GEMINI_API_KEY/);
  assert.deepEqual(buildLimits({}), { maxRounds: 6, fileMaxChars: 100 * 1024, totalMaxChars: 600 * 1024, maxFiles: 40 });
  assert.equal(buildLimits({ DUA_BUILD_MAX_ROUNDS: '99' }).maxRounds, 6, 'out-of-range values fall back to the default');
  const config = await (await handleChat(new Request('https://dua.test/api/config'), { env: buildEnv })).json();
  assert.equal(config.build.enabled, true);
  assert.equal(config.build.readOnly, true);
  assert.deepEqual(config.build.tools, ['list_files', 'read_file', 'search_code']);
  const off = await (await handleChat(new Request('https://dua.test/api/config'), { env: filesEnv })).json();
  assert.equal(off.build.enabled, false);
  assert.match(off.build.reason, /GitHub connector/);
  assert.doesNotMatch(JSON.stringify(config), new RegExp(TOKEN));
  assert.deepEqual(advancedConfiguration(buildEnv).connectors.github.actions, ['list-repositories', 'get-tree', 'get-file', 'search-code']);
});

test('chat validation: Build needs a repository and Dua/Gemini; repository is Build-only', () => {
  assert.equal(validateChat(ask()), null);
  const invalidRepository = ['Choose a repository for Build mode. Use owner/name (for example owner/repo), not a GitHub URL.', 400];
  assert.deepEqual(validateChat(ask({ repository: undefined })), invalidRepository);
  assert.deepEqual(validateChat(ask({ repository: '../etc/passwd' })), invalidRepository);
  assert.deepEqual(validateChat(ask({ repository: 'a/b/c' })), invalidRepository);
  assert.deepEqual(validateChat(ask({ model: 'ChatGPT' })), ['Build mode works with Dua or Gemini. Select one of them.', 400]);
  assert.deepEqual(validateChat({ ...ask(), mode: 'auto' }), ['A repository is only used in Build mode.', 400]);
  assert.equal(validateChat(ask({ model: 'Gemini' })), null);
});

test('owner gating: setup, sign-in and the allowed email are checked before GitHub or Gemini are called', async () => {
  const cases = [
    [{ ...buildEnv, ENABLE_GITHUB_CONNECTOR: '0' }, authedChat(ask()), 503, /ENABLE_GITHUB_CONNECTOR/],
    [{ ...buildEnv, DUA_ALLOWED_EMAIL: '' }, authedChat(ask()), 503, /DUA_ALLOWED_EMAIL/],
    [buildEnv, new Request('https://dua.test/api/chat', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(ask()) }), 401, /Sign in from Profile to use Build mode/]
  ];
  for (const [env, request, status, message] of cases) {
    const fetcher = world();
    const response = await handleChat(request, { env, fetcher });
    assert.equal(response.status, status);
    assert.match((await response.json()).error, message);
    assert.equal(fetcher.github().length + fetcher.gemini().length, 0);
  }
  const stranger = world({ email: 'someone@example.com' });
  const denied = await handleChat(authedChat(ask()), { env: buildEnv, fetcher: stranger });
  assert.equal(denied.status, 403);
  assert.match((await denied.json()).error, /Only the configured personal account can use Build mode/);
  assert.equal(stranger.github().length + stranger.gemini().length, 0);
});

test('tool loop: reads files with GET only, echoes model turns verbatim (thought signatures) and returns a diff', async () => {
  let second;
  const fetcher = world({ gemini: [
    body => {
      assert.deepEqual(body.tools[0].functionDeclarations.map(f => f.name), ['list_files', 'read_file', 'search_code']);
      assert.equal(body.toolConfig.functionCallingConfig.mode, 'AUTO');
      const system = body.systemInstruction.parts.map(p => p.text).join('\n');
      assert.match(system, /read-only/i);
      assert.match(system, /```diff/);
      assert.match(system, /untrusted/);
      assert.match(system, /README\.md\nbig\.txt|README\.md/);
      assert.doesNotMatch(JSON.stringify(body), new RegExp(TOKEN));
      return geminiReply([call('read_file', { path: 'README.md' }, { thoughtSignature: 'sig-1' }), call('list_files', { path_prefix: 'web/' }), call('search_code', { query: 'title repo:evil/other' })]);
    },
    body => { second = body; return geminiReply([{ text: DIFF_REPLY }]); }
  ] });
  const response = await handleChat(authedChat(ask({ memory: 'Prefer short answers.' })), { env: buildEnv, fetcher });
  assert.equal(response.status, 200);
  const reply = await response.json();
  assert.match(reply.text, /```diff\n--- a\/README.md/);
  assert.deepEqual(reply.build.files, [{ path: 'README.md', bytes: README.length }]);
  assert.equal(reply.build.repository, REPO);
  assert.equal(reply.build.branch, 'main');
  assert.equal(reply.build.rounds, 2);
  assert.equal(reply.build.searches, 1);
  const [user, model, tools] = second.contents;
  assert.equal(user.role, 'user');
  assert.deepEqual(model, { role: 'model', parts: [call('read_file', { path: 'README.md' }, { thoughtSignature: 'sig-1' }), call('list_files', { path_prefix: 'web/' }), call('search_code', { query: 'title repo:evil/other' })] });
  assert.equal(tools.role, 'user');
  const [read, listed, searched] = tools.parts.map(p => p.functionResponse);
  assert.equal(read.name, 'read_file');
  assert.equal(read.response.content, README);
  assert.deepEqual(listed.response.files, ['web/src/core/providers.mjs (1000 bytes)']);
  assert.deepEqual(searched.response.results, [{ path: 'README.md' }]);
  const search = fetcher.github().find(c => c.url.includes('/search/code'));
  assert.match(decodeURIComponent(search.url), /q=title repo:KRFbrothers\/Dua-ai&/, 'repo: qualifiers from the model are stripped; search stays in the chosen repository');
  assert.ok(allGet(fetcher));
  assert.ok(fetcher.github().every(c => c.headers.get('authorization') === 'Bearer ' + TOKEN));
  assert.ok(fetcher.gemini().every(c => !c.headers.get('authorization') && !c.body.includes(TOKEN)), 'the GitHub token never goes to Gemini');
});

test('streaming Build: build events with files read, then the answer text and done', async () => {
  const fetcher = world({ gemini: [geminiReply([call('read_file', { path: 'README.md' })]), geminiReply([{ text: DIFF_REPLY }])] });
  const response = await handleChat(authedChat(ask({ stream: true, attachments: [], code: true })), { env: buildEnv, fetcher });
  assert.equal(response.headers.get('content-type'), 'text/event-stream; charset=utf-8');
  const events = await readEvents(response);
  assert.deepEqual(events.map(e => e.type), ['build', 'notice', 'build', 'build', 'text', 'done']);
  assert.deepEqual(events[0].files, []);
  assert.match(events[1].message, /Run code are off in Build mode/);
  assert.deepEqual(events[3].files, [{ path: 'README.md', bytes: README.length }]);
  assert.equal(events[4].delta, DIFF_REPLY.trim());
  assert.equal(formatEvent({ type: 'build', repository: REPO, files: [] }).startsWith('event: build\n'), true);
});

test('loop limits: rounds are capped and the last round forces a plain answer (mode NONE)', async () => {
  const modes = [];
  const always = body => { modes.push(body.toolConfig.functionCallingConfig.mode); return geminiReply([call('list_files', {})]); };
  const capped = world({ gemini: [always, always, body => { modes.push(body.toolConfig.functionCallingConfig.mode); return geminiReply([{ text: 'Answer from what I read.' }]); }] });
  const reply = await (await handleChat(authedChat(ask()), { env: { ...buildEnv, DUA_BUILD_MAX_ROUNDS: '3' }, fetcher: capped })).json();
  assert.equal(reply.text, 'Answer from what I read.');
  assert.deepEqual(modes, ['AUTO', 'AUTO', 'NONE']);
  assert.equal(capped.gemini().length, 3);
  const stubborn = world({ gemini: [always, always, always] });
  const failed = await handleChat(authedChat(ask()), { env: { ...buildEnv, DUA_BUILD_MAX_ROUNDS: '3' }, fetcher: stubborn });
  assert.equal(failed.status, 422);
  assert.match((await failed.json()).error, /did not return an answer/);
  assert.equal(stubborn.gemini().length, 3, 'never more rounds than the cap');
});

test('deadline: with little time left the loop answers immediately instead of calling more tools', async () => {
  const fetcher = world({ gemini: [body => { assert.equal(body.toolConfig.functionCallingConfig.mode, 'NONE'); return geminiReply([{ text: 'Short answer.' }]); }] });
  const prepared = await prepareRepository(REPO, { env: buildEnv, fetcher });
  const clock = 1_000_000;
  const result = await runBuildLoop({ model: 'gemini-3.8-flash', key: 'k', env: buildEnv, fetcher, prepared, messages: ask().messages, startedAt: clock - 250_000, now: () => clock, deadlineMs: 280_000 });
  assert.equal(result.text, 'Short answer.');
  assert.equal(result.build.rounds, 1);
});

test('size caps: per-file cut, total budget, file count, cache, binary/oversized/folder refusals and path safety', async () => {
  const reads = (...paths) => geminiReply(paths.map(path => call('read_file', { path })));
  let tools;
  const fetcher = world({ gemini: [reads('big.txt', 'README.md', 'README.md', 'huge.bin', 'logo.png', 'web', '../secrets', '/etc/passwd'), body => { tools = body.contents[2].parts.map(p => p.functionResponse.response); return geminiReply([{ text: 'ok' }]); }] });
  const env = { ...buildEnv, DUA_BUILD_FILE_MAX_KB: '64' };
  const reply = await (await handleChat(authedChat(ask()), { env, fetcher })).json();
  assert.equal(tools[0].content.length, 64 * 1024);
  assert.equal(tools[0].truncated, true);
  assert.equal(tools[1].content, README);
  assert.equal(tools[2].content, README, 'second read of the same file is served from cache');
  assert.match(tools[3].error, /over 1 MB/);
  assert.match(tools[4].error, /binary/);
  assert.match(tools[5].error, /folder/);
  assert.match(tools[6].error, /relative to the repository root/);
  assert.match(tools[7].error, /relative to the repository root/, 'absolute paths are rejected before any GitHub read');
  assert.deepEqual(reply.build.files, [{ path: 'big.txt', bytes: 64 * 1024, truncated: true }, { path: 'README.md', bytes: README.length }]);
  assert.equal(fetcher.github().filter(c => c.url.includes('/contents/README.md')).length, 1);
  assert.equal(fetcher.github().filter(c => c.url.includes('secrets')).length, 0);
  assert.equal(fetcher.github().filter(c => c.url.includes('passwd')).length, 0);

  // Reject controls before normalization/cache lookup, including a previously cached file.
  let guarded;
  const unsafe = world({ gemini: [reads('README.md', 'README.md\n', '/README.md', './README.md', '\tREADME.md'), body => {
    guarded = body.contents[2].parts.map(p => p.functionResponse.response);
    return geminiReply([{ text: 'ok' }]);
  }] });
  await handleChat(authedChat(ask()), { env: buildEnv, fetcher: unsafe });
  assert.equal(guarded[0].content, README);
  assert.equal(guarded[3].content, README);
  for (const i of [1, 2, 4]) assert.match(guarded[i].error, /relative to the repository root/);
  assert.equal(unsafe.github().filter(c => c.url.includes('/contents/')).length, 1);

  // Total budget: 16 KB total with 64 KB files → the second large read is refused.
  let budget;
  const small = world({ gemini: [reads('big.txt', 'web/src/core/providers.mjs'), body => { budget = body.contents[2].parts.map(p => p.functionResponse.response); return geminiReply([{ text: 'ok' }]); }] });
  await handleChat(authedChat(ask()), { env: { ...buildEnv, DUA_BUILD_MAX_KB: '16' }, fetcher: small });
  assert.equal(budget[0].content.length, 16 * 1024);
  assert.match(budget[1].error, /budget for this reply is used up/);

  // File count and per-round call caps.
  let limited;
  const many = world({ gemini: [reads('README.md', 'big.txt', 'web/src/core/providers.mjs', 'README.md', 'x1', 'x2', 'x3', 'x4', 'x5'), body => { limited = body.contents[2].parts.map(p => p.functionResponse.response); return geminiReply([{ text: 'ok' }]); }] });
  await handleChat(authedChat(ask()), { env: { ...buildEnv, DUA_BUILD_MAX_FILES: '2' }, fetcher: many });
  assert.match(limited[2].error, /File limit for this reply reached \(2 files\)/);
  assert.equal(limited[3].content, README);
  assert.match(limited[8].error, /Too many tool calls in one round/);
  assert.equal(limited.length, 9);
});

test('read-only guarantee: the GitHub client only GETs allowlisted paths and the model has no write tools', async () => {
  let fetched = 0;
  const fetcher = async () => { fetched += 1; return Response.json({}); };
  for (const path of [`/repos/${REPO}/git/refs`, `/repos/${REPO}/pulls`, `/repos/${REPO}/merges`, `/repos/${REPO}/contents/a?ref=x`, `/repos/${REPO}/git/trees/HEAD`, '/user', 'https://evil.test/x', `/repos/${REPO}/../../user`]) {
    await assert.rejects(githubGet(path, { env: buildEnv, fetcher }), error => error instanceof GitHubError && error.status === 400, path);
  }
  assert.equal(fetched, 0);
  assert.equal(ALLOWED_GITHUB_PATHS.length, 5);
  const source = readFileSync(new URL('../src/core/github.mjs', import.meta.url), 'utf8');
  assert.match(source, /method: 'GET'/);
  assert.doesNotMatch(source, /'(POST|PUT|PATCH|DELETE)'/, 'github.mjs has no write methods');
  assert.deepEqual(FUNCTION_DECLARATIONS.map(f => f.name), ['list_files', 'read_file', 'search_code']);
  assert.equal(encodeRepoPath('docs/../x'), null);
  assert.equal(encodeRepoPath('a b/c#d.md'), 'a%20b/c%23d.md');
  assert.equal(treeListing({ files: Array.from({ length: 2000 }, (_, i) => ({ path: `deep/folder/structure/file-${i}.kt` })).concat([{ path: 'README.md' }]), total: 2001, truncated: false }).includes('README.md'), true, 'shallow paths are kept when the tree is trimmed');
});

test('GitHub connector route: owner-only read actions; write-like actions and extra keys are refused', async () => {
  const post = (body, token = 'user-token') => new Request('https://dua.test/api/connectors/github', { method: 'POST', headers: { 'content-type': 'application/json', ...(token ? { authorization: 'Bearer ' + token } : {}) }, body: JSON.stringify(body) });
  const fetcher = world();
  const tree = await (await handleService(post({ action: 'get-tree', repository: REPO }), { env: buildEnv, fetcher })).json();
  assert.equal(tree.total, 3);
  assert.equal(tree.readOnly, true);
  const file = await (await handleService(post({ action: 'get-file', repository: REPO, path: 'big.txt' }), { env: buildEnv, fetcher })).json();
  assert.equal(file.text.length, 100 * 1024);
  assert.equal(file.truncated, true);
  const found = await (await handleService(post({ action: 'search-code', repository: REPO, query: 'Dua' }), { env: buildEnv, fetcher })).json();
  assert.deepEqual(found.results, [{ path: 'README.md' }]);
  const missing = await handleService(post({ action: 'get-file', repository: REPO, path: 'nope.md' }), { env: buildEnv, fetcher });
  assert.equal(missing.status, 404);
  for (const body of [{ action: 'create-branch', repository: REPO }, { action: 'get-file', repository: REPO, path: 'a', content: 'x' }, { action: 'list-repositories', method: 'DELETE' }]) {
    const refused = await handleService(post(body), { env: buildEnv, fetcher });
    assert.equal(refused.status, 400, JSON.stringify(body));
  }
  const anonymous = await handleService(post({ action: 'get-tree', repository: REPO }, null), { env: buildEnv, fetcher });
  assert.equal(anonymous.status, 401);
  assert.ok(allGet(fetcher));
});

test('Gemini and GitHub failures map to safe messages', async () => {
  const quota = world({ gemini: [new Response('{"error":{"message":"raw quota text"}}', { status: 429, headers: { 'retry-after': '30' } })] });
  const limited = await handleChat(authedChat(ask()), { env: buildEnv, fetcher: quota });
  assert.equal(limited.status, 429);
  assert.doesNotMatch(await limited.text(), /raw quota text/);
  const wrong = await handleChat(authedChat(ask({ repository: 'KRFbrothers/missing' })), { env: buildEnv, fetcher: router([[url => url.startsWith(GH), () => new Response('{}', { status: 404 })]]) });
  assert.equal(wrong.status, 404);
  assert.match((await wrong.json()).error, /Not found/);
  const streamed = world({ gemini: [new Response('{}', { status: 500 })] });
  const events = await readEvents(await handleChat(authedChat(ask({ stream: true })), { env: buildEnv, fetcher: streamed }));
  assert.deepEqual(events.map(e => e.type), ['build', 'error']);
});

const connectorRequest = body => new Request('https://dua.test/api/connectors/github', {
  method: 'POST', headers: { 'content-type': 'application/json', authorization: 'Bearer user-token' }, body: JSON.stringify(body)
});

test('connector setup and invalid inputs explain recovery before any GitHub read', async () => {
  for (const env of [{ ...buildEnv, ENABLE_GITHUB_CONNECTOR: '0' }, { ...buildEnv, GITHUB_CONNECTOR_TOKEN: '' }]) {
    const fetcher = world();
    const response = await handleService(connectorRequest({ action: 'list-repositories' }), { env, fetcher });
    assert.equal(response.status, 503);
    const { error } = await response.json();
    assert.match(error, /ENABLE_GITHUB_CONNECTOR=1/);
    assert.match(error, /read-only GITHUB_CONNECTOR_TOKEN.*redeploy/);
    assert.equal(fetcher.github().length, 0);
  }
  for (const body of [
    { action: 'get-tree', repository: 'https://github.com/owner/repo' },
    { action: 'search-code', repository: 'owner/repo/extra', query: 'test' },
    { action: 'get-file', repository: REPO, path: '../private-value' }
  ]) {
    const fetcher = world();
    const response = await handleService(connectorRequest(body), { env: buildEnv, fetcher });
    assert.equal(response.status, 400);
    const { error } = await response.json();
    assert.match(error, body.action === 'get-file' ? /relative to the repository root.*README.md/ : /owner\/name.*owner\/repo/);
    assert.doesNotMatch(error, /private-value/);
    assert.equal(fetcher.github().length, 0);
  }
});

test('read failures give token/access/reset recovery while preserving status mappings and hiding upstream bodies', async () => {
  for (const [status, clientStatus, routeStatus, hint] of [
    [401, 502, 502, /Replace GITHUB_CONNECTOR_TOKEN.*redeploy/],
    [403, 429, 502, /Contents: Read-only.*If rate-limited.*reset/],
    [404, 404, 404, /Check the repository name\/file path.*GITHUB_CONNECTOR_TOKEN/],
    [429, 429, 502, /Wait.*limit to reset/],
    [500, 502, 502, /service availability.*retry/]
  ]) {
    const fetcher = router([[url => url.startsWith(GH), () => Response.json({ message: `raw upstream ${TOKEN}` }, { status })]]);
    await assert.rejects(githubGet(`/repos/${REPO}`, { env: buildEnv, fetcher }), error => {
      assert.equal(error.status, clientStatus);
      assert.match(error.message, hint);
      assert.doesNotMatch(error.message, /raw upstream|fake-secret/);
      assert.ok(!error.message.includes(TOKEN));
      return true;
    });
    const response = await handleService(connectorRequest({ action: 'get-tree', repository: REPO }), { env: buildEnv, fetcher });
    assert.equal(response.status, routeStatus);
    const text = await response.text();
    assert.match(JSON.parse(text).error, hint);
    assert.ok(!text.includes(TOKEN));
    assert.doesNotMatch(text, /raw upstream/);
    assert.equal(fetcher.calls.filter(c => c.url.startsWith(GH)).length, 2, 'one read per attempt, no automatic retries');
    assert.ok(fetcher.calls.every(call => call.method === 'GET'));
  }
});

test('bounded and malformed GitHub responses offer recovery without exposing response content', async () => {
  for (const [response, hint] of [
    [new Response('private malformed data', { headers: { 'content-length': '20' } }), /too large.*smaller repository or file/],
    [new Response('private malformed data'), /invalid response.*Retry this repository read/]
  ]) {
    await assert.rejects(githubGet(`/repos/${REPO}`, { env: buildEnv, maxBytes: response.headers.has('content-length') ? 5 : 100, fetcher: async () => response }), error => {
      assert.equal(error.status, 502);
      assert.match(error.message, hint);
      assert.doesNotMatch(error.message, /private malformed data/);
      return true;
    });
  }
});

test('connector and Build read network failures keep their existing statuses and safe recovery', async () => {
  for (const [failure, connectorStatus, buildStatus, hint] of [
    [new Error(`network ${TOKEN}`), 502, 502, /Check your connection and GitHub service availability.*retry this read/],
    [new DOMException(`timeout ${TOKEN}`, 'TimeoutError'), 502, 504, /timed out.*Retry this read.*smaller repository/]
  ]) {
    const fetcher = router([[url => url.startsWith(GH), () => { throw failure; }]]);
    const connector = await handleService(connectorRequest({ action: 'get-tree', repository: REPO }), { env: buildEnv, fetcher });
    const build = await handleChat(authedChat(ask()), { env: buildEnv, fetcher });
    assert.equal(connector.status, connectorStatus);
    assert.equal(build.status, buildStatus);
    for (const response of [connector, build]) {
      const { error } = await response.json();
      assert.match(error, hint);
      assert.ok(!error.includes(TOKEN));
    }
    const reads = fetcher.calls.filter(call => call.url.startsWith(GH));
    assert.equal(reads.length, 3, 'connector reads the tree; Build prepares repository info and tree');
    assert.equal(reads.filter(call => call.url === `${GH}/repos/${REPO}`).length, 1);
    assert.ok(fetcher.calls.every(call => call.method === 'GET'));
  }
});
