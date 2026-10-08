// Fakes for voice tests: Supabase auth (via files-helpers router) plus xAI / Gemini / ElevenLabs TTS endpoints.
import { SUPA, UID, filesEnv, router } from './files-helpers.mjs';

export const voiceEnv = { ...filesEnv };
export const MP3 = Buffer.concat([Buffer.from([0x49, 0x44, 0x33, 0x04, 0, 0, 0, 0, 0, 0]), Buffer.from('fake-mp3-frames')]);
export function wav(bytes = 64) {
  const header = Buffer.alloc(44);
  header.write('RIFF', 0, 'latin1'); header.writeUInt32LE(36 + bytes, 4); header.write('WAVEfmt ', 8, 'latin1');
  header.writeUInt32LE(16, 16); header.writeUInt16LE(1, 20); header.writeUInt16LE(1, 22); header.writeUInt32LE(24000, 24);
  header.writeUInt32LE(48000, 28); header.writeUInt16LE(2, 32); header.writeUInt16LE(16, 34); header.write('data', 36, 'latin1'); header.writeUInt32LE(bytes, 40);
  return Buffer.concat([header, Buffer.alloc(bytes, 1)]);
}
export const WAV = wav();
export const mp3Response = () => new Response(MP3, { headers: { 'content-type': 'audio/mpeg', 'content-length': String(MP3.length) } });

/** routes extend the default fake world; xai/gemini/eleven override the TTS handlers. */
export function voiceWorld({ xai, gemini, eleven, routes = [], email } = {}) {
  return router([
    ...routes,
    ['https://api.x.ai/v1/tts', (url, init, call) => xai ? xai(url, init, call) : mp3Response()],
    ['https://generativelanguage.googleapis.com/v1beta/interactions', (url, init, call) => gemini ? gemini(url, init, call)
      : Response.json({ status: 'completed', steps: [{ type: 'model_output', content: [{ type: 'audio', data: WAV.toString('base64'), mime_type: 'audio/wav' }] }] })],
    ['https://api.elevenlabs.io/', (url, init, call) => eleven ? eleven(url, init, call) : mp3Response()]
  ], email ? { email } : undefined);
}
export function voiceRequest(action, body, { method = 'POST', token = 'user-token', headers = {} } = {}) {
  return new Request('https://dua.test/api/voice/' + action, { method, headers: { 'content-type': 'application/json', ...(token ? { authorization: 'Bearer ' + token } : {}), ...headers }, ...(method === 'POST' ? { body: JSON.stringify(body ?? {}) } : {}) });
}
export { SUPA, UID };
