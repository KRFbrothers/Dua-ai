// Gemini Files API (resumable upload) for stored files too large to send inline.
// Docs: ai.google.dev/gemini-api/docs/files. Uploaded files are kept by Google for 48 hours.
const UPLOAD = 'https://generativelanguage.googleapis.com/upload/v1beta/files';
const API = 'https://generativelanguage.googleapis.com/v1beta/';
export class ProviderFileError extends Error { constructor(message, status = 502) { super(message); this.status = status; } }

export async function uploadGeminiFile({ bytes, mimeType, name = 'file', key, fetcher = fetch, signal, pollMs = 2000, maxWaitMs = 60000 }) {
  const timed = ms => AbortSignal.any([signal || new AbortController().signal, AbortSignal.timeout(ms)]);
  const start = await fetcher(UPLOAD, {
    method: 'POST', redirect: 'error', signal: timed(20000),
    headers: { 'x-goog-api-key': key, 'X-Goog-Upload-Protocol': 'resumable', 'X-Goog-Upload-Command': 'start',
      'X-Goog-Upload-Header-Content-Length': String(bytes.length), 'X-Goog-Upload-Header-Content-Type': mimeType, 'Content-Type': 'application/json' },
    body: JSON.stringify({ file: { display_name: String(name).slice(0, 120) } })
  });
  const uploadUrl = start.ok ? start.headers.get('x-goog-upload-url') : null;
  let safe = false;
  try { safe = new URL(uploadUrl).protocol === 'https:' && new URL(uploadUrl).hostname.endsWith('.googleapis.com'); } catch {}
  if (!safe) throw new ProviderFileError(`Gemini could not start the file upload${start.ok ? '' : ` (HTTP ${start.status})`}. Retry.`);
  const finish = await fetcher(uploadUrl, {
    method: 'POST', redirect: 'error', signal: timed(120000),
    headers: { 'Content-Length': String(bytes.length), 'X-Goog-Upload-Offset': '0', 'X-Goog-Upload-Command': 'upload, finalize' },
    body: bytes
  });
  let file = (await finish.json().catch(() => null))?.file;
  if (!finish.ok || typeof file?.uri !== 'string' || typeof file?.name !== 'string') throw new ProviderFileError('Gemini file upload failed. Retry.');
  const deadline = Date.now() + maxWaitMs;
  while (file.state === 'PROCESSING') {
    if (Date.now() > deadline) throw new ProviderFileError('Gemini is still processing the file. Retry in a minute.', 504);
    await new Promise(resolve => setTimeout(resolve, pollMs));
    const check = await fetcher(API + file.name.split('/').map(encodeURIComponent).join('/'), { headers: { 'x-goog-api-key': key }, redirect: 'error', signal: timed(15000) });
    file = check.ok ? (await check.json().catch(() => null)) || file : file;
    if (!check.ok) throw new ProviderFileError('Gemini file status check failed. Retry.');
  }
  if (file.state === 'FAILED') throw new ProviderFileError('Gemini could not process this file.', 422);
  return { uri: file.uri, mimeType: file.mimeType || mimeType };
}
