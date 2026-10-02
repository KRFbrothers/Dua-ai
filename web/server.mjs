import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = fileURLToPath(new URL('./dist/', import.meta.url));
const types = { '.html': 'text/html; charset=utf-8', '.png': 'image/png', '.svg': 'image/svg+xml', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8' };
function json(res, code, data) {
  res.writeHead(code, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
  res.end(JSON.stringify(data));
}
export function makeServer({ key = process.env.GEMINI_API_KEY, model = process.env.GEMINI_MODEL || 'gemini-2.5-flash', fetcher = fetch } = {}) {
  return createServer(async (req, res) => {
    try {
      const url = new URL(req.url, 'http://localhost');
      if (url.pathname === '/api/config' && req.method === 'GET') {
        return json(res, 200, { geminiConfigured: Boolean(key), model });
      }
      if (url.pathname === '/api/chat' && req.method === 'POST') {
        if (req.headers.origin && new URL(req.headers.origin).host !== req.headers.host) return json(res, 403, { error: 'Request origin is not allowed.' });
        if (!key) return json(res, 503, { error: 'Gemini API key server par configure karein.' });
        let body = ''; let size = 0;
        for await (const chunk of req) {
          size += chunk.length;
          if (size > 262144) return json(res, 413, { error: 'Message history is too large.' });
          body += chunk.toString();
        }
        let data;
        try { data = JSON.parse(body); } catch { return json(res, 400, { error: 'Invalid request.' }); }
        if (!['Dua', 'Gemini'].includes(data.model)) return json(res, 400, { error: 'Filhaal Gemini ya Dua select karein.' });
        if (!Array.isArray(data.messages) || !data.messages.length || data.messages.length > 40 || data.messages.some(m => !['user', 'model'].includes(m.role) || typeof m.text !== 'string' || !m.text.trim() || m.text.length > 8000) || data.messages.at(-1).role !== 'user') {
          return json(res, 400, { error: 'Invalid messages. Maximum message length: 8000 characters.' });
        }
        const upstream = await fetcher(`https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'x-goog-api-key': key },
          body: JSON.stringify({
            systemInstruction: { parts: [{ text: 'You are Dua, a helpful AI assistant powered by Gemini. Reply in the language the user uses. Be clear and concise.' }] },
            contents: data.messages.map(m => ({ role: m.role, parts: [{ text: m.text }] }))
          }),
          signal: AbortSignal.timeout(45000)
        });
        if (!upstream.ok) {
          const error = upstream.status === 429 ? 'Gemini quota limit aa gayi. Thodi der baad try karein.' : [401,403].includes(upstream.status) ? 'Gemini API key ya access check karein.' : 'Gemini request fail hui. Model configuration check karke dobara try karein.';
          return json(res, upstream.status === 429 ? 429 : 502, { error });
        }
        const result = await upstream.json();
        const text = (result.candidates?.[0]?.content?.parts || []).filter(p => !p.thought && typeof p.text === 'string').map(p => p.text).join('\n');
        if (!text) return json(res, 422, { error: 'Gemini ne text response nahi diya. Message badal kar try karein.' });
        return json(res, 200, { text });
      }
      if (url.pathname.startsWith('/api/')) return json(res, 404, { error: 'Not found.' });
      if (!['GET','HEAD'].includes(req.method)) return json(res, 405, { error: 'Method not allowed.' });
      const name = decodeURIComponent(url.pathname === '/' ? '/index.html' : url.pathname);
      const file = path.resolve(root, '.' + name);
      if (!file.startsWith(root + path.sep) || !types[path.extname(file)]) return json(res, 404, { error: 'Not found.' });
      let content;
      try { content = await readFile(file); } catch { return json(res, 404, { error: 'Not found.' }); }
      res.writeHead(200, { 'Content-Type': types[path.extname(file)], 'Cache-Control': 'no-cache', 'X-Content-Type-Options': 'nosniff' });
      res.end(req.method === 'HEAD' ? undefined : content);
    } catch (error) {
      json(res, error.name === 'TimeoutError' ? 504 : 500, { error: 'Connection fail hua. Dobara try karein.' });
    }
  });
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  makeServer().listen(Number(process.env.PORT || 3000), '127.0.0.1', () => console.log(`Dua ready: http://localhost:${process.env.PORT || 3000}`));
}
