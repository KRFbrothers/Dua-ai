// /api/imagine/* — owner-only image and video generation (Phase 3).
//   POST /api/imagine/image   {prompt, n?, aspectRatio?, provider?, sourcePaths?, sourceImages?}
//        -> {images:[{path, mimeType, size, url, expiresIn}], provider, model, edit, usage, notice?}
//   POST /api/imagine/video   {prompt, duration?, aspectRatio?, resolution?, sourcePath? | sourceImage?}
//        -> {jobId, status:"pending", seconds, resolution, pollAfterSeconds, usage}
//   POST /api/imagine/status  {jobId}
//        -> {status:"pending", progress} | {status:"done", video:{path, mimeType, size, url, expiresIn, duration}} | {status:"failed"|"expired", error}
//   GET  /api/imagine/usage   -> {images:{used, limit}, videoSeconds:{used, limit}, resetAt}
// Results are copied into the private dua-files bucket under the owner's folder with the owner's own
// token (provider URLs are temporary, and base64 would not fit Vercel's 4.5 MB response limit).
// Video never runs inside one function call: submit returns a signed job token and the app polls.
import { randomUUID } from 'node:crypto';
import { readJsonLimited } from './limited-json.mjs';
import { envInteger } from './adapters.mjs';
import { ownsPath } from './file-types.mjs';
import { storageConfig, resolveOwner, signDownload, downloadSigned, uploadObject, StorageError } from './storage.mjs';
import { DEFAULT_MODELS, PROVIDER_NAMES, ImagineError, xaiImageRequest, geminiImageRequest, xaiVideoRequest, xaiVideoStatusRequest,
  parseXaiImages, parseGeminiImages, parseXaiVideoStatus, callProvider, downloadMedia, sniffImageType } from './imagine-providers.mjs';
import { consumeImagine, readImagineUsage } from './imagine-usage.mjs';
import { signJob, verifyJob } from './imagine-jobs.mjs';

const json = (data, status = 200, headers = {}) => Response.json(data, { status, headers: { 'Cache-Control': 'no-store', ...headers } });
export const IMAGE_ASPECTS = Object.freeze(['auto', '1:1', '3:4', '4:3', '9:16', '16:9']);
export const VIDEO_ASPECTS = Object.freeze(['auto', '1:1', '3:4', '4:3', '9:16', '16:9']);
export const VIDEO_DURATIONS = Object.freeze([5, 8, 10, 15]);
export const MAX_IMAGES = 4;
export const MAX_SOURCES = 3;
export const RESULT_URL_SECONDS = 3600;
const SOURCE_EXT = /\.(jpg|png|webp)$/;
const hasKey = value => typeof value === 'string' && Boolean(value.trim());
const off = value => String(value ?? '').trim() === '0' || String(value ?? '').trim().toLowerCase() === 'false';
const on = value => ['1', 'true'].includes(String(value ?? '').trim().toLowerCase());
const modelId = (value, fallback) => typeof value === 'string' && /^[a-zA-Z0-9][a-zA-Z0-9._:/-]{0,119}$/.test(value.trim()) && !/^(?:sk-|AIza|xai-)/.test(value.trim()) ? value.trim() : fallback;

/**
 * Public Imagine capabilities for /api/config (no secrets). Older apps ignore it; apps that do not
 * find it (older backends) hide the Imagine mode.
 * Env: DUA_IMAGINE_IMAGE=0 / DUA_IMAGINE_VIDEO=0 turn a feature off; DUA_IMAGINE_DAILY_IMAGES (default 20) and
 * DUA_IMAGINE_DAILY_VIDEO_SECONDS (default 60) are the daily caps (0 turns the feature off);
 * DUA_IMAGINE_IMAGE_PROVIDER=xai|gemini picks the default image provider; DUA_IMAGINE_IMAGE_FALLBACK=0 stops
 * falling back to the other provider when the first is unavailable; DUA_IMAGINE_VIDEO_1080P=1 offers 1080p;
 * DUA_IMAGINE_VIDEO_MAX_SECONDS (1–15, default 15); DUA_IMAGINE_MAX_SOURCE_MB (1–20, default 15).
 */
export function imagineConfiguration(env = {}) {
  const storage = storageConfig(env);
  const owner = Boolean(String(env.DUA_ALLOWED_EMAIL || '').trim());
  const keys = { xai: hasKey(env.GROK_API_KEY), gemini: hasKey(env.GEMINI_API_KEY) };
  const models = { xai: modelId(env.DUA_IMAGINE_XAI_IMAGE_MODEL, DEFAULT_MODELS.xaiImage), gemini: modelId(env.DUA_IMAGINE_GEMINI_IMAGE_MODEL, DEFAULT_MODELS.geminiImage) };
  const imageProviders = ['xai', 'gemini'].filter(id => keys[id]);
  const preferred = String(env.DUA_IMAGINE_IMAGE_PROVIDER || '').trim().toLowerCase();
  const defaultProvider = imageProviders.includes(preferred) ? preferred : imageProviders[0] || null;
  const dailyImages = envInteger(env, 'DUA_IMAGINE_DAILY_IMAGES', 20, 0, 1000);
  const dailyVideoSeconds = envInteger(env, 'DUA_IMAGINE_DAILY_VIDEO_SECONDS', 60, 0, 3600);
  const videoModel = modelId(env.DUA_IMAGINE_XAI_VIDEO_MODEL, DEFAULT_MODELS.xaiVideo);
  const maxSeconds = envInteger(env, 'DUA_IMAGINE_VIDEO_MAX_SECONDS', 15, 1, 15);
  // xAI documents 1080p only for grok-imagine-video-1.5 (text- and image-to-video); it stays opt-in.
  const fullHd = videoModel === 'grok-imagine-video-1.5' && on(env.DUA_IMAGINE_VIDEO_1080P);
  const base = !storage.enabled ? 'Imagine needs stored files: Supabase settings and the dua-files migration (see SUPABASE.md).'
    : !owner ? 'Imagine is owner-only. Set DUA_ALLOWED_EMAIL in hosting settings.' : null;
  const imageReason = base || (off(env.DUA_IMAGINE_IMAGE) ? 'Image generation is turned off on the server (DUA_IMAGINE_IMAGE=0).'
    : !imageProviders.length ? 'Image generation needs GROK_API_KEY (xAI) or GEMINI_API_KEY on the server.'
    : !dailyImages ? 'Image generation is off: DUA_IMAGINE_DAILY_IMAGES is 0.' : null);
  const videoReason = base || (off(env.DUA_IMAGINE_VIDEO) ? 'Video generation is turned off on the server (DUA_IMAGINE_VIDEO=0).'
    : !keys.xai ? 'Video generation needs GROK_API_KEY (xAI) on the server.'
    : !dailyVideoSeconds ? 'Video generation is off: DUA_IMAGINE_DAILY_VIDEO_SECONDS is 0.' : null);
  return {
    enabled: !imageReason || !videoReason,
    requiresSignIn: true,
    maxSourceBytes: envInteger(env, 'DUA_IMAGINE_MAX_SOURCE_MB', 15, 1, 20) * 1024 * 1024,
    image: {
      enabled: !imageReason, ...(imageReason ? { reason: imageReason } : {}),
      providers: imageProviders.map(id => ({ id, name: PROVIDER_NAMES[id], model: models[id] })), defaultProvider,
      fallback: imageProviders.length > 1 && !off(env.DUA_IMAGINE_IMAGE_FALLBACK),
      aspectRatios: IMAGE_ASPECTS, maxImages: MAX_IMAGES, maxSourceImages: MAX_SOURCES, dailyLimit: dailyImages
    },
    video: {
      enabled: !videoReason, ...(videoReason ? { reason: videoReason } : {}),
      provider: 'xai', model: videoModel, aspectRatios: VIDEO_ASPECTS,
      durations: VIDEO_DURATIONS.filter(seconds => seconds <= maxSeconds).concat(VIDEO_DURATIONS.includes(maxSeconds) || maxSeconds < 5 ? [] : [maxSeconds]).sort((a, b) => a - b),
      maxSeconds, defaultSeconds: Math.min(5, maxSeconds), resolutions: ['480p', '720p', ...(fullHd ? ['1080p'] : [])], defaultResolution: '720p',
      dailyLimit: dailyVideoSeconds, pollAfterSeconds: 5
    },
    _models: models
  };
}
/** The public part (no internal fields). */
export const publicImagineConfiguration = env => { const { _models, ...rest } = imagineConfiguration(env); return rest; };

const fail = (message, status = 400) => { throw new ImagineError(message, status); };
const prompt = (value, required) => {
  if (value === undefined) value = '';
  if (typeof value !== 'string' || value.length > 4000 || /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/.test(value)) fail('Enter a prompt up to 4,000 characters.');
  if (required && !value.trim()) fail('Describe what to create.');
  return value.trim();
};
function inlineSource(item, maxBytes) {
  if (!item || typeof item !== 'object' || Array.isArray(item) || Object.keys(item).some(key => !['mimeType', 'data'].includes(key))) fail('Invalid source image.');
  if (typeof item.data !== 'string' || !/^[A-Za-z0-9+/]+={0,2}$/.test(item.data) || item.data.length > Math.ceil(maxBytes / 3) * 4) fail('Source image is too large or invalid.', 413);
  const bytes = Buffer.from(item.data, 'base64');
  const mimeType = sniffImageType(bytes);
  if (!mimeType) fail('Use a JPEG, PNG or WebP source image.', 415);
  return { bytes, mimeType };
}

/** Validates POST /api/imagine/image. Pure (tested). */
export function validateImageRequest(body, config) {
  if (!body || typeof body !== 'object' || Array.isArray(body) || Object.keys(body).some(key => !['prompt', 'n', 'aspectRatio', 'provider', 'sourcePaths', 'sourceImages'].includes(key))) fail('Invalid Imagine request.');
  const n = body.n ?? 1;
  if (!Number.isInteger(n) || n < 1 || n > config.image.maxImages) fail(`Choose 1 to ${config.image.maxImages} images.`);
  const aspectRatio = body.aspectRatio ?? 'auto';
  if (!config.image.aspectRatios.includes(aspectRatio)) fail('Choose a supported aspect ratio.');
  const ids = config.image.providers.map(provider => provider.id);
  if (body.provider !== undefined && !ids.includes(body.provider)) fail('That image provider is not configured on the server.');
  const sourcePaths = body.sourcePaths ?? [];
  const sourceImages = body.sourceImages ?? [];
  if (!Array.isArray(sourcePaths) || !Array.isArray(sourceImages) || sourcePaths.length + sourceImages.length > config.image.maxSourceImages) fail(`Use up to ${config.image.maxSourceImages} source images.`);
  if (sourcePaths.some(path => typeof path !== 'string' || !SOURCE_EXT.test(path))) fail('Source images must be JPEG, PNG or WebP files.');
  return { prompt: prompt(body.prompt, true), n, aspectRatio, provider: body.provider || config.image.defaultProvider, sourcePaths: [...new Set(sourcePaths)],
    inline: sourceImages.map(item => inlineSource(item, config.maxSourceBytes)) };
}

/** Validates POST /api/imagine/video. Pure (tested). */
export function validateVideoRequest(body, config) {
  if (!body || typeof body !== 'object' || Array.isArray(body) || Object.keys(body).some(key => !['prompt', 'duration', 'aspectRatio', 'resolution', 'sourcePath', 'sourceImage'].includes(key))) fail('Invalid Imagine request.');
  if (body.sourcePath !== undefined && body.sourceImage !== undefined) fail('Use one source image for a video.');
  if (body.sourcePath !== undefined && (typeof body.sourcePath !== 'string' || !SOURCE_EXT.test(body.sourcePath))) fail('The source image must be a JPEG, PNG or WebP file.');
  const hasSource = body.sourcePath !== undefined || body.sourceImage !== undefined;
  const duration = body.duration ?? config.video.defaultSeconds;
  if (!Number.isInteger(duration) || duration < 1 || duration > config.video.maxSeconds) fail(`Choose a duration from 1 to ${config.video.maxSeconds} seconds.`);
  const aspectRatio = body.aspectRatio ?? (hasSource ? 'auto' : '16:9');
  if (!config.video.aspectRatios.includes(aspectRatio)) fail('Choose a supported aspect ratio.');
  const resolution = body.resolution ?? config.video.defaultResolution;
  if (!config.video.resolutions.includes(resolution)) fail(resolution === '1080p' ? '1080p is not enabled on the server (DUA_IMAGINE_VIDEO_1080P=1, grok-imagine-video-1.5 only).' : 'Choose a supported resolution.');
  return { prompt: prompt(body.prompt, !hasSource), duration, aspectRatio, resolution, sourcePath: body.sourcePath,
    inline: body.sourceImage === undefined ? null : inlineSource(body.sourceImage, config.maxSourceBytes) };
}

async function loadSource(path, user, ctx) {
  if (!ownsPath(path, user.id)) throw new ImagineError('Source image not found.', 404);
  const url = await signDownload(ctx.storage, ctx.token, path, 120, ctx.io);
  let bytes;
  try { bytes = await downloadSigned(url, ctx.config.maxSourceBytes, ctx.io); }
  catch (error) { if (error instanceof StorageError && error.status === 413) throw new ImagineError(`Source image is larger than ${Math.floor(ctx.config.maxSourceBytes / 1048576)} MB. Use a smaller image.`, 413); throw error; }
  const mimeType = sniffImageType(bytes);
  if (!mimeType) throw new ImagineError('Use a JPEG, PNG or WebP source image.', 415);
  return { bytes, mimeType };
}

async function generateImages(provider, request, sources, ctx) {
  const key = provider === 'xai' ? ctx.env.GROK_API_KEY : ctx.env.GEMINI_API_KEY;
  const model = ctx.config._models[provider];
  const maxBytes = ctx.storage.maxBytes;
  if (provider === 'xai') {
    const data = await callProvider('xai', xaiImageRequest({ key, model, prompt: request.prompt, n: request.n, aspectRatio: request.aspectRatio, sources }), { ...ctx.io, timeoutMs: 180000 });
    return { ...parseXaiImages(data, maxBytes), model };
  }
  // Gemini returns one image per interaction: run n in parallel and keep what succeeded.
  const results = await Promise.allSettled(Array.from({ length: request.n }, () =>
    callProvider('gemini', geminiImageRequest({ key, model, prompt: request.prompt, aspectRatio: request.aspectRatio, sources }), { ...ctx.io, timeoutMs: 180000 }).then(data => parseGeminiImages(data, maxBytes))));
  const images = results.filter(result => result.status === 'fulfilled').flatMap(result => result.value.images);
  if (!images.length) throw results.find(result => result.status === 'rejected').reason;
  return { images, costUsd: null, model };
}

const extension = mimeType => ({ 'image/jpeg': 'jpg', 'image/png': 'png', 'image/webp': 'webp', 'video/mp4': 'mp4' })[mimeType];
async function storeResult(ctx, bytes, mimeType) {
  const path = `${ctx.user.id.toLowerCase()}/${randomUUID()}.${extension(mimeType)}`;
  await uploadObject(ctx.storage, ctx.token, path, bytes, mimeType, ctx.io);
  const url = await signDownload(ctx.storage, ctx.token, path, RESULT_URL_SECONDS, ctx.io);
  return { path, mimeType, size: bytes.byteLength, url, expiresIn: RESULT_URL_SECONDS };
}

async function imageAction(body, ctx) {
  const request = validateImageRequest(body, ctx.config);
  // Sources load before the cap is charged, so a bad image never costs part of the daily budget.
  const sources = [...await Promise.all(request.sourcePaths.map(path => loadSource(path, ctx.user, ctx))), ...request.inline];
  const usage = await consumeImagine(ctx.storage, ctx.token, 'image', request.n, ctx.config.image.dailyLimit, ctx.io);
  const order = [request.provider, ...(ctx.config.image.fallback ? ctx.config.image.providers.map(provider => provider.id).filter(id => id !== request.provider) : [])];
  let result, used, firstError;
  for (const provider of order) {
    try { result = await generateImages(provider, request, sources, ctx); used = provider; break; }
    catch (error) {
      if (!(error instanceof ImagineError) || !error.retryable) throw Object.assign(error, { usage });
      firstError ||= error;
    }
  }
  if (!result) throw Object.assign(firstError, { usage });
  let images;
  try { images = await Promise.all(result.images.map(image => storeResult(ctx, image.bytes, image.mimeType))); }
  catch (error) {
    if (error instanceof StorageError) throw new ImagineError(`Images were created but could not be saved: ${error.message}`, error.status);
    throw error;
  }
  const notices = [];
  if (used !== request.provider) notices.push(`${PROVIDER_NAMES[request.provider]} was unavailable, so ${PROVIDER_NAMES[used]} made ${images.length === 1 ? 'this image' : 'these images'}.`);
  if (images.length < request.n) notices.push(`${images.length} of ${request.n} images were created; the provider filtered or skipped the rest.`);
  return json({ images, provider: used, model: result.model, edit: sources.length > 0,
    usage: { images: { used: usage.used, limit: usage.limit }, resetAt: usage.resetAt }, ...(notices.length ? { notice: notices.join(' ') } : {}) });
}

async function videoAction(body, ctx) {
  const request = validateVideoRequest(body, ctx.config);
  const source = request.sourcePath ? await loadSource(request.sourcePath, ctx.user, ctx) : request.inline;
  const usage = await consumeImagine(ctx.storage, ctx.token, 'video', request.duration, ctx.config.video.dailyLimit, ctx.io);
  const model = ctx.config.video.model;
  let data;
  try {
    data = await callProvider('video', xaiVideoRequest({ key: ctx.env.GROK_API_KEY, model, prompt: request.prompt, duration: request.duration,
      aspectRatio: request.aspectRatio, resolution: request.resolution, source }), { ...ctx.io, timeoutMs: 60000 });
  } catch (error) { throw Object.assign(error, { usage }); }
  if (typeof data?.request_id !== 'string' || !data.request_id || data.request_id.length > 200) throw new ImagineError('Grok Imagine did not start the video. Retry.', 502);
  const path = `${ctx.user.id.toLowerCase()}/${randomUUID()}.mp4`;
  const jobId = signJob({ r: data.request_id, p: path, s: request.duration, u: ctx.user.id.toLowerCase(), t: Math.floor(Date.now() / 1000), m: model }, ctx.env.GROK_API_KEY);
  return json({ jobId, status: 'pending', seconds: request.duration, resolution: request.resolution, aspectRatio: request.aspectRatio, model,
    pollAfterSeconds: ctx.config.video.pollAfterSeconds, usage: { videoSeconds: { used: usage.used, limit: usage.limit }, resetAt: usage.resetAt } });
}

async function statusAction(body, ctx) {
  if (!body || typeof body !== 'object' || Array.isArray(body) || Object.keys(body).some(key => key !== 'jobId')) fail('Invalid status request.');
  const job = verifyJob(body.jobId, ctx.env.GROK_API_KEY, { userId: ctx.user.id });
  if (!job || !ownsPath(job.p, ctx.user.id)) throw new ImagineError('Video job not found. Start the video again.', 404);
  if (job.expired) return json({ status: 'expired', error: 'This video request expired before it finished. Start it again.' });
  const data = await callProvider('video', xaiVideoStatusRequest({ key: ctx.env.GROK_API_KEY, requestId: job.r }), { ...ctx.io, timeoutMs: 20000, notFoundAsNull: true });
  if (data === null) return json({ status: 'expired', error: 'This video request is no longer available. Start it again.' });
  const state = parseXaiVideoStatus(data);
  if (state.status === 'pending') return json({ status: 'pending', progress: state.progress, pollAfterSeconds: ctx.config.video.pollAfterSeconds });
  if (state.status !== 'done') return json({ status: state.status, error: state.error, ...(state.code ? { code: state.code } : {}) });
  const duration = state.duration ?? job.s;
  // Another poll may have stored it already: reuse the object instead of downloading again.
  try {
    const url = await signDownload(ctx.storage, ctx.token, job.p, RESULT_URL_SECONDS, ctx.io);
    return json({ status: 'done', video: { path: job.p, mimeType: 'video/mp4', size: null, url, expiresIn: RESULT_URL_SECONDS, duration } });
  } catch (error) { if (!(error instanceof StorageError) || error.status !== 404) throw error; }
  const bytes = await downloadMedia(state.url, ctx.storage.maxBytes, ctx.io);
  if (!bytes) return json({ status: 'done', video: { path: null, mimeType: 'video/mp4', size: null, url: state.url, expiresIn: null, duration, stored: false },
    notice: `This video is larger than the ${Math.floor(ctx.storage.maxBytes / 1048576)} MB storage limit, so it was not saved to your storage. Save it to the gallery now; the provider link expires.` });
  try { await uploadObject(ctx.storage, ctx.token, job.p, bytes, 'video/mp4', { ...ctx.io, allowExisting: true }); }
  catch (error) {
    if (error instanceof StorageError && error.status === 415) throw new ImagineError('Storage does not accept MP4 videos yet. Run the Phase 3 migration (supabase/migrations/202610090001_dua_imagine.sql) in the Supabase SQL editor.', 503);
    throw error;
  }
  const url = await signDownload(ctx.storage, ctx.token, job.p, RESULT_URL_SECONDS, ctx.io);
  return json({ status: 'done', video: { path: job.p, mimeType: 'video/mp4', size: bytes.byteLength, url, expiresIn: RESULT_URL_SECONDS, duration } });
}

async function usageAction(ctx) {
  const usage = await readImagineUsage(ctx.storage, ctx.token, ctx.io);
  return json({ images: { used: usage.images, limit: ctx.config.image.dailyLimit }, videoSeconds: { used: usage.videoSeconds, limit: ctx.config.video.dailyLimit }, resetAt: usage.resetAt });
}

const ROUTES = { image: 'POST', video: 'POST', status: 'POST', usage: 'GET' };
export async function handleImagine(request, { env = process.env, fetcher = fetch } = {}) {
  const action = new URL(request.url).pathname.replace(/^\/api\/imagine\//, '');
  if (!ROUTES[action]) return json({ error: 'Not found.' }, 404);
  if (request.method !== ROUTES[action]) return json({ error: 'Method not allowed.' }, 405);
  const config = imagineConfiguration(env);
  const storage = storageConfig(env);
  // A video already started keeps polling even if video generation is switched off afterwards.
  const statusPossible = storage.enabled && hasKey(env.GROK_API_KEY) && Boolean(String(env.DUA_ALLOWED_EMAIL || '').trim());
  if (action === 'image' && !config.image.enabled) return json({ error: config.image.reason }, 503);
  if (action === 'video' && !config.video.enabled) return json({ error: config.video.reason }, 503);
  if (action === 'status' && !statusPossible) return json({ error: config.video.reason || 'Video status is unavailable.' }, 503);
  if (action === 'usage' && !config.enabled) return json({ error: config.image.reason || config.video.reason }, 503);
  try {
    const { user, token } = await resolveOwner(request, { env, fetcher, feature: 'Imagine' });
    const io = { fetcher, signal: request.signal };
    const ctx = { env, config, storage, user, token, io };
    if (action === 'usage') return await usageAction(ctx);
    let body;
    try { body = await readJsonLimited(request, action === 'status' ? 8192 : 4 * 1024 * 1024); }
    catch (error) { return json({ error: error.status === 413 ? 'Request too large. Use a smaller source image.' : 'Invalid request.' }, error.status === 413 ? 413 : 400); }
    if (action === 'image') return await imageAction(body, ctx);
    if (action === 'video') return await videoAction(body, ctx);
    return await statusAction(body, ctx);
  } catch (error) {
    if (error instanceof ImagineError) {
      const usage = error.usage ? { usage: error.usage } : {};
      const headers = error.retryAfterSeconds ? { 'Retry-After': String(error.retryAfterSeconds) } : {};
      return json({ error: error.message, ...(error.code ? { code: error.code } : {}), ...usage, ...(error.retryAfterSeconds ? { retryAfterSeconds: error.retryAfterSeconds } : {}) }, error.status, headers);
    }
    if (error instanceof StorageError) return json({ error: error.message }, error.status);
    if (error?.name === 'TimeoutError' || error?.name === 'AbortError' || request.signal.aborted) return json({ error: 'Imagine request timed out. Retry.' }, 504);
    return json({ error: 'Imagine request failed. Retry.' }, 502);
  }
}
