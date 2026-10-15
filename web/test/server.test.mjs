import test from 'node:test';
import assert from 'node:assert/strict';
import { request } from 'node:http';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { fileURLToPath } from 'node:url';
import { makeServer, listenOptions } from '../src/server/server.mjs';
import { createVercelHandler } from '../src/server/vercel-handler.mjs';
import { mockFetch, sseResponse, sse, env, chatRequest, readEvents } from './helpers.mjs';

const geminiChunks = words => words.map((word, index) => sse({ candidates: [{ content: { parts: [{ text: word }] }, ...(index === words.length - 1 ? { finishReason: 'STOP' } : {}) }] }));

test('local development keeps loopback:3000; production/Cloud Run bind all interfaces:8080', () => {
  assert.deepEqual(listenOptions({}), { port: 3000, host: '127.0.0.1' });
  assert.deepEqual(listenOptions({ PORT: '4321' }), { port: 4321, host: '127.0.0.1' });
  for (const container of [{ NODE_ENV: 'production' }, { K_SERVICE: 'dua-api' }]) {
    assert.deepEqual(listenOptions(container), { port: 8080, host: '0.0.0.0' });
    assert.deepEqual(listenOptions({ ...container, PORT: '9090' }), { port: 9090, host: '0.0.0.0' });
    assert.deepEqual(listenOptions({ ...container, PORT: '0' }), { port: 0, host: '0.0.0.0' });
  }
  for (const port of ['bad', '-1', '65536', '1.5']) assert.throws(() => listenOptions({ PORT: port }), /PORT must be an integer/);
});

test('server entry point honors container binding and serves unauthenticated configuration smoke tests', { timeout: 15000 }, async () => {
  const child = spawn(process.execPath, [fileURLToPath(new URL('../src/server/server.mjs', import.meta.url))], {
    env: { PATH: process.env.PATH, NODE_ENV: 'production', PORT: '0', DUA_REQUIRE_LOGIN: '1', DUA_CHAT_DEADLINE_SECONDS: '780' },
    stdio: ['ignore', 'pipe', 'pipe']
  });
  const exited = once(child, 'exit');
  const ready = new Promise((resolve, reject) => {
    let output = '';
    AbortSignal.timeout(10000).addEventListener('abort', () => reject(new Error('Server did not become ready')), { once: true });
    child.stdout.on('data', chunk => {
      output += chunk;
      const match = output.match(/http:\/\/0\.0\.0\.0:(\d+)\/api\//);
      if (match) resolve(Number(match[1]));
    });
    child.once('error', reject);
    child.once('exit', code => reject(new Error(`Server exited before listening (${code})`)));
  });
  try {
    const port = await ready;
    for (const route of ['/api/config', '/api/setup']) {
      const response = await fetch(`http://127.0.0.1:${port}${route}`);
      assert.equal(response.status, 200);
      assert.equal(response.headers.get('cache-control'), 'no-store');
      const data = await response.json();
      assert.ok(data && typeof data === 'object');
      if (route === '/api/setup') assert.equal(data.account.requireLogin, true);
    }
    assert.equal((await fetch(`http://127.0.0.1:${port}/`)).status, 404);
  } finally {
    child.kill('SIGTERM');
    await exited;
  }
});

test('local server streams chunks to the client as they are produced', async () => {
  const fetcher = mockFetch(sseResponse(geminiChunks(['One ', 'two ', 'three']), { delayMs: 60 }));
  const server = makeServer({ env: { ...env }, key: env.GEMINI_API_KEY, model: env.GEMINI_MODEL, fetcher, supabaseUrl: '', supabaseKey: '' }).listen(0, '127.0.0.1');
  await new Promise(resolve => server.once('listening', resolve));
  try {
    const arrivals = [];
    const text = await new Promise((resolve, reject) => {
      const req = request({ host: '127.0.0.1', port: server.address().port, path: '/api/chat', method: 'POST', headers: { 'content-type': 'application/json' } }, res => {
        assert.equal(res.headers['content-type'], 'text/event-stream; charset=utf-8');
        let all = '';
        res.on('data', chunk => { arrivals.push(Date.now()); all += chunk; });
        res.on('end', () => resolve(all));
      });
      req.on('error', reject);
      req.end(JSON.stringify({ model: 'Gemini', messages: [{ role: 'user', text: 'Count' }], stream: true }));
    });
    assert.match(text, /event: text\ndata: \{"type":"text","delta":"One "\}/);
    assert.match(text, /event: done/);
    assert.ok(arrivals.length >= 3, 'expected several network chunks');
    assert.ok(arrivals.at(-1) - arrivals[0] >= 100, 'chunks should be spread over time, not buffered');
  } finally { server.close(); }
});

test('Vercel handler passes streams through and keeps the burst guard', async () => {
  const handler = createVercelHandler({ env, fetcher: mockFetch(sseResponse(geminiChunks(['Hi']))), guard: () => 0 });
  const response = await handler.fetch(chatRequest({ model: 'Dua', messages: [{ role: 'user', text: 'Hi' }], stream: true }));
  assert.deepEqual((await readEvents(response)).map(e => e.type), ['text', 'done']);
  const limited = createVercelHandler({ env, fetcher: mockFetch(), guard: () => 7 });
  const blocked = await limited.fetch(chatRequest({ model: 'Dua', messages: [{ role: 'user', text: 'Hi' }], stream: true }));
  assert.equal(blocked.status, 429);
});
