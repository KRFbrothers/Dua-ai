// Video jobs are stateless on the server: the app keeps an opaque, HMAC-signed job token
// ({provider request id, target storage path, seconds, owner, created}) and polls with it.
// The signing key is derived from the xAI key, so tokens cannot be forged or pointed at another path.
import { createHmac, createHash, timingSafeEqual } from 'node:crypto';

export const JOB_MAX_AGE_SECONDS = 48 * 3600;
const keyFor = secret => createHash('sha256').update('dua-imagine-job-v1\0' + String(secret)).digest();

export function signJob(job, secret) {
  const payload = Buffer.from(JSON.stringify({ v: 1, ...job })).toString('base64url');
  const mac = createHmac('sha256', keyFor(secret)).update(payload).digest('base64url');
  return payload + '.' + mac;
}

/** Returns the job or null when the token is malformed, forged, for another account or too old. */
export function verifyJob(token, secret, { userId, now = Date.now() } = {}) {
  if (typeof token !== 'string' || token.length > 2000 || !/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]{43}$/.test(token)) return null;
  const [payload, mac] = token.split('.');
  // Compare the canonical base64url text, not decoded bytes: the last character's low bits are ignored
  // when decoding, so byte comparison would accept up to four spellings of the same signature.
  const expected = Buffer.from(createHmac('sha256', keyFor(secret)).update(payload).digest('base64url'));
  const given = Buffer.from(mac);
  if (given.length !== expected.length || !timingSafeEqual(given, expected)) return null;
  let job;
  try { job = JSON.parse(Buffer.from(payload, 'base64url').toString('utf8')); } catch { return null; }
  if (job?.v !== 1 || typeof job.r !== 'string' || typeof job.p !== 'string' || !Number.isSafeInteger(job.t) || !Number.isSafeInteger(job.s)) return null;
  if (userId && job.u !== String(userId).toLowerCase()) return null;
  if (Math.floor(now / 1000) - job.t > JOB_MAX_AGE_SECONDS) return { ...job, expired: true };
  return job;
}
