import test from 'node:test';
import assert from 'node:assert/strict';
import { request } from 'node:http';
import { makeServer } from '../src/server/server.mjs';
import { createVercelHandler } from '../src/server/vercel-handler.mjs';
import { mockFetch, sseResponse, sse, env, chatRequest, readEvents } from './helpers.mjs';

const geminiChunks = words => words.map((word, index) => sse({ candidates: [{ content: { parts: [{ text: word }] }, ...(index === words.length - 1 ? { finishReason: 'STOP' } : {}) }] }));

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
