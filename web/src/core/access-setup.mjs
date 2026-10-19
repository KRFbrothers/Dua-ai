// Fail-closed owner setup check for dua-api.
//
// Protected routes (AI chat/search/connectors, account sign-in and cloud data, Build PR) need the
// owner lock: DUA_ALLOWED_EMAIL plus Supabase login (SUPABASE_URL + SUPABASE_ANON_KEY). When any of
// these is missing the server refuses those requests with 503 instead of serving everyone.
// Local development without login is an explicit opt-in (DUA_ALLOW_OPEN_ACCESS=1) and is ignored on
// Cloud Run (K_SERVICE is set there), so a missing hosting secret can never open the service.

const truthy = value => ['1', 'true'].includes(String(value ?? '').trim().toLowerCase());
const has = value => Boolean(String(value ?? '').trim());

/** True on Cloud Run (K_SERVICE is set by the platform) or with NODE_ENV=production. */
export const hosted = env => has(env.K_SERVICE) || String(env.NODE_ENV || '').trim() === 'production';

/** Local open mode: only with DUA_ALLOW_OPEN_ACCESS=1 and never on hosted runs. */
export const openAccessAllowed = env => truthy(env.DUA_ALLOW_OPEN_ACCESS) && !hosted(env);

/** Names of the owner-lock settings that are missing (empty list = complete). */
export function missingOwnerSettings(env = {}) {
  return ['DUA_ALLOWED_EMAIL', 'SUPABASE_URL', 'SUPABASE_ANON_KEY'].filter(name => !has(env[name]));
}

/** Null when protected routes may run, otherwise the user-facing reason (setting names only, never values). */
export function ownerSetupProblem(env = {}) {
  if (openAccessAllowed(env)) return null;
  const missing = missingOwnerSettings(env);
  if (!missing.length) return null;
  return `This Dua server is locked because owner login setup is incomplete (missing: ${missing.join(', ')}). Add the missing setting(s) in hosting settings, then retry.`;
}

/** Account routes that stay open so the app can show sign-in state and log out. */
const OPEN_ACCOUNT = new Set(['/api/account/session', '/api/account/features', '/api/account/logout']);

/** Whether a path is protected by the owner lock. /api/setup, /api/config and the website stay open. */
export function protectedPath(path) {
  if (OPEN_ACCOUNT.has(path)) return false;
  return path === '/api/chat' || path === '/api/search' || path === '/api/connectors/github' || path === '/api/build/pr'
    || path === '/api/cloud' || path === '/api/cloud/chat' || path.startsWith('/api/account/')
    || path.startsWith('/api/files/') || path.startsWith('/api/imagine/') || path.startsWith('/api/voice/');
}

/** One startup line: a loud warning when locked or in local open mode, never any value. */
export function startupAccessWarning(env = {}) {
  if (truthy(env.DUA_ALLOW_OPEN_ACCESS) && hosted(env)) {
    const problem = ownerSetupProblem(env);
    return '[dua-access] WARNING: DUA_ALLOW_OPEN_ACCESS is ignored on hosted runs.' + (problem ? ' ' + problem : '');
  }
  if (openAccessAllowed(env) && missingOwnerSettings(env).length) return '[dua-access] WARNING: DUA_ALLOW_OPEN_ACCESS=1: local open mode, AI routes accept requests without the owner login.';
  const problem = ownerSetupProblem(env);
  return problem ? '[dua-access] WARNING: protected routes will answer 503. ' + problem : null;
}
