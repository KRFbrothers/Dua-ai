import { createServer } from 'node:http';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { MAX_REQUEST_BYTES } from '../core/attachments.mjs';
import { handleService } from './service-handler.mjs';

function json(res, code, data) {
  res.writeHead(code, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
  res.end(JSON.stringify(data));
}

/** Containers expose HTTP to the platform; local development stays on loopback. */
export function listenOptions(env = process.env) {
  const container = env.NODE_ENV === 'production' || Boolean(env.K_SERVICE);
  const port = Number(env.PORT || (container ? 8080 : 3000));
  if (!Number.isInteger(port) || port < 0 || port > 65535) throw new Error('PORT must be an integer between 0 and 65535.');
  return { port, host: container ? '0.0.0.0' : '127.0.0.1' };
}
export function makeServer({ key = process.env.GEMINI_API_KEY, model = process.env.GEMINI_MODEL, fetcher = fetch, supabaseUrl = process.env.SUPABASE_URL, supabaseKey = process.env.SUPABASE_ANON_KEY, env = process.env } = {}) {
  return createServer(async (req, res) => {
    const controller = new AbortController();
    const onDisconnect = () => {
      if (!res.writableEnded) controller.abort();
    };
    res.on?.('close', onDisconnect);
    try {
      const url = new URL(req.url, 'http://localhost');
      if (url.pathname.startsWith('/api/')) {
        const chunks = []; let size = 0;
        for await (const chunk of req) {
          size += chunk.length;
          if (size > (url.pathname === '/api/cloud' ? 4 * 1024 * 1024 + 1024 : url.pathname.startsWith('/api/account/') || url.pathname.startsWith('/api/files/') ? 65536 : MAX_REQUEST_BYTES)) return json(res, 413, { error: 'Message history is too large.' });
          chunks.push(chunk);
        }
        const body = Buffer.concat(chunks).toString('utf8');
        const request = new Request('http://' + (req.headers.host || 'localhost') + req.url, {
          method: req.method,
          headers: req.headers,
          signal: controller.signal,
          ...(!['GET','HEAD'].includes(req.method) ? { body } : {})
        });
        const response = await handleService(request,{env:{...env,GEMINI_API_KEY:key,GEMINI_MODEL:model,SUPABASE_URL:supabaseUrl,SUPABASE_ANON_KEY:supabaseKey},fetcher});
        if (res.destroyed) return;
        const headers=Object.fromEntries(response.headers);const cookies=response.headers.getSetCookie();if(cookies.length)headers['set-cookie']=cookies;res.writeHead(response.status, headers);
        if (!response.body) return res.end();
        // Stream bodies (SSE chat replies) through as they are produced.
        const reader = response.body.getReader();
        const stop = () => reader.cancel().catch(() => {});
        controller.signal.addEventListener('abort', stop, { once: true });
        try {
          while (true) {
            const { value, done } = await reader.read();
            if (done) break;
            if (res.destroyed) { stop(); break; }
            res.write(value);
          }
        } finally { controller.signal.removeEventListener('abort', stop); }
        return res.end();
      }
      // Local development server for the backend API only; the web client was removed.
      return json(res, 404, { error: 'Not found.' });
    } catch (error) {
      if (res.destroyed) return;
      json(res, error.name === 'TimeoutError' ? 504 : 500, { error: 'Connection fail hua. Dobara try karein.' });
    } finally {
      res.off?.('close', onDisconnect);
    }
  });
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const server = makeServer();
  const options = listenOptions();
  server.listen(options, () => console.log(`Dua API ready: http://${options.host}:${server.address().port}/api/`));
}
