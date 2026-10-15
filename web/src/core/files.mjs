// /api/files/* — owner-only stored files (Supabase Storage, private bucket).
//   POST /api/files/sign        {name, mimeType, size}  -> {path, uploadUrl, headers, expiresIn, maxBytes, kind, mimeType}
//   POST /api/files/url         {path}                  -> {url, expiresIn}           (preview / open)
//   POST /api/files/delete      {paths: [...]}          -> {deleted}
//   POST /api/files/delete-all  {}                      -> {deleted}
//   GET  /api/files/usage                               -> {files, bytes, maxBytes, bucket}
//   GET  /api/files/list                                -> {files: [{path,name,mimeType,size}], truncated}
import { randomUUID } from 'node:crypto';
import { handleCloudRead } from './account.mjs';
import { readJsonLimited } from './limited-json.mjs';
import { storedType, fileKind, ownsPath, STORED_TYPES, STORED_MIME_TYPES } from './file-types.mjs';
import { storageConfig, resolveOwner, signUpload, signDownload, listOwnerFiles, removeFiles, StorageError } from './storage.mjs';

const json = (data, status = 200, headers = {}) => Response.json(data, { status, headers: { 'Cache-Control': 'no-store', ...headers } });
export const PREVIEW_URL_SECONDS = 600;

/** Public capability summary for /api/config (no secrets). */
export function filesConfiguration(env = {}) {
  const config = storageConfig(env);
  return { enabled: config.enabled && Boolean(String(env.DUA_ALLOWED_EMAIL || '').trim()), maxBytes: config.maxBytes, types: STORED_MIME_TYPES, requiresSignIn: true };
}

/** Validates a sign request; returns {mimeType, kind, ext} or throws StorageError. Pure (tested). */
export function validateUploadRequest(body, maxBytes) {
  if (!body || typeof body !== 'object' || Array.isArray(body) || Object.keys(body).some(key => !['name', 'mimeType', 'size'].includes(key))) throw new StorageError('Invalid upload request.', 400);
  if (typeof body.name !== 'string' || !body.name.trim() || body.name.length > 200 || /[\u0000-\u001f\u007f]/.test(body.name)) throw new StorageError('Invalid file name.', 400);
  if (!Number.isSafeInteger(body.size) || body.size <= 0) throw new StorageError('Invalid file size.', 400);
  if (body.size > maxBytes) throw new StorageError(`File is larger than ${Math.floor(maxBytes / 1048576)} MB. Choose a smaller file.`, 413);
  const mimeType = storedType(body.mimeType, body.name);
  if (!mimeType) throw new StorageError('Choose an image (JPEG, PNG, WebP), PDF, spreadsheet (XLSX, XLS, ODS, CSV), text (TXT, MD, JSON) or audio (M4A, AAC, MP3, WAV, OGG, OPUS, WEBM, FLAC) file.', 415);
  return { mimeType, kind: fileKind(mimeType), ext: STORED_TYPES[mimeType].ext };
}

export async function handleFiles(request, { env = process.env, fetcher = fetch } = {}) {
  const url = new URL(request.url);
  const action = url.pathname.replace(/^\/api\/files\//, '');
  if (!['sign', 'url', 'delete', 'delete-all', 'usage', 'list'].includes(action)) return json({ error: 'Not found.' }, 404);
  if (['usage', 'list'].includes(action) ? request.method !== 'GET' : request.method !== 'POST') return json({ error: 'Method not allowed.' }, 405);
  const config = storageConfig(env);
  if (!config.enabled) return json({ error: 'Stored files are not configured. Set Supabase settings and apply the dua-files migration.' }, 503);
  try {
    const { user, token } = await resolveOwner(request, { env, fetcher });
    const signal = request.signal;
    let body = {};
    if (request.method === 'POST') {
      try { body = await readJsonLimited(request, 65536); } catch (error) { return json({ error: error.status === 413 ? 'Request too large.' : 'Invalid request.' }, error.status === 413 ? 413 : 400); }
      if (!body || typeof body !== 'object' || Array.isArray(body)) return json({ error: 'Invalid request.' }, 400);
    }
    if (action === 'sign') {
      const { mimeType, kind, ext } = validateUploadRequest(body, config.maxBytes);
      const path = `${user.id.toLowerCase()}/${randomUUID()}.${ext}`;
      const signed = await signUpload(config, token, path, { fetcher, signal });
      // The phone uploads with: PUT uploadUrl, body = raw bytes, these headers. The anon key is public by design.
      return json({ path, uploadUrl: signed.uploadUrl, headers: { 'Content-Type': mimeType, 'x-upsert': 'false', apikey: config.key }, expiresIn: 7200, maxBytes: config.maxBytes, kind, mimeType });
    }
    if (action === 'url') {
      if (!ownsPath(body.path, user.id)) return json({ error: 'File not found.' }, 404);
      return json({ url: await signDownload(config, token, body.path, PREVIEW_URL_SECONDS, { fetcher, signal }), expiresIn: PREVIEW_URL_SECONDS });
    }
    if (action === 'delete') {
      if (!Array.isArray(body.paths) || !body.paths.length || body.paths.length > 500 || body.paths.some(path => !ownsPath(path, user.id))) return json({ error: 'Invalid file list.' }, 400);
      // Old APKs also queue per-file deletes when clearing cached chats. Keep server references alive.
      const cloud = await handleCloudRead(new Request(new URL('/api/cloud', request.url), {
        headers: { authorization: 'Bearer ' + token, 'x-dua-native': '1' }, signal
      }), { url: config.url, key: config.key, allowedEmail: env.DUA_ALLOWED_EMAIL, fetcher });
      if (!cloud.ok) throw new StorageError('Server file references could not be checked. No files were deleted. Retry.', cloud.status);
      const review = await cloud.json();
      if (review.ownerId !== user.id) throw new StorageError('Cloud account changed. No files were deleted. Retry.', 409);
      const referenced = new Set(review.snapshot.chats.flatMap(chat => chat.messages.flatMap(message =>
        [...(message.files || []), ...(message.media || [])].map(file => file.path))));
      const requested = [...new Set(body.paths)];
      const removable = requested.filter(path => !referenced.has(path));
      const retained = requested.length - removable.length;
      const deleted = removable.length ? await removeFiles(config, token, removable, { fetcher, signal }) : 0;
      return json({ deleted, ...(retained ? { retained } : {}) });
    }
    const files = await listOwnerFiles(config, token, user.id.toLowerCase(), { fetcher, signal });
    if (action === 'list') return json({ files: files.filter(file => ownsPath(file.path, user.id)).map(file => {
      const name = file.path.slice(file.path.lastIndexOf('/') + 1);
      return { path: file.path, name, mimeType: storedType('application/octet-stream', name) || 'application/octet-stream', size: Math.max(0, file.size) };
    }), truncated: files.length >= 10000 });
    if (action === 'usage') return json({ files: files.length, bytes: files.reduce((sum, file) => sum + file.size, 0), maxBytes: config.maxBytes, bucket: config.bucket });
    return json({ deleted: files.length ? await removeFiles(config, token, files.map(file => file.path), { fetcher, signal }) : 0 });
  } catch (error) {
    if (error instanceof StorageError) return json({ error: error.message }, error.status);
    if (error?.name === 'TimeoutError' || error?.name === 'AbortError' || request.signal.aborted) return json({ error: 'File storage request timed out. Retry.' }, 504);
    return json({ error: 'File storage request failed. Retry.' }, 502);
  }
}
