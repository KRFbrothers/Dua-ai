import test from 'node:test';
import assert from 'node:assert/strict';
import { createDuaBridge, DUA_ORIGIN } from '../hosting/dua-bridge.mjs';
import { readFile } from 'node:fs/promises';
import { stripTypeScriptTypes } from 'node:module';
import { prepareHostedFiles } from '../hosting/prepare.mjs';

const token = 'test-token-'.repeat(6);
const publicUser = { id: '11111111-1111-4111-8111-111111111111', email: 'reader@example.invalid', displayName: 'Reader', avatarUrl: '' };
const signedIn = (expiresAt = Math.floor(Date.now() / 1000) + 3600) => ({ configured: true, user: publicUser,
  session: { accessToken: token, refreshToken: 'test-refresh-'.repeat(6), expiresAt } });
const turn = { role: 'user', text: 'Hello' };
const apiSource = stripTypeScriptTypes(await readFile(new URL('../src/api.ts', import.meta.url), 'utf8'), { mode: 'transform' });
const apiUrl = 'data:text/javascript;base64,' + Buffer.from(apiSource).toString('base64');
const adapterSource = stripTypeScriptTypes(await readFile(new URL('../src/appdeploy-connection.ts', import.meta.url), 'utf8'), { mode: 'transform' })
  .replace("from './api'", `from '${apiUrl}'`);
const { createAppDeployConnection } = await import('data:text/javascript;base64,' + Buffer.from(adapterSource).toString('base64'));

test('host packaging retains canonical validators and excludes native network fallbacks', async () => {
  const files = await prepareHostedFiles();
  const source = files.find(file => file.filename === 'src/api.ts').content;
  assert.equal(/\bfetch\s*\(/.test(source), false);
  const module = await import('data:text/javascript;base64,' + Buffer.from(stripTypeScriptTypes(source, { mode: 'transform' })).toString('base64'));
  await assert.rejects(module.getSession(), /not initialized/);
  assert.equal(module.approvalFromEvent({ type: 'approval', id: 'bad' }), null);
  module.installConnection({ getSession: async () => ({ configured: true, user: publicUser }) });
  assert.equal((await module.getSession()).user.id, publicUser.id);
  assert.ok(files.find(file => file.filename === 'src/main.ts').content.includes("import('./application')"));
  assert.equal(new Set(files.map(file => file.filename)).size, files.length);
});

test('public status never relays user identity or credentials', async () => {
  const bridge = createDuaBridge({ fetcher: async (url, options) => {
    assert.equal(url, DUA_ORIGIN + '/api/account/session');
    assert.equal(options.headers.Authorization, undefined);
    return Response.json(signedIn());
  } });
  assert.deepEqual(await bridge('status'), { status: 200, data: { connected: true, configured: true } });
});
test('all protected operations refuse missing or malformed tokens before network access', async () => {
  let calls = 0; const bridge = createDuaBridge({ fetcher: async () => { calls++; throw Error(); } });
  for (const operation of ['chat', 'history', 'logout', 'refresh']) {
    assert.equal((await bridge(operation, {})).status, 401);
    assert.equal((await bridge(operation, { accessToken: 'bad\r\nheader', refreshToken: 'bad\r\nheader' })).status, 401);
  }
  assert.equal(calls, 0);
});
test('login uses the existing native endpoint and preserves owner rejection', async () => {
  const bridge = createDuaBridge({ fetcher: async (url, options) => {
    assert.equal(url, DUA_ORIGIN + '/api/account/email-login');
    assert.equal(options.redirect, 'error');
    assert.equal(options.headers['x-dua-native'], '1');
    assert.equal(options.headers.Origin, undefined);
    assert.deepEqual(JSON.parse(options.body), { email: 'reader@example.invalid', password: 'test-only-password' });
    return Response.json({ error: 'Only the configured personal account can sign in.' }, { status: 403 });
  } });
  assert.equal((await bridge('login', { email: 'reader@example.invalid', password: 'test-only-password', url: 'https://untrusted.invalid' })).status, 403);
  assert.equal((await bridge('login', { email: 'invalid', password: '' })).status, 400);
});
test('chat forwards only validated native chat fields and leaves quota enforcement upstream', async () => {
  const bridge = createDuaBridge({ fetcher: async (url, options) => {
    assert.equal(url, DUA_ORIGIN + '/api/chat');
    assert.equal(options.headers.Authorization, `Bearer ${token}`);
    assert.deepEqual(JSON.parse(options.body), { model: 'Dua', messages: [turn], attachments: [], stream: false, mode: 'auto' });
    return Response.json({ error: 'Daily request limit reached.' }, { status: 429 });
  } });
  const reply = await bridge('chat', { accessToken: token, model: 'Dua', messages: [{ ...turn, arbitrary: 'discarded' }] });
  assert.equal(reply.status, 429); assert.equal(reply.data.error, 'Daily request limit reached.');
});
test('invalid models, roles, last turns and history budgets are rejected locally', async () => {
  let calls = 0; const bridge = createDuaBridge({ fetcher: async () => { calls++; throw Error(); } });
  for (const value of [
    { model: 'unconfigured', messages: [turn] }, { model: 'Dua', messages: [] },
    { model: 'Dua', messages: [{ role: 'system', text: 'unsafe' }] },
    { model: 'Dua', messages: [{ role: 'model', text: 'not a user turn' }] },
    { model: 'Dua', messages: [{ ...turn, text: 'x'.repeat(8001) }] },
  ]) assert.equal((await bridge('chat', { accessToken: token, ...value })).status, 400);
  assert.equal(calls, 0);
});
test('history and refresh use fixed authenticated account routes', async () => {
  const calls = [];
  const bridge = createDuaBridge({ fetcher: async (url, options) => { calls.push({ url, options }); return Response.json({}); } });
  await bridge('history', { accessToken: token });
  await bridge('refresh', { refreshToken: token });
  assert.equal(calls[0].url, DUA_ORIGIN + '/api/cloud');
  assert.equal(calls[0].options.headers.Authorization, `Bearer ${token}`);
  assert.equal(calls[1].url, DUA_ORIGIN + '/api/account/refresh');
  assert.deepEqual(JSON.parse(calls[1].options.body), { refreshToken: token });
});
test('unexpected upstream formats and connection failures return a bounded public error', async () => {
  for (const [fetcher, status] of [[async () => new Response('<html>gateway error</html>'), 503], [async () => { throw Error('private upstream details'); }, 502]]) {
    const reply = await createDuaBridge({ fetcher })('status');
    assert.equal(reply.status, status); assert.equal(JSON.stringify(reply).includes('private upstream details'), false);
  }
});
test('adapter authenticates in memory, delivers real text and discards late results after logout', async () => {
  const api = { get: async () => ({ data: { connected: true, configured: true } }), post: async path => ({ data: path.endsWith('/login') ? signedIn() : { text: 'Actual server reply' } }) };
  const connection = createAppDeployConnection(api);
  assert.equal((await connection.getSession()).user, null);
  assert.equal((await connection.login('reader@example.invalid', 'test-only-password')).user.id, publicUser.id);
  const events = []; await connection.chat({ model: 'Dua', mode: 'auto', messages: [turn] }, event => events.push(event), new AbortController().signal);
  assert.ok(events.some(event => event.type === 'text' && event.delta === 'Actual server reply'));
  assert.equal(events.at(-1).type, 'done');
  await connection.logout();
  await assert.rejects(connection.cloud(), /Sign in/);
});
test('stopping the UI rejects immediately and never delivers an eventual server reply', async () => {
  let complete; let started;
  const observed = new Promise(resolve => { started = resolve; });
  const connection = createAppDeployConnection({ get: async () => ({ data: {} }), post: path => path.endsWith('/login')
    ? Promise.resolve({ data: signedIn() }) : new Promise(resolve => { complete = resolve; started(); }) });
  await connection.login('reader@example.invalid', 'test-only-password');
  const controller = new AbortController(); const events = [];
  const pending = connection.chat({ model: 'Dua', mode: 'auto', messages: [turn] }, event => events.push(event), controller.signal);
  await observed; controller.abort(); await assert.rejects(pending, { name: 'AbortError' });
  complete({ data: { text: 'Late result' } }); await new Promise(resolve => setImmediate(resolve));
  assert.equal(events.some(event => event.type === 'text'), false);
});
test('an expired session refreshes once and fails closed after refresh rejection', async () => {
  let refreshes = 0;
  const connection = createAppDeployConnection({ get: async () => ({ data: {} }), post: async path => {
    if (path.endsWith('/login')) return { data: signedIn(1) };
    if (path.endsWith('/refresh')) { refreshes++; throw { response: { status: 401, data: { error: 'Session expired' } } }; }
    throw Error('Protected request must not run');
  } });
  await connection.login('reader@example.invalid', 'test-only-password');
  await assert.rejects(connection.cloud(), /Session expired/);
  await assert.rejects(connection.cloud(), /Sign in/);
  assert.equal(refreshes, 1);
});

test('logout prevents pending chat and cloud results from crossing account boundaries', async () => {
  for (const operation of ['chat', 'cloud']) {
    let finish; let started;
    const observed = new Promise(resolve => { started = resolve; });
    const connection = createAppDeployConnection({ get: async () => ({ data: {} }), post: async path => {
      if (path.endsWith('/login')) return { data: signedIn() };
      if (path.endsWith('/logout')) return { data: { revoked: true } };
      return new Promise(resolve => { finish = resolve; started(); });
    } });
    await connection.login('reader@example.invalid', 'test-only-password');
    const events = [];
    const pending = operation === 'chat'
      ? connection.chat({ model: 'Dua', mode: 'auto', messages: [turn] }, event => events.push(event), new AbortController().signal)
      : connection.cloud();
    await observed; await connection.logout(); finish({ data: { text: 'Old account result' } });
    await assert.rejects(pending, /Account changed/);
    assert.equal(events.some(event => event.type === 'text'), false);
  }
});
