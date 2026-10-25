#!/usr/bin/env node
import { pathToFileURL } from 'node:url';

export const DEFAULT_TABLES = Object.freeze(['chats', 'messages', 'projects', 'memories']);
const fail = reason => ({ ok: false, reason });
function configuration(env) {
  const url = env.SUPABASE_URL?.trim();
  const key = (env.SUPABASE_ANON_KEY || env.SUPABASE_PUBLISHABLE_KEY)?.trim();
  if (!url || !key) return null;
  let parsed;
  try { parsed = new URL(url); } catch { throw Error('SUPABASE_URL must be the project root URL.'); }
  const local = ['localhost', '127.0.0.1', '[::1]'].includes(parsed.hostname);
  if (!(parsed.protocol === 'https:' || parsed.protocol === 'http:' && local) || parsed.username || parsed.password || parsed.search || parsed.hash || parsed.pathname !== '/')
    throw Error('Use the Supabase project root URL, without /rest/v1, credentials or query parameters.');
  let anonymous = /^sb_publishable_[A-Za-z0-9_-]+$/.test(key);
  if (/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(key)) {
    try { anonymous = JSON.parse(Buffer.from(key.split('.')[1], 'base64url').toString()).role === 'anon'; } catch { anonymous = false; }
  }
  if (!anonymous || key.length > 10000) throw Error('Use only a publishable key or legacy anon key, never a user token, secret or service-role key.');
  return { url: parsed.origin, key };
}

/** Conservative anonymous-access smoke check, not a policy audit. Never submits a row. */
export async function verifyRls({ env = process.env, fetcher = fetch, tables = DEFAULT_TABLES } = {}) {
  let config;
  try { config = configuration(env); } catch (error) { return { ok: false, skipped: false, reason: error.message, checks: [] }; }
  if (!config) return { ok: true, skipped: true, reason: 'SKIP: set SUPABASE_URL and SUPABASE_ANON_KEY (or SUPABASE_PUBLISHABLE_KEY) to verify anonymous access.', checks: [] };
  if (!Array.isArray(tables) || !tables.length || tables.length > 32 || new Set(tables).size !== tables.length || tables.some(t => !/^[a-z_][a-z0-9_]{0,62}$/.test(t)))
    return { ok: false, skipped: false, reason: 'Choose valid, unique public table names with --tables=name,name.', checks: [] };
  const request = (path, init = {}) => fetcher(config.url + path, {
    ...init, headers: { apikey: config.key, ...init.headers }, redirect: 'error', signal: AbortSignal.timeout(10000)
  });
  // An invalid key's 401 must not make every table falsely appear protected.
  try {
    const health = await request('/auth/v1/settings');
    if (!health.ok) { await health.body?.cancel(); return { ok: false, skipped: false, reason: 'Could not validate the public API key using /auth/v1/settings. No table was verified.', checks: [] }; }
    const settings = await health.json();
    if (!settings || typeof settings !== 'object' || Array.isArray(settings)) throw Error();
  } catch { return { ok: false, skipped: false, reason: 'Public API key validation failed (network, timeout or unexpected response). No table was verified.', checks: [] }; }
  const checks = await Promise.all(tables.map(async table => {
    let read, insert;
    try {
      const response = await request(`/rest/v1/${table}?select=*&limit=1`);
      const status = response.status;
      if ([401, 403].includes(status)) { await response.body?.cancel(); read = { ok: true, status, reason: 'Anonymous reads denied.' }; }
      else if (status === 200) {
        const rows = await response.json();
        read = Array.isArray(rows) && rows.length === 0 ? { ok: true, status, reason: 'No rows visible to anon.' } : { ...fail('Anonymous read returned data or an unexpected body.'), status };
      } else { await response.body?.cancel(); read = { ...fail(status === 404 ? 'Table missing/not exposed; protection is unverified.' : 'Unexpected read response; protection is unverified.'), status }; }
    } catch { read = fail('Read failed (network, timeout or invalid JSON).'); }
    try {
      // Empty batch is deliberately non-mutating even if RLS is broken or tx=rollback is ignored.
      // It proves denial of INSERT admission, not evaluation of a WITH CHECK expression on a row.
      const response = await request(`/rest/v1/${table}`, { method: 'POST', headers: { 'content-type': 'application/json', Prefer: 'return=minimal,tx=rollback' }, body: '[]' });
      const status = response.status;
      await response.body?.cancel();
      insert = [401, 403].includes(status) ? { ok: true, status, reason: 'Anonymous INSERT admission denied.' }
        : { ...fail(response.ok ? 'Empty INSERT batch accepted; row-level INSERT protection is inconclusive. Inspect INSERT policies/grants.' : 'INSERT denial was not 401/403; missing schema/constraint errors do not prove protection.'), status };
    } catch { insert = fail('INSERT probe failed (network or timeout).'); }
    return { table, read, insert, ok: read.ok && insert.ok };
  }));
  return { ok: checks.every(c => c.ok), skipped: false, checks };
}

export async function main(args = process.argv.slice(2), options = {}) {
  const log = options.log || console.log;
  if (args.length > 1 || args[0] && !args[0].startsWith('--tables=')) { log('Usage: npm run verify:rls -- [--tables=chats,messages,projects,memories]'); return 1; }
  const result = await verifyRls({ ...options, ...(args[0] ? { tables: args[0].slice(9).split(',') } : {}) });
  if (result.reason) log(result.reason);
  for (const check of result.checks) log(`${check.ok ? 'PASS' : 'UNVERIFIED'} ${check.table}: read ${check.read.status ?? 'error'} — ${check.read.reason} insert ${check.insert.status ?? 'error'} — ${check.insert.reason}`);
  if (!result.skipped && result.ok) log('Anonymous smoke checks passed. Empty reads do not prove RLS on an empty table; inspect policies separately. No rows were submitted.');
  return result.ok ? 0 : 1;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) process.exitCode = await main();
