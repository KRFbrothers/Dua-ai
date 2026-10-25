// Imagine provider adapters: request building and response parsing only (no storage, auth or caps).
// Checked against the official docs in October 2026:
//  xAI (docs.x.ai › Inference API › Images / Videos)
//   POST /v1/images/generations {model, prompt, n (1–10), aspect_ratio, resolution "1k"|"2k", response_format "url"|"b64_json"}
//   POST /v1/images/edits       same, plus image:{url} or images:[{url}, …] (≤ 5; JSON only; url may be a base64 data URI)
//     → {data:[{b64_json | url, mime_type}], usage:{cost_in_usd_ticks}}. Provider URLs are temporary, so Dua asks for b64_json.
//   POST /v1/videos/generations {model, prompt, duration 1–15 (default 8), aspect_ratio, resolution "480p"|"720p"|"1080p", image:{url}}
//     → {request_id}. 1080p only on grok-imagine-video-1.5. With an image and no aspect_ratio the clip keeps the image's ratio.
//   GET  /v1/videos/{request_id} → {status: pending|done|failed|expired, progress 0–100, video:{url, duration, respect_moderation}, error:{code, message}}
//  Gemini (ai.google.dev › Nano Banana image generation, Interactions API)
//   POST /v1beta/interactions {model, input:[{type:"text"}, {type:"image", data, mime_type}], response_format:{type:"image", aspect_ratio, image_size}, store}
//     → {status, steps:[{type:"model_output", content:[{type:"image", data, mime_type} | {type:"text", text}]}]}. One image per call.
import { readJsonLimited } from './limited-json.mjs';

export const XAI_BASE = 'https://api.x.ai';
export const GEMINI_INTERACTIONS = 'https://generativelanguage.googleapis.com/v1beta/interactions';
export const DEFAULT_MODELS = Object.freeze({ xaiImage: 'grok-imagine-image-2.0', geminiImage: 'gemini-nano-banana-2.1', xaiVideo: 'grok-imagine-video-1.5' });
export const PROVIDER_NAMES = Object.freeze({ xai: 'Grok Imagine', gemini: 'Gemini (Nano Banana)' });
const KEYS = { xai: 'GROK_API_KEY', gemini: 'GEMINI_API_KEY' };
const MODEL_ENV = { xai: 'DUA_IMAGINE_XAI_IMAGE_MODEL', gemini: 'DUA_IMAGINE_GEMINI_IMAGE_MODEL', video: 'DUA_IMAGINE_XAI_VIDEO_MODEL' };

export class ImagineError extends Error {
  constructor(message, status = 502, { retryable = false, code = null } = {}) { super(message); this.status = status; this.retryable = retryable; this.code = code; }
}
export const POLICY_MESSAGE = 'The provider refused this request under its content policy. Rephrase the prompt or use a different image.';
const POLICY = /moderat|content[ _-]?polic|safety|unsafe|inappropriate|violat|prohibited|filtered|refus|responsible ai|usage polic|nsfw|harmful/i;

/** Maps a provider HTTP error to a safe, user-facing ImagineError (raw upstream text is never returned). */
export function providerFailure(provider, status, data) {
  const name = provider === 'video' ? 'Grok Imagine video' : PROVIDER_NAMES[provider] || 'The image provider';
  const key = provider === 'gemini' ? KEYS.gemini : KEYS.xai;
  const parts = [data?.error?.message, data?.error?.status, data?.error?.code, data?.error, data?.message, data?.code, data?.detail];
  const text = parts.filter(value => typeof value === 'string').join(' ').slice(0, 4000);
  if (status >= 400 && status < 500 && POLICY.test(text)) return new ImagineError(POLICY_MESSAGE, 422, { code: 'content_policy' });
  if (status === 401 || status === 403) return new ImagineError(`${name} rejected the server API key. Check ${key} in hosting settings.`, 503, { retryable: true });
  if (status === 404) return new ImagineError(`${name} model is unavailable. Check ${MODEL_ENV[provider] || 'the model setting'} in hosting settings.`, 503, { retryable: true });
  if (status === 429) return new ImagineError(`${name} rate limit or credit limit reached. Retry shortly.`, 429, { retryable: true });
  if (status >= 400 && status < 500) return new ImagineError(`${name} rejected the request. Check the prompt, options or image and try again.`, 400);
  return new ImagineError(`${name} is temporarily unavailable. Retry shortly.`, 502, { retryable: true });
}

const dataUri = source => `data:${source.mimeType};base64,${source.bytes.toString('base64')}`;
const jsonInit = (headers, body) => ({ method: 'POST', redirect: 'error', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body) });

/** xAI image generation (no sources) or edit (1–5 sources). Asks for b64_json so nothing depends on temporary provider URLs. */
export function xaiImageRequest({ key, model, prompt, n, aspectRatio, sources = [] }) {
  const body = { model, prompt, n, response_format: 'b64_json' };
  if (aspectRatio && aspectRatio !== 'auto') body.aspect_ratio = aspectRatio;
  if (sources.length === 1) body.image = { url: dataUri(sources[0]) };
  else if (sources.length > 1) body.images = sources.map(source => ({ url: dataUri(source) }));
  return { url: XAI_BASE + (sources.length ? '/v1/images/edits' : '/v1/images/generations'), init: jsonInit({ Authorization: 'Bearer ' + key }, body) };
}

/** Gemini Interactions image request (one image per call). store:false keeps the interaction off Google's servers. */
export function geminiImageRequest({ key, model, prompt, aspectRatio, sources = [] }) {
  const format = { type: 'image', mime_type: 'image/jpeg', image_size: '1K' };
  if (aspectRatio && aspectRatio !== 'auto') format.aspect_ratio = aspectRatio;
  const input = [{ type: 'text', text: prompt }, ...sources.map(source => ({ type: 'image', data: source.bytes.toString('base64'), mime_type: source.mimeType }))];
  return { url: GEMINI_INTERACTIONS, init: jsonInit({ 'x-goog-api-key': key }, { model, input, response_format: format, store: false }) };
}

/** xAI video job. aspect "auto" is omitted so image-to-video keeps the source image's ratio (setting one stretches it). */
export function xaiVideoRequest({ key, model, prompt, duration, aspectRatio, resolution, source }) {
  const body = { model, prompt, duration };
  if (aspectRatio && aspectRatio !== 'auto') body.aspect_ratio = aspectRatio;
  if (resolution) body.resolution = resolution;
  if (source) body.image = { url: dataUri(source) };
  return { url: XAI_BASE + '/v1/videos/generations', init: jsonInit({ Authorization: 'Bearer ' + key }, body) };
}
export function xaiVideoStatusRequest({ key, requestId }) {
  return { url: `${XAI_BASE}/v1/videos/${encodeURIComponent(requestId)}`, init: { method: 'GET', redirect: 'error', headers: { Authorization: 'Bearer ' + key } } };
}

const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
/** Detects JPEG/PNG/WebP from magic bytes; the reported type is only trusted when it matches. */
export function sniffImageType(bytes) {
  if (bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) return 'image/jpeg';
  if (bytes.length >= 8 && bytes.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) return 'image/png';
  if (bytes.length >= 12 && bytes.subarray(0, 4).toString('latin1') === 'RIFF' && bytes.subarray(8, 12).toString('latin1') === 'WEBP') return 'image/webp';
  return null;
}
function decodeImage(base64, maxBytes) {
  if (typeof base64 !== 'string' || !base64 || base64.length > Math.ceil(maxBytes / 3) * 4 + 8) return null;
  const bytes = Buffer.from(base64.replace(/^data:[^,]*,/, ''), 'base64');
  const mimeType = sniffImageType(bytes);
  return mimeType && IMAGE_TYPES.includes(mimeType) && bytes.length <= maxBytes ? { bytes, mimeType } : null;
}

/** Parses an xAI images response into [{bytes, mimeType}]. Missing image data is treated as a moderation filter. */
export function parseXaiImages(data, maxBytes) {
  const items = Array.isArray(data?.data) ? data.data : null;
  if (!items) throw new ImagineError('Grok Imagine returned an unexpected response. Retry.', 502, { retryable: true });
  const images = items.map(item => decodeImage(item?.b64_json, maxBytes)).filter(Boolean);
  if (!images.length) throw new ImagineError(POLICY_MESSAGE, 422, { code: 'content_policy' });
  const ticks = data?.usage?.cost_in_usd_ticks;
  return { images, costUsd: Number.isSafeInteger(ticks) && ticks >= 0 ? ticks / 1e10 : null };
}

/** Parses a Gemini interaction into [{bytes, mimeType}] (normally one). A text-only answer is a refusal. */
export function parseGeminiImages(data, maxBytes) {
  if (!data || !Array.isArray(data.steps)) throw new ImagineError('Gemini returned an unexpected response. Retry.', 502, { retryable: true });
  if (data.status === 'failed') throw new ImagineError('Gemini could not create this image. Retry or rephrase the prompt.', 502, { retryable: true });
  const content = data.steps.filter(step => step?.type === 'model_output').flatMap(step => Array.isArray(step.content) ? step.content : []);
  const images = content.filter(part => part?.type === 'image').map(part => decodeImage(part.data, maxBytes)).filter(Boolean);
  if (!images.length) throw new ImagineError(POLICY_MESSAGE, 422, { code: 'content_policy' });
  return { images: images.slice(-1), costUsd: null };
}

/** Maps an xAI video status payload to {status, progress?, url?, duration?, error?}. */
export function parseXaiVideoStatus(data) {
  const status = typeof data?.status === 'string' ? data.status.toLowerCase() : '';
  if (status === 'pending' || status === 'queued' || status === 'in_progress') return { status: 'pending', progress: Number.isInteger(data.progress) ? Math.max(0, Math.min(99, data.progress)) : null };
  if (status === 'done') {
    const video = data.video || {};
    if (video.respect_moderation === false || typeof video.url !== 'string' || !video.url) return { status: 'failed', error: POLICY_MESSAGE, code: 'content_policy' };
    let url;
    try { url = new URL(video.url); } catch { return { status: 'failed', error: 'Grok Imagine returned an invalid video link. Try again.' }; }
    if (url.protocol !== 'https:') return { status: 'failed', error: 'Grok Imagine returned an invalid video link. Try again.' };
    return { status: 'done', url: url.href, duration: Number.isInteger(video.duration) ? video.duration : null };
  }
  if (status === 'expired') return { status: 'expired', error: 'This video request expired before it finished. Start it again.' };
  if (status === 'failed') {
    const code = String(data?.error?.code || '');
    const message = String(data?.error?.message || '');
    if (POLICY.test(message) || code === 'permission_denied') return { status: 'failed', error: POLICY_MESSAGE, code: 'content_policy' };
    if (code === 'invalid_argument' || code === 'failed_precondition') return { status: 'failed', error: 'Grok Imagine could not use these video options or this image. Change them and try again.' };
    return { status: 'failed', error: 'Grok Imagine could not finish this video. Try again.' };
  }
  return { status: 'pending', progress: null };
}

/** POSTs/GETs a provider request with a timeout and returns parsed JSON or throws ImagineError. */
export async function callProvider(provider, request, { fetcher = fetch, signal, timeoutMs = 120000, maxBytes = 64 * 1024 * 1024, notFoundAsNull = false } = {}) {
  let response;
  try {
    response = await fetcher(request.url, { ...request.init, signal: AbortSignal.any([signal || new AbortController().signal, AbortSignal.timeout(timeoutMs)]) });
  } catch (error) {
    if (signal?.aborted) throw error;
    throw new ImagineError(error?.name === 'TimeoutError' ? `${PROVIDER_NAMES[provider] || 'Grok Imagine'} took too long. Retry.` : `${PROVIDER_NAMES[provider] || 'Grok Imagine'} could not be reached. Retry.`, 504, { retryable: true });
  }
  let data = null;
  try { data = await readJsonLimited(response, maxBytes); } catch {}
  if (notFoundAsNull && response.status === 404) return null;
  if (!response.ok) throw providerFailure(provider, response.status, data);
  if (data === null) throw new ImagineError('The provider returned an unreadable response. Retry.', 502, { retryable: true });
  return data;
}

/** Downloads a provider media URL (HTTPS only) with a byte cap. Returns null when larger than maxBytes. */
export async function downloadMedia(url, maxBytes, { fetcher = fetch, signal, timeoutMs = 120000 } = {}) {
  if (!/^https:\/\//.test(url)) throw new ImagineError('Invalid media link.', 502);
  const response = await fetcher(url, { redirect: 'follow', signal: AbortSignal.any([signal || new AbortController().signal, AbortSignal.timeout(timeoutMs)]) });
  if (!response.ok) throw new ImagineError(response.status === 404 || response.status === 403 ? 'The finished video link expired. Start the video again.' : 'The finished video could not be downloaded. Retry.', response.status === 404 || response.status === 403 ? 410 : 502, { retryable: response.status >= 500 });
  const length = response.headers.get('content-length');
  if (length && Number(length) > maxBytes) { await response.body?.cancel().catch(() => {}); return null; }
  const reader = response.body?.getReader();
  if (!reader) throw new ImagineError('The finished video could not be downloaded. Retry.', 502, { retryable: true });
  const chunks = []; let size = 0;
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > maxBytes) { await reader.cancel().catch(() => {}); return null; }
    chunks.push(value);
  }
  return Buffer.concat(chunks, size);
}
