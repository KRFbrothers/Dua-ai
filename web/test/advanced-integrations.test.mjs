import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { advancedConfiguration, handleAdvanced } from '../src/core/advanced-integrations.mjs';

const configured = { ENABLE_GITHUB_CONNECTOR: '1', GITHUB_CONNECTOR_TOKEN: 'PRIVATE-GITHUB-TOKEN', ENABLE_GENERATED_VOICE: '1', ELEVENLABS_API_KEY: 'PRIVATE-AUDIO-KEY', ELEVENLABS_VOICE_ID: 'voice123' };
const request = (path, body, headers = {}) => new Request('https://dua.example' + path, { method: 'POST', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body) });
const options = fetcher => ({ env: configured, fetcher, authorized: true });

test('advanced setup is disabled by default and manifest does not expose credentials', () => {
  const defaults = advancedConfiguration();
  assert.equal(defaults.connectors.github.configured, false);
  assert.equal(defaults.voice.generatedAudio.configured, false);
  const data = JSON.stringify(advancedConfiguration(configured));
  assert.equal(data.includes('PRIVATE'), false);
  assert.equal(data.includes('voice123'), false);
  assert.equal(advancedConfiguration({ ...configured, ELEVENLABS_VOICE_ID: '../user' }).voice.generatedAudio.configured, false);
});

test('unauthorized and foreign-origin integration calls make no provider requests', async () => {
  const fetcher = () => assert.fail('No provider access allowed');
  assert.equal((await handleAdvanced(request('/api/connectors/github', { action: 'list-repositories' }), { env: configured, fetcher })).status, 401);
  assert.equal((await handleAdvanced(request('/api/voice/audio', { text: 'Hi' }, { Origin: 'https://other.example' }), options(fetcher))).status, 403);
});

test('connector accepts only explicit read-only action and never forwards supplied hosts', async () => {
  const fetcher = () => assert.fail('No provider access allowed');
  for (const body of [{ action: 'delete-repository' }, { action: 'list-repositories', url: 'http://127.0.0.1' }, { action: 'list-repositories', token: 'browser-token' }]) {
    assert.equal((await handleAdvanced(request('/api/connectors/github', body), options(fetcher))).status, 400);
  }
  assert.equal((await handleAdvanced(request('/api/connectors/github', { action: 'list-repositories' }), { env: { ...configured, ENABLE_GITHUB_CONNECTOR: '0' }, authorized: true, fetcher })).status, 503);
});

test('GitHub request has fixed host, read-only GET, redirect refusal, and sanitized canonical links', async () => {
  let sent;
  const response = await handleAdvanced(request('/api/connectors/github', { action: 'list-repositories' }), options(async (url, init) => {
    sent = { url, init };
    return Response.json([{ full_name: 'owner/project', private: true, description: 'A project', html_url: 'javascript:alert(1)', token: 'PRIVATE-GITHUB-TOKEN' }]);
  }));
  assert.equal(response.status, 200);
  assert.equal(sent.url, 'https://api.github.com/user/repos?per_page=30&sort=updated&affiliation=owner');
  assert.equal(sent.init.method, 'GET');
  assert.equal(sent.init.redirect, 'error');
  assert.equal(sent.init.headers.Authorization, 'Bearer PRIVATE-GITHUB-TOKEN');
  const result = await response.json();
  assert.equal(result.repositories[0].url, 'https://github.com/owner/project');
  assert.equal(JSON.stringify(result).includes('TOKEN'), false);
  assert.equal(response.headers.get('cache-control'), 'no-store');
});

test('provider errors, excess repositories, and oversized JSON are bounded and never disclose upstream details', async () => {
  for (const make of [() => Response.json({ error: 'PRIVATE-GITHUB-TOKEN' }, { status: 401 }), () => Response.json(Array(31).fill({ full_name: 'owner/project' })), () => new Response('x'.repeat(1024 * 1024 + 1))]) {
    const response = await handleAdvanced(request('/api/connectors/github', { action: 'list-repositories' }), options(async () => make()));
    assert.equal(response.status, 502);
    assert.equal((await response.text()).includes('PRIVATE'), false);
  }
});

test('generated audio requires opt-in, bounded text, and no client-selected provider or voice', async () => {
  const fetcher = () => assert.fail('No provider access allowed');
  assert.equal((await handleAdvanced(request('/api/voice/audio', { text: 'Hi' }), { env: {}, authorized: true, fetcher })).status, 503);
  for (const body of [{ text: 'x'.repeat(2001) }, { text: '' }, { text: 'Hi', voiceId: 'other' }, { text: 'Hi', url: 'https://evil.example' }]) {
    assert.equal((await handleAdvanced(request('/api/voice/audio', body), options(fetcher))).status, 400);
  }
});

test('generated speech sends selected text only and returns non-cacheable MP3 bytes', async () => {
  let sent;
  const response = await handleAdvanced(request('/api/voice/audio', { text: ' Hello! ' }), options(async (url, init) => {
    sent = { url, init };
    return new Response(Uint8Array.of(1, 2, 3), { headers: { 'Content-Type': 'audio/mpeg' } });
  }));
  assert.equal(response.status, 200);
  assert.equal(sent.url, 'https://api.elevenlabs.io/v1/text-to-speech/voice123?output_format=mp3_22050_32');
  assert.equal(sent.init.redirect, 'error');
  assert.deepEqual(JSON.parse(sent.init.body), { text: 'Hello!', model_id: 'eleven_multilingual_v2' });
  assert.equal(response.headers.get('content-type'), 'audio/mpeg');
  assert.equal(response.headers.get('cache-control'), 'no-store');
  assert.deepEqual(new Uint8Array(await response.arrayBuffer()), Uint8Array.of(1, 2, 3));
});

test('invalid, empty, oversized, or failed provider audio is rejected', async () => {
  for (const make of [() => new Response('PRIVATE-AUDIO-KEY', { status: 429 }), () => new Response('PRIVATE-AUDIO-KEY', { headers: { 'Content-Type': 'text/html' } }), () => new Response(new Uint8Array(), { headers: { 'Content-Type': 'audio/mpeg' } }), () => new Response(Uint8Array.of(1), { headers: { 'Content-Type': 'audio/mpeg', 'Content-Length': '8388609' } })]) {
    const response = await handleAdvanced(request('/api/voice/audio', { text: 'Hi' }), options(async () => make()));
    assert.equal(response.status, 502);
    assert.equal((await response.text()).includes('PRIVATE'), false);
  }
});

test('integration request input and methods are validated before provider calls', async () => {
  const settings = options(() => assert.fail('No provider access allowed'));
  assert.equal((await handleAdvanced(new Request('https://dua.example/api/voice/audio'), settings)).status, 405);
  assert.equal((await handleAdvanced(new Request('https://dua.example/api/voice/audio', { method: 'POST', body: '{}' }), settings)).status, 415);
  assert.equal((await handleAdvanced(request('/api/voice/audio', { text: 'x'.repeat(13000) }), settings)).status, 400);
});

const mediaSource = readFileSync(new URL('../dist/media-session.js', import.meta.url), 'utf8');
function fakeStream() {
  const listeners = {};
  const track = { stopped: 0, stop() { this.stopped++; }, addEventListener(name, callback) { listeners[name] = callback; } };
  return { track, ended() { listeners.ended?.(); }, getTracks: () => [track], getVideoTracks: () => [track] };
}
function mediaContext(devices = {}) {
  const events = {}, visibility = {}, document = { visibilityState: 'visible', addEventListener(name, callback) { visibility[name] = callback; }, removeEventListener(name) { delete visibility[name]; } };
  let cancelled = 0;
  const context = { navigator: { userActivation: { isActive: true }, mediaDevices: devices }, document, speechSynthesis: { cancel() { cancelled++; } }, addEventListener(name, callback) { events[name] = callback; }, removeEventListener(name) { delete events[name]; } };
  vm.runInNewContext(mediaSource, context);
  return { context, events, visibility, get cancelled() { return cancelled; } };
}

test('media construction is passive; explicit camera capture is local and video only', async () => {
  let calls = 0, constraints;
  const stream = fakeStream(), fixture = mediaContext({ async getUserMedia(value) { calls++; constraints = value; return stream; } });
  const preview = { play() { return Promise.resolve(); } };
  const session = fixture.context.DuaMediaSession({ preview });
  assert.equal(calls, 0);
  assert.equal(await session.startCamera(), true);
  assert.equal(calls, 1);
  assert.equal(constraints.audio, false);
  assert.equal(preview.srcObject, stream);
  assert.equal(preview.muted, true);
  assert.equal(session.state().status, 'active');
  session.stop();
  assert.equal(stream.track.stopped, 1);
  assert.equal(preview.srcObject, null);
});

test('unsupported capture and denied permission report recoverable errors without stale tracks', async () => {
  const unsupported = mediaContext().context.DuaMediaSession();
  assert.equal(await unsupported.startScreen(), false);
  assert.match(unsupported.state().error, /desktop Chrome or Edge/);
  const denied = mediaContext({ async getUserMedia() { throw Object.assign(Error('private device name'), { name: 'NotAllowedError' }); } }).context.DuaMediaSession();
  assert.equal(await denied.startCamera(), false);
  assert.match(denied.state().error, /Permission was denied/);
  assert.equal(denied.state().error.includes('private'), false);
});

test('Stop during a pending permission prompt releases tracks once permission resolves', async () => {
  let resolve;
  const stream = fakeStream(), fixture = mediaContext({ getUserMedia: () => new Promise(done => { resolve = done; }) });
  const session = fixture.context.DuaMediaSession();
  const pending = session.startCamera();
  session.stop();
  resolve(stream);
  assert.equal(await pending, false);
  assert.equal(stream.track.stopped, 1);
  assert.equal(session.state().status, 'idle');
});

test('screen sharing ended by browser and hidden pages stop capture; interruption cancels speech', async () => {
  const stream = fakeStream(), fixture = mediaContext({ getDisplayMedia: async constraints => { assert.equal(constraints.audio, false); return stream; } });
  let interrupted = 0;
  const session = fixture.context.DuaMediaSession({ onInterrupt() { interrupted++; } });
  await session.startScreen();
  stream.ended();
  assert.equal(stream.track.stopped, 1);
  await session.startScreen();
  fixture.context.document.visibilityState = 'hidden';
  fixture.visibility.visibilitychange();
  assert.equal(stream.track.stopped, 2);
  assert.equal(interrupted, 1);
  assert.equal(fixture.cancelled, 1);
  session.destroy();
  assert.equal(Object.keys(fixture.events).length, 0);
  assert.equal(Object.keys(fixture.visibility).length, 0);
});

test('media capture requires an active user gesture and closes on page navigation', async () => {
  let calls = 0;
  const stream = fakeStream(), fixture = mediaContext({ getUserMedia: async () => { calls++; return stream; } });
  const session = fixture.context.DuaMediaSession();
  fixture.context.navigator.userActivation.isActive = false;
  assert.equal(await session.startCamera(), false);
  assert.equal(calls, 0);
  fixture.context.navigator.userActivation.isActive = true;
  assert.equal(await session.startCamera(), true);
  fixture.events.pagehide();
  assert.equal(stream.track.stopped, 1);
  assert.equal(session.state().status, 'idle');
});

test('switching to unsupported screen capture stops an already active camera', async () => {
  const stream = fakeStream(), fixture = mediaContext({ getUserMedia: async () => stream });
  const preview = { play() { return Promise.resolve(); } };
  const session = fixture.context.DuaMediaSession({ preview });
  assert.equal(await session.startCamera(), true);
  assert.equal(await session.startScreen(), false);
  assert.equal(stream.track.stopped, 1);
  assert.equal(preview.srcObject, null);
  assert.equal(session.state().status, 'error');
});
