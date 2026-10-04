import { handleAccount } from './account.mjs';

const json = (body, status, headers = new Headers()) => {
  headers.set('Cache-Control', 'no-store');
  headers.set('Content-Type', 'application/json');
  return new Response(JSON.stringify(body), { status, headers });
};
function adminKey(value) {
  if (typeof value !== 'string' || value.length > 10000) return null;
  if (/^sb_secret_[A-Za-z0-9_-]+$/.test(value)) return value;
  try { if (/^[A-Za-z0-9._-]+$/.test(value) && JSON.parse(Buffer.from(value.split('.')[1] || '', 'base64url').toString()).role === 'service_role') return value; } catch {}
  return null;
}

/** Optional owner policy and durable request count; this is not a token or billing cap. */
export async function guardChatAccess(request, { env = {}, fetcher = env.fetcher || fetch } = {}) {
  const owner = String(env.allowedEmail || env.DUA_ALLOWED_EMAIL || '').trim().toLowerCase();
  const loginValue = String(env.requireLogin ?? env.DUA_REQUIRE_LOGIN ?? '').trim().toLowerCase();
  const requireLogin = loginValue === 'true' || loginValue === '1';
  const rawLimit = env.dailyRequests ?? env.DUA_DAILY_REQUEST_LIMIT;
  const hasLimit = rawLimit !== undefined && rawLimit !== null && rawLimit !== '';
  const limit = Number(rawLimit);
  const headers = new Headers();
  const denied = (message, status, extra = {}) => ({ allowed: false, headers, response: json({ error: message, ...extra }, status, headers) });
  if (!['', 'false', '0', 'true', '1'].includes(loginValue)) return denied('Required login setup is invalid.', 503);
  if (hasLimit && (typeof rawLimit !== 'number' && (typeof rawLimit !== 'string' || !/^[1-9][0-9]*$/.test(rawLimit.trim())) || !Number.isInteger(limit) || limit < 1 || limit > 100000)) return denied('Daily request limit setup is invalid.', 503);
  if (owner && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(owner)) return denied('Allowed account email setup is invalid.', 503);
  if (!requireLogin && !owner && !hasLimit) return { allowed: true, user: null, headers };

  const settings = { ...env, url: env.url || env.SUPABASE_URL, key: env.key || env.SUPABASE_ANON_KEY, fetcher };
  const session = await handleAccount(new Request(new URL('/api/account/session', request.url), { headers: request.headers, signal: request.signal }), settings);
  for (const cookie of session.headers.getSetCookie?.() || []) headers.append('Set-Cookie', cookie);
  let details;
  try { details = await session.json(); } catch { return denied('Account verification is unavailable. Retry.', 503); }
  if (!session.ok) return denied('Account verification failed. Sign in and retry.', session.status);
  if (!details.configured) return denied('Supabase login setup is required for this access policy.', 503);
  if (!details.user) return denied('Sign in from Profile before using online AI.', 401);
  if (owner && details.user.email.toLowerCase() !== owner) return denied('Only the configured personal account can use online AI.', 403);
  if (!hasLimit) return { allowed: true, user: details.user, headers };

  const serviceKey = adminKey(env.serviceRoleKey || env.SUPABASE_SERVICE_ROLE_KEY);
  if (!serviceKey) return denied('Daily request usage setup requires its server admin key and migration.', 503);
  try {
    const response = await fetcher(String(settings.url).replace(/\/$/, '') + '/rest/v1/rpc/dua_consume_daily_request', {
      method: 'POST',
      headers: { apikey: serviceKey, Authorization: 'Bearer ' + serviceKey, 'Content-Type': 'application/json' },
      body: JSON.stringify({ account_id: details.user.id, daily_limit: limit }),
      signal: AbortSignal.any([request.signal, AbortSignal.timeout(15000)]), redirect: 'error'
    });
    if (!response.ok) return denied('Daily request usage storage is unavailable. Retry after checking setup.', 503);
    const usage = await response.json();
    if (typeof usage?.allowed !== 'boolean' || !Number.isSafeInteger(usage.used) || usage.used < 0 || !Number.isSafeInteger(usage.resetAt) || usage.resetAt <= 0) throw Error('Invalid usage response');
    headers.set('X-Dua-Requests-Remaining', String(Math.max(0, limit - usage.used)));
    if (!usage.allowed) {
      const retryAfterSeconds = Math.max(1, usage.resetAt - Math.floor(Date.now() / 1000));
      headers.set('Retry-After', String(retryAfterSeconds));
      return denied('Daily request limit reached. It resets at midnight UTC.', 429, { retryAfterSeconds });
    }
    return { allowed: true, user: details.user, headers, usage: { used: usage.used, limit, resetAt: usage.resetAt } };
  } catch {
    return denied('Daily request usage storage is unavailable. Retry after checking setup.', 503);
  }
}
