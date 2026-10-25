import { readJsonLimited } from './limited-json.mjs';
import { killSwitches, maintenanceMessage, PAUSED } from './kill-switches.mjs';

const ID = /^[A-Za-z0-9_-]{16,64}$/;
const DEFAULT_ENGINE = 'https://dua-ai-engine-228305912525.us-central1.run.app';

/** Browser-only bridge. The engine remains responsible for owner-bound, single-use execution. */
export async function handleWebAgentAction(request, { env = process.env, fetcher = fetch, sessionHandler } = {}) {
  const cookies = new Headers();
  const json = (data, status = 200) => {
    const headers = new Headers(cookies);
    headers.set('Content-Type', 'application/json');
    headers.set('Cache-Control', 'no-store');
    return new Response(JSON.stringify(data), { status, headers });
  };
  if (request.method !== 'POST') return json({ error: 'Method not allowed.' }, 405);
  const origin = new URL(request.url).origin;
  if (request.headers.get('origin') !== origin || !['', 'same-origin'].includes(request.headers.get('sec-fetch-site') || ''))
    return json({ error: 'Request origin is not allowed.' }, 403);
  const switches = killSwitches(env);
  if (switches.maintenance.enabled) return json({ error: maintenanceMessage(env), maintenance: true }, 503);
  if (switches.agent) return json({ error: PAUSED.agent, paused: 'agent' }, 503);
  const owner = String(env.DUA_ALLOWED_EMAIL || '').trim().toLowerCase();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(owner)) return json({ error: 'Owner account setup is required.' }, 503);
  if (!(request.headers.get('content-type') || '').toLowerCase().startsWith('application/json'))
    return json({ error: 'Use JSON.' }, 415);
  let data;
  try { data = await readJsonLimited(request, 4096); }
  catch (error) { return json({ error: 'Invalid approval request.' }, error.status === 413 ? 413 : 400); }
  if (!data || typeof data !== 'object' || Array.isArray(data) || typeof data.id !== 'string' || !ID.test(data.id) ||
      !['approve', 'reject'].includes(data.decision) || Object.keys(data).some(k => !['id', 'decision'].includes(k)))
    return json({ error: 'Invalid approval request.' }, 400);
  let target;
  try {
    target = new URL(String(env.DUA_ENGINE_URL || DEFAULT_ENGINE));
    if (target.protocol !== 'https:' || target.username || target.password || target.search || target.hash || !['', '/'].includes(target.pathname)) throw Error();
    target = new URL('/api/agent/action', target.origin);
  } catch { return json({ error: 'Agent engine is not configured.' }, 503); }
  // Verify the browser cookie session, allowing the existing Auth handler to renew it.
  let token = Object.fromEntries((request.headers.get('cookie') || '').split(';').map(p => p.trim().split('=')).filter(p => p.length === 2)).dua_access;
  try {
    const authenticate = sessionHandler || (await import('./account.mjs')).handleAccount;
    const sessionHeaders = new Headers(request.headers);
    sessionHeaders.delete('authorization');
    const session = await authenticate(new Request(new URL('/api/account/session', request.url), {
      headers: sessionHeaders, signal: request.signal
    }), { url: env.SUPABASE_URL, key: env.SUPABASE_ANON_KEY, fetcher });
    for (const cookie of session.headers.getSetCookie()) {
      cookies.append('Set-Cookie', cookie);
      const match = /^dua_access=([^;]*)/.exec(cookie);
      if (match) token = match[1];
    }
    if (!session.ok) return json({ error: 'Account verification failed. Sign in and retry.' }, session.status);
    const details = await session.json();
    if (!details.configured) return json({ error: 'Account verification is unavailable.' }, 503);
    if (!details.user) return json({ error: 'Sign in to review this action.' }, 401);
    if (String(details.user.email).toLowerCase() !== owner) return json({ error: 'Only the owner can review actions.' }, 403);
    if (!token || token.length > 10000 || !/^[A-Za-z0-9._-]+$/.test(token)) return json({ error: 'Sign in to review this action.' }, 401);
  } catch { return json({ error: 'Account verification is unavailable.' }, 503); }
  // No retries: the stored action may already have executed if its response is lost.
  const unknown = () => json({ code: 'ACTION_OUTCOME_UNKNOWN', error: 'The action result could not be confirmed. Check the destination before asking the agent again.' }, 502);
  try {
    const headers = { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token };
    if (env.DUA_APP_TOKEN) headers['X-Dua-App-Token'] = env.DUA_APP_TOKEN;
    const response = await fetcher(target, {
      method: 'POST', headers, body: JSON.stringify({ id: data.id, decision: data.decision }), redirect: 'error',
      signal: AbortSignal.any([request.signal, AbortSignal.timeout(120000)])
    });
    if (response.status >= 500) return unknown();
    const result = await readJsonLimited(response, 65536);
    if (!response.ok) return json({ error: typeof result?.error === 'string' ? result.error.slice(0, 600) : 'This approval is unavailable.' }, response.status);
    if (!['done', 'rejected', 'failed'].includes(result?.status) || typeof result.text !== 'string') return unknown();
    return json({ status: result.status, text: result.text.slice(0, 16000) });
  } catch { return unknown(); }
}
