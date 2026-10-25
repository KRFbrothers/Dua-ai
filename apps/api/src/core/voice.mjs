// /api/voice/* — owner-only voice features (Phase 4).
//   POST /api/voice/audio {text, provider?, voice?, language?, speed?} -> audio bytes (audio/mpeg or audio/wav)
//        Apps chunk long replies (≤ provider maxCharacters per request) and cache each chunk on the device.
//        Older apps send only {text}: ElevenLabs when configured, otherwise Grok voice (both MP3, as those apps expect).
// Keys stay on this server. Every route requires the signed-in owner (Supabase sign-in + DUA_ALLOWED_EMAIL).
import { readJsonLimited } from './limited-json.mjs';
import { liveConfiguration, validateSessionRequest, startLiveSession, endLiveSession, readLiveUsage } from './voice-live.mjs';
import { storageConfig, resolveOwner, StorageError } from './storage.mjs';
import { XAI_VOICES, GEMINI_VOICES, XAI_LANGUAGES, SPEED, DEFAULT_GEMINI_TTS_MODEL, VoiceError,
  xaiTtsRequest, geminiTtsRequest, elevenLabsTtsRequest, synthesize } from './voice-tts.mjs';

const noStore = { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' };
const json = (data, status = 200, headers = {}) => Response.json(data, { status, headers: { ...noStore, ...headers } });
const hasKey = value => typeof value === 'string' && Boolean(value.trim());
const off = value => ['0', 'false'].includes(String(value ?? '').trim().toLowerCase());
const modelId = (value, fallback) => typeof value === 'string' && /^[a-zA-Z0-9][a-zA-Z0-9._:/-]{0,119}$/.test(value.trim()) && !/^(?:sk-|AIza|xai-)/.test(value.trim()) ? value.trim() : fallback;

export function elevenLabsReady(env = {}) {
  return env.ENABLE_GENERATED_VOICE === '1' && hasKey(env.ELEVENLABS_API_KEY) && /^[A-Za-z0-9_-]{1,64}$/.test(env.ELEVENLABS_VOICE_ID || '');
}
/** Shared owner gate for every voice feature: the owner email and Supabase sign-in (used to verify the caller). */
export function voiceBaseReason(env = {}) {
  if (!String(env.DUA_ALLOWED_EMAIL || '').trim()) return 'Voice features are owner-only. Set DUA_ALLOWED_EMAIL in hosting settings.';
  if (!storageConfig({ ...env, DUA_FILES: '1', DUA_FILES_BUCKET: 'dua-files' }).enabled) return 'Voice features need Supabase sign-in (SUPABASE_URL and SUPABASE_ANON_KEY).';
  return null;
}

/**
 * Reply voice (TTS) capabilities. Env: DUA_VOICE_TTS=0 turns Grok and Gemini voices off; DUA_VOICE_TTS_PROVIDER=xai|gemini|elevenlabs
 * picks the default; DUA_VOICE_GEMINI_TTS_MODEL (default gemini-3.8-flash-tts). ElevenLabs keeps ENABLE_GENERATED_VOICE=1,
 * ELEVENLABS_API_KEY and ELEVENLABS_VOICE_ID.
 */
export function ttsConfiguration(env = {}) {
  const base = voiceBaseReason(env);
  const ttsOff = off(env.DUA_VOICE_TTS);
  const providers = [];
  if (!ttsOff && hasKey(env.GROK_API_KEY)) providers.push({ id: 'xai', name: 'Grok', voices: XAI_VOICES, defaultVoice: 'eve', languages: XAI_LANGUAGES.map(({ api, ...rest }) => rest),
    speed: SPEED, maxCharacters: 2000, format: 'audio/mpeg', price: '$15 per 1M characters' });
  if (!ttsOff && hasKey(env.GEMINI_API_KEY)) providers.push({ id: 'gemini', name: 'Gemini', model: modelId(env.DUA_VOICE_GEMINI_TTS_MODEL, DEFAULT_GEMINI_TTS_MODEL), voices: GEMINI_VOICES, defaultVoice: 'Kore',
    languages: [{ id: 'auto', name: 'Auto (detected from the reply)' }], speed: SPEED, maxCharacters: 1000, format: 'audio/wav', price: 'about $0.00225 per 10 s of audio' });
  if (elevenLabsReady(env)) providers.push({ id: 'elevenlabs', name: 'ElevenLabs', voices: [{ id: 'configured', name: 'Configured voice', description: 'The voice set in ELEVENLABS_VOICE_ID' }], defaultVoice: 'configured',
    languages: [{ id: 'auto', name: 'Auto (multilingual)' }], speed: null, maxCharacters: 2000, format: 'audio/mpeg' });
  const ids = providers.map(provider => provider.id);
  const preferred = String(env.DUA_VOICE_TTS_PROVIDER || '').trim().toLowerCase();
  const reason = base || (!providers.length ? (ttsOff ? 'Grok and Gemini reply voices are turned off (DUA_VOICE_TTS=0).' : 'Reply voices need GROK_API_KEY or GEMINI_API_KEY (or the ElevenLabs settings) on the server.') : null);
  return { enabled: !reason, ...(reason ? { reason } : {}), providers: reason ? [] : providers, defaultProvider: reason ? null : ids.includes(preferred) ? preferred : ids[0] };
}

/** Public voice capabilities for /api/config. Apps that do not find `voice` (older backends) hide the new options. */
export function voiceConfiguration(env = {}) {
  return { version: 1, tts: ttsConfiguration(env), live: liveConfiguration(env, voiceBaseReason(env)) };
}

/** Old apps only know one "generated audio" switch and expect MP3. */
export function legacyAudioProvider(env = {}) {
  if (voiceBaseReason(env)) return null;
  if (elevenLabsReady(env)) return 'elevenlabs';
  return ttsConfiguration(env).providers.some(provider => provider.id === 'xai') ? 'xai' : null;
}

const fail = (message, status = 400) => { throw new VoiceError(message, status); };
/** Validates POST /api/voice/audio against the current configuration. Pure (tested). */
export function validateAudioRequest(body, config, legacy = null) {
  if (!body || typeof body !== 'object' || Array.isArray(body) || Object.keys(body).some(key => !['text', 'provider', 'voice', 'language', 'speed'].includes(key))) fail('Invalid voice request.');
  const providerId = body.provider ?? (Object.keys(body).length === 1 ? legacy : config.defaultProvider);
  const provider = config.providers.find(item => item.id === providerId);
  if (!provider) fail(body.provider === undefined ? 'Generated voice setup is not enabled. Device voice remains available.' : 'That voice provider is not configured on the server. Device voice remains available.', 503);
  if (typeof body.text !== 'string' || /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/.test(body.text)) fail(`Enter up to ${provider.maxCharacters} characters to speak.`);
  const text = body.text.trim();
  if (!text || text.length > provider.maxCharacters) fail(`Enter up to ${provider.maxCharacters} characters to speak.`);
  const voice = body.voice ?? provider.defaultVoice;
  if (!provider.voices.some(item => item.id === voice)) fail('Choose one of the listed voices.');
  const language = body.language ?? 'auto';
  if (!provider.languages.some(item => item.id === language)) fail('Choose one of the listed languages.');
  const speed = body.speed ?? 1;
  if (typeof speed !== 'number' || !Number.isFinite(speed) || speed < SPEED.min || speed > SPEED.max) fail(`Choose a speed from ${SPEED.min} to ${SPEED.max}.`);
  return { provider: provider.id, model: provider.model, text, voice, language, speed: provider.speed ? speed : 1 };
}

/** Builds the provider request for a validated voice request. Pure (tested). */
export function buildTtsRequest(request, env) {
  if (request.provider === 'xai') {
    const language = XAI_LANGUAGES.find(item => item.id === request.language)?.api || 'auto';
    return xaiTtsRequest({ key: env.GROK_API_KEY.trim(), text: request.text, voice: request.voice, language, speed: request.speed });
  }
  if (request.provider === 'gemini') return geminiTtsRequest({ key: env.GEMINI_API_KEY.trim(), model: request.model, text: request.text, voice: request.voice, speed: request.speed });
  return elevenLabsTtsRequest({ key: env.ELEVENLABS_API_KEY.trim(), voiceId: env.ELEVENLABS_VOICE_ID, text: request.text });
}

async function audioAction(request, ctx) {
  let body;
  try { body = await readJsonLimited(request, 16384); }
  catch (error) { return json({ error: error.status === 413 ? 'Text is too long for one voice request.' : 'Invalid request.' }, error.status === 413 ? 413 : 400); }
  const config = ttsConfiguration(ctx.env);
  const valid = validateAudioRequest(body, config, legacyAudioProvider(ctx.env));
  const audio = await synthesize(valid.provider, buildTtsRequest(valid, ctx.env), { fetcher: ctx.fetcher, signal: request.signal });
  return new Response(audio.bytes, { headers: { ...noStore, 'Content-Type': audio.mimeType, 'Content-Length': String(audio.bytes.length), 'X-Dua-Voice-Provider': valid.provider } });
}

const liveConfig = env => storageConfig({ ...env, DUA_FILES: '1', DUA_FILES_BUCKET: 'dua-files' });
function liveEnabled(env) {
  const live = liveConfiguration(env, voiceBaseReason(env));
  if (!live.enabled) throw new VoiceError(live.reason, 503, { code: 'live_off' });
}
async function sessionAction(request, ctx) {
  liveEnabled(ctx.env);
  let body;
  try { body = await readJsonLimited(request, 65536); }
  catch (error) { return json({ error: error.status === 413 ? 'Chat context is too long for live voice.' : 'Invalid request.' }, error.status === 413 ? 413 : 400); }
  const valid = validateSessionRequest(body);
  const session = await startLiveSession(valid, { env: ctx.env, fetcher: ctx.fetcher, signal: request.signal, config: liveConfig(ctx.env), token: ctx.token });
  return json(session);
}
async function sessionEndAction(request, ctx) {
  let body;
  try { body = await readJsonLimited(request, 4096); } catch { return json({ error: 'Invalid request.' }, 400); }
  if (!body || typeof body !== 'object' || Array.isArray(body) || Object.keys(body).some(key => key !== 'sessionId')) return json({ error: 'Invalid request.' }, 400);
  return json(await endLiveSession(body.sessionId, { env: ctx.env, fetcher: ctx.fetcher, signal: request.signal, config: liveConfig(ctx.env), token: ctx.token }));
}
async function usageAction(request, ctx) {
  return json(await readLiveUsage({ env: ctx.env, fetcher: ctx.fetcher, signal: request.signal, config: liveConfig(ctx.env), token: ctx.token }));
}

const ROUTES = { audio: 'POST', session: 'POST', 'session-end': 'POST', usage: 'GET' };
const ACTIONS = { audio: audioAction, session: sessionAction, 'session-end': sessionEndAction, usage: usageAction };
export async function handleVoice(request, { env = process.env, fetcher = fetch } = {}) {
  const action = new URL(request.url).pathname.replace(/^\/api\/voice\//, '');
  if (!Object.hasOwn(ROUTES, action)) return json({ error: 'Not found.' }, 404);
  if (request.method !== ROUTES[action]) return json({ error: 'Method not allowed.' }, 405);
  const base = voiceBaseReason(env);
  if (base) return json({ error: base }, 503);
  if (request.method === 'POST' && !(request.headers.get('content-type') || '').startsWith('application/json')) return json({ error: 'Use JSON.' }, 415);
  try {
    const { user, token } = await resolveOwner(request, { env, fetcher, feature: 'voice' });
    const ctx = { env, fetcher, user, token };
    return await ACTIONS[action](request, ctx);
  } catch (error) {
    if (error instanceof VoiceError) {
      const headers = error.retryAfterSeconds ? { 'Retry-After': String(error.retryAfterSeconds) } : {};
      return json({ error: error.message, ...(error.code ? { code: error.code } : {}), ...(error.usage ? { usage: error.usage } : {}), ...(error.retryAfterSeconds ? { retryAfterSeconds: error.retryAfterSeconds } : {}) }, error.status, headers);
    }
    if (error instanceof StorageError) return json({ error: error.message }, error.status);
    if (error?.name === 'TimeoutError' || error?.name === 'AbortError' || request.signal.aborted) return json({ error: 'Voice request timed out. Retry.' }, 504);
    return json({ error: 'Voice request failed. Retry or use device voice.' }, 502);
  }
}
