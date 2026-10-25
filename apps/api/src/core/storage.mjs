// Supabase Storage REST calls for the private `dua-files` bucket. Every call uses the owner's own
// access token, so the bucket's row-level security policies (infra/database/migrations/202610080001_dua_files_storage.sql)
// decide access. No service-role key is needed or used here.
import { handleAccount } from './account.mjs';
import { readJsonLimited } from './limited-json.mjs';
import { envInteger } from './adapters.mjs';

export class StorageError extends Error {
  constructor(message, status = 502) { super(message); this.status = status; }
}

/** DUA_FILES=0 turns stored files off. DUA_FILES_BUCKET (default dua-files). DUA_FILES_MAX_MB (default 50 = Supabase Free plan per-file cap). */
export function storageConfig(env = {}) {
  const url = String(env.SUPABASE_URL || '').trim().replace(/\/$/, '');
  const key = String(env.SUPABASE_ANON_KEY || '').trim();
  const bucket = String(env.DUA_FILES_BUCKET || 'dua-files').trim();
  const maxBytes = envInteger(env, 'DUA_FILES_MAX_MB', 50, 1, 5000) * 1024 * 1024;
  let valid = false;
  try { const parsed = new URL(url); valid = parsed.protocol === 'https:' && parsed.pathname === '/' && !parsed.username && !parsed.search; } catch {}
  // The public (anon/publishable) key only: a secret key would bypass the bucket's row-level security.
  const publicKey = Boolean(key) && !key.startsWith('sb_secret_') && !isServiceRoleJwt(key);
  const enabled = valid && publicKey && /^[a-z0-9][a-z0-9_-]{2,62}$/.test(bucket) && String(env.DUA_FILES ?? '').trim() !== '0';
  return { enabled, url, key, bucket, maxBytes };
}
function isServiceRoleJwt(key) {
  try { return JSON.parse(Buffer.from(key.split('.')[1] || '', 'base64url').toString()).role === 'service_role'; } catch { return false; }
}

/** The caller's Supabase access token (native Bearer header, or the HttpOnly cookie). */
export function userToken(request) {
  const bearer = request.headers.get('authorization')?.match(/^Bearer ([A-Za-z0-9._-]{1,10000})$/)?.[1];
  if (bearer) return bearer;
  const cookie = (request.headers.get('cookie') || '').split(';').map(v => v.trim()).find(v => v.startsWith('dua_access='));
  const value = cookie?.slice('dua_access='.length);
  return value && /^[A-Za-z0-9._-]{1,10000}$/.test(value) ? value : null;
}

/**
 * Stored files are an owner-only feature: requires Supabase sign-in and DUA_ALLOWED_EMAIL.
 * Returns {user, token} or throws StorageError with a user-facing message.
 */
export async function resolveOwner(request, { env = {}, fetcher = fetch, feature = 'stored files' } = {}) {
  const owner = String(env.DUA_ALLOWED_EMAIL || '').trim().toLowerCase();
  if (!owner) throw new StorageError(`Set DUA_ALLOWED_EMAIL (your personal account) before enabling ${feature}.`, 503);
  const token = userToken(request);
  if (!token) throw new StorageError(feature === 'stored files' ? 'Sign in from Profile to upload and use stored files.' : `Sign in from Profile to use ${feature}.`, 401);
  const session = await handleAccount(new Request(new URL('/api/account/session', request.url), { headers: { authorization: 'Bearer ' + token, 'x-dua-native': '1' }, signal: request.signal }),
    { url: env.SUPABASE_URL, key: env.SUPABASE_ANON_KEY, fetcher });
  let details;
  try { details = await session.json(); } catch { throw new StorageError('Account verification is unavailable. Retry.', 503); }
  if (!session.ok || !details?.configured) throw new StorageError('Account verification is unavailable. Retry.', 503);
  if (!details.user) throw new StorageError(`Session expired. Sign in again to use ${feature}.`, 401);
  if (String(details.user.email).toLowerCase() !== owner) throw new StorageError(`Only the configured personal account can use ${feature}.`, 403);
  return { user: details.user, token };
}

const encodePath = path => path.split('/').map(encodeURIComponent).join('/');
async function call(config, token, path, { method = 'POST', body, fetcher = fetch, signal, maxBytes = 2 * 1024 * 1024 } = {}) {
  const response = await fetcher(`${config.url}/storage/v1${path}`, {
    method, redirect: 'error', signal: AbortSignal.any([signal || new AbortController().signal, AbortSignal.timeout(20000)]),
    headers: { apikey: config.key, Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
    ...(body === undefined ? {} : { body: JSON.stringify(body) })
  });
  let data = null;
  try { data = await readJsonLimited(response, maxBytes); } catch {}
  if (!response.ok) throw storageFailure(response.status, data);
  return data;
}
/** Maps Storage errors to safe messages (raw upstream text is not returned to the app). */
export function storageFailure(status, data) {
  const text = `${data?.error || ''} ${data?.message || ''}`.toLowerCase();
  if (text.includes('bucket not found')) return new StorageError('File storage is not set up. Apply the dua-files Supabase migration (see SUPABASE.md).', 503);
  if (status === 403 || text.includes('row-level security') || text.includes('policy')) return new StorageError('Storage refused this file for your account. Check the dua-files bucket policies.', 403);
  if (status === 401 || text.includes('jwt')) return new StorageError('Session expired. Sign in again to use stored files.', 401);
  if (status === 413 || text.includes('too large') || text.includes('maximum allowed size')) return new StorageError('File is larger than the storage limit.', 413);
  if (text.includes('mime type') || status === 415) return new StorageError('Storage does not accept this file type.', 415);
  if (text.includes('not found') || status === 404) return new StorageError('Stored file was not found. It may have been deleted.', 404);
  if (status === 429) return new StorageError('Storage rate limit reached. Retry shortly.', 429);
  return new StorageError('File storage is temporarily unavailable. Retry.', 502);
}

/** Signed upload URL (valid 2 hours per Supabase). The phone PUTs bytes straight to Storage, bypassing the API's 4.5 MB body limit. */
export async function signUpload(config, token, path, { fetcher, signal } = {}) {
  const data = await call(config, token, `/object/upload/sign/${encodeURIComponent(config.bucket)}/${encodePath(path)}`, { body: {}, fetcher, signal });
  const relative = typeof data?.url === 'string' ? data.url : '';
  const url = new URL(config.url + '/storage/v1' + (relative.startsWith('/') ? relative : '/' + relative));
  const uploadToken = url.searchParams.get('token');
  if (!uploadToken || url.origin !== new URL(config.url).origin) throw new StorageError('File storage returned an invalid upload URL. Retry.', 502);
  return { uploadUrl: url.href, token: uploadToken };
}

/** Short-lived signed download URL that providers (or the phone, for previews) can fetch without credentials. */
export async function signDownload(config, token, path, expiresIn, { fetcher, signal } = {}) {
  const data = await call(config, token, `/object/sign/${encodeURIComponent(config.bucket)}/${encodePath(path)}`, { body: { expiresIn }, fetcher, signal });
  const relative = typeof data?.signedURL === 'string' ? data.signedURL : typeof data?.signedUrl === 'string' ? data.signedUrl : '';
  const url = new URL(config.url + '/storage/v1' + (relative.startsWith('/') ? relative : '/' + relative));
  if (url.origin !== new URL(config.url).origin || !url.searchParams.get('token')) throw new StorageError('File storage returned an invalid download URL. Retry.', 502);
  return url.href;
}

/** Lists the owner's folder (flat layout), up to 10,000 objects. */
export async function listOwnerFiles(config, token, userId, { fetcher, signal } = {}) {
  const files = [];
  for (let page = 0; page < 10; page++) {
    const data = await call(config, token, `/object/list/${encodeURIComponent(config.bucket)}`, {
      body: { prefix: userId + '/', limit: 1000, offset: page * 1000, sortBy: { column: 'name', order: 'asc' } }, fetcher, signal, maxBytes: 8 * 1024 * 1024
    });
    if (!Array.isArray(data)) throw new StorageError('File storage returned an invalid list. Retry.', 502);
    for (const item of data) {
      if (typeof item?.name !== 'string' || !item.id) continue; // folders have no id
      files.push({ path: `${userId}/${item.name}`, size: Number.isSafeInteger(item.metadata?.size) ? item.metadata.size : 0, createdAt: typeof item.created_at === 'string' ? item.created_at : null });
    }
    if (data.length < 1000) break;
  }
  return files;
}

/** Deletes up to 1000 paths per call. Returns the number Storage reports as removed. */
export async function removeFiles(config, token, paths, { fetcher, signal } = {}) {
  let removed = 0;
  for (let index = 0; index < paths.length; index += 1000) {
    const data = await call(config, token, `/object/${encodeURIComponent(config.bucket)}`, { method: 'DELETE', body: { prefixes: paths.slice(index, index + 1000) }, fetcher, signal, maxBytes: 8 * 1024 * 1024 });
    removed += Array.isArray(data) ? data.length : 0;
  }
  return removed;
}

/** Downloads a signed URL with a byte cap. */
export async function downloadSigned(url, maxBytes, { fetcher = fetch, signal } = {}) {
  const response = await fetcher(url, { redirect: 'error', signal: AbortSignal.any([signal || new AbortController().signal, AbortSignal.timeout(60000)]) });
  if (!response.ok) throw storageFailure(response.status, null);
  const length = response.headers.get('content-length');
  if (length && Number(length) > maxBytes) throw new StorageError('Stored file is too large to read here.', 413);
  const reader = response.body?.getReader();
  if (!reader) throw new StorageError('Stored file could not be read. Retry.', 502);
  const chunks = []; let size = 0;
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > maxBytes) { await reader.cancel().catch(() => {}); throw new StorageError('Stored file is too large to read here.', 413); }
    chunks.push(value);
  }
  return Buffer.concat(chunks, size);
}

/**
 * Server-side upload with the owner's own access token (Imagine results), so the bucket's row-level
 * security still decides access. Objects are immutable (x-upsert: false). With `allowExisting`, an
 * object that already exists at `path` counts as stored (a concurrent status poll saved it first).
 */
export async function uploadObject(config, token, path, bytes, mimeType, { fetcher = fetch, signal, allowExisting = false } = {}) {
  const response = await fetcher(`${config.url}/storage/v1/object/${encodeURIComponent(config.bucket)}/${encodePath(path)}`, {
    method: 'POST', redirect: 'error', signal: AbortSignal.any([signal || new AbortController().signal, AbortSignal.timeout(120000)]),
    headers: { apikey: config.key, Authorization: 'Bearer ' + token, 'Content-Type': mimeType, 'x-upsert': 'false', 'Cache-Control': 'max-age=3600' },
    body: bytes
  });
  let data = null;
  try { data = await readJsonLimited(response, 65536); } catch {}
  if (response.ok) return { path, size: bytes.byteLength, existed: false };
  const text = `${data?.statusCode || ''} ${data?.error || ''} ${data?.message || ''}`.toLowerCase();
  if (allowExisting && (response.status === 409 || text.includes('409') || text.includes('duplicate') || text.includes('already exists'))) return { path, size: bytes.byteLength, existed: true };
  throw storageFailure(response.status, data);
}
