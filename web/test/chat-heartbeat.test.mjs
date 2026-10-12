import test from 'node:test';
import assert from 'node:assert/strict';
import { handleChat } from '../src/core/providers.mjs';
import { handleService } from '../src/server/service-handler.mjs';
import { chatRequest, readEvents, sseResponse, sse, env, mockFetch } from './helpers.mjs';

const body = extra => ({ model: 'Dua', messages: [{ role: 'user', text: 'Five tips please' }], stream: true, ...extra });
const answer = () => sseResponse([sse({ candidates: [{ content: { parts: [{ text: 'Here are five tips.' }] }, finishReason: 'STOP' }] })]);

test('stream headers and heartbeats arrive while provider response headers are still pending', { timeout: 2000 }, async t => {
  t.mock.timers.enable({ apis: ['setInterval'] });
  const pending = Promise.withResolvers();
  const started = Promise.withResolvers();
  const fetcher = (_url, init) => { started.resolve(init.signal); return pending.promise; };
  t.after(() => pending.resolve(answer()));
  const response = await handleChat(chatRequest(body()), { env, fetcher });
  assert.equal(response.headers.get('content-type'), 'text/event-stream; charset=utf-8');
  const reader = response.body.getReader();
  t.after(() => reader.cancel());
  await started.promise;
  const decoder = new TextDecoder();
  assert.equal(decoder.decode((await reader.read()).value), ': stream\n\n');
  t.mock.timers.tick(15_000);
  assert.equal(decoder.decode((await reader.read()).value), ': ping\n\n');
  pending.resolve(answer());
  let raw = '';
  for (;;) {
    const chunk = await reader.read();
    if (chunk.done) break;
    raw += decoder.decode(chunk.value);
  }
  assert.deepEqual((await readEvents(new Response(raw))).map(event => event.type), ['text', 'done']);
});

test('Stop cancels a provider fetch before any response headers arrive', { timeout: 2000 }, async t => {
  const started = Promise.withResolvers();
  const settled = Promise.withResolvers();
  const fetcher = (_url, init) => new Promise((_resolve, reject) => {
    started.resolve(init.signal);
    init.signal.addEventListener('abort', () => { reject(init.signal.reason); settled.resolve(); }, { once: true });
  });
  const response = await handleChat(chatRequest(body()), { env, fetcher });
  t.after(() => response.body.cancel());
  const signal = await started.promise;
  await response.body.cancel();
  await settled.promise;
  assert.equal(signal.aborted, true);
});

test('slow Brave preparation gets an immediate stream and cancellation prevents the model call', { timeout: 2000 }, async t => {
  const started = Promise.withResolvers();
  const settled = Promise.withResolvers();
  const calls = [];
  const fetcher = (url, init) => new Promise((_resolve, reject) => {
    calls.push(url);
    started.resolve(init.signal);
    init.signal.addEventListener('abort', () => { reject(init.signal.reason); settled.resolve(); }, { once: true });
  });
  const response = await handleChat(chatRequest(body({ web: 'search' })), {
    env: { ...env, BRAVE_SEARCH_API_KEY: 'test-brave', DUA_SEARCH_PREFER_BRAVE: '1' }, fetcher
  });
  t.after(() => response.body.cancel());
  assert.match(response.headers.get('content-type'), /text\/event-stream/);
  const signal = await started.promise;
  await response.body.cancel();
  await settled.promise;
  assert.equal(signal.aborted, true);
  assert.equal(calls.length, 1);
  assert.match(calls[0], /api\.search\.brave\.com/);
});

test('an aborted initial provider wait ends with a sanitized error event, without a success event', async () => {
  const stop = new AbortController();
  const started = Promise.withResolvers();
  const fetcher = (_url, init) => new Promise((_resolve, reject) => {
    started.resolve();
    init.signal.addEventListener('abort', () => reject(init.signal.reason), { once: true });
  });
  const response = await handleChat(chatRequest(body(), { signal: stop.signal }), { env, fetcher });
  await started.promise;
  stop.abort(new DOMException('private upstream detail', 'TimeoutError'));
  const events = await readEvents(response);
  assert.deepEqual(events.map(event => event.type), ['error']);
  assert.equal(events[0].status, 504);
  assert.match(events[0].message, /timed out/);
  assert.doesNotMatch(events[0].message, /private upstream detail/);
});

test('owner authentication fails before a stream opens or a provider is contacted', async () => {
  const fetcher = mockFetch();
  const response = await handleService(chatRequest(body()), {
    env: { ...env, DUA_REQUIRE_LOGIN: '1', DUA_ALLOWED_EMAIL: 'owner@example.com', SUPABASE_URL: 'https://project.supabase.co', SUPABASE_ANON_KEY: 'public-anon-key' }, fetcher
  });
  assert.equal(response.status, 401);
  assert.match(response.headers.get('content-type'), /application\/json/);
  assert.equal(fetcher.calls.length, 0);
});

test('Gemini upstream status survives SSE error conversion without exposing provider details', async t => {
  t.mock.method(console, 'error', () => {});
  const response = await handleChat(chatRequest(body()), {
    env, fetcher: mockFetch(Response.json({ error: { message: 'private upstream body' } }, { status: 503 }))
  });
  const events = await readEvents(response);
  assert.equal(response.status, 200);
  assert.equal(events.length, 1);
  assert.equal(events[0].type, 'error');
  assert.equal(events[0].status, 502);
  assert.equal(events[0].upstreamStatus, 503);
  assert.match(events[0].message, /HTTP 503/);
  assert.doesNotMatch(events[0].message, /private upstream body/);
});
