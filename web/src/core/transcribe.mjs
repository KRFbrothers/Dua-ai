// Audio -> transcript. The transcript is returned to the app and kept with the message, so follow-ups
// never transcribe the same recording twice.
//   Grok:   POST https://api.x.ai/v1/stt (multipart; `url` = signed Storage URL, downloaded server-side by xAI)
//   Gemini: generateContent with the audio inline (<= 20 MB) or via the Files API
//   OpenAI: POST https://api.openai.com/v1/audio/transcriptions (<= 25 MB; mp3, mp4, mpeg, mpga, m4a, wav, webm)
// DUA_TRANSCRIBE_PROVIDER = auto (default: grok, then gemini, then openai, using whichever keys exist) | grok | gemini | openai.
import { uploadGeminiFile, ProviderFileError } from './gemini-files.mjs';

export const MAX_TRANSCRIPT_CHARS = 100000;
const OPENAI_TYPES = { 'audio/mpeg': 'mp3', 'audio/mp3': 'mp3', 'audio/mp4': 'm4a', 'audio/m4a': 'm4a', 'audio/x-m4a': 'm4a', 'audio/wav': 'wav', 'audio/x-wav': 'wav', 'audio/wave': 'wav', 'audio/webm': 'webm' };
const GEMINI_INLINE_AUDIO_BYTES = 18 * 1024 * 1024; // documented inline audio limit is 20 MB per request
const key = (env, name) => String(env[name] || '').trim();

export function transcribeOrder(env = {}) {
  const available = { grok: Boolean(key(env, 'GROK_API_KEY')), gemini: Boolean(key(env, 'GEMINI_API_KEY') && key(env, 'GEMINI_MODEL')), openai: Boolean(key(env, 'OPENAI_API_KEY')) };
  const choice = String(env.DUA_TRANSCRIBE_PROVIDER || 'auto').trim().toLowerCase();
  const order = ['grok', 'gemini', 'openai'].includes(choice) ? [choice] : ['grok', 'gemini', 'openai'];
  return order.filter(name => available[name]);
}

const clean = text => typeof text === 'string' ? text.trim().slice(0, MAX_TRANSCRIPT_CHARS) : '';
const timed = (signal, ms) => AbortSignal.any([signal || new AbortController().signal, AbortSignal.timeout(ms)]);

async function grok({ signedUrl, env, fetcher, signal }) {
  const form = new FormData();
  form.append('model', key(env, 'GROK_TRANSCRIBE_MODEL') || 'grok-voice-transcribe-2.0');
  form.append('url', signedUrl);
  const response = await fetcher('https://api.x.ai/v1/stt', { method: 'POST', headers: { Authorization: 'Bearer ' + key(env, 'GROK_API_KEY') }, body: form, redirect: 'error', signal: timed(signal, 180000) });
  if (!response.ok) throw new ProviderFileError(`Grok transcription failed (HTTP ${response.status}).`);
  return clean((await response.json())?.text);
}

async function gemini({ ref, loadBytes, env, fetcher, signal }) {
  const bytes = await loadBytes();
  const apiKey = key(env, 'GEMINI_API_KEY');
  const media = bytes.length <= GEMINI_INLINE_AUDIO_BYTES
    ? { inlineData: { mimeType: ref.mimeType, data: bytes.toString('base64') } }
    : { fileData: await uploadGeminiFile({ bytes, mimeType: ref.mimeType, name: ref.name, key: apiKey, fetcher, signal }).then(file => ({ mimeType: file.mimeType, fileUri: file.uri })) };
  const response = await fetcher(`https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(key(env, 'GEMINI_MODEL'))}:generateContent`, {
    method: 'POST', redirect: 'error', signal: timed(signal, 180000),
    headers: { 'Content-Type': 'application/json', 'x-goog-api-key': apiKey },
    body: JSON.stringify({ contents: [{ role: 'user', parts: [{ text: 'Generate a verbatim transcript of the speech in this audio, in the language spoken. Output only the transcript text. If there is no speech, output: [no speech]' }, media] }] })
  });
  if (!response.ok) throw new ProviderFileError(`Gemini transcription failed (HTTP ${response.status}).`);
  const parts = (await response.json())?.candidates?.[0]?.content?.parts;
  return clean(Array.isArray(parts) ? parts.filter(part => !part?.thought && typeof part?.text === 'string').map(part => part.text).join('') : '');
}

async function openai({ ref, loadBytes, env, fetcher, signal }) {
  const ext = OPENAI_TYPES[ref.mimeType];
  if (!ext) throw new ProviderFileError('OpenAI transcription does not accept this audio format.', 415);
  if (ref.size && ref.size > 25 * 1024 * 1024) throw new ProviderFileError('OpenAI transcription accepts audio up to 25 MB.', 413);
  const bytes = await loadBytes();
  if (bytes.length > 25 * 1024 * 1024) throw new ProviderFileError('OpenAI transcription accepts audio up to 25 MB.', 413);
  const form = new FormData();
  form.append('model', key(env, 'OPENAI_TRANSCRIBE_MODEL') || 'gpt-transcribe');
  form.append('file', new Blob([bytes], { type: ref.mimeType }), 'audio.' + ext);
  const response = await fetcher('https://api.openai.com/v1/audio/transcriptions', { method: 'POST', headers: { Authorization: 'Bearer ' + key(env, 'OPENAI_API_KEY') }, body: form, redirect: 'error', signal: timed(signal, 180000) });
  if (!response.ok) throw new ProviderFileError(`OpenAI transcription failed (HTTP ${response.status}).`);
  return clean((await response.json())?.text);
}

const RUNNERS = { grok, gemini, openai };
/** Tries each configured transcription provider in order. Returns {text, provider}. */
export async function transcribe({ ref, signedUrl, loadBytes, env = {}, fetcher = fetch, signal }) {
  const order = transcribeOrder(env);
  if (!order.length) throw new ProviderFileError('Audio needs a transcription provider. Set GROK_API_KEY, GEMINI_API_KEY or OPENAI_API_KEY.', 503);
  let last;
  for (const provider of order) {
    try {
      const text = await RUNNERS[provider]({ ref, signedUrl, loadBytes, env, fetcher, signal });
      if (text) return { text, provider };
      last = new ProviderFileError('Transcription returned no text.', 422);
    } catch (error) {
      if (signal?.aborted) throw error;
      last = error;
    }
  }
  throw last instanceof ProviderFileError ? new ProviderFileError(`Could not transcribe "${ref.name}". ${last.message}`, last.status) : new ProviderFileError(`Could not transcribe "${ref.name}". Retry.`);
}
