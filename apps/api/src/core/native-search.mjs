// Provider-native web search (Phase 2) and the citations it returns. Request building lives in
// adapters.mjs; this module decides which search runs and turns citations into Dua sources.
//
// Official tool names (verified against provider docs, Oct 2026):
//   Gemini generateContent: tools: [{ google_search: {} }]  -> candidates[].groundingMetadata.groundingChunks[].web {uri,title}
//   xAI Responses:          tools: [{ type: 'web_search' }, { type: 'x_search' }] -> output_text annotations (url_citation) + response.citations
//   OpenAI Responses:       tools: [{ type: 'web_search' }] -> output_text annotations (url_citation {url,title})
import { searchConfiguration } from './search.mjs';

const off = value => String(value ?? '').trim() === '0';
const on = value => String(value ?? '').trim() === '1';
/** Native search per provider; Claude and Kimi use the Brave fallback. */
export const NATIVE_SEARCH = Object.freeze({ Dua: 'google_search', Gemini: 'google_search', ChatGPT: 'web_search', Grok: 'web_search' });
export const MAX_SOURCES = 10;

/** Providers that can search natively with this configuration (DUA_NATIVE_SEARCH=0 turns all off, <PREFIX>_NATIVE_SEARCH=0 one). */
export function nativeSearchProviders(env = {}, definitions = []) {
  if (off(env.DUA_NATIVE_SEARCH)) return [];
  return definitions.filter(provider => NATIVE_SEARCH[provider.name] && !off(env[provider.prefix + '_NATIVE_SEARCH'])).map(provider => provider.name);
}
/** X search (Grok only) is opt-in per message; DUA_X_SEARCH=0 removes it entirely. */
export const xSearchAvailable = (env = {}) => !off(env.DUA_X_SEARCH);
export const preferBrave = (env = {}) => on(env.DUA_SEARCH_PREFER_BRAVE) && searchConfiguration(env).configured;

/**
 * Which search a chat request uses: {native, brave, x}. `native` adds the provider's own search tool,
 * `brave` keeps the Phase 1 path (Brave results as untrusted context), `x` adds xAI x_search.
 */
export function searchPlan(provider, data, env = {}, definitions = []) {
  const x = data.xSearch === true && provider.name === 'Grok' && xSearchAvailable(env);
  if (data.web !== 'search') return { native: false, brave: false, x };
  const native = nativeSearchProviders(env, definitions).includes(provider.name) && !preferBrave(env);
  return { native, brave: !native, x };
}

function safeUrl(value) {
  if (typeof value !== 'string' || value.length > 2048) return null;
  try {
    const url = new URL(value);
    if (url.protocol !== 'https:' || url.username || url.password || !url.hostname.includes('.')) return null;
    return url.href;
  } catch { return null; }
}
const clean = (value, limit) => typeof value === 'string' ? value.replace(/[\u0000-\u001f\u007f]/g, ' ').replace(/\s+/g, ' ').trim().slice(0, limit) : '';
const DOMAIN = /^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/i;
const host = url => new URL(url).hostname.replace(/^www\./, '');

/** Adds one provider citation to `state.citations` (deduplicated by URL, capped). */
export function addCitation(state, url, title) {
  const href = safeUrl(url);
  if (!href) return;
  state.citations ||= [];
  if (state.citations.length >= MAX_SOURCES * 3 || state.citations.some(item => item.url === href)) return;
  state.citations.push({ url: href, title: clean(title, 200) });
}
/** Collects url_citation annotations from a Responses output array (OpenAI and xAI). */
export function addOutputCitations(state, output) {
  for (const item of Array.isArray(output) ? output : []) {
    if (item?.type !== 'message') continue;
    for (const part of Array.isArray(item.content) ? item.content : []) {
      for (const annotation of Array.isArray(part?.annotations) ? part.annotations : []) {
        if (annotation?.type === 'url_citation') addCitation(state, annotation.url, annotation.title);
      }
    }
  }
}
export function addGroundingCitations(state, metadata) {
  if (!metadata || typeof metadata !== 'object') return;
  for (const chunk of Array.isArray(metadata.groundingChunks) ? metadata.groundingChunks : []) addCitation(state, chunk?.web?.uri, chunk?.web?.title);
  if (Array.isArray(metadata.webSearchQueries)) {
    state.queries ||= [];
    for (const query of metadata.webSearchQueries) {
      const text = clean(query, 200);
      if (text && !state.queries.includes(text) && state.queries.length < 5) state.queries.push(text);
    }
  }
}

/**
 * Provider citations -> Dua sources [{id,title,url,domain}]. Gemini returns Google redirect URLs
 * whose title is the site's domain; xAI titles are citation numbers, so the host is used instead.
 */
export function citationSources(citations = []) {
  return citations.slice(0, MAX_SOURCES).map((citation, index) => {
    const urlHost = host(citation.url);
    const titleDomain = DOMAIN.test(citation.title) ? citation.title.toLowerCase().replace(/^www\./, '') : null;
    const redirect = urlHost === 'vertexaisearch.cloud.google.com';
    const domain = redirect && titleDomain ? titleDomain : urlHost;
    const numeric = /^\d{1,3}$/.test(citation.title);
    const xPost = /^(?:x|twitter)\.com$/.test(urlHost);
    const title = !citation.title || numeric ? (xPost ? 'Post on X' : domain) : citation.title;
    return { id: index + 1, title, url: citation.url, domain };
  });
}
export const sourceDomain = url => { try { return host(url); } catch { return ''; } };
