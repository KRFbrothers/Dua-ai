import { createBurstGuard } from './vercel-burst.mjs';
import { handleGemini } from './gemini.mjs';
import { handleAccount, handleCloudRead } from './account.mjs';

// Vercel's Web Standard fetch signature keeps the original URL, Origin,
// cancellation signal and separate Set-Cookie headers intact.
export function createVercelHandler({ env = process.env, fetcher = fetch, guard = createBurstGuard() } = {}) {
  return {
    async fetch(request) {
      const path = new URL(request.url).pathname;
      try {
        const group = path === '/api/chat' && request.method === 'POST' ? 'chat' : path.startsWith('/api/account/') || path === '/api/cloud' ? 'account' : null;
        const wait = group ? guard(request, group, group === 'chat' ? 5 : 20) : 0;
        if (wait) return Response.json({ error: 'Too many requests. Retry shortly.', retryAfterSeconds: wait }, { status: 429, headers: { 'Cache-Control': 'no-store', 'Retry-After': String(wait) } });
        if (path === '/api/config' || path === '/api/chat') {
          return await handleGemini(request, {
            key: env.GEMINI_API_KEY, model: env.GEMINI_MODEL, fetcher
          });
        }
        if (path === '/api/cloud' || /^\/api\/account\/(session|signin|signup|refresh|profile|logout)$/.test(path)) {
          const handler = path === '/api/cloud' && request.method === 'GET' ? handleCloudRead : handleAccount;
          return await handler(request, {
            url: env.SUPABASE_URL, key: env.SUPABASE_ANON_KEY, fetcher
          });
        }
        return Response.json({ error: 'Not found.' }, { status: 404, headers: { 'Cache-Control': 'no-store' } });
      } catch {
        return Response.json({ error: 'Connection failed. Retry without changing local data.' }, {
          status: 502, headers: { 'Cache-Control': 'no-store' }
        });
      }
    }
  };
}
