import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { stripTypeScriptTypes } from 'node:module';
const source = await readFile(new URL('../src/api.ts', import.meta.url), 'utf8');
const { streamChat, decideAction, approvalFromEvent } = await import('data:text/javascript;base64,' + Buffer.from(stripTypeScriptTypes(source, { mode: 'transform' })).toString('base64'));
const request = { model: 'Dua', mode: 'auto', messages: [{ role: 'user', text: 'hi' }] };
const stream = (body) => new Response(body, { headers: { 'Content-Type': 'text/event-stream' } });
const event = data => `data: ${JSON.stringify(data)}\n\n`;

test('complete replies deliver split UTF-8 chunks and finish on done', async t => {
  const bytes = new TextEncoder().encode(event({ type: 'text', delta: 'नमस्ते' }) + event({ type: 'done' }));
  t.mock.method(globalThis, 'fetch', async () => stream(new ReadableStream({ start(c) { for (const byte of bytes) c.enqueue(Uint8Array.of(byte)); c.close(); } })));
  const events = [];
  await streamChat(request, e => events.push(e), new AbortController().signal);
  assert.deepEqual(events, [{ type: 'text', delta: 'नमस्ते' }, { type: 'done' }]);
});
test('partial EOF and empty streams are errors, preserving delivered text', async t => {
  for (const body of ['', event({ type: 'text', delta: 'partial' })]) {
    t.mock.method(globalThis, 'fetch', async () => stream(body));
    const events = [];
    await assert.rejects(streamChat(request, e => events.push(e), new AbortController().signal), /interrupted/);
    if (body) assert.equal(events[0].delta, 'partial');
  }
});
test('provider errors, truncation, and malformed events never count as complete', async t => {
  for (const [body, message] of [[event({ type: 'error', message: 'Provider failed' }), /Provider failed/], [event({ type: 'done', truncated: true }), /incomplete/], ['data: invalid\n\n', /invalid/]]) {
    t.mock.method(globalThis, 'fetch', async () => stream(body));
    await assert.rejects(streamChat(request, () => {}, new AbortController().signal), message);
  }
});
test('callback failures are not swallowed by the stream parser', async t => {
  t.mock.method(globalThis, 'fetch', async () => stream(event({ type: 'text', delta: 'hi' })));
  await assert.rejects(streamChat(request, () => { throw Error('UI failure'); }, new AbortController().signal), /UI failure/);
});
test('approval validation rejects unsafe or incomplete events', () => {
  assert.equal(approvalFromEvent({ type: 'approval', id: '../bad' }), null);
  const action = approvalFromEvent({ type: 'approval', id: 'a'.repeat(24), tool: 'save_note', title: 'Save note', details: ['A note'], expiresIn: 900 });
  assert.equal(action.title, 'Save note');
  assert.ok(action.expiresAt > Date.now());
});
test('action decisions send only id and decision and never retry a lost response', async t => {
  let calls = 0;
  t.mock.method(globalThis, 'fetch', async (_url, init) => {
    calls++; assert.deepEqual(JSON.parse(init.body), { id: 'a'.repeat(24), decision: 'approve' });
    throw Error('Connection lost');
  });
  await assert.rejects(decideAction('a'.repeat(24), 'approve'), /Action result unknown/);
  assert.equal(calls, 1);
});

test('the reader is cancelled after completion even when upstream stays open', async t => {
  let cancelled = false;
  t.mock.method(globalThis, 'fetch', async () => stream(new ReadableStream({
    start(controller) { controller.enqueue(new TextEncoder().encode(event({ type: 'done' }))); },
    cancel() { cancelled = true; }
  })));
  await streamChat(request, () => {}, new AbortController().signal);
  assert.equal(cancelled, true);
});
test('chat forwards cancellation to the request instead of retrying', async t => {
  let calls = 0;
  const controller = new AbortController();
  t.mock.method(globalThis, 'fetch', async (_url, init) => {
    calls++; assert.equal(init.signal, controller.signal);
    return stream(new ReadableStream({ start(streamController) {
      init.signal.addEventListener('abort', () => streamController.error(new DOMException('Stopped', 'AbortError')), { once: true });
    } }));
  });
  const pending = streamChat(request, () => {}, controller.signal);
  controller.abort();
  await assert.rejects(pending, { name: 'AbortError' });
  assert.equal(calls, 1);
});
