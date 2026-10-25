import test from 'node:test';
import assert from 'node:assert/strict';
import { killSwitches, maintenanceMessage, switchResponse, DEFAULT_MAINTENANCE_MESSAGE, PAUSED } from '../src/core/kill-switches.mjs';
import { handleChat } from '../src/core/providers.mjs';
import { handleService } from '../src/server/service-handler.mjs';
import { env } from './helpers.mjs';

const config = async extra => (await handleChat(new Request('https://dua.test/api/config'), { env: { ...env, ...extra } })).json();

test('all switches default to off and /api/config publishes them', async () => {
  assert.deepEqual(killSwitches({}), { agent: false, imagine: false, voice: false, maintenance: { enabled: false } });
  assert.deepEqual((await config({})).switches, { agent: false, imagine: false, voice: false, maintenance: { enabled: false } });
  assert.deepEqual(killSwitches({ DUA_DISABLE_AGENT: '0', DUA_MAINTENANCE: 'false', DUA_DISABLE_VOICE: '' }).agent, false);
});

test('switches turn on with 1/true and maintenance carries a cleaned message', () => {
  const s = killSwitches({ DUA_DISABLE_AGENT: '1', DUA_DISABLE_IMAGINE: 'true', DUA_DISABLE_VOICE: 'TRUE', DUA_MAINTENANCE: '1', DUA_MAINTENANCE_MESSAGE: ' Back\nat 6 pm ' });
  assert.deepEqual(s, { agent: true, imagine: true, voice: true, maintenance: { enabled: true, message: 'Back at 6 pm' } });
  assert.equal(maintenanceMessage({}), DEFAULT_MAINTENANCE_MESSAGE);
  assert.equal(maintenanceMessage({ DUA_MAINTENANCE_MESSAGE: 'x'.repeat(500) }).length, 300);
});

test('paused imagine and voice are reported as off to older apps', async () => {
  const body = await config({ DUA_DISABLE_IMAGINE: '1', DUA_DISABLE_VOICE: '1' });
  assert.equal(body.imagine.enabled, false); assert.equal(body.imagine.image.reason, PAUSED.imagine);
  assert.equal(body.voice.tts.enabled, false); assert.deepEqual(body.voice.tts.providers, []);
  assert.equal(body.voice.live.enabled, false); assert.equal(body.voice.live.reason, PAUSED.voice);
});

test('maintenance refuses chat, imagine and voice but keeps config and account routes', async () => {
  const on = { ...env, DUA_MAINTENANCE: '1', DUA_MAINTENANCE_MESSAGE: 'Back soon' };
  for (const path of ['/api/chat', '/api/search', '/api/imagine/image', '/api/voice/audio', '/api/build/pr']) {
    const response = await handleService(new Request(`https://dua.test${path}`, { method: 'POST', body: '{}' }), { env: on });
    assert.equal(response.status, 503, path);
    assert.deepEqual(await response.json(), { error: 'Back soon', maintenance: true });
  }
  const cfg = await handleService(new Request('https://dua.test/api/config'), { env: on });
  assert.equal(cfg.status, 200);
  assert.equal((await cfg.json()).switches.maintenance.message, 'Back soon');
  assert.equal(switchResponse('/api/account/session', on), null);
  assert.equal(switchResponse('/api/cloud', on), null);
});

test('imagine and voice switches refuse only their own routes', async () => {
  const imagine = switchResponse('/api/imagine/video', { DUA_DISABLE_IMAGINE: '1' });
  assert.equal(imagine.status, 503); assert.equal((await imagine.json()).error, PAUSED.imagine);
  assert.equal(switchResponse('/api/chat', { DUA_DISABLE_IMAGINE: '1' }), null);
  const voice = switchResponse('/api/voice/live/session', { DUA_DISABLE_VOICE: '1' });
  assert.equal(voice.status, 503);
  assert.equal(switchResponse('/api/imagine/image', {}), null);
});
