import test from 'node:test';
import assert from 'node:assert/strict';
import { handleChat, providerConfiguration, validateChat } from '../src/core/providers.mjs';
import { handleService, chatDeadlineMs } from '../src/server/service-handler.mjs';
import { validateCloudSnapshot } from '../src/core/cloud-snapshot.mjs';
import { mockFetch, chatRequest, readEvents, sseResponse, sse, env, b64 } from './helpers.mjs';

const geminiJson = parts => Response.json({ candidates: [{ content: { parts }, finishReason: 'STOP' }] });
const body = (extra = {}) => ({ model: 'Dua', messages: [{ role: 'user', text: 'Hello' }], ...extra });

test('configuration advertises support for persisted chat renaming', async () => {
  const response = await handleChat(new Request('https://dua.test/api/config'), { env });
  assert.equal(response.status, 200);
  assert.deepEqual((await response.json()).history, { rename: true, pin: true, delete: false });
});
test('server chat deletion is advertised only with owner and cloud setup', async () => {
  const configured = { ...env, DUA_ALLOWED_EMAIL: 'owner@example.com', SUPABASE_URL: 'https://project.supabase.co', SUPABASE_ANON_KEY: 'public-anon-key' };
  const response = await handleChat(new Request('https://dua.test/api/config'), { env: configured });
  assert.equal((await response.json()).history.delete, true);
});

test('legacy JSON path is unchanged for older apps (no stream, no mode)', async () => {
  const fetcher = mockFetch(geminiJson([{ text: 'secret plan', thought: true }, { text: 'Hi there' }]));
  const response = await handleChat(chatRequest(body({ attachments: [], memory: '' })), { env, fetcher });
  assert.equal(response.status, 200);
  assert.match(response.headers.get('content-type'), /application\/json/);
  assert.deepEqual(await response.json(), { text: 'Hi there' });
  assert.match(fetcher.calls[0].url, /:generateContent$/);
  assert.equal(fetcher.calls[0].body.generationConfig, undefined);
});

test('legacy JSON path returns thinking only when Reasoning was requested', async () => {
  const fetcher = mockFetch(geminiJson([{ text: 'plan', thought: true }, { text: 'Answer' }]));
  const response = await handleChat(chatRequest(body({ mode: 'reasoning' })), { env, fetcher });
  assert.deepEqual(await response.json(), { text: 'Answer', thinking: 'plan' });
  assert.equal(fetcher.calls[0].body.generationConfig.thinkingConfig.includeThoughts, true);
});

test('stream: Gemini thinking and text arrive as separate events, then done', async () => {
  const fetcher = mockFetch(sseResponse([
    sse({ candidates: [{ content: { parts: [{ text: 'Considering', thought: true }] } }] }),
    sse({ candidates: [{ content: { parts: [{ text: 'Hel' }] } }] }),
    sse({ candidates: [{ content: { parts: [{ text: 'lo' }] }, finishReason: 'STOP' }], usageMetadata: { promptTokenCount: 4, candidatesTokenCount: 2, thoughtsTokenCount: 10 } })
  ]));
  const response = await handleChat(chatRequest(body({ stream: true, mode: 'reasoning' })), { env, fetcher });
  assert.equal(response.headers.get('content-type'), 'text/event-stream; charset=utf-8');
  assert.equal(response.headers.get('cache-control'), 'no-store');
  assert.deepEqual(await readEvents(response), [
    { type: 'thinking', delta: 'Considering' }, { type: 'text', delta: 'Hel' }, { type: 'text', delta: 'lo' },
    { type: 'done', truncated: false, usage: { inputTokens: 4, outputTokens: 2, reasoningTokens: 10 } }]);
  assert.match(fetcher.calls[0].url, /:streamGenerateContent\?alt=sse$/);
});

test('stream: Auto mode hides thought deltas', async () => {
  const fetcher = mockFetch(sseResponse([sse({ candidates: [{ content: { parts: [{ text: 'hidden', thought: true }, { text: 'Visible' }] }, finishReason: 'STOP' }] })]));
  const events = await readEvents(await handleChat(chatRequest(body({ stream: true })), { env, fetcher }));
  assert.deepEqual(events.map(e => e.type), ['text', 'done']);
});

test('stream: Grok reasoning summaries via xAI Responses', async () => {
  const fetcher = mockFetch(sseResponse([
    sse({ type: 'response.created' }, 'response.created'),
    sse({ type: 'response.reasoning_summary_text.delta', delta: 'Checking primes' }, 'response.reasoning_summary_text.delta'),
    sse({ type: 'response.output_text.delta', delta: 'p = 3' }, 'response.output_text.delta'),
    sse({ type: 'response.completed', response: { usage: { input_tokens: 9, output_tokens: 30 } } }, 'response.completed')
  ]));
  const events = await readEvents(await handleChat(chatRequest(body({ model: 'Grok', stream: true, mode: 'reasoning' })), { env, fetcher }));
  assert.deepEqual(events.map(e => e.delta ?? e.type), ['Checking primes', 'p = 3', 'done']);
  assert.equal(fetcher.calls[0].url, 'https://api.x.ai/v1/responses');
  assert.deepEqual(fetcher.calls[0].body.reasoning, { effort: 'high' });
});

test('stream: Claude in Reasoning mode gets a notice and normal streaming', async () => {
  const fetcher = mockFetch(sseResponse([sse({ type: 'content_block_delta', delta: { type: 'text_delta', text: 'Hi' } }, 'content_block_delta'), sse({ type: 'message_stop' }, 'message_stop')]));
  const events = await readEvents(await handleChat(chatRequest(body({ model: 'Claude', stream: true, mode: 'reasoning' })), { env, fetcher }));
  assert.deepEqual(events.map(e => e.type), ['notice', 'text', 'done']);
  assert.match(events[0].message, /Claude has no visible thinking/);
});

test('stream: upstream that ignores stream falls back to one text event', async () => {
  const fetcher = mockFetch(Response.json({ choices: [{ message: { content: 'Whole reply' }, finish_reason: 'stop' }] }));
  const events = await readEvents(await handleChat(chatRequest(body({ model: 'Kimi', stream: true })), { env, fetcher }));
  assert.deepEqual(events, [{ type: 'text', delta: 'Whole reply' }, { type: 'done', truncated: false }]);
});

test('stream: provider failure mid-reply ends with an error event after partial text', async () => {
  const fetcher = mockFetch(sseResponse([sse({ type: 'response.output_text.delta', delta: 'Partial' }), sse({ type: 'response.failed', response: { error: { message: 'raw upstream detail' } } })]));
  const events = await readEvents(await handleChat(chatRequest(body({ model: 'ChatGPT', stream: true })), { env, fetcher }));
  assert.deepEqual(events.map(e => e.type), ['text', 'error']);
  assert.doesNotMatch(events[1].message, /raw upstream detail/);
});

test('stream: provider errors use error events while setup errors stay JSON', async () => {
  const fetcher = mockFetch(new Response('{"error":{"message":"quota"}}', { status: 429, headers: { 'retry-after': '30', 'content-type': 'application/json' } }));
  const response = await handleChat(chatRequest(body({ model: 'Grok', stream: true })), { env, fetcher });
  assert.equal(response.status, 200);
  const events = await readEvents(response);
  assert.equal(events.length, 1);
  assert.equal(events[0].type, 'error');
  assert.equal(events[0].status, 429);
  assert.equal(events[0].upstreamStatus, 429);
  assert.equal(events[0].retryAfterSeconds, 30);
  assert.match(events[0].message, /Grok quota limit/);
  const missing = await handleChat(chatRequest(body({ stream: true })), { env: {}, fetcher: mockFetch() });
  assert.equal(missing.status, 503);
});

test('stream: web search emits sources and keeps the Sources footer the app parses', async () => {
  const fetcher = mockFetch(
    Response.json({ web: { results: [{ title: 'Example', url: 'https://example.com/a', description: 'Snippet' }] } }),
    sseResponse([sse({ candidates: [{ content: { parts: [{ text: 'Answer [1]' }] }, finishReason: 'STOP' }] })]));
  const events = await readEvents(await handleChat(chatRequest(body({ stream: true, web: 'search' })), { env: { ...env, BRAVE_SEARCH_API_KEY: 'brave', DUA_SEARCH_PREFER_BRAVE: '1' }, fetcher }));
  assert.deepEqual(events[0], { type: 'sources', sources: [{ id: 1, title: 'Example', url: 'https://example.com/a', domain: 'example.com' }] });
  assert.equal(events.filter(e => e.type === 'text').map(e => e.delta).join(''), 'Answer [1]\n\nSources:\n[1] Example\nhttps://example.com/a');
  assert.match(JSON.stringify(fetcher.calls[1].body), /External source material/);
});

test('Grok rejects WebP with a conversion hint even if opted in; text/code files work everywhere', async () => {
  const webp = Buffer.concat([Buffer.from('RIFF'), Buffer.alloc(4), Buffer.from('WEBP')]).toString('base64');
  const response = await handleChat(chatRequest(body({ model: 'Grok', attachments: [{ name: 'a.webp', mimeType: 'image/webp', data: webp }] })), { env: { ...env, GROK_ATTACHMENT_TYPES: 'image/webp,image/jpeg' }, fetcher: mockFetch() });
  assert.equal(response.status, 415);
  assert.match((await response.json()).error, /JPEG or PNG/);
  const fetcher = mockFetch(Response.json({ output: [{ type: 'message', role: 'assistant', content: [{ type: 'output_text', text: 'Sum is 3' }] }] }));
  const csv = await handleChat(chatRequest(body({ model: 'Grok', attachments: [{ name: 'n.csv', mimeType: 'text/csv', data: b64('a\n1\n2') }] })), { env, fetcher });
  assert.deepEqual(await csv.json(), { text: 'Sum is 3' });
  assert.match(JSON.stringify(fetcher.calls[0].body.input.at(-1)), /Attached text file \(n\.csv\)/);
});

test('request validation covers the new optional fields', () => {
  assert.equal(validateChat(body({ stream: true, mode: 'reasoning' })), null);
  assert.deepEqual(validateChat(body({ stream: 'yes' })), ['Invalid stream option.', 400]);
  assert.deepEqual(validateChat(body({ mode: 'genius' })), ['Choose Auto, Reasoning or Build mode.', 400]);
  assert.deepEqual(validateChat(body({ extra: 1 })), ['Invalid request.', 400]);
  assert.equal(validateChat(body({ messages: [{ role: 'user', text: 'a', attachmentText: 'x' }, { role: 'model', text: 'b' }, { role: 'user', text: 'c' }] })), null);
  assert.deepEqual(validateChat(body({ messages: [{ role: 'model', text: 'b', attachmentText: 'x' }, { role: 'user', text: 'c' }] })), ['Invalid messages.', 400]);
  assert.deepEqual(validateChat(body({ messages: [{ role: 'user', text: 'c', attachmentText: 'x'.repeat(65537) }] })), ['Invalid messages.', 400]);
});

test('config advertises reasoning support and Grok image types without WebP', () => {
  const providers = providerConfiguration({ ...env, GROK_ATTACHMENT_TYPES: 'image/webp,image/png' });
  const grok = providers.find(p => p.name === 'Grok');
  assert.deepEqual(grok.attachmentTypes, ['text/plain', 'image/png']);
  assert.deepEqual(providers.map(p => [p.name, p.reasoning]), [['Dua', true], ['Gemini', true], ['ChatGPT', true], ['Claude', false], ['Grok', true], ['Kimi', false]]);
  assert.equal(providers.find(p => p.name === 'Gemini').model, 'gemini-3.8-flash');
});

test('service handler applies the configurable chat deadline', async () => {
  assert.equal(chatDeadlineMs({}), 280000);
  assert.equal(chatDeadlineMs({ DUA_CHAT_DEADLINE_SECONDS: '120' }), 120000);
  assert.equal(chatDeadlineMs({ DUA_CHAT_DEADLINE_SECONDS: '5000' }), 280000);
  const hanging = (url, init) => new Promise((resolve, reject) => init.signal.addEventListener('abort', () => reject(init.signal.reason), { once: true }));
  const started = Date.now();
  const response = await handleService(chatRequest(body({ stream: true })), { env: { ...env, DUA_CHAT_DEADLINE_SECONDS: '10' }, fetcher: hanging });
  assert.equal(response.status, 200);
  const events = await readEvents(response);
  assert.equal(events.length, 1);
  assert.equal(events[0].type, 'error');
  assert.equal(events[0].status, 504);
  assert.ok(Date.now() - started >= 9500);
});

test('cloud snapshots keep optional thinking fields and still accept old snapshots', () => {
  const chat = messages => ({ format: 'dua-cloud-snapshot', version: 1, memory: [], chats: [{ id: 'c', updatedAt: 1, model: 'Dua', draft: '', messages }] });
  const old = validateCloudSnapshot(chat([{ role: 'user', text: 'q', state: 'complete' }, { role: 'model', text: 'a', state: 'complete' }]));
  assert.deepEqual(old.chats[0].messages[1], { role: 'model', text: 'a', state: 'complete' });
  const next = validateCloudSnapshot(chat([{ role: 'model', text: 'a', state: 'complete', thinking: 't', thinkingSeconds: 12, unknown: 1 }]));
  assert.deepEqual(next.chats[0].messages[0], { role: 'model', text: 'a', state: 'complete', thinking: 't', thinkingSeconds: 12 });
  assert.throws(() => validateCloudSnapshot(chat([{ role: 'user', text: 'q', state: 'complete', thinking: 't' }])));
  assert.throws(() => validateCloudSnapshot(chat([{ role: 'model', text: 'a', state: 'complete', thinkingSeconds: -1 }])));
});
