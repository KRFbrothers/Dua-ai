import test from 'node:test';
import assert from 'node:assert/strict';
import { handleVoice, voiceConfiguration } from '../src/core/voice.mjs';
import { liveConfiguration, validateSessionRequest, buildInstructions, buildSessionConfig, clientSecretRequest, REALTIME_LANGUAGES, LIMITS, VOICE_PROMPT } from '../src/core/voice-live.mjs';
import { setupConfiguration as setupStatus } from '../src/core/setup.mjs';
import { voiceEnv, voiceRequest, liveWorld, SUPA } from './voice-helpers.mjs';

const SID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

test('live config: on with GROK_API_KEY and the owner gate; env switches and caps', () => {
  const live = liveConfiguration(voiceEnv);
  assert.equal(live.enabled, true);
  assert.equal(live.model, 'grok-voice-latest');
  assert.equal(live.dailyMinutes, 30);
  assert.equal(live.maxSessionMinutes, 10);
  assert.equal(live.voices.length, 28);
  assert.equal(live.languages.find(l => l.id === 'ur').note.includes('instructions'), true);
  assert.equal('hint' in live.languages.find(l => l.id === 'ur'), false);
  assert.equal(liveConfiguration({ ...voiceEnv, DUA_VOICE_REALTIME: '0' }).reason, 'Live voice is turned off on the server (DUA_VOICE_REALTIME=0).');
  assert.match(liveConfiguration({ ...voiceEnv, GROK_API_KEY: '' }).reason, /GROK_API_KEY/);
  assert.equal(liveConfiguration({ ...voiceEnv, DUA_VOICE_DAILY_MINUTES: '0' }).enabled, false);
  assert.equal(liveConfiguration({ ...voiceEnv, DUA_VOICE_DAILY_MINUTES: '90', DUA_VOICE_SESSION_MAX_MINUTES: '500' }).maxSessionMinutes, 120);
  assert.equal(liveConfiguration({ ...voiceEnv, DUA_VOICE_DAILY_MINUTES: 'lots' }).dailyMinutes, 30);
  assert.equal(liveConfiguration({ ...voiceEnv, DUA_VOICE_REALTIME_MODEL: 'xai-secret-looking' }).model, 'grok-voice-latest');
  assert.equal(liveConfiguration({ ...voiceEnv, DUA_VOICE_REALTIME_MODEL: 'grok-voice-think-fast-2.0' }).model, 'grok-voice-think-fast-2.0');
  assert.equal(voiceConfiguration({ ...voiceEnv, DUA_ALLOWED_EMAIL: '' }).live.enabled, false);
  assert.equal(JSON.stringify(voiceConfiguration(voiceEnv)).includes(voiceEnv.GROK_API_KEY), false);
  const setup = setupStatus(voiceEnv);
  assert.match(setup.groups.find(g => g.id === 'voice').limitations[0], /Live voice: Grok grok-voice-latest, up to 30 min a day/);
});

test('session request validation, instructions and session.update payload', () => {
  assert.deepEqual(validateSessionRequest({}), { voice: 'eve', language: 'auto', speed: 1, memory: '', context: [] });
  for (const bad of [null, [], { extra: 1 }, { voice: 'nobody' }, { language: 'xx' }, { speed: 2 }, { speed: '1' }, { memory: 'x'.repeat(LIMITS.memory + 1) },
    { context: [{ role: 'system', text: 'hi' }] }, { context: Array(21).fill({ role: 'user', text: 'a' }) }, { context: [{ role: 'user', text: 'a\u0000' }] }]) {
    assert.throws(() => validateSessionRequest(bad), error => error.status === 400, JSON.stringify(bad));
  }
  const valid = validateSessionRequest({ voice: 'ara', language: 'hi', speed: 1.2, memory: ' Prefers Hindi ', context: [{ role: 'user', text: 'Plan my day' }, { role: 'assistant', text: 'Sure:\n**gym** at 7' }] });
  const instructions = buildInstructions(valid);
  assert.ok(instructions.startsWith(VOICE_PROMPT));
  assert.match(instructions, /Speak Hindi by default/);
  assert.match(instructions, /User-provided preferences \(unverified.*\):\nPrefers Hindi/);
  assert.match(instructions, /User: Plan my day\nDua: Sure: \*\*gym\*\* at 7/);
  const long = buildInstructions({ language: 'auto', memory: '', context: Array.from({ length: 20 }, (_, i) => ({ role: 'user', text: i + ':' + 'x'.repeat(1990) })) });
  assert.ok(long.length <= LIMITS.instructions);
  assert.match(long, /User: 19:/, 'newest turns are kept');
  assert.doesNotMatch(long, /User: 0:/);

  const session = buildSessionConfig(valid);
  assert.equal(session.voice, 'ara');
  assert.deepEqual(session.turn_detection, { type: 'server_vad' });
  assert.deepEqual(session.audio.input, { format: { type: 'audio/pcm', rate: 24000 }, transcription: { model: 'grok-transcribe', language_hint: 'hi' } });
  assert.deepEqual(session.audio.output, { format: { type: 'audio/pcm', rate: 24000 }, speed: 1.2 });
  const urdu = buildSessionConfig(validateSessionRequest({ language: 'ur' }));
  assert.deepEqual(urdu.audio.input.transcription, { model: 'grok-transcribe' }, 'Urdu is not a documented language_hint');
  assert.match(urdu.instructions, /Speak Urdu by default/);
  assert.equal('speed' in urdu.audio.output, false);
  assert.ok(REALTIME_LANGUAGES.some(l => l.id === 'es-MX') && !REALTIME_LANGUAGES.some(l => l.id === 'es'), 'Spanish needs a region');

  const mint = clientSecretRequest('xai-test-key', 660);
  assert.equal(mint.url, 'https://api.x.ai/v1/realtime/client_secrets');
  assert.equal(mint.init.headers.Authorization, 'Bearer xai-test-key');
  assert.deepEqual(JSON.parse(mint.init.body), { expires_after: { seconds: 660 } });
});

test('POST /api/voice/session: owner-only token mint with a durable reservation; the API key never leaves the server', async () => {
  const fetcher = liveWorld();
  assert.equal((await handleVoice(voiceRequest('session', {}, { token: null }), { env: voiceEnv, fetcher })).status, 401);
  assert.equal((await handleVoice(voiceRequest('session', {}, { token: 'other-user' }), { env: voiceEnv, fetcher: liveWorld({ email: 'someone@else.com' }) })).status, 403);
  assert.equal(fetcher.find('https://api.x.ai/').length, 0, 'no token is minted before the owner is verified');

  const response = await handleVoice(voiceRequest('session', { voice: 'leo', language: 'en', context: [{ role: 'user', text: 'Hi' }] }), { env: voiceEnv, fetcher });
  assert.equal(response.status, 200);
  assert.equal(response.headers.get('cache-control'), 'no-store');
  const body = await response.json();
  assert.match(body.sessionId, SID);
  assert.equal(body.token, 'xai-realtime-client-secret-test');
  assert.equal(body.url, 'wss://api.x.ai/v1/realtime?model=grok-voice-latest');
  assert.equal(body.maxSeconds, 600);
  assert.equal(body.session.voice, 'leo');
  assert.deepEqual(body.usage, { usedSeconds: 0, activeSeconds: 600, limitSeconds: 1800, resetAt: 1791590400 });
  assert.equal(JSON.stringify(body).includes(voiceEnv.GROK_API_KEY), false);
  const start = fetcher.find(SUPA + '/rest/v1/rpc/dua_start_voice_session')[0];
  assert.equal(start.headers.get('authorization'), 'Bearer user-token', 'RPC runs as the owner');
  assert.deepEqual({ ...start.json, session_id: 'x' }, { session_id: 'x', reserve_seconds: 600, daily_limit_seconds: 1800, min_seconds: 30 });
  const mint = fetcher.find('https://api.x.ai/v1/realtime/client_secrets')[0];
  assert.equal(mint.headers.get('authorization'), 'Bearer ' + voiceEnv.GROK_API_KEY);
  assert.deepEqual(mint.json, { expires_after: { seconds: 660 } });
});

test('daily minute cap: reservations hold minutes, ending charges actual time once, caps deny with Retry-After', async () => {
  const env = { ...voiceEnv, DUA_VOICE_DAILY_MINUTES: '15', DUA_VOICE_SESSION_MAX_MINUTES: '10' };
  const fetcher = liveWorld();
  let clock = 1_000_000; fetcher.state.now = () => clock;
  const first = await (await handleVoice(voiceRequest('session', {}), { env, fetcher })).json();
  assert.equal(first.maxSeconds, 600);
  const second = await (await handleVoice(voiceRequest('session', {}), { env, fetcher })).json();
  assert.equal(second.maxSeconds, 300, 'only the remaining minutes are granted');
  const denied = await handleVoice(voiceRequest('session', {}), { env, fetcher });
  assert.equal(denied.status, 429);
  assert.ok(Number(denied.headers.get('retry-after')) >= 1);
  const deniedBody = await denied.json();
  assert.equal(deniedBody.code, 'cap');
  assert.match(deniedBody.error, /still holding/);
  clock += 95_500;
  const ended = await handleVoice(voiceRequest('session-end', { sessionId: first.sessionId }), { env, fetcher });
  assert.deepEqual(await ended.json(), { chargedSeconds: 96, usedSeconds: 96, limitSeconds: 900 });
  const again = await (await handleVoice(voiceRequest('session-end', { sessionId: first.sessionId }), { env, fetcher })).json();
  assert.equal(again.usedSeconds, 96, 'ending twice charges once');
  const usage = await (await handleVoice(voiceRequest('usage', undefined, { method: 'GET' }), { env, fetcher })).json();
  assert.deepEqual(usage, { usedSeconds: 96, activeSeconds: 300, limitSeconds: 900, resetAt: 1791590400 });
  assert.equal((await handleVoice(voiceRequest('session-end', { sessionId: 'not-a-uuid' }), { env, fetcher })).status, 400);
  assert.equal((await handleVoice(voiceRequest('session-end', { sessionId: '00000000-0000-4000-8000-000000000000' }), { env, fetcher })).status, 404);
  assert.equal((await handleVoice(voiceRequest('session-end', { sessionId: first.sessionId, extra: 1 }), { env, fetcher })).status, 400);

  const spent = await handleVoice(voiceRequest('session', {}), { env: voiceEnv, fetcher: liveWorld({ usedSeconds: 1790 }) });
  assert.equal(spent.status, 429);
  assert.match((await spent.json()).error, /Daily live voice limit reached \(30 min\)/);
});

test('failed token mint frees the reservation; setup and switches are explained', async () => {
  for (const [status, expected] of [[401, 503], [403, 503], [429, 429], [500, 502]]) {
    const fetcher = liveWorld({ secret: () => Response.json({ error: { message: 'secret detail xai-abc' } }, { status }) });
    fetcher.state.now = () => 5000;
    const response = await handleVoice(voiceRequest('session', {}), { env: voiceEnv, fetcher });
    assert.equal(response.status, expected);
    const text = await response.text();
    assert.equal(text.includes('secret detail'), false);
    assert.equal(fetcher.find(SUPA + '/rest/v1/rpc/dua_end_voice_session').length, 1, 'reservation released');
    assert.equal(fetcher.state.usedSeconds, 0);
  }
  const malformed = liveWorld({ secret: () => Response.json({ value: '', expires_at: 'soon' }) });
  assert.equal((await handleVoice(voiceRequest('session', {}), { env: voiceEnv, fetcher: malformed })).status, 502);

  const missing = await handleVoice(voiceRequest('session', {}), { env: voiceEnv, fetcher: liveWorld({ rpcMissing: true }) });
  assert.equal(missing.status, 503);
  const missingBody = await missing.json();
  assert.equal(missingBody.code, 'setup');
  assert.match(missingBody.error, /202610100001_dua_voice\.sql/);

  const off = await handleVoice(voiceRequest('session', {}), { env: { ...voiceEnv, DUA_VOICE_REALTIME: '0' }, fetcher: liveWorld() });
  assert.equal(off.status, 503);
  assert.equal((await off.json()).code, 'live_off');
  assert.equal((await handleVoice(voiceRequest('session', { voice: 'nobody' }), { env: voiceEnv, fetcher: liveWorld() })).status, 400);
  assert.equal((await handleVoice(voiceRequest('usage', {}), { env: voiceEnv, fetcher: liveWorld() })).status, 405);
  assert.equal((await handleVoice(voiceRequest('constructor', {}), { env: voiceEnv, fetcher: liveWorld() })).status, 404);
  assert.equal((await handleVoice(voiceRequest('session', {}, { headers: { 'content-type': 'text/plain' } }), { env: voiceEnv, fetcher: liveWorld() })).status, 415);
});
