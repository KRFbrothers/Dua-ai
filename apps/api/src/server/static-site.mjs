import { readFile, stat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/** Built website (apps/web/ → apps/api/site by `npm run build`). Absent in tests/local API-only runs. */
export const defaultSiteDir = fileURLToPath(new URL('../../site/', import.meta.url));

const TYPES = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png', '.webp': 'image/webp',
  '.ico': 'image/x-icon', '.woff2': 'font/woff2', '.txt': 'text/plain; charset=utf-8', '.webmanifest': 'application/manifest+json'
};

// The site talks to dua-api (same origin) and the dua-ai-engine Cloud Run service.
const CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https:; " +
  "connect-src 'self' https://*.run.app; font-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'";

/** Origin of the incoming request. Cloud Run terminates TLS and reports the scheme in X-Forwarded-Proto. */
export function requestOrigin(headers) {
  const proto = String(headers['x-forwarded-proto'] || '').split(',')[0].trim().toLowerCase() === 'https' ? 'https' : 'http';
  return proto + '://' + (headers.host || 'localhost');
}

async function isFile(file) {
  try { return (await stat(file)).isFile(); } catch { return false; }
}

/** Serve the built single-page website for non-/api paths. Returns false when no site is built. */
export async function serveSite(req, res, { siteDir = defaultSiteDir } = {}) {
  const root = path.resolve(siteDir);
  const index = path.join(root, 'index.html');
  if (!(await isFile(index))) return false;
  const headers = { 'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'same-origin', 'X-Frame-Options': 'DENY', 'Content-Security-Policy': CSP };
  if (!['GET', 'HEAD'].includes(req.method)) {
    res.writeHead(405, { ...headers, Allow: 'GET, HEAD', 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store' });
    res.end('Method not allowed.');
    return true;
  }
  let pathname;
  try { pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname); } catch { pathname = '/'; }
  let file = index;
  if (!pathname.includes('\0') && pathname !== '/') {
    const candidate = path.resolve(root, '.' + pathname);
    if (candidate.startsWith(root + path.sep) && await isFile(candidate)) file = candidate;
    else if (path.extname(pathname)) {
      res.writeHead(404, { ...headers, 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store' });
      res.end('Not found.');
      return true;
    }
  }
  const body = await readFile(file);
  const hashed = file.startsWith(path.join(root, 'assets') + path.sep);
  res.writeHead(200, {
    ...headers,
    'Content-Type': TYPES[path.extname(file).toLowerCase()] || 'application/octet-stream',
    'Content-Length': body.length,
    'Cache-Control': hashed ? 'public, max-age=31536000, immutable' : 'no-cache'
  });
  res.end(req.method === 'HEAD' ? undefined : body);
  return true;
}
