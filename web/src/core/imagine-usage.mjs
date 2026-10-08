// Durable per-feature daily caps for Imagine (Supabase RPCs from supabase/migrations/202610090001_dua_imagine.sql).
// The RPCs run as the signed-in owner (auth.uid()), so no service-role key is needed. A caller can only
// add to its own usage: amounts are positive and the limit only decides whether the request is allowed.
import { readJsonLimited } from './limited-json.mjs';
import { ImagineError } from './imagine-providers.mjs';

const SETUP = 'Imagine usage caps need the Phase 3 Supabase migration (supabase/migrations/202610090001_dua_imagine.sql). Run it in the Supabase SQL editor.';

async function rpc(config, token, name, body, { fetcher = fetch, signal } = {}) {
  let response;
  try {
    response = await fetcher(`${config.url}/rest/v1/rpc/${name}`, {
      method: 'POST', redirect: 'error', signal: AbortSignal.any([signal || new AbortController().signal, AbortSignal.timeout(15000)]),
      headers: { apikey: config.key, Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' }, body: JSON.stringify(body)
    });
  } catch (error) {
    if (signal?.aborted) throw error;
    throw new ImagineError('Imagine usage storage is unavailable. Retry.', 503);
  }
  let data = null;
  try { data = await readJsonLimited(response, 65536); } catch {}
  if (response.status === 404 || data?.code === 'PGRST202' || data?.code === '42883') throw new ImagineError(SETUP, 503, { code: 'setup' });
  if (response.status === 401) throw new ImagineError('Session expired. Sign in again to use Imagine.', 401);
  if (!response.ok) throw new ImagineError('Imagine usage storage is unavailable. Retry after checking setup.', 503);
  return data;
}

/**
 * Atomically adds `amount` (images or video seconds) to today's UTC usage when it fits `limit`.
 * Returns {used, limit, resetAt}; throws 429 ImagineError when the cap would be exceeded.
 */
export async function consumeImagine(config, token, feature, amount, limit, options = {}) {
  const data = await rpc(config, token, 'dua_consume_imagine', { feature, amount, daily_limit: limit }, options);
  if (typeof data?.allowed !== 'boolean' || !Number.isSafeInteger(data.used) || data.used < 0 || !Number.isSafeInteger(data.resetAt)) throw new ImagineError('Imagine usage storage returned an invalid response. Retry.', 503);
  const usage = { used: data.used, limit, resetAt: data.resetAt };
  if (!data.allowed) {
    const unit = feature === 'image' ? 'images' : 'video seconds';
    const left = Math.max(0, limit - data.used);
    throw Object.assign(new ImagineError(`Daily Imagine limit reached: ${data.used} of ${limit} ${unit} used${left ? ` (${left} left, this request needs ${amount})` : ''}. It resets at midnight UTC.`, 429, { code: 'cap' }),
      { usage, retryAfterSeconds: Math.max(1, data.resetAt - Math.floor(Date.now() / 1000)) });
  }
  return usage;
}

/** Today's usage: {images, videoSeconds, resetAt}. */
export async function readImagineUsage(config, token, options = {}) {
  const data = await rpc(config, token, 'dua_read_imagine_usage', {}, options);
  const count = value => Number.isSafeInteger(value) && value >= 0 ? value : 0;
  return { images: count(data?.images), videoSeconds: count(data?.videoSeconds), resetAt: Number.isSafeInteger(data?.resetAt) ? data.resetAt : null };
}
