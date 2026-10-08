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

/**
 * Live voice fakes: the Phase 4 RPCs (a small in-memory model of the SQL) and xAI client secrets.
 * Options: used/limit state, rpcMissing, secret (handler override). Returns the router with `.state`.
 */
export function liveWorld({ usedSeconds = 0, rpcMissing = false, secret, email } = {}) {
  const state = { usedSeconds, sessions: new Map(), now: () => Date.now() };
  const missing = () => Response.json({ code: 'PGRST202', message: 'Could not find the function' }, { status: 404 });
  const resetAt = 1791590400;
  const fetcher = voiceWorld({ email, routes: [
    [SUPA + '/rest/v1/rpc/dua_start_voice_session', (url, init, call) => {
      if (rpcMissing) return missing();
      const { session_id, reserve_seconds, daily_limit_seconds, min_seconds } = call.json;
      const active = [...state.sessions.values()].filter(s => !s.ended).reduce((sum, s) => sum + s.reserved, 0);
      const remaining = Math.max(0, daily_limit_seconds - state.usedSeconds - active);
      if (remaining < min_seconds) return Response.json({ allowed: false, usedSeconds: state.usedSeconds, activeSeconds: active, limitSeconds: daily_limit_seconds, resetAt });
      const granted = Math.min(reserve_seconds, remaining);
      state.sessions.set(session_id, { reserved: granted, started: state.now(), ended: false });
      return Response.json({ allowed: true, grantedSeconds: granted, usedSeconds: state.usedSeconds, activeSeconds: active + granted, limitSeconds: daily_limit_seconds, resetAt });
    }],
    [SUPA + '/rest/v1/rpc/dua_end_voice_session', (url, init, call) => {
      if (rpcMissing) return missing();
      const session = state.sessions.get(call.json.session_id);
      if (!session) return Response.json({ found: false });
      if (!session.ended) { session.charged = Math.min(session.reserved, Math.ceil((state.now() - session.started) / 1000)); session.ended = true; state.usedSeconds += session.charged; }
      return Response.json({ found: true, chargedSeconds: session.charged, usedSeconds: state.usedSeconds });
    }],
    [SUPA + '/rest/v1/rpc/dua_read_voice_usage', () => rpcMissing ? missing()
      : Response.json({ usedSeconds: state.usedSeconds, activeSeconds: [...state.sessions.values()].filter(s => !s.ended).reduce((sum, s) => sum + s.reserved, 0), resetAt })],
    ['https://api.x.ai/v1/realtime/client_secrets', (url, init, call) => secret ? secret(url, init, call)
      : Response.json({ value: 'xai-realtime-client-secret-test', expires_at: 1791500000 })],
  ] });
  fetcher.state = state;
  return fetcher;
}
