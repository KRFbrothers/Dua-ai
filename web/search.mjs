import { readJsonLimited } from './providers.mjs';

const json = (data, status = 200) => Response.json(data, { status, headers: { 'Cache-Control': 'no-store' } });
export function searchConfiguration(env = {}) { return { configured: typeof env.BRAVE_SEARCH_API_KEY === 'string' && Boolean(env.BRAVE_SEARCH_API_KEY.trim()), provider: 'Brave' }; }
function citationUrl(value) {
  if (typeof value !== 'string' || value.length > 2048) return null;
  try {
    const url = new URL(value);
    if (url.protocol !== 'https:' || url.username || url.password || !url.hostname.includes('.') || url.hostname.endsWith('.local') || url.hostname.endsWith('.localhost') || /^(?:\d{1,3}\.){3}\d{1,3}$/.test(url.hostname) || url.hostname.startsWith('[')) return null;
    return url.href;
  } catch { return null; }
}
function plainText(value, limit) {
  if (typeof value !== 'string') return '';
  return value.replace(/<[^>]*>/g, '').replace(/[\u0000-\u001f\u007f]/g, ' ').trim().slice(0, limit);
}

export async function handleSearch(request, { env = process.env, fetcher = fetch } = {}) {
  const url = new URL(request.url);
  if (url.pathname !== '/api/search' || request.method !== 'POST') return json({ error: 'Method not allowed.' }, 405);
  if (request.headers.get('origin') && request.headers.get('origin') !== url.origin) return json({ error: 'Request origin is not allowed.' }, 403);
  let data;
  try { data = await readJsonLimited(request, 4096); }
  catch (error) { if (request.signal.aborted) return json({ error: 'Web search stopped.' }, 504); return json({ error: error.status === 413 ? 'Search query is too large.' : 'Invalid search request.' }, error.status === 413 ? 413 : 400); }
  if (!data || typeof data !== 'object' || Array.isArray(data) || Object.keys(data).some(key => key !== 'query') || typeof data.query !== 'string' || !data.query.trim() || data.query.length > 500 || /[\u0000-\u001f\u007f]/.test(data.query)) return json({ error: 'Enter a search query of 1–500 characters.' }, 400);
  if (!searchConfiguration(env).configured) return json({ error: 'Web search is not configured. Set BRAVE_SEARCH_API_KEY in hosting settings.' }, 503);
  const query = data.query.trim();
  const upstream = new URL('https://api.search.brave.com/res/v1/web/search');
  upstream.searchParams.set('q', query);
  upstream.searchParams.set('count', '5');
  upstream.searchParams.set('safesearch', 'moderate');
  upstream.searchParams.set('text_decorations', 'false');
  try {
    request.signal.throwIfAborted();
    const signal = AbortSignal.any([request.signal, AbortSignal.timeout(15000)]);
    const response = await fetcher(upstream.href, { headers: { Accept: 'application/json', 'X-Subscription-Token': env.BRAVE_SEARCH_API_KEY }, redirect: 'error', signal });
    signal.throwIfAborted();
    if (!response.ok) return json({ error: response.status === 429 ? 'Web search quota limit reached. Retry later.' : response.status === 401 || response.status === 403 ? 'Web search API access is unavailable. Check hosting settings.' : 'Web search service is temporarily unavailable. Retry.' }, response.status === 429 ? 429 : 502);
    const result = await readJsonLimited(response, 512 * 1024);
    signal.throwIfAborted();
    if (!result || !result.web || !Array.isArray(result.web.results)) return json({ error: 'Web search returned an invalid response. Retry.' }, 502);
    const seen = new Set();
    const results = [];
    for (const item of result.web.results.slice(0, 20)) {
      const url = citationUrl(item?.url);
      const title = plainText(item?.title, 200);
      if (!url || !title || seen.has(url)) continue;
      seen.add(url);
      results.push({ title, url, snippet: plainText(item.description, 1000) });
      if (results.length === 5) break;
    }
    return json({ query, results });
  } catch (error) {
    if (error?.name === 'TimeoutError' || error?.name === 'AbortError' || request.signal.aborted) return json({ error: 'Web search stopped or timed out. Retry.' }, 504);
    return json({ error: 'Web search connection or response failed. Retry.' }, 502);
  }
}
