import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { MAX_REQUEST_BYTES } from './attachments.mjs';
import { handleGemini } from './gemini.mjs';

const root = path.resolve(fileURLToPath(new URL('./dist/', import.meta.url)));
const types = { '.html': 'text/html; charset=utf-8', '.png': 'image/png', '.svg': 'image/svg+xml', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8' };
function json(res, code, data) {
  res.writeHead(code, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
  res.end(JSON.stringify(data));
}
export function makeServer({ key = process.env.GEMINI_API_KEY, model = process.env.GEMINI_MODEL, fetcher = fetch } = {}) {
  return createServer(async (req, res) => {
    const controller = new AbortController();
    const onDisconnect = () => {
      if (!res.writableEnded) controller.abort();
    };
    res.on?.('close', onDisconnect);
    try {
      const url = new URL(req.url, 'http://localhost');
      if (url.pathname === '/api/config' || url.pathname === '/api/chat') {
        const chunks = []; let size = 0;
        for await (const chunk of req) {
          size += chunk.length;
          if (size > MAX_REQUEST_BYTES) return json(res, 413, { error: 'Message history is too large.' });
          chunks.push(chunk);
        }
        const body = Buffer.concat(chunks).toString('utf8');
        const request = new Request('http://' + (req.headers.host || 'localhost') + req.url, {
          method: req.method,
          headers: req.headers,
          signal: controller.signal,
          ...(!['GET','HEAD'].includes(req.method) ? { body } : {})
        });
        const response = await handleGemini(request, { key, model, fetcher });
        if (res.destroyed) return;
        res.writeHead(response.status, Object.fromEntries(response.headers));
        return res.end(await response.text());
      }
      if (url.pathname.startsWith('/api/')) return json(res, 404, { error: 'Not found.' });
      if (!['GET','HEAD'].includes(req.method)) return json(res, 405, { error: 'Method not allowed.' });
      const pathname = decodeURIComponent(url.pathname);
      const name = pathname.endsWith('/') ? pathname + 'index.html' : pathname;
      const file = path.resolve(root, '.' + name);
      if (!file.startsWith(root + path.sep) || !types[path.extname(file)]) return json(res, 404, { error: 'Not found.' });
      let content;
      try { content = await readFile(file); } catch { return json(res, 404, { error: 'Not found.' }); }
      res.writeHead(200, { 'Content-Type': types[path.extname(file)], 'Cache-Control': 'no-cache', 'X-Content-Type-Options': 'nosniff' });
      res.end(req.method === 'HEAD' ? undefined : content);
    } catch (error) {
      if (res.destroyed) return;
      json(res, error.name === 'TimeoutError' ? 504 : 500, { error: 'Connection fail hua. Dobara try karein.' });
    } finally {
      res.off?.('close', onDisconnect);
    }
  });
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  makeServer().listen(Number(process.env.PORT || 3000), '127.0.0.1', () => console.log(`Dua ready: http://localhost:${process.env.PORT || 3000}`));
}
