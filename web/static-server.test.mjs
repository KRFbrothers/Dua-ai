import test from 'node:test';
import assert from 'node:assert/strict';
import { makeServer } from './server.mjs';

// Exercise the actual server request listener without depending on socket access.
async function request(server, url, { method = 'GET', chunks = [], headers = {} } = {}) {
  let status, responseHeaders;
  const result = new Promise(resolve => {
    server.listeners('request')[0]({
      url, method, headers: { host: 'localhost', ...headers },
      async *[Symbol.asyncIterator]() { yield* chunks; }
    }, {
      writeHead(code, values) { status = code; responseHeaders = values; },
      end(body) { resolve({ status, headers: responseHeaders, body }); }
    });
  });
  return result;
}

test('static pages and preview directory indexes are served', async () => {
  const server = makeServer();
  for (const url of ['/', '/chat.html', '/preview/', '/preview/index.html']) {
    const response = await request(server, url);
    assert.equal(response.status, 200, url);
    assert.match(response.headers['Content-Type'], /text\/html/);
    assert.match(response.body.toString(), /<!doctype html>/i);
  }
});

test('HEAD returns headers without a body', async () => {
  const response = await request(makeServer(), '/chat.html', { method: 'HEAD' });
  assert.equal(response.status, 200);
  assert.equal(response.body, undefined);
});

test('missing files, encoded traversal and unsupported methods stay blocked', async () => {
  const server = makeServer();
  for (const url of ['/missing.html', '/..%2fpackage.json', '/..%2fserver.mjs']) {
    assert.equal((await request(server, url)).status, 404, url);
  }
  assert.equal((await request(server, '/chat.html', { method: 'POST' })).status, 405);
});

test('Hindi and emoji survive every possible network chunk boundary', async () => {
  const text = 'नमस्ते Dua 👋';
  const bytes = Buffer.from(JSON.stringify({ model: 'Dua', messages: [{ role: 'user', text }] }));
  let received;
  const server = makeServer({ key: 'test-key', model: 'test-model', fetcher: async (_url, options) => {
    received = JSON.parse(options.body).contents.at(-1).parts[0].text;
    return Response.json({ candidates: [{ content: { parts: [{ text: 'OK' }] } }] });
  } });
  for (let split = 1; split < bytes.length; split++) {
    const response = await request(server, '/api/chat', { method: 'POST',
      headers: { 'content-type': 'application/json' }, chunks: [bytes.subarray(0, split), bytes.subarray(split)] });
    assert.equal(response.status, 200);
    assert.equal(received, text, `boundary ${split}`);
  }
});

test('request size limit still rejects oversized bodies before calling provider', async () => {
  let called = false;
  const server = makeServer({ key: 'test-key', model: 'test-model', fetcher: async () => { called = true; } });
  const response = await request(server, '/api/chat', { method: 'POST', chunks: [Buffer.alloc(3 * 1024 * 1024), Buffer.from('x')] });
  assert.equal(response.status, 413);
  assert.equal(called, false);
});
