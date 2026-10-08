// Phase 5: provider-hosted code execution ("Run code") with mocked providers; nothing runs locally.
import test from 'node:test';
import assert from 'node:assert/strict';
import { handleChat, validateChat, PROVIDER_DEFINITIONS } from '../src/core/providers.mjs';
import { buildRequest, extractReply, parseStreamRecord } from '../src/core/adapters.mjs';
import { codeExecutionProviders, codeExecutionConfiguration, MAX_CODE_RUNS, MAX_OUTPUT_CHARS } from '../src/core/code-execution.mjs';
import { formatEvent } from '../src/core/sse.mjs';
import { mockFetch, chatRequest, readEvents, sseResponse, sse, env } from './helpers.mjs';

const ask = (model, extra = {}) => ({ model, messages: [{ role: 'user', text: 'What is the sum of the first 50 primes?' }], ...extra });
const provider = name => PROVIDER_DEFINITIONS.find(p => p.name === name);
const geminiOnly = { GEMINI_API_KEY: 'test-gemini-key', GEMINI_MODEL: 'gemini-3.8-flash' };
const code = "primes=[]\nn=2\nwhile len(primes)<50:\n  if all(n%p for p in primes): primes.append(n)\n  n+=1\nprint(sum(primes))";
const text = events => events.filter(e => e.type === 'text').map(e => e.delta).join('');

test('Run code is on for Gemini by default; ChatGPT/Grok need an explicit opt-in; DUA_CODE_EXECUTION=0 turns it off', () => {
  assert.deepEqual(codeExecutionProviders(env, PROVIDER_DEFINITIONS), ['Dua', 'Gemini']);
  assert.deepEqual(codeExecutionProviders({ ...env, OPENAI_CODE_EXECUTION: '1', GROK_CODE_EXECUTION: '1' }, PROVIDER_DEFINITIONS), ['Dua', 'Gemini', 'ChatGPT', 'Grok']);
  assert.deepEqual(codeExecutionProviders({ ...env, DUA_CODE_EXECUTION: '0', GROK_CODE_EXECUTION: '1' }, PROVIDER_DEFINITIONS), []);
  assert.deepEqual(codeExecutionProviders({ ...env, GEMINI_CODE_EXECUTION: '0' }, PROVIDER_DEFINITIONS), []);
  const config = codeExecutionConfiguration(geminiOnly, PROVIDER_DEFINITIONS, new Set(['Dua', 'Gemini']));
  assert.deepEqual(config, { enabled: true, providers: ['Dua', 'Gemini'], language: 'python', maxRuns: MAX_CODE_RUNS, maxChars: MAX_OUTPUT_CHARS });
  assert.equal(codeExecutionConfiguration({}, PROVIDER_DEFINITIONS, new Set()).enabled, false);
  assert.match(codeExecutionConfiguration({ DUA_CODE_EXECUTION: '0' }, PROVIDER_DEFINITIONS, new Set(['Dua'])).reason, /DUA_CODE_EXECUTION=0/);
});

test('/api/config exposes the Run code capability without secrets', async () => {
  const config = await (await handleChat(new Request('https://dua.test/api/config'), { env: geminiOnly })).json();
  assert.deepEqual(config.code.providers, ['Dua', 'Gemini']);
  assert.equal(config.code.enabled, true);
  assert.doesNotMatch(JSON.stringify(config), /test-gemini-key/);
  const off = await (await handleChat(new Request('https://dua.test/api/config'), { env: { ...geminiOnly, DUA_CODE_EXECUTION: '0' } })).json();
  assert.equal(off.code.enabled, false);
});

test('request builders add the hosted code tool only when asked, combined with search', () => {
  const base = { model: 'm', key: 'k', systemParts: ['s'], messages: [{ role: 'user', text: 'hi' }] };
  assert.equal(buildRequest(provider('Gemini'), base).body.tools, undefined);
  assert.deepEqual(buildRequest(provider('Gemini'), { ...base, code: true }).body.tools, [{ code_execution: {} }]);
  assert.deepEqual(buildRequest(provider('Gemini'), { ...base, code: true, search: { native: true } }).body.tools, [{ google_search: {} }, { code_execution: {} }]);
  const openai = buildRequest(provider('ChatGPT'), { ...base, code: true }).body;
  assert.deepEqual(openai.tools, [{ type: 'code_interpreter', container: { type: 'auto' } }]);
  assert.deepEqual(openai.include, ['code_interpreter_call.outputs']);
  const xai = buildRequest(provider('Grok'), { ...base, code: true, search: { native: true } }).body;
  assert.deepEqual(xai.tools, [{ type: 'web_search' }, { type: 'code_interpreter' }]);
  assert.equal(xai.include, undefined);
});

test('validateChat: code must be boolean', () => {
  assert.deepEqual(validateChat(ask('Dua', { code: 'yes' })), ['Invalid Run code option.', 400]);
  assert.equal(validateChat(ask('Dua', { code: true })), null);
});

test('streaming: Gemini executableCode and codeExecutionResult become code events between text, saved order kept', async () => {
  const fetcher = mockFetch(sseResponse([
    sse({ candidates: [{ content: { parts: [{ text: 'Let me calculate.' }] } }] }),
    sse({ candidates: [{ content: { parts: [{ executableCode: { language: 'PYTHON', code } }] } }] }),
    sse({ candidates: [{ content: { parts: [{ codeExecutionResult: { outcome: 'OUTCOME_OK', output: '5117\n' } }] } }] }),
    sse({ candidates: [{ content: { parts: [{ text: 'The sum is 5117.' }] }, finishReason: 'STOP' }] })
  ]));
  const events = await readEvents(await handleChat(chatRequest(ask('Dua', { stream: true, code: true })), { env: geminiOnly, fetcher }));
  assert.deepEqual(fetcher.calls[0].body.tools, [{ code_execution: {} }]);
  assert.match(fetcher.calls[0].body.systemInstruction.parts.at(-1).text, /Run code is on/);
  const codeEvents = events.filter(e => e.type === 'code');
  assert.deepEqual(codeEvents, [{ type: 'code', index: 0, language: 'python', code }, { type: 'code', index: 0, output: '5117\n', outcome: 'ok' }]);
  assert.equal(text(events), 'Let me calculate.\n\nThe sum is 5117.');
  assert.ok(events.indexOf(codeEvents[0]) > events.findIndex(e => e.type === 'text'));
  assert.equal(events.at(-1).type, 'done');
});

test('non-streaming: code runs ride along in the JSON reply; failed and timed-out runs are labelled', async () => {
  const fetcher = mockFetch(Response.json({ candidates: [{ finishReason: 'STOP', content: { parts: [
    { executableCode: { language: 'PYTHON', code: '1/0' } }, { codeExecutionResult: { outcome: 'OUTCOME_FAILED', output: 'ZeroDivisionError' } },
    { executableCode: { language: 'PYTHON', code: 'while True: pass' } }, { codeExecutionResult: { outcome: 'OUTCOME_DEADLINE_EXCEEDED' } },
    { text: 'Division by zero fails.' }] } }] }));
  const result = await (await handleChat(chatRequest(ask('Gemini', { code: true })), { env: geminiOnly, fetcher })).json();
  assert.equal(result.text, 'Division by zero fails.');
  assert.deepEqual(result.code, [
    { language: 'python', code: '1/0', output: 'ZeroDivisionError', outcome: 'error' },
    { language: 'python', code: 'while True: pass', output: '', outcome: 'timeout' }]);
});

test('a chart from code adds a notice; code and output are capped, at most 10 runs', () => {
  const big = 'x'.repeat(MAX_OUTPUT_CHARS + 50);
  const parts = [];
  for (let i = 0; i < MAX_CODE_RUNS + 3; i++) parts.push({ executableCode: { language: 'PYTHON', code: 'print(1)' } }, { codeExecutionResult: { outcome: 'OUTCOME_OK', output: big } });
  parts.push({ inlineData: { mimeType: 'image/png', data: 'AAAA' } }, { text: 'Done.' });
  const reply = extractReply('gemini', { candidates: [{ content: { parts } }] });
  assert.equal(reply.code.length, MAX_CODE_RUNS);
  assert.ok(reply.code[0].output.length < MAX_OUTPUT_CHARS + 20 && reply.code[0].output.endsWith('[…truncated]'));
  assert.equal(reply.codeImages, 1);
});

test('chart notice is sent before done in the stream', async () => {
  const fetcher = mockFetch(sseResponse([
    sse({ candidates: [{ content: { parts: [{ executableCode: { language: 'PYTHON', code: 'plot()' } }, { codeExecutionResult: { outcome: 'OUTCOME_OK', output: '' } }, { inlineData: { mimeType: 'image/png', data: 'AAAA' } }] } }] }),
    sse({ candidates: [{ content: { parts: [{ text: 'Here is the chart.' }] }, finishReason: 'STOP' }] })
  ]));
  const events = await readEvents(await handleChat(chatRequest(ask('Dua', { stream: true, code: true })), { env: geminiOnly, fetcher }));
  assert.match(events.at(-2).message, /chart/);
});

test('Run code off on the server or for a provider without it: answers normally with a notice, no tool sent', async () => {
  const off = mockFetch(Response.json({ candidates: [{ content: { parts: [{ text: '5117' }] } }] }));
  const result = await (await handleChat(chatRequest(ask('Dua', { code: true })), { env: { ...geminiOnly, DUA_CODE_EXECUTION: '0' }, fetcher: off })).json();
  assert.equal(off.calls[0].body.tools, undefined);
  assert.match(result.notice, /turned off on this server/);
  const claude = mockFetch(Response.json({ content: [{ type: 'text', text: '5117' }] }));
  const other = await (await handleChat(chatRequest(ask('Claude', { code: true })), { env, fetcher: claude })).json();
  assert.equal(claude.calls[0].body.tools, undefined);
  assert.match(other.notice, /Claude cannot run code/);
  const grok = mockFetch(Response.json({ output: [{ type: 'message', role: 'assistant', content: [{ type: 'output_text', text: 'ok' }] }] }));
  await handleChat(chatRequest(ask('Grok', { code: true })), { env, fetcher: grok });
  assert.equal(grok.calls[0].body.tools, undefined, 'paid xAI code tool needs GROK_CODE_EXECUTION=1');
});

test('a model that rejects the code tool (400) is asked again without it', async () => {
  const fetcher = mockFetch(Response.json({ error: { message: 'tool not supported' } }, { status: 400 }), Response.json({ candidates: [{ content: { parts: [{ text: 'About 5117.' }] } }] }));
  const result = await (await handleChat(chatRequest(ask('Gemini', { code: true })), { env: geminiOnly, fetcher })).json();
  assert.equal(fetcher.calls.length, 2);
  assert.deepEqual(fetcher.calls[0].body.tools, [{ code_execution: {} }]);
  assert.equal(fetcher.calls[1].body.tools, undefined);
  assert.doesNotMatch(JSON.stringify(fetcher.calls[1].body.systemInstruction), /Run code is on/);
  assert.equal(result.text, 'About 5117.');
  assert.match(result.notice, /could not run code/);
});

test('OpenAI / xAI code_interpreter_call items (opt-in) map to the same code events', async () => {
  const item = { type: 'code_interpreter_call', status: 'completed', code: 'print(2+2)', outputs: [{ type: 'logs', logs: '4' }, { type: 'image', url: 'https://x' }] };
  const state = {};
  assert.deepEqual(parseStreamRecord('responses', { event: 'response.output_item.done', data: JSON.stringify({ type: 'response.output_item.done', item }) }, state), [
    { type: 'code', index: 0, language: 'python', code: 'print(2+2)' }, { type: 'code', index: 0, output: '4\n[1 image not shown]', outcome: 'ok' }]);
  const reply = extractReply('responses', { output: [item, { type: 'message', role: 'assistant', content: [{ type: 'output_text', text: '4' }] }] });
  assert.deepEqual(reply.code, [{ language: 'python', code: 'print(2+2)', output: '4\n[1 image not shown]', outcome: 'ok' }]);
  const fetcher = mockFetch(sseResponse([
    sse({ type: 'response.output_item.done', item }, 'response.output_item.done'),
    sse({ type: 'response.output_text.delta', delta: 'It is 4.' }, 'response.output_text.delta'),
    sse({ type: 'response.completed', response: { output: [] } }, 'response.completed')
  ]));
  const events = await readEvents(await handleChat(chatRequest(ask('Grok', { stream: true, code: true })), { env: { ...env, GROK_CODE_EXECUTION: '1' }, fetcher }));
  assert.deepEqual(fetcher.calls[0].body.tools, [{ type: 'code_interpreter' }]);
  assert.equal(events.filter(e => e.type === 'code').length, 2);
});

test('code is a known SSE event type', () => {
  assert.equal(formatEvent({ type: 'code', index: 0, code: 'x' }), 'event: code\ndata: {"type":"code","index":0,"code":"x"}\n\n');
});
