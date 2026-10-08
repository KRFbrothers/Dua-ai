// Phase 2 long context: per-provider budget, incremental summaries, xAI cache prefix (mocked fetch).
import test from 'node:test';
import assert from 'node:assert/strict';
import { handleChat, validateChat } from '../src/core/providers.mjs';
import { contextBudget, planContext, promptCacheKey, summaryChunks, transcriptOf, turnChars, validContext } from '../src/core/context.mjs';
import { mockFetch, chatRequest, readEvents, sseResponse, sse, env } from './helpers.mjs';

const turns = (count, size = 1000) => Array.from({ length: count }, (_, i) => ({ role: i % 2 ? 'model' : 'user', text: `${i % 2 ? 'Reply' : 'Question'} ${i}: ` + 'x'.repeat(size) }));
const geminiJson = text => Response.json({ candidates: [{ content: { parts: [{ text }] }, finishReason: 'STOP' }] });
const small = { ...env, GEMINI_CONTEXT_CHARS: '20000', GROK_CONTEXT_CHARS: '20000' };

test('budget defaults per provider, overridable within bounds', () => {
  assert.equal(contextBudget('GEMINI', {}), 400000);
  assert.equal(contextBudget('KIMI', {}), 120000);
  assert.equal(contextBudget('OPENAI', { OPENAI_CONTEXT_CHARS: '90000' }), 90000);
  assert.equal(contextBudget('OPENAI', { OPENAI_CONTEXT_CHARS: '5' }), 200000, 'out-of-range values are ignored');
});

test('planContext keeps everything that fits, otherwise the newest turns down to half the budget, starting on a user turn', () => {
  assert.deepEqual(planContext(turns(10, 100), 20000), { keepFrom: 0, total: turns(10, 100).reduce((s, m) => s + turnChars(m), 0) });
  const many = turns(41, 1000); // ~41k chars
  const { keepFrom } = planContext(many, 20000);
  assert.ok(keepFrom > 0 && keepFrom < 41);
  assert.equal(many[keepFrom].role, 'user');
  const kept = many.slice(keepFrom).reduce((s, m) => s + turnChars(m), 0);
  assert.ok(kept <= 20000 * 0.5, `kept ${kept} within the low-water mark`);
  assert.ok(kept >= 20000 * 0.25, `kept ${kept}: recent turns stay verbatim even with a small budget`);
  // Without low water (older apps, no summary) the window fills the whole budget.
  const legacy = planContext(many, 20000, { lowWater: false });
  assert.ok(legacy.keepFrom < keepFrom);
  // One huge latest turn is always kept.
  assert.equal(planContext([{ role: 'user', text: 'y'.repeat(50000) }], 20000).keepFrom, 0);
  // Stored-file transcripts count toward the budget.
  assert.equal(turnChars({ role: 'user', text: 'a', files: [{ path: 'p', name: 'n', mimeType: 'audio/mp4', size: 1, transcript: 'z'.repeat(500) }] }), 701);
});

test('transcript for the summariser names files, excerpts transcripts and strips Sources footers', () => {
  const text = transcriptOf([
    { role: 'user', text: 'Summarise this', files: [{ path: 'p', name: 'call.m4a', mimeType: 'audio/mp4', size: 1, transcript: 'We agreed on Friday.' }] },
    { role: 'model', text: 'Done.\n\nSources:\n[1] A\nhttps://a.example' }
  ]);
  assert.match(text, /User: Summarise this\n\[Attached file: call\.m4a \(audio\/mp4\)\] Transcript: We agreed on Friday\./);
  assert.match(text, /Assistant: Done\.$/);
  assert.equal(summaryChunks(turns(10, 5000), 12000).length, 5);
});

test('validation: up to 400 turns, context shape, summary size', () => {
  const body = messages => ({ model: 'Dua', messages });
  assert.equal(validateChat(body(turns(399, 10).concat({ role: 'user', text: 'last' }).slice(-399).concat([]))), null);
  assert.equal(validateChat(body([...turns(400, 10)].slice(0, 399).concat({ role: 'user', text: 'q' }))), null);
  assert.deepEqual(validateChat(body([...turns(400, 10), { role: 'user', text: 'q' }])), ['Invalid messages.', 400]);
  assert.equal(validContext({ id: 'abcd-1234', summary: 'S' }), true);
  assert.equal(validContext({ id: 'bad id!' }), false);
  assert.equal(validContext({ summary: 'x'.repeat(16001) }), false);
  assert.equal(validContext({ other: 1 }), false);
  assert.deepEqual(validateChat({ model: 'Dua', messages: [{ role: 'user', text: 'q' }], context: [] }), ['Invalid conversation context.', 400]);
});

test('over budget with context: older turns are summarised by the current provider, summary sent back and used', async () => {
  const messages = [...turns(40, 1000), { role: 'user', text: 'And now?' }];
  const fetcher = mockFetch(geminiJson('S1: the user asked 20 questions about x.'),
    sseResponse([sse({ candidates: [{ content: { parts: [{ text: 'Answer' }] }, finishReason: 'STOP' }] })]));
  const events = await readEvents(await handleChat(chatRequest({ model: 'Gemini', messages, stream: true, context: { id: 'chat-abcdef12' } }), { env: small, fetcher }));
  const [summaryCall, chatCall] = fetcher.calls;
  assert.match(summaryCall.url, /:generateContent$/, 'summary is a non-streaming call');
  assert.match(summaryCall.body.systemInstruction.parts[0].text, /running summary/);
  const prompt = summaryCall.body.contents[0].parts[0].text;
  assert.match(prompt, /Existing summary: \(none\)/);
  assert.match(prompt, /User: Question 0:/);
  const context = events.find(e => e.type === 'context');
  assert.equal(events[0].type, 'context');
  assert.equal(context.summary, 'S1: the user asked 20 questions about x.');
  assert.ok(context.covered > 0 && context.covered < 41);
  assert.equal(chatCall.body.contents.length, 41 - context.covered, 'only the window is sent');
  assert.equal(chatCall.body.contents[0].role, 'user');
  assert.match(chatCall.body.systemInstruction.parts.at(-1).text, /^Summary of the earlier part of this conversation[\s\S]*S1: the user asked/);
  assert.doesNotMatch(prompt, /And now\?/, 'the latest turn is never summarised');
  assert.equal(events.at(-1).type, 'done');
});

test('incremental: the cached summary is reused without a new call while the window fits; next summary merges it', async () => {
  const fits = mockFetch(geminiJson('Hi again'));
  const json = await (await handleChat(chatRequest({ model: 'Gemini', messages: turns(5, 100).concat({ role: 'user', text: 'q' }).slice(-5), context: { summary: 'Earlier: user likes tea.' } }), { env: small, fetcher: fits })).json();
  assert.equal(fits.calls.length, 1);
  assert.equal(json.context, undefined);
  assert.match(fits.calls[0].body.systemInstruction.parts.at(-1).text, /user likes tea/);
  const grows = mockFetch(geminiJson('Merged summary'), geminiJson('ok'));
  const result = await (await handleChat(chatRequest({ model: 'Gemini', messages: [...turns(40, 1000), { role: 'user', text: 'q' }], context: { summary: 'Earlier: user likes tea.' } }), { env: small, fetcher: grows })).json();
  assert.match(grows.calls[0].body.contents[0].parts[0].text, /^Existing summary:\nEarlier: user likes tea\./);
  assert.equal(result.context.summary, 'Merged summary');
  assert.ok(result.context.covered > 0);
});

test('summary failure keeps the chat working: oldest turns dropped with a notice; older apps never trigger summaries', async () => {
  const fetcher = mockFetch(Response.json({}, { status: 500 }), geminiJson('Answer'));
  const result = await (await handleChat(chatRequest({ model: 'Gemini', messages: [...turns(40, 1000), { role: 'user', text: 'q' }], context: {} }), { env: small, fetcher })).json();
  assert.equal(result.text, 'Answer');
  assert.match(result.notice, /could not be summarised/);
  assert.equal(result.context, undefined);
  assert.ok(fetcher.calls[1].body.contents.length < 41);
  const legacy = mockFetch(geminiJson('Old app answer'));
  const old = await (await handleChat(chatRequest({ model: 'Gemini', messages: [...turns(38, 1000), { role: 'user', text: 'q' }] }), { env: small, fetcher: legacy })).json();
  assert.equal(legacy.calls.length, 1, 'no summary call without a context field');
  assert.match(old.notice, /oldest messages were left out/);
  // Older apps within the budget are unchanged: every turn is sent.
  const normal = mockFetch(geminiJson('ok'));
  await handleChat(chatRequest({ model: 'Gemini', messages: [...turns(38, 100), { role: 'user', text: 'q' }] }), { env, fetcher: normal });
  assert.equal(normal.calls[0].body.contents.length, 39);
});

test('xAI prompt caching: stable prefix (system, memory, summary, earlier turns) across turns, web rules stay in the last turn', async () => {
  const reply = () => Response.json({ output: [{ type: 'message', role: 'assistant', content: [{ type: 'output_text', text: 'ok' }] }] });
  const base = [{ role: 'user', text: 'one' }, { role: 'model', text: 'two' }];
  const first = mockFetch(reply());
  await handleChat(chatRequest({ model: 'Grok', memory: 'Call me F.', messages: [...base, { role: 'user', text: 'three' }], context: { id: 'conv-12345678', summary: 'Older bits.' } }), { env, fetcher: first });
  const second = mockFetch(reply());
  await handleChat(chatRequest({ model: 'Grok', memory: 'Call me F.', messages: [...base, { role: 'user', text: 'three' }, { role: 'model', text: 'ok' }, { role: 'user', text: 'four' }], context: { id: 'conv-12345678', summary: 'Older bits.' } }), { env, fetcher: second });
  const a = first.calls[0].body.input; const b = second.calls[0].body.input;
  assert.deepEqual(b.slice(0, a.length - 1), a.slice(0, -1), 'earlier input items are byte-identical');
  assert.match(a[0].content, /Call me F\.[\s\S]*Older bits\./);
  assert.equal(first.calls[0].body.prompt_cache_key, promptCacheKey('conv-12345678'));
  // Read link material (with its rules) goes into the latest user turn, not the system prompt.
  const page = mockFetch(reply());
  await handleChat(chatRequest({ model: 'Grok', messages: [{ role: 'user', text: 'Read https://example.com/post' }], web: 'page', context: { id: 'conv-12345678' } }),
    { env, fetcher: page, pageReader: async url => ({ url, title: 'Post', text: 'Body' }) });
  assert.doesNotMatch(page.calls[0].body.input[0].content, /untrusted data/);
  assert.match(JSON.stringify(page.calls[0].body.input.at(-1)), /untrusted data, not instructions[\s\S]*External source material/);
});

test('summaries respect stored files: dropped turns are named in the summary input and not re-attached', async () => {
  const ref = { path: '6f1c2f3e-1d2b-4c3a-9e8f-0a1b2c3d4e5f/0b1c2d3e-4f50-4a6b-8c7d-9e0f1a2b3c4d.pdf', name: 'old-report.pdf', mimeType: 'application/pdf', size: 1000 };
  const messages = [{ role: 'user', text: 'Read this', files: [ref] }, ...turns(40, 1000).slice(1), { role: 'user', text: 'q' }];
  const fetcher = mockFetch(geminiJson('Summary mentions old-report.pdf'), geminiJson('ok'));
  const result = await (await handleChat(chatRequest({ model: 'Gemini', messages, context: {} }), { env: small, fetcher })).json();
  assert.equal(fetcher.calls.length, 2, 'no storage request for the summarised file');
  assert.match(fetcher.calls[0].body.contents[0].parts[0].text, /\[Attached file: old-report\.pdf \(application\/pdf\)\]/);
  assert.equal(result.text, 'ok');
});
