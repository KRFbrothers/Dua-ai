import test from 'node:test';
import assert from 'node:assert/strict';
import { buildRequest, extractReply, parseStreamRecord, maxOutputTokens, geminiThinkingConfig, reasoningEffort, historyTurns } from '../src/core/adapters.mjs';
import { PROVIDER_DEFINITIONS } from '../src/core/providers.mjs';

const provider = name => PROVIDER_DEFINITIONS.find(p => p.name === name);
const ctx = (extra = {}) => ({ model: 'm', key: 'k', env: {}, systemParts: ['System', 'Memory'], messages: [{ role: 'user', text: 'Hi' }], ...extra });

test('Gemini: Auto mode sends no thinking config; Reasoning asks for thoughts with thinkingLevel', () => {
  const auto = buildRequest(provider('Gemini'), ctx({ model: 'gemini-3.8-flash' }));
  assert.equal(auto.url, 'https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent');
  assert.equal(auto.headers['x-goog-api-key'], 'k');
  assert.equal(auto.body.generationConfig, undefined);
  assert.deepEqual(auto.body.systemInstruction.parts, [{ text: 'System' }, { text: 'Memory' }]);
  const reasoning = buildRequest(provider('Gemini'), ctx({ model: 'gemini-3.8-flash', reasoning: true, stream: true }));
  assert.equal(reasoning.url, 'https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:streamGenerateContent?alt=sse');
  assert.deepEqual(reasoning.body.generationConfig, { thinkingConfig: { includeThoughts: true, thinkingLevel: 'high' } });
});

test('Gemini: 2.5 models use thinkingBudget, level and output cap come from env', () => {
  assert.deepEqual(geminiThinkingConfig('gemini-2.5-flash', {}), { includeThoughts: true, thinkingBudget: -1 });
  assert.deepEqual(geminiThinkingConfig('gemini-3.8-flash', { GEMINI_THINKING_LEVEL: 'Medium' }), { includeThoughts: true, thinkingLevel: 'medium' });
  assert.deepEqual(geminiThinkingConfig('gemini-3.8-flash', { GEMINI_THINKING_LEVEL: 'turbo' }), { includeThoughts: true, thinkingLevel: 'high' });
  const capped = buildRequest(provider('Dua'), ctx({ model: 'gemini-3.8-flash', env: { GEMINI_MAX_OUTPUT_TOKENS: '20000' } }));
  assert.deepEqual(capped.body.generationConfig, { maxOutputTokens: 20000 });
});

test('Grok uses the xAI Responses API with reasoning effort only in Reasoning mode', () => {
  const auto = buildRequest(provider('Grok'), ctx({ model: 'grok-4.7', stream: true }));
  assert.equal(auto.url, 'https://api.x.ai/v1/responses');
  assert.equal(auto.headers.Authorization, 'Bearer k');
  assert.deepEqual(auto.body, { model: 'grok-4.7', input: [{ role: 'system', content: 'System\nMemory' }, { role: 'user', content: 'Hi' }], max_output_tokens: 16384, store: false, stream: true });
  const reasoning = buildRequest(provider('Grok'), ctx({ model: 'grok-4.7', reasoning: true, env: { GROK_REASONING_EFFORT: 'xhigh' } }));
  assert.deepEqual(reasoning.body.reasoning, { effort: 'xhigh' });
  assert.equal(reasoning.body.max_output_tokens, 32768);
  assert.equal(reasoning.body.stream, undefined);
});

test('ChatGPT asks for reasoning summaries in Reasoning mode', () => {
  const auto = buildRequest(provider('ChatGPT'), ctx());
  assert.equal(auto.body.instructions, 'System\nMemory');
  assert.equal(auto.body.reasoning, undefined);
  assert.equal(auto.body.max_output_tokens, 16384);
  const reasoning = buildRequest(provider('ChatGPT'), ctx({ reasoning: true }));
  assert.deepEqual(reasoning.body.reasoning, { effort: 'high', summary: 'auto' });
});

test('Claude and Kimi ignore reasoning and raise the old 2048 cap', () => {
  const claude = buildRequest(provider('Claude'), ctx({ reasoning: true, stream: true }));
  assert.deepEqual(claude.body, { model: 'm', system: 'System\nMemory', messages: [{ role: 'user', content: 'Hi' }], max_tokens: 8192, stream: true });
  assert.equal(claude.headers['anthropic-version'], '2023-06-01');
  const kimi = buildRequest(provider('Kimi'), ctx({ env: { KIMI_MAX_OUTPUT_TOKENS: '4096' } }));
  assert.equal(kimi.body.max_tokens, 4096);
  assert.equal(kimi.body.messages[0].role, 'system');
  assert.equal(maxOutputTokens('KIMI', { KIMI_MAX_OUTPUT_TOKENS: 'lots' }, false), 8192);
  assert.equal(maxOutputTokens('OPENAI', { OPENAI_MAX_OUTPUT_TOKENS: '10' }, false), 16384);
  assert.equal(reasoningEffort('ANTHROPIC', {}), null);
});

test('Attachments and earlier text files are placed on the right turns', () => {
  const messages = [{ role: 'user', text: 'Read this\nFiles: a.csv', attachmentText: 'Attached text file (a.csv):\nx,y' }, { role: 'model', text: 'Done' }, { role: 'user', text: 'Sum y', attachmentText: 'ignored on last turn' }];
  assert.match(historyTurns(messages)[0].text, /re-sent for context[\s\S]*x,y/);
  assert.equal(historyTurns(messages)[2].text, 'Sum y');
  const parts = [{ text: 'Attached file: p.png' }, { inlineData: { mimeType: 'image/png', data: 'AAAA' } }];
  const files = [{ name: 'p.png', mimeType: 'image/png', data: 'AAAA' }];
  const grok = buildRequest(provider('Grok'), ctx({ messages, parts, files, webContext: 'Sources' }));
  assert.deepEqual(grok.body.input.at(-1).content, [
    { type: 'input_text', text: 'Sum y' }, { type: 'input_text', text: 'Attached file: p.png' },
    { type: 'input_image', image_url: 'data:image/png;base64,AAAA' }, { type: 'input_text', text: 'Sources' }]);
  const gemini = buildRequest(provider('Gemini'), ctx({ messages, parts, files }));
  assert.equal(gemini.body.contents.length, 3);
  assert.equal(gemini.body.contents.at(-1).parts.length, 3);
  assert.equal(gemini.body.contents[1].role, 'model');
});

test('extractReply splits thinking from text for each provider', () => {
  assert.deepEqual(extractReply('gemini', { candidates: [{ content: { parts: [{ text: 'plan', thought: true }, { text: 'Hello ' }, { text: 'world' }] }, finishReason: 'STOP' }], usageMetadata: { promptTokenCount: 3, candidatesTokenCount: 2, thoughtsTokenCount: 9 } }),
    { text: 'Hello world', thinking: 'plan', truncated: false, usage: { inputTokens: 3, outputTokens: 2, reasoningTokens: 9 } });
  const responses = extractReply('responses', { status: 'incomplete', incomplete_details: { reason: 'max_output_tokens' }, output: [{ type: 'reasoning', summary: [{ type: 'summary_text', text: 'A' }, { type: 'summary_text', text: 'B' }] }, { type: 'message', role: 'assistant', content: [{ type: 'output_text', text: 'Hi' }] }] });
  assert.equal(responses.text, 'Hi'); assert.equal(responses.thinking, 'A\n\nB'); assert.equal(responses.truncated, true);
  assert.deepEqual(extractReply('anthropic', { content: [{ type: 'thinking', thinking: 't' }, { type: 'text', text: 'x' }], stop_reason: 'end_turn' }).text, 'x');
  assert.equal(extractReply('compatible', { choices: [{ message: { content: 'k', reasoning_content: 'r' }, finish_reason: 'length' }] }).truncated, true);
});

test('parseStreamRecord maps provider stream events to thinking/text deltas', () => {
  const run = (type, records) => { const state = {}; const events = records.flatMap(r => parseStreamRecord(type, typeof r === 'string' ? { event: 'message', data: r } : r, state)); return { events, state }; };
  const gemini = run('gemini', [JSON.stringify({ candidates: [{ content: { parts: [{ text: 'Thinking…', thought: true }] } }] }), JSON.stringify({ candidates: [{ content: { parts: [{ text: 'Hi' }] }, finishReason: 'MAX_TOKENS' }], usageMetadata: { candidatesTokenCount: 1 } })]);
  assert.deepEqual(gemini.events, [{ type: 'thinking', delta: 'Thinking…' }, { type: 'text', delta: 'Hi' }]);
  assert.equal(gemini.state.truncated, true); assert.equal(gemini.state.done, true);
  const xai = run('responses', [
    { event: 'response.reasoning_summary_text.delta', data: JSON.stringify({ type: 'response.reasoning_summary_text.delta', delta: 'R1' }) },
    { event: 'response.reasoning_summary_part.added', data: JSON.stringify({ type: 'response.reasoning_summary_part.added', summary_index: 1 }) },
    { event: 'response.reasoning_text.delta', data: JSON.stringify({ type: 'response.reasoning_text.delta', delta: 'R2' }) },
    { event: 'response.output_text.delta', data: JSON.stringify({ type: 'response.output_text.delta', delta: 'Answer' }) },
    { event: 'response.completed', data: JSON.stringify({ type: 'response.completed', response: { usage: { input_tokens: 5, output_tokens: 7, output_tokens_details: { reasoning_tokens: 4 } } } }) }]);
  assert.deepEqual(xai.events.map(e => e.delta), ['R1', '\n\n', 'R2', 'Answer']);
  assert.deepEqual(xai.state.usage, { inputTokens: 5, outputTokens: 7, reasoningTokens: 4 });
  const failed = run('responses', [JSON.stringify({ type: 'response.failed', response: { error: { message: 'x' } } })]);
  assert.ok(failed.state.error);
  const claude = run('anthropic', [JSON.stringify({ type: 'message_start', message: { usage: { input_tokens: 2 } } }), JSON.stringify({ type: 'content_block_delta', delta: { type: 'text_delta', text: 'C' } }), JSON.stringify({ type: 'message_delta', delta: { stop_reason: 'max_tokens' }, usage: { output_tokens: 1 } }), JSON.stringify({ type: 'message_stop' })]);
  assert.deepEqual(claude.events, [{ type: 'text', delta: 'C' }]);
  assert.deepEqual(claude.state, { usage: { inputTokens: 2, outputTokens: 1 }, truncated: true, done: true });
  const kimi = run('compatible', [JSON.stringify({ choices: [{ delta: { reasoning_content: 'k' } }] }), JSON.stringify({ choices: [{ delta: { content: 'K' }, finish_reason: 'stop' }] }), '[DONE]']);
  assert.deepEqual(kimi.events.map(e => e.type), ['thinking', 'text']);
  assert.equal(kimi.state.done, true);
  assert.deepEqual(run('gemini', ['not json']).events, []);
});
