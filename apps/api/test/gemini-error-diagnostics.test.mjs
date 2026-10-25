import test from 'node:test';
import assert from 'node:assert/strict';
import { geminiUpstreamError } from '../src/core/gemini.mjs';

test('Gemini service failures identify the actual status while bodies stay private in responses and logs', async t => {
  const log = t.mock.method(console, 'error', () => {});
  for (const [status, hint] of [[500, /internal error/], [502, /gateway error/], [503, /unavailable/], [504, /timeout/], [418, /request fail/]]) {
    const response = await geminiUpstreamError(Response.json({ error: { message: 'private-provider-detail', credential: 'fake-secret', prompt: 'private-question' } }, { status }));
    assert.equal(response.status, 502, 'backend transport status stays compatible');
    assert.equal(response.headers.get('Cache-Control'), 'no-store');
    const body = await response.json();
    assert.equal(body.upstreamStatus, status);
    assert.match(body.error, hint);
    assert.match(body.error, new RegExp('HTTP ' + status));
    assert.doesNotMatch(JSON.stringify(body), /private-provider-detail|fake-secret|private-question/);
  }
  assert.deepEqual(log.mock.calls.map(call => call.arguments), [500, 502, 503, 504, 418].map(status => [`[dua-gemini] Upstream request failed (HTTP ${status}).`]));
});

test('quota errors retain their retry guidance and safe diagnostic status', async t => {
  const log = t.mock.method(console, 'error', () => {});
  const response = await geminiUpstreamError(Response.json({ error: { message: 'private quota details', details: [{ '@type': 'type.googleapis.com/google.rpc.RetryInfo', retryDelay: '45s' }] } }, { status: 429, headers: { 'Retry-After': '30' } }));
  assert.equal(response.status, 429);
  assert.equal(response.headers.get('Retry-After'), '45');
  const body = await response.json();
  assert.equal(body.retryAfterSeconds, 45);
  assert.equal(body.upstreamStatus, 429);
  assert.match(body.error, /45 second/);
  assert.doesNotMatch(body.error, /private quota details/);
  assert.deepEqual(log.mock.calls[0].arguments, ['[dua-gemini] Upstream request failed (HTTP 429).']);
});

test('a cancelled provider error read does not report a normal upstream failure', async t => {
  const log = t.mock.method(console, 'error', () => {});
  const stop = new AbortController();
  stop.abort(new DOMException('Stopped', 'AbortError'));
  await assert.rejects(geminiUpstreamError(Response.json({ error: {} }, { status: 503 }), stop.signal), { name: 'AbortError' });
  assert.equal(log.mock.callCount(), 0);
});
