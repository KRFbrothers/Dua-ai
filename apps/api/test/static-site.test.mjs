import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { serveSite, requestOrigin } from '../src/server/static-site.mjs';

test('request origin honors Cloud Run X-Forwarded-Proto so same-origin browser checks pass', () => {
  assert.equal(requestOrigin({ host: 'dua-api.example.run.app', 'x-forwarded-proto': 'https' }), 'https://dua-api.example.run.app');
  assert.equal(requestOrigin({ host: 'dua-api.example.run.app', 'x-forwarded-proto': 'https,http' }), 'https://dua-api.example.run.app');
  assert.equal(requestOrigin({ host: '127.0.0.1:3000' }), 'http://127.0.0.1:3000');
  assert.equal(requestOrigin({ host: 'x', 'x-forwarded-proto': 'gopher' }), 'http://x');
  assert.equal(requestOrigin({}), 'http://localhost');
});

async function withSite(fn, build = true) {
  const dir = mkdtempSync(join(tmpdir(), 'dua-site-'));
  if (build) {
    mkdirSync(join(dir, 'assets'));
    writeFileSync(join(dir, 'index.html'), '<!doctype html><title>Dua AI</title>');
    writeFileSync(join(dir, 'assets', 'app-abc.js'), 'console.log(1)');
    writeFileSync(join(dir, 'logo.webp'), 'RIFF');
  }
  writeFileSync(join(tmpdir(), 'dua-secret-outside.txt'), 'secret');
  const server = createServer(async (req, res) => {
    if (!(await serveSite(req, res, { siteDir: dir }))) { res.writeHead(404); res.end('api-only'); }
  }).listen(0, '127.0.0.1');
  await new Promise(resolve => server.once('listening', resolve));
  try { await fn(`http://127.0.0.1:${server.address().port}`); } finally { server.close(); rmSync(dir, { recursive: true, force: true }); }
}

test('serves the built website with SPA fallback, caching and security headers', () => withSite(async base => {
  const home = await fetch(base + '/');
  assert.equal(home.status, 200);
  assert.match(home.headers.get('content-type'), /text\/html/);
  assert.equal(home.headers.get('cache-control'), 'no-cache');
  assert.match(home.headers.get('content-security-policy'), /frame-ancestors 'none'/);
  assert.equal(home.headers.get('x-content-type-options'), 'nosniff');
  assert.match(await home.text(), /Dua AI/);
  const asset = await fetch(base + '/assets/app-abc.js');
  assert.equal(asset.status, 200);
  assert.match(asset.headers.get('cache-control'), /immutable/);
  assert.match(asset.headers.get('content-type'), /javascript/);
  assert.equal((await fetch(base + '/logo.webp')).headers.get('content-type'), 'image/webp');
  const deep = await fetch(base + '/c/some-chat');
  assert.equal(deep.status, 200);
  assert.match(await deep.text(), /Dua AI/);
  assert.equal((await fetch(base + '/missing.js')).status, 404);
  assert.equal((await fetch(base + '/', { method: 'POST' })).status, 405);
  assert.equal((await fetch(base + '/', { method: 'HEAD' })).status, 200);
}));

test('never serves files outside the site folder', () => withSite(async base => {
  for (const p of ['/../dua-secret-outside.txt', '/%2e%2e/dua-secret-outside.txt', '/assets/..%2f..%2fdua-secret-outside.txt']) {
    const res = await fetch(base + p);
    assert.notEqual(await res.text(), 'secret', p);
  }
}));

test('without a built site the server stays API-only (404 for /)', () => withSite(async base => {
  const res = await fetch(base + '/');
  assert.equal(res.status, 404);
  assert.equal(await res.text(), 'api-only');
}, false));
