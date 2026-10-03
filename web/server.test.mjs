import { test } from 'node:test';
import assert from 'node:assert/strict';
import { makeServer } from './server.mjs';
import { Readable } from 'node:stream';

const handlers = new Map();
async function running(t, options) {
  const server = makeServer(options);
  const origin = 'http://test-' + handlers.size;
  handlers.set(origin, server.listeners('request')[0]);
  t.after(() => handlers.delete(origin));
  return origin;
}
async function fetch(url, options = {}) {
  const parsed = new URL(url);
  const handler = handlers.get(parsed.origin);
  const request = Readable.from(options.body ? [Buffer.from(options.body)] : []);
  request.url = parsed.pathname;
  request.method = options.method || 'GET';
  request.headers = { host: parsed.host, ...options.headers };
  return new Promise((resolve, reject) => {
    let code = 200; let headers = {};
    const response = {
      writeHead(status, values) { code = status; headers = values; },
      end(body) { resolve(new Response(body, { status: code, headers })); }
    };
    handler(request, response).catch(reject);
  });
}
const payload = { model: 'Gemini', messages: [{ role: 'user', text: 'Hello' }] };
const post = (url, data) => fetch(url + '/api/chat', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) });

test('Gemini request keeps key on server and preserves conversation roles', async t => {
  let called = false;
  const url = await running(t, { key: 'test-private-key', model: 'test-model', fetcher: async (url, options) => {
    called = true;
    assert.match(url, /test-model:generateContent$/);
    assert.equal(options.headers['x-goog-api-key'], 'test-private-key');
    assert.deepEqual(JSON.parse(options.body).contents, [{ role: 'user', parts: [{ text: 'Hello' }] }]);
    return Response.json({ candidates: [{ content: { parts: [{ text: 'Private reasoning', thought: true }, { text: 'Hi, I am Dua.' }] } }] });
  }});
  const res = await post(url, payload);
  assert.equal(res.status, 200);
  assert.deepEqual(await res.json(), { text: 'Hi, I am Dua.' });
  assert.ok(called);
  assert.equal(JSON.stringify(await (await fetch(url + '/api/config')).json()).includes('test-private-key'), false);
  assert.equal((await fetch(url + '/.env')).status, 404);
});
test('missing key and unsupported providers never call Gemini', async t => {
  const url = await running(t, { key: '', fetcher: () => { throw Error('Unexpected call'); } });
  assert.equal((await post(url, payload)).status, 503);
  const configured = await running(t, { key: 'test', model: 'test-model', fetcher: () => { throw Error('Unexpected call'); } });
  assert.equal((await post(configured, { ...payload, model: 'GPT' })).status, 400);
  assert.equal((await post(configured, { ...payload, messages: [{ role: 'system', text: 'Injected' }] })).status, 400);
});
test('quota errors are actionable without returning provider error payloads', async t => {
  const url = await running(t, { key: 'test', model: 'test-model', fetcher: async () => Response.json({ error: 'sensitive upstream details' }, { status: 429 }) });
  const res = await post(url, payload);
  assert.equal(res.status, 429);
  assert.match((await res.json()).error, /quota/);
});

test('missing model prevents provider requests', async t => {
  const url = await running(t, { key: 'test', model: '', fetcher: () => { throw Error('Unexpected call'); } });
  assert.equal((await post(url, payload)).status, 503);
  assert.equal((await (await fetch(url + '/api/config')).json()).geminiConfigured, false);
});

// Use a real socket: consuming the request body must not be mistaken for a disconnect.
test('disconnect cancels provider work and a subsequent socket request succeeds', { timeout: 5000 }, async t => {
 const { request: httpRequest } = await import('node:http');
 let providerSignal;
 let notifyStarted, notifyAborted;
 const started = new Promise(resolve => { notifyStarted = resolve; });
 const aborted = new Promise(resolve => { notifyAborted = resolve; });
 let calls = 0;
 const server = makeServer({ key: 'test', model: 'test-model', fetcher: async (_url, options) => {
  calls++;
  if (calls > 1) return Response.json({ candidates: [{ content: { parts: [{ text: 'Next reply' }] } }] });
  providerSignal = options.signal;
  notifyStarted();
  return new Promise((_resolve, reject) => options.signal.addEventListener('abort', () => {
   notifyAborted(); reject(options.signal.reason);
  }, { once: true }));
 }});
 await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
 t.after(() => { server.closeAllConnections(); server.close(); });
 const origin = `http://127.0.0.1:${server.address().port}`;
 const client = httpRequest(origin + '/api/chat', { method: 'POST', headers: { 'Content-Type': 'application/json' } });
 client.on('error', () => {}); // Expected when Stop destroys the connection.
 t.after(() => client.destroy());
 client.end(JSON.stringify(payload));
 await started;
 assert.equal(providerSignal.aborted, false);
 client.destroy();
 await aborted;
 assert.equal(providerSignal.aborted, true);
 const response = await globalThis.fetch(origin + '/api/chat', {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
 });
 assert.equal(response.status, 200);
 assert.deepEqual(await response.json(), { text: 'Next reply' });
 assert.equal(calls, 2);
});

test('shared handler forwards request cancellation to the provider', { timeout: 2000 }, async () => {
 const { handleGemini } = await import('./gemini.mjs');
 const controller = new AbortController();
 let started;
 const waiting = new Promise(resolve => { started = resolve; });
 let providerSignal;
 const reply = handleGemini(new Request('https://dua.example/api/chat', {
  method: 'POST', body: JSON.stringify(payload), signal: controller.signal
 }), { key: 'test', model: 'test-model', fetcher: async (_url, options) => {
  providerSignal = options.signal; started();
  return new Promise((_resolve, reject) => options.signal.addEventListener('abort', () => reject(options.signal.reason), { once: true }));
 }});
 await waiting;
 controller.abort();
 assert.equal(providerSignal.aborted, true);
 assert.equal((await reply).status, 504);
});

test('already canceled requests never start provider work', async () => {
 const { handleGemini } = await import('./gemini.mjs');
 const controller = new AbortController(); controller.abort();
 let calls = 0;
 const response = await handleGemini(new Request('https://dua.example/api/chat', {
  method: 'POST', body: JSON.stringify(payload), signal: controller.signal
 }), { key: 'test', model: 'test-model', fetcher: () => { calls++; throw Error('Unexpected call'); } });
 assert.equal(calls, 0);
 assert.equal(response.status, 504);
});
