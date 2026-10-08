import test from 'node:test';
import assert from 'node:assert/strict';
import { formatEvent, readSse, eventStreamResponse, SSE_HEADERS } from '../src/core/sse.mjs';
import { readEvents } from './helpers.mjs';

const streamOf = (...chunks) => new ReadableStream({ start(c) { for (const chunk of chunks) c.enqueue(new TextEncoder().encode(chunk)); c.close(); } });
const collect = async iterable => { const out = []; for await (const item of iterable) out.push(item); return out; };

test('formatEvent writes the event name and a single JSON data line', () => {
  assert.equal(formatEvent({ type: 'text', delta: 'Hi\nthere' }), 'event: text\ndata: {"type":"text","delta":"Hi\\nthere"}\n\n');
  assert.equal(formatEvent({ type: 'done', truncated: false }), 'event: done\ndata: {"type":"done","truncated":false}\n\n');
  assert.throws(() => formatEvent({ type: 'shell' }), TypeError);
});

test('readSse joins records split across chunks, CRLF and multi-line data', async () => {
  const records = await collect(readSse(streamOf('event: a\r', '\ndata: {"x":', '1}\r\n\r\n: comment\n\ndata: line1\ndata: line2\n\ndata: tail')));
  assert.deepEqual(records, [{ event: 'a', data: '{"x":1}' }, { event: 'message', data: 'line1\nline2' }, { event: 'message', data: 'tail' }]);
});

test('readSse rejects an unbounded upstream event', async () => {
  await assert.rejects(collect(readSse(streamOf('data: ' + 'x'.repeat(100)), { maxBufferBytes: 50 })), /too large/);
});

test('eventStreamResponse streams events with SSE headers and heartbeats', async () => {
  const response = eventStreamResponse(async send => {
    send({ type: 'thinking', delta: 'Hmm' });
    await new Promise(resolve => setTimeout(resolve, 30));
    send({ type: 'text', delta: 'Answer' });
    send({ type: 'done', truncated: false });
  }, { heartbeatMs: 5 });
  for (const [key, value] of Object.entries(SSE_HEADERS)) assert.equal(response.headers.get(key), value);
  const raw = await response.clone().text();
  assert.match(raw, /^: stream\n\n/);
  assert.match(raw, /: ping\n\n/);
  assert.deepEqual((await readEvents(response)).map(e => e.type), ['thinking', 'text', 'done']);
});

test('eventStreamResponse turns an unexpected throw into a final error event', async () => {
  const response = eventStreamResponse(async send => { send({ type: 'text', delta: 'part' }); throw new Error('boom'); }, { onUnexpected() {} });
  const events = await readEvents(response);
  assert.deepEqual(events.map(e => e.type), ['text', 'error']);
  assert.match(events[1].message, /Partial reply kept/);
});

test('cancelling the stream aborts the producer signal', async () => {
  let seen;
  const response = eventStreamResponse(async (send, signal) => {
    seen = signal;
    await new Promise(resolve => signal.addEventListener('abort', resolve, { once: true }));
  });
  const reader = response.body.getReader();
  await reader.read();
  await reader.cancel();
  assert.equal(seen.aborted, true);
});
