// Reply voice (text-to-speech) adapters: catalogs, request building and response parsing only (no auth).
// Checked against the official docs in October 2026:
//  xAI (docs.x.ai › Voice › Text to Speech; REST reference › Voice › Text to speech - REST / List voices)
//   POST https://api.x.ai/v1/tts {text (≤ 60,000 chars), voice_id (default "eve"), language (BCP-47 or "auto", required),
//        output_format:{codec mp3|wav|pcm|mulaw|alaw, sample_rate, bit_rate (mp3)}, speed 0.7–1.5}
//     → raw audio bytes (Content-Type audio/mpeg for mp3). $15.00 per 1M characters.
//   Built-in voices (docs.x.ai › Voice overview › Voices, also GET /v1/tts/voices): the 28 below; all speak every supported language.
//  Gemini (ai.google.dev › Speech generation (text-to-speech), Interactions API)
//   POST /v1beta/interactions {model:"gemini-3.8-flash-tts", input:[{type:"user_input", content:[{type:"text", text,
//        annotations:[{type:"speech_metadata", style}]}]}], response_format:{type:"audio", mime_type:"audio/wav"},
//        generation_config:{speech_config:[{voice}]}, store:false}
//     → steps[].content[] {type:"audio", data(base64)}; unary default is WAV (24 kHz mono 16-bit). Language is detected from the text.
//     Pace is set with speech_metadata.style ("speaking slowly" / "speaking rapidly"). 30 prebuilt voices below.
//     Paid tier (through Dec 31, 2026): $0.50 / 1M text input tokens, $9.00 / 1M audio output tokens (≈ $0.00225 per 10 s).
//  ElevenLabs (existing): POST /v1/text-to-speech/{ELEVENLABS_VOICE_ID}?output_format=mp3_22050_32 {text, model_id:"eleven_multilingual_v2"}.
import { readJsonLimited } from './limited-json.mjs';

export const XAI_TTS_URL = 'https://api.x.ai/v1/tts';
export const GEMINI_INTERACTIONS = 'https://generativelanguage.googleapis.com/v1beta/interactions';
export const DEFAULT_GEMINI_TTS_MODEL = 'gemini-3.8-flash-tts';
export const MAX_AUDIO_BYTES = 4 * 1024 * 1024; // stays under Vercel's 4.5 MB response limit

const voice = (id, name, description) => Object.freeze({ id, name, description });
/** xAI built-in voices with the tone descriptions from docs.x.ai (eve is the default). */
export const XAI_VOICES = Object.freeze([
  voice('eve', 'Eve', 'Energetic and upbeat · British accent'), voice('ara', 'Ara', 'Warm and friendly'),
  voice('leo', 'Leo', 'Authoritative and strong · British accent'), voice('rex', 'Rex', 'Confident and clear'), voice('sal', 'Sal', 'Smooth and balanced'),
  voice('naksh', 'Naksh', 'Warm, thoughtful and wise · Indian accent'), voice('carina', 'Carina', 'Soft, empathetic and soothing'),
  voice('zagan', 'Zagan', 'Powerful, dramatic and unmistakable'), voice('helix', 'Helix', 'Bold, dynamic and adrenaline-fueled'),
  voice('orion', 'Orion', 'Rich, cinematic and resonant'), voice('luna', 'Luna', 'Gentle, patient and deeply nurturing'),
  voice('iris', 'Iris', 'Friendly, upbeat and naturally charming'), voice('altair', 'Altair', 'Elegant, refined and effortlessly premium'),
  voice('zenith', 'Zenith', 'Sharp, focused and driven'), voice('perseus', 'Perseus', 'Strong, confident and trustworthy'),
  voice('helios', 'Helios', 'Upbeat, energetic and endlessly versatile'), voice('lux', 'Lux', 'Grounded, calm and quietly wise'),
  voice('kepler', 'Kepler', 'Inventive, forward-thinking and charismatic'), voice('rigel', 'Rigel', 'Precise, professional and calmly confident · Australian accent'),
  voice('cosmo', 'Cosmo', 'Bright, curious and easy to follow'), voice('celeste', 'Celeste', 'Compassionate, confident and reassuring'),
  voice('ursa', 'Ursa', 'Friendly, warm and steadfast'), voice('sirius', 'Sirius', 'Quick-witted, clever and playful'),
  voice('lumen', 'Lumen', 'Warm, articulate and engaging'), voice('castor', 'Castor', 'Charismatic, down-to-earth and easygoing'),
  voice('atlas', 'Atlas', 'Confident, commanding and reassuring'), voice('aurora', 'Aurora', 'Serene, steady and radiant'),
  voice('liora', 'Liora', 'Calm, grounded and luminous')
]);
/** Gemini 3.8 TTS prebuilt studio voices (ai.google.dev › Speech generation › Voice options). */
export const GEMINI_VOICES = Object.freeze([
  ['Kore', 'Firm'], ['Puck', 'Upbeat'], ['Zephyr', 'Bright'], ['Charon', 'Informative'], ['Fenrir', 'Excitable'], ['Leda', 'Youthful'],
  ['Orus', 'Firm'], ['Aoede', 'Breezy'], ['Callirrhoe', 'Easy-going'], ['Autonoe', 'Bright'], ['Enceladus', 'Breathy'], ['Iapetus', 'Clear'],
  ['Umbriel', 'Easy-going'], ['Algieba', 'Smooth'], ['Despina', 'Smooth'], ['Erinome', 'Clear'], ['Algenib', 'Gravelly'], ['Rasalgethi', 'Informative'],
  ['Laomedeia', 'Upbeat'], ['Achernar', 'Soft'], ['Alnilam', 'Firm'], ['Schedar', 'Even'], ['Gacrux', 'Mature'], ['Pulcherrima', 'Forward'],
  ['Achird', 'Friendly'], ['Zubenelgenubi', 'Casual'], ['Vindemiatrix', 'Gentle'], ['Sadachbia', 'Lively'], ['Sadaltager', 'Knowledgeable'], ['Sulafat', 'Warm']
].map(([id, description]) => voice(id, id, description)));

/**
 * Reply languages. `api` is what the provider receives: xAI documents these BCP-47 codes plus "auto" (Urdu is not on its list,
 * so it is sent as "auto" and the model detects it from the text). Gemini always detects the language from the text.
 */
export const XAI_LANGUAGES = Object.freeze([
  ['auto', 'Auto'], ['en', 'English'], ['hi', 'Hindi'], ['ur', 'Urdu', 'auto'], ['ar-SA', 'Arabic (Saudi Arabia)'], ['ar-AE', 'Arabic (UAE)'],
  ['ar-EG', 'Arabic (Egypt)'], ['bn', 'Bengali'], ['zh', 'Chinese (Simplified)'], ['fr', 'French'], ['de', 'German'], ['id', 'Indonesian'],
  ['it', 'Italian'], ['ja', 'Japanese'], ['ko', 'Korean'], ['pt-BR', 'Portuguese (Brazil)'], ['pt-PT', 'Portuguese (Portugal)'], ['ru', 'Russian'],
  ['es-MX', 'Spanish (Mexico)'], ['es-ES', 'Spanish (Spain)'], ['tr', 'Turkish'], ['vi', 'Vietnamese']
].map(([id, name, api = id]) => Object.freeze({ id, name, api, ...(api !== id ? { note: 'Auto-detected (not on the provider\'s list)' } : {}) })));
export const SPEED = Object.freeze({ min: 0.7, max: 1.5, default: 1 });

export class VoiceError extends Error {
  constructor(message, status = 502, { code = null, retryAfterSeconds = null, usage = null } = {}) {
    super(message); this.status = status; this.code = code; this.retryAfterSeconds = retryAfterSeconds; this.usage = usage;
  }
}
const jsonInit = (headers, body) => ({ method: 'POST', redirect: 'error', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body) });
const round = value => Math.round(value * 100) / 100;

/** xAI TTS: MP3 24 kHz / 64 kbps keeps a 2,000-character chunk well under the response limit. */
export function xaiTtsRequest({ key, text, voice = 'eve', language = 'auto', speed = 1 }) {
  const body = { text, voice_id: voice, language, output_format: { codec: 'mp3', sample_rate: 24000, bit_rate: 64000 } };
  if (speed !== 1) body.speed = round(speed);
  return { url: XAI_TTS_URL, init: jsonInit({ Authorization: 'Bearer ' + key, Accept: 'audio/mpeg' }, body) };
}

/** Gemini TTS via the Interactions API (unary WAV). Pace goes in speech_metadata.style as the docs describe. */
export function geminiTtsRequest({ key, model = DEFAULT_GEMINI_TTS_MODEL, text, voice = 'Kore', speed = 1 }) {
  const style = speed <= 0.85 ? 'speaking slowly' : speed < 0.97 ? 'speaking a little slowly' : speed >= 1.25 ? 'speaking rapidly' : speed > 1.03 ? 'speaking a little faster than usual' : null;
  const content = { type: 'text', text, ...(style ? { annotations: [{ type: 'speech_metadata', style }] } : {}) };
  return { url: GEMINI_INTERACTIONS, init: jsonInit({ 'x-goog-api-key': key }, {
    model, input: [{ type: 'user_input', content: [content] }], response_format: { type: 'audio', mime_type: 'audio/wav' },
    generation_config: { speech_config: [{ voice }] }, store: false
  }) };
}

export function elevenLabsTtsRequest({ key, voiceId, text }) {
  return { url: 'https://api.elevenlabs.io/v1/text-to-speech/' + voiceId + '?output_format=mp3_22050_32',
    init: jsonInit({ 'xi-api-key': key, Accept: 'audio/mpeg' }, { text, model_id: 'eleven_multilingual_v2' }) };
}

const NAMES = { xai: 'Grok voice', gemini: 'Gemini voice', elevenlabs: 'ElevenLabs voice' };
const KEYS = { xai: 'GROK_API_KEY', gemini: 'GEMINI_API_KEY', elevenlabs: 'ELEVENLABS_API_KEY' };
/** Maps a provider HTTP error to a safe message (raw upstream text is never returned). */
export function ttsFailure(provider, status) {
  const name = NAMES[provider] || 'The voice provider';
  if (status === 401 || status === 403) return new VoiceError(`${name} rejected the server API key. Check ${KEYS[provider]} in hosting settings. Device voice remains available.`, 503);
  if (status === 404) return new VoiceError(`${name} or this voice is unavailable. Pick another voice or check the model setting.`, 503);
  if (status === 429) return new VoiceError(`${name} rate or credit limit reached. Retry shortly or use device voice.`, 429);
  if (status >= 400 && status < 500) return new VoiceError(`${name} could not read this text. Try device voice.`, 400);
  return new VoiceError(`${name} is temporarily unavailable. Try device voice.`, 502);
}

async function boundedBytes(response, limit) {
  const declared = Number(response.headers.get('content-length'));
  if (declared > limit) { await response.body?.cancel().catch(() => {}); throw new VoiceError('The voice audio was too large. Use shorter text or device voice.', 502); }
  if (!response.body) return new Uint8Array();
  const reader = response.body.getReader();
  const chunks = []; let size = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > limit) { await reader.cancel().catch(() => {}); throw new VoiceError('The voice audio was too large. Use shorter text or device voice.', 502); }
    chunks.push(value);
  }
  return Buffer.concat(chunks, size);
}
const isMp3 = bytes => bytes.length > 3 && ((bytes[0] === 0x49 && bytes[1] === 0x44 && bytes[2] === 0x33) || (bytes[0] === 0xff && (bytes[1] & 0xe0) === 0xe0));
const isWav = bytes => bytes.length > 44 && bytes.subarray(0, 4).toString('latin1') === 'RIFF' && bytes.subarray(8, 12).toString('latin1') === 'WAVE';

/** Calls a provider and returns {bytes, mimeType}. Throws VoiceError with a safe message. */
export async function synthesize(provider, request, { fetcher = fetch, signal, timeoutMs = 30000 } = {}) {
  let response;
  try { response = await fetcher(request.url, { ...request.init, signal: AbortSignal.any([signal || new AbortController().signal, AbortSignal.timeout(timeoutMs)]) }); }
  catch (error) {
    if (signal?.aborted) throw error;
    throw new VoiceError(error?.name === 'TimeoutError' ? `${NAMES[provider]} took too long. Retry or use device voice.` : `${NAMES[provider]} could not be reached. Retry.`, 504);
  }
  if (!response.ok) { await response.body?.cancel().catch(() => {}); throw ttsFailure(provider, response.status); }
  if (provider === 'gemini') {
    let data = null;
    try { data = await readJsonLimited(response, Math.ceil(MAX_AUDIO_BYTES * 4 / 3) + 65536); } catch {}
    const parts = Array.isArray(data?.steps) ? data.steps.filter(step => step?.type === 'model_output').flatMap(step => Array.isArray(step.content) ? step.content : []).filter(part => part?.type === 'audio' && typeof part.data === 'string') : [];
    const encoded = parts.at(-1)?.data;
    if (!encoded) throw new VoiceError('Gemini voice returned no audio. Retry or use device voice.', 502);
    const bytes = Buffer.from(encoded, 'base64');
    if (bytes.length > MAX_AUDIO_BYTES) throw new VoiceError('The voice audio was too large. Use shorter text or device voice.', 502);
    if (!isWav(bytes)) throw new VoiceError('Gemini voice returned audio in an unexpected format. Retry.', 502);
    return { bytes, mimeType: 'audio/wav' };
  }
  const type = (response.headers.get('content-type') || '').toLowerCase();
  if (!/^audio\/(mpeg|mp3)(?:;|$)/.test(type)) { await response.body?.cancel().catch(() => {}); throw new VoiceError(`${NAMES[provider]} returned an unexpected response. Retry.`, 502); }
  const bytes = await boundedBytes(response, MAX_AUDIO_BYTES);
  if (!bytes.length || !isMp3(bytes)) throw new VoiceError(`${NAMES[provider]} returned empty or invalid audio. Retry.`, 502);
  return { bytes, mimeType: 'audio/mpeg' };
}
