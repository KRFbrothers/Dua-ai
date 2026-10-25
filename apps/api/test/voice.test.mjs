import test from 'node:test';
import assert from 'node:assert/strict';
import { ttsConfiguration, voiceConfiguration, legacyAudioProvider, validateAudioRequest, buildTtsRequest } from '../src/core/voice.mjs';
import { xaiTtsRequest, geminiTtsRequest, XAI_VOICES, GEMINI_VOICES, XAI_LANGUAGES, ttsFailure } from '../src/core/voice-tts.mjs';
import { advancedConfiguration } from '../src/core/advanced-integrations.mjs';
import { handleService } from '../src/server/service-handler.mjs';
import { voiceEnv, voiceWorld, voiceRequest, MP3, WAV } from './voice-helpers.mjs';

const eleven = { ENABLE_GENERATED_VOICE: '1', ELEVENLABS_API_KEY: 'el-key', ELEVENLABS_VOICE_ID: 'voice_123' };
const withGemini = { ...voiceEnv, GEMINI_API_KEY: 'test-gemini-key' };

test('TTS config: providers follow keys, owner and env switches; catalogs match the documented voices', () => {
  const full = ttsConfiguration({ ...withGemini, ...eleven });
  assert.equal(full.enabled, true);
  assert.deepEqual(full.providers.map(p => p.id), ['xai', 'gemini', 'elevenlabs']);
  assert.equal(full.defaultProvider, 'xai');
  const xai = full.providers[0];
  assert.equal(xai.voices.length, 28);
  assert.equal(xai.defaultVoice, 'eve');
  assert.ok(['eve', 'ara', 'leo', 'rex', 'sal', 'naksh'].every(id => xai.voices.some(v => v.id === id)));
  assert.deepEqual(xai.languages.slice(0, 4).map(l => l.id), ['auto', 'en', 'hi', 'ur']);
  assert.equal('api' in xai.languages[0], false, 'internal mapping stays on the server');
  assert.deepEqual(xai.speed, { min: 0.7, max: 1.5, default: 1 });
  assert.equal(full.providers[1].voices.length, 30);
  assert.equal(full.providers[1].model, 'gemini-3.8-flash-tts');
  assert.equal(full.providers[1].format, 'audio/wav');
  assert.equal(full.providers[2].speed, null);
  assert.equal(ttsConfiguration({ ...withGemini, DUA_VOICE_TTS_PROVIDER: 'gemini' }).defaultProvider, 'gemini');
  assert.equal(ttsConfiguration({ ...withGemini, DUA_VOICE_GEMINI_TTS_MODEL: 'gemini-3.8-flash-lite-tts' }).providers[1].model, 'gemini-3.8-flash-lite-tts');
  const off = ttsConfiguration({ ...withGemini, DUA_VOICE_TTS: '0' });
  assert.equal(off.enabled, false);
  assert.match(off.reason, /DUA_VOICE_TTS=0/);
  assert.deepEqual(ttsConfiguration({ ...withGemini, DUA_VOICE_TTS: '0', ...eleven }).providers.map(p => p.id), ['elevenlabs']);
  assert.match(ttsConfiguration({ ...voiceEnv, DUA_ALLOWED_EMAIL: '' }).reason, /DUA_ALLOWED_EMAIL/);
  assert.match(ttsConfiguration({ ...voiceEnv, SUPABASE_URL: '' }).reason, /Supabase/);
  assert.match(ttsConfiguration({ ...voiceEnv, GROK_API_KEY: '', GEMINI_API_KEY: '' }).reason, /GROK_API_KEY or GEMINI_API_KEY/);
  assert.doesNotMatch(JSON.stringify(voiceConfiguration({ ...withGemini, ...eleven })), /test-grok-key|test-gemini-key|el-key|voice_123/);
  assert.equal(XAI_VOICES.length, 28);
  assert.equal(GEMINI_VOICES.length, 30);
  assert.equal(XAI_LANGUAGES.find(l => l.id === 'ur').api, 'auto');
});

test('old apps keep one generated-audio switch: ElevenLabs first, else Grok (both MP3)', () => {
  assert.equal(legacyAudioProvider({ ...voiceEnv, ...eleven }), 'elevenlabs');
  assert.equal(legacyAudioProvider(voiceEnv), 'xai');
  assert.equal(legacyAudioProvider({ ...voiceEnv, GROK_API_KEY: '', GEMINI_API_KEY: 'g' }), null, 'Gemini is WAV, which old apps reject');
  assert.equal(legacyAudioProvider({ ...voiceEnv, DUA_ALLOWED_EMAIL: '' }), null);
  assert.equal(advancedConfiguration(voiceEnv).voice.generatedAudio.configured, true);
  assert.equal(advancedConfiguration({ ...voiceEnv, GROK_API_KEY: '' }).voice.generatedAudio.configured, false);
});

test('request validation: provider, voice, language, speed and per-provider length', () => {
  const config = ttsConfiguration({ ...withGemini, ...eleven });
  assert.deepEqual(validateAudioRequest({ text: ' Hello ', provider: 'xai', voice: 'naksh', language: 'hi', speed: 1.2 }, config),
    { provider: 'xai', model: undefined, text: 'Hello', voice: 'naksh', language: 'hi', speed: 1.2 });
  assert.equal(validateAudioRequest({ text: 'Hi' }, config, 'elevenlabs').provider, 'elevenlabs', 'text-only body is a legacy request');
  assert.equal(validateAudioRequest({ text: 'Hi', voice: 'eve' }, config, 'elevenlabs').provider, 'xai', 'new apps without provider get the default');
  assert.equal(validateAudioRequest({ text: 'Hi', provider: 'elevenlabs', speed: 1.4 }, config).speed, 1, 'speed ignored where unsupported');
  const bad = (body, pattern, status = 400) => assert.throws(() => validateAudioRequest(body, config), error => pattern.test(error.message) && error.status === status);
  bad({ text: '' , provider: 'xai' }, /up to 2000/);
  bad({ text: 'x'.repeat(2001), provider: 'xai' }, /up to 2000/);
  bad({ text: 'x'.repeat(1001), provider: 'gemini' }, /up to 1000/);
  bad({ text: 'Hi', provider: 'xai', voice: 'Kore' }, /listed voices/);
  bad({ text: 'Hi', provider: 'gemini', language: 'hi' }, /listed languages/);
  bad({ text: 'Hi', provider: 'xai', speed: 2 }, /speed/);
  bad({ text: 'Hi', provider: 'xai', speed: '1' }, /speed/);
  bad({ text: 'Hi', provider: 'openai' }, /not configured/, 503);
  bad({ text: 'Hi', extra: 1 }, /Invalid voice request/);
  bad({ text: 'a\u0000b', provider: 'xai' }, /up to 2000/);
});

test('TTS request building matches the documented xAI and Gemini shapes', () => {
  const x = xaiTtsRequest({ key: 'k', text: 'Namaste', voice: 'naksh', language: 'hi', speed: 1.25 });
  assert.equal(x.url, 'https://api.x.ai/v1/tts');
  assert.equal(x.init.headers.Authorization, 'Bearer k');
  assert.deepEqual(JSON.parse(x.init.body), { text: 'Namaste', voice_id: 'naksh', language: 'hi', output_format: { codec: 'mp3', sample_rate: 24000, bit_rate: 64000 }, speed: 1.25 });
  assert.equal('speed' in JSON.parse(xaiTtsRequest({ key: 'k', text: 'a' }).init.body), false);
  const g = geminiTtsRequest({ key: 'g', text: 'Hello', voice: 'Puck', speed: 0.8 });
  assert.equal(g.url, 'https://generativelanguage.googleapis.com/v1beta/interactions');
  assert.equal(g.init.headers['x-goog-api-key'], 'g');
  assert.deepEqual(JSON.parse(g.init.body), {
    model: 'gemini-3.8-flash-tts', input: [{ type: 'user_input', content: [{ type: 'text', text: 'Hello', annotations: [{ type: 'speech_metadata', style: 'speaking slowly' }] }] }],
    response_format: { type: 'audio', mime_type: 'audio/wav' }, generation_config: { speech_config: [{ voice: 'Puck' }] }, store: false });
  assert.equal(JSON.parse(geminiTtsRequest({ key: 'g', text: 'a' }).init.body).input[0].content[0].annotations, undefined);
  // Urdu is not on xAI's list: the server sends "auto" so the model detects it from the text.
  const config = ttsConfiguration(voiceEnv);
  const urdu = buildTtsRequest(validateAudioRequest({ text: 'آپ کیسے ہیں', provider: 'xai', language: 'ur' }, config), voiceEnv);
  assert.equal(JSON.parse(urdu.init.body).language, 'auto');
  assert.equal(ttsFailure('xai', 401).status, 503);
  assert.match(ttsFailure('gemini', 429).message, /rate or credit/);
});

test('POST /api/voice/audio: owner-only, returns provider audio with its type, never leaks keys', async () => {
  const fetcher = voiceWorld();
  const ok = await handleService(voiceRequest('audio', { text: 'Hello Fareed', provider: 'xai', voice: 'ara', language: 'en', speed: 0.9 }), { env: voiceEnv, fetcher });
  assert.equal(ok.status, 200);
  assert.equal(ok.headers.get('content-type'), 'audio/mpeg');
  assert.equal(ok.headers.get('x-dua-voice-provider'), 'xai');
  assert.deepEqual(Buffer.from(await ok.arrayBuffer()), MP3);
  const tts = fetcher.find('https://api.x.ai/v1/tts')[0];
  assert.equal(tts.headers.get('authorization'), 'Bearer test-grok-key');
  assert.deepEqual(tts.json, { text: 'Hello Fareed', voice_id: 'ara', language: 'en', output_format: { codec: 'mp3', sample_rate: 24000, bit_rate: 64000 }, speed: 0.9 });

  const gem = await handleService(voiceRequest('audio', { text: 'Hello', provider: 'gemini', voice: 'Sulafat' }), { env: withGemini, fetcher: voiceWorld() });
  assert.equal(gem.headers.get('content-type'), 'audio/wav');
  assert.deepEqual(Buffer.from(await gem.arrayBuffer()), WAV);

  const legacy = voiceWorld();
  const old = await handleService(voiceRequest('audio', { text: 'Hi' }), { env: { ...voiceEnv, ...eleven }, fetcher: legacy });
  assert.equal(old.headers.get('content-type'), 'audio/mpeg');
  assert.equal(legacy.find('https://api.elevenlabs.io/v1/text-to-speech/voice_123')[0].headers.get('xi-api-key'), 'el-key');

  const anon = await handleService(voiceRequest('audio', { text: 'Hi' }, { token: null }), { env: voiceEnv, fetcher: voiceWorld() });
  assert.equal(anon.status, 401);
  const stranger = await handleService(voiceRequest('audio', { text: 'Hi' }), { env: voiceEnv, fetcher: voiceWorld({ email: 'someone@else.com' }) });
  assert.equal(stranger.status, 403);
  const noOwner = await handleService(voiceRequest('audio', { text: 'Hi' }), { env: { ...voiceEnv, DUA_ALLOWED_EMAIL: '' }, fetcher: voiceWorld() });
  assert.equal(noOwner.status, 503);
  const notJson = await handleService(voiceRequest('audio', { text: 'Hi' }, { headers: { 'content-type': 'text/plain' } }), { env: voiceEnv, fetcher: voiceWorld() });
  assert.equal(notJson.status, 415);
  assert.equal((await handleService(voiceRequest('audio', null, { method: 'GET' }), { env: voiceEnv, fetcher: voiceWorld() })).status, 405);
  assert.equal((await handleService(voiceRequest('nope', {}), { env: voiceEnv, fetcher: voiceWorld() })).status, 404);
});

test('provider failures map to safe messages; bad audio is rejected', async () => {
  const denied = await handleService(voiceRequest('audio', { text: 'Hi', provider: 'xai' }), { env: voiceEnv, fetcher: voiceWorld({ xai: () => Response.json({ error: 'invalid key xai-123' }, { status: 401 }) }) });
  assert.equal(denied.status, 503);
  const body = await denied.json();
  assert.match(body.error, /GROK_API_KEY/);
  assert.doesNotMatch(JSON.stringify(body), /xai-123/);
  const html = await handleService(voiceRequest('audio', { text: 'Hi', provider: 'xai' }), { env: voiceEnv, fetcher: voiceWorld({ xai: () => new Response('<html>', { headers: { 'content-type': 'text/html' } }) }) });
  assert.equal(html.status, 502);
  const empty = await handleService(voiceRequest('audio', { text: 'Hi', provider: 'gemini' }), { env: withGemini, fetcher: voiceWorld({ gemini: () => Response.json({ steps: [{ type: 'model_output', content: [{ type: 'text', text: 'no' }] }] }) }) });
  assert.equal(empty.status, 502);
  assert.match((await empty.json()).error, /no audio/);
  const limited = await handleService(voiceRequest('audio', { text: 'Hi', provider: 'xai' }), { env: voiceEnv, fetcher: voiceWorld({ xai: () => new Response('', { status: 429 }) }) });
  assert.equal(limited.status, 429);
});

test('/api/config advertises voice for new apps', async () => {
  const data = await (await handleService(new Request('https://dua.test/api/config'), { env: withGemini, fetcher: voiceWorld() })).json();
  assert.equal(data.voice.version, 1);
  assert.deepEqual(data.voice.tts.providers.map(p => p.id), ['xai', 'gemini']);
  assert.ok(data.imagine && data.files, 'existing fields unchanged');
  const setup = await (await handleService(new Request('https://dua.test/api/setup'), { env: withGemini })).json();
  assert.match(setup.groups.find(g => g.id === 'voice').limitations[0], /Grok \(28 voices\), Gemini \(30 voices\)/);
});
