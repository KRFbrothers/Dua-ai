// Phase 2: provider-native search, structured sources, Brave fallback and X search (mocked fetch).
import test from 'node:test';
import assert from 'node:assert/strict';
import { handleChat, validateChat } from '../src/core/providers.mjs';
import { buildRequest, extractReply, parseStreamRecord } from '../src/core/adapters.mjs';
import { citationSources, searchPlan, PROVIDERS_FOR_TEST } from './search-helpers.mjs';
import { mockFetch, chatRequest, readEvents, sseResponse, sse, env } from './helpers.mjs';

const ask = (model, extra = {}) => ({ model, messages: [{ role: 'user', text: 'Who won the match yesterday?' }], ...extra });
const brave = () => Response.json({ web: { results: [{ title: 'Brave result', url: 'https://news.example/a', description: 'Snippet' }] } });
const text = events => events.filter(e => e.type === 'text').map(e => e.delta).join('');
const geminiDone = (parts, groundingMetadata) => ({ candidates: [{ content: { parts }, finishReason: 'STOP', ...(groundingMetadata ? { groundingMetadata } : {}) }] });

test('Gemini uses google_search grounding; sources follow the text with site domains, footer kept for older apps', async () => {
  const fetcher = mockFetch(sseResponse([
    sse({ candidates: [{ content: { parts: [{ text: 'Spain won ' }] } }] }),
    sse(geminiDone([{ text: 'Euro 2024.' }], {
      webSearchQueries: ['euro 2024 winner'],
      groundingChunks: [{ web: { uri: 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/abc', title: 'uefa.com' } },
        { web: { uri: 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/def', title: 'aljazeera.com' } }]
    }))
  ]));
  const events = await readEvents(await handleChat(chatRequest(ask('Dua', { stream: true, web: 'search' })), { env: { ...env, BRAVE_SEARCH_API_KEY: 'brave' }, fetcher }));
  assert.equal(fetcher.calls.length, 1, 'no Brave request');
  assert.deepEqual(fetcher.calls[0].body.tools, [{ google_search: {} }]);
  assert.doesNotMatch(JSON.stringify(fetcher.calls[0].body), /External source material/);
  const sources = events.find(e => e.type === 'sources');
  assert.deepEqual(sources.sources, [
    { id: 1, title: 'uefa.com', url: 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/abc', domain: 'uefa.com' },
    { id: 2, title: 'aljazeera.com', url: 'https://vertexaisearch.cloud.google.com/grounding-api-redirect/def', domain: 'aljazeera.com' }]);
  assert.ok(events.indexOf(sources) > events.findIndex(e => e.type === 'text'), 'sources after answer text');
  assert.equal(text(events), 'Spain won Euro 2024.\n\nSources:\n[1] uefa.com\nhttps://vertexaisearch.cloud.google.com/grounding-api-redirect/abc\n\n[2] aljazeera.com\nhttps://vertexaisearch.cloud.google.com/grounding-api-redirect/def');
  assert.equal(events.at(-1).type, 'done');
});

test('ChatGPT uses the Responses web_search tool; url_citation titles become source chips', async () => {
  const fetcher = mockFetch(sseResponse([
    sse({ type: 'response.output_text.delta', delta: 'Rain likely ([bbc.com](https://www.bbc.com/weather)).' }, 'response.output_text.delta'),
    sse({ type: 'response.output_text.annotation.added', annotation: { type: 'url_citation', url: 'https://www.bbc.com/weather', title: 'BBC Weather', start_index: 13, end_index: 50 } }, 'response.output_text.annotation.added'),
    sse({ type: 'response.completed', response: { output: [{ type: 'message', content: [{ type: 'output_text', annotations: [{ type: 'url_citation', url: 'https://www.bbc.com/weather', title: 'BBC Weather' }, { type: 'url_citation', url: 'https://metoffice.gov.uk/x', title: 'Met Office' }] }] }] } }, 'response.completed')
  ]));
  const events = await readEvents(await handleChat(chatRequest(ask('ChatGPT', { stream: true, web: 'search' })), { env, fetcher }));
  assert.deepEqual(fetcher.calls[0].body.tools, [{ type: 'web_search' }]);
  assert.equal(fetcher.calls[0].body.prompt_cache_key, undefined, 'cache key is xAI only');
  assert.deepEqual(events.find(e => e.type === 'sources').sources, [
    { id: 1, title: 'BBC Weather', url: 'https://www.bbc.com/weather', domain: 'bbc.com' },
    { id: 2, title: 'Met Office', url: 'https://metoffice.gov.uk/x', domain: 'metoffice.gov.uk' }]);
});

test('Grok: web_search plus opt-in x_search, prompt_cache_key from the conversation id, numeric citation titles replaced', async () => {
  const fetcher = mockFetch(Response.json({ output: [{ type: 'message', role: 'assistant', content: [{ type: 'output_text', text: 'Launch moved.[[1]](https://x.com/i/status/1)[[2]](https://www.space.com/a)',
    annotations: [{ type: 'url_citation', url: 'https://x.com/i/status/1', title: '1' }, { type: 'url_citation', url: 'https://www.space.com/a', title: '2' }] }] }] }));
  const response = await handleChat(chatRequest(ask('Grok', { web: 'search', xSearch: true, context: { id: 'chat-1234-abcd' } })), { env, fetcher });
  const result = await response.json();
  const sent = fetcher.calls[0].body;
  assert.deepEqual(sent.tools, [{ type: 'web_search' }, { type: 'x_search' }]);
  assert.match(sent.prompt_cache_key, /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
  assert.doesNotMatch(JSON.stringify(sent), /chat-1234-abcd/, 'raw chat id never leaves the server');
  assert.deepEqual(result.sources, [{ id: 1, title: 'Post on X', url: 'https://x.com/i/status/1', domain: 'x.com' }, { id: 2, title: 'space.com', url: 'https://www.space.com/a', domain: 'space.com' }]);
  assert.match(result.text, /\n\nSources:\n\[1\] Post on X\nhttps:\/\/x\.com\/i\/status\/1/);
  // Same conversation id -> same key (stable cache routing).
  const again = mockFetch(Response.json({ output: [{ type: 'message', role: 'assistant', content: [{ type: 'output_text', text: 'ok' }] }] }));
  await handleChat(chatRequest(ask('Grok', { context: { id: 'chat-1234-abcd' } })), { env, fetcher: again });
  assert.equal(again.calls[0].body.prompt_cache_key, sent.prompt_cache_key);
  assert.equal(again.calls[0].body.tools, undefined);
});

test('X search alone adds only x_search; xAI citations list is used when nothing was annotated', async () => {
  const fetcher = mockFetch(Response.json({ citations: ['https://x.com/i/status/9'], output: [{ type: 'message', role: 'assistant', content: [{ type: 'output_text', text: 'People say yes.' }] }] }));
  const result = await (await handleChat(chatRequest(ask('Grok', { xSearch: true })), { env, fetcher })).json();
  assert.deepEqual(fetcher.calls[0].body.tools, [{ type: 'x_search' }]);
  assert.deepEqual(result.sources, [{ id: 1, title: 'Post on X', url: 'https://x.com/i/status/9', domain: 'x.com' }]);
});

test('X search is Grok only, can be disabled with DUA_X_SEARCH=0 and must be boolean', async () => {
  assert.deepEqual(validateChat(ask('Gemini', { xSearch: true })), ['X search works with Grok only. Select Grok or turn X off.', 400]);
  assert.deepEqual(validateChat(ask('Grok', { xSearch: 'yes' })), ['Invalid X search option.', 400]);
  assert.equal(validateChat(ask('Gemini', { xSearch: false })), null);
  const off = await handleChat(chatRequest(ask('Grok', { xSearch: true })), { env: { ...env, DUA_X_SEARCH: '0' }, fetcher: mockFetch() });
  assert.equal(off.status, 403);
  assert.match((await off.json()).error, /X search is turned off/);
});

test('DUA_SEARCH_PREFER_BRAVE=1 keeps Brave for every provider; Claude and Kimi always use Brave', async () => {
  const preferred = mockFetch(brave(), Response.json({ candidates: [{ content: { parts: [{ text: 'From Brave [1]' }] }, finishReason: 'STOP' }] }));
  const result = await (await handleChat(chatRequest(ask('Gemini', { web: 'search' })), { env: { ...env, BRAVE_SEARCH_API_KEY: 'k', DUA_SEARCH_PREFER_BRAVE: '1' }, fetcher: preferred })).json();
  assert.match(preferred.calls[0].url, /api\.search\.brave\.com/);
  assert.equal(preferred.calls[1].body.tools, undefined);
  assert.match(JSON.stringify(preferred.calls[1].body.contents.at(-1)), /External source material/);
  assert.deepEqual(result.sources, [{ id: 1, title: 'Brave result', url: 'https://news.example/a', domain: 'news.example' }]);
  const claude = mockFetch(brave(), Response.json({ content: [{ type: 'text', text: 'Claude [1]' }] }));
  await handleChat(chatRequest(ask('Claude', { web: 'search' })), { env: { ...env, BRAVE_SEARCH_API_KEY: 'k' }, fetcher: claude });
  assert.match(claude.calls[0].url, /brave/);
  assert.equal(claude.calls[1].body.tools, undefined);
  // Prefer-Brave without a Brave key falls back to native search.
  assert.deepEqual(searchPlan('Gemini', { web: 'search' }, { DUA_SEARCH_PREFER_BRAVE: '1' }), { native: true, brave: false, x: false });
  assert.deepEqual(searchPlan('Kimi', { web: 'search' }, {}), { native: false, brave: true, x: false });
  assert.deepEqual(searchPlan('Grok', { web: 'search' }, { DUA_NATIVE_SEARCH: '0' }), { native: false, brave: true, x: false });
  assert.deepEqual(searchPlan('Grok', { web: 'search' }, { GROK_NATIVE_SEARCH: '0' }), { native: false, brave: true, x: false });
  assert.deepEqual(searchPlan('ChatGPT', { xSearch: true }, {}), { native: false, brave: false, x: false });
});

test('Brave fallback: a model that rejects the native tool (400) is asked again with Brave results', async () => {
  const fetcher = mockFetch(Response.json({ error: { message: 'tools not supported' } }, { status: 400 }), brave(),
    Response.json({ output: [{ type: 'message', role: 'assistant', content: [{ type: 'output_text', text: 'Answer [1]' }] }] }));
  const result = await (await handleChat(chatRequest(ask('ChatGPT', { web: 'search' })), { env: { ...env, BRAVE_SEARCH_API_KEY: 'k' }, fetcher })).json();
  assert.deepEqual(fetcher.calls[0].body.tools, [{ type: 'web_search' }]);
  assert.match(fetcher.calls[1].url, /brave/);
  assert.equal(fetcher.calls[2].body.tools, undefined);
  assert.match(result.notice, /Brave results were used/);
  assert.equal(result.sources[0].url, 'https://news.example/a');
  // Without Brave the provider error is returned as before.
  const plain = mockFetch(Response.json({}, { status: 400 }));
  assert.equal((await handleChat(chatRequest(ask('ChatGPT', { web: 'search' })), { env, fetcher: plain })).status, 502);
});

test('config advertises native search providers, X search and context budgets (additive fields)', async () => {
  const config = await (await handleChat(new Request('https://dua.test/api/config'), { env: { ...env, GEMINI_CONTEXT_CHARS: '50000' } })).json();
  assert.equal(config.web.searchConfigured, false, 'Phase 1 meaning (Brave key) unchanged');
  assert.deepEqual(config.web.nativeSearch, ['Dua', 'Gemini', 'ChatGPT', 'Grok']);
  assert.equal(config.web.xSearch, true);
  assert.equal(config.web.preferBrave, false);
  assert.equal(config.context.summaries, true);
  assert.equal(config.context.budgets.Gemini, 50000);
  assert.equal(config.context.budgets.Grok, 300000);
  const off = await (await handleChat(new Request('https://dua.test/api/config'), { env: { ...env, DUA_X_SEARCH: '0', DUA_NATIVE_SEARCH: '0' } })).json();
  assert.deepEqual([off.web.nativeSearch, off.web.xSearch], [[], false]);
});

test('request builders and parsers: tools per provider, streamed grounding chunks accumulate, unsafe URLs dropped', () => {
  const [gemini, openai, grok, claude] = ['Gemini', 'ChatGPT', 'Grok', 'Claude'].map(name => PROVIDERS_FOR_TEST.find(p => p.name === name));
  const ctx = { model: 'm', key: 'k', systemParts: ['s'], messages: [{ role: 'user', text: 'q' }], search: { native: true, x: true } };
  assert.deepEqual(buildRequest(gemini, ctx).body.tools, [{ google_search: {} }]);
  assert.deepEqual(buildRequest(openai, ctx).body.tools, [{ type: 'web_search' }], 'x_search is never sent to OpenAI');
  assert.deepEqual(buildRequest(grok, ctx).body.tools, [{ type: 'web_search' }, { type: 'x_search' }]);
  assert.equal(buildRequest(claude, ctx).body.tools, undefined);
  const state = {};
  parseStreamRecord('gemini', { data: JSON.stringify({ candidates: [{ groundingMetadata: { groundingChunks: [{ web: { uri: 'https://a.example/1', title: 'a.example' } }] } }] }) }, state);
  parseStreamRecord('gemini', { data: JSON.stringify({ candidates: [{ groundingMetadata: { groundingChunks: [{ web: { uri: 'http://insecure.example', title: 'x' } }, { web: { uri: 'https://b.example/2', title: 'B' } }] } }] }) }, state);
  assert.deepEqual(state.citations.map(c => c.url), ['https://a.example/1', 'https://b.example/2']);
  const reply = extractReply('gemini', geminiDone([{ text: 'x' }], { webSearchQueries: ['q1'], groundingChunks: [{ web: { uri: 'https://c.example/', title: 'C site' } }] }));
  assert.deepEqual([reply.citations, reply.queries], [[{ url: 'https://c.example/', title: 'C site' }], ['q1']]);
  assert.equal(citationSources(Array.from({ length: 15 }, (_, i) => ({ url: `https://s${i}.example/`, title: 'T' }))).length, 10);
});
