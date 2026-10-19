import test from 'node:test';
import assert from 'node:assert/strict';
import { handleService } from '../src/server/service-handler.mjs';
import { ownerSetupProblem, protectedPath, startupAccessWarning, openAccessAllowed, missingOwnerSettings } from '../src/core/access-setup.mjs';
import { mockFetch, env, chatRequest } from './helpers.mjs';

const owner = { DUA_ALLOWED_EMAIL: 'owner@example.com', SUPABASE_URL: 'https://project.supabase.co', SUPABASE_ANON_KEY: 'public-anon-secretish-value' };
const post = (path, body = {}) => new Request('https://dua.test' + path, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
const chat = () => chatRequest({ model: 'Gemini', messages: [{ role: 'user', text: 'Hi' }], stream: false });

test('protected routes fail closed with 503 when any owner-lock setting is missing', async () => {
  for (const missing of ['DUA_ALLOWED_EMAIL', 'SUPABASE_URL', 'SUPABASE_ANON_KEY']) {
    const settings = { ...env, ...owner, [missing]: '' };
    for (const request of [chat(), post('/api/search', { query: 'x' }), post('/api/connectors/github'), post('/api/build/pr'), post('/api/cloud', {}),
      post('/api/account/google-token', {}), post('/api/files/sign', {}), post('/api/imagine/image', {}), post('/api/voice/audio', { text: 'hi' })]) {
      const fetcher = mockFetch();
      const response = await handleService(request, { env: settings, fetcher, logWriter: () => {} });
      assert.equal(response.status, 503, `${request.url} without ${missing}`);
      assert.equal(response.headers.get('cache-control'), 'no-store');
      const data = await response.json();
      assert.equal(data.code, 'owner_setup_required');
      assert.match(data.error, new RegExp('missing: .*' + missing));
      assert.doesNotMatch(data.error, /owner@example\.com|public-anon-secretish-value|project\.supabase\.co/, 'never echoes values');
      assert.equal(fetcher.calls.length, 0, 'no provider, Supabase or GitHub call is made');
    }
  }
});

test('setup, config, session, features and logout stay reachable while locked', async () => {
  const settings = { ...env };
  for (const [path, method] of [['/api/setup', 'GET'], ['/api/config', 'GET'], ['/api/account/session', 'GET'], ['/api/account/features', 'GET']]) {
    const response = await handleService(new Request('https://dua.test' + path, { method }), { env: settings, fetcher: mockFetch(), logWriter: () => {} });
    // Not blocked by the owner gate (features may still report its own "Supabase not configured").
    const data = await response.json();
    assert.notEqual(data.code, 'owner_setup_required', path);
    if (path !== '/api/account/features') assert.equal(response.status, 200, path);
  }
  assert.equal(protectedPath('/api/account/logout'), false);
  assert.equal(protectedPath('/api/account/delete'), true);
  assert.equal(protectedPath('/'), false);
});

test('a complete owner setup passes the gate to the normal login checks', async () => {
  assert.equal(ownerSetupProblem(owner), null);
  // Reaches guardChatAccess, which asks Supabase who is signed in (no session -> 401, not 503).
  const fetcher = mockFetch(Response.json({ message: 'no user' }, { status: 401 }));
  const response = await handleService(chatRequest({ model: 'Gemini', messages: [{ role: 'user', text: 'Hi' }] }, { headers: { authorization: 'Bearer abc' } }), { env: { ...env, ...owner }, fetcher, logWriter: () => {} });
  assert.equal(response.status, 401);
});

test('DUA_ALLOW_OPEN_ACCESS=1 opens local development only, never Cloud Run or production', () => {
  assert.equal(openAccessAllowed({ DUA_ALLOW_OPEN_ACCESS: '1' }), true);
  assert.equal(ownerSetupProblem({ DUA_ALLOW_OPEN_ACCESS: 'true' }), null);
  for (const hostedEnv of [{ K_SERVICE: 'dua-api' }, { NODE_ENV: 'production' }]) {
    assert.equal(openAccessAllowed({ ...hostedEnv, DUA_ALLOW_OPEN_ACCESS: '1' }), false);
    assert.match(ownerSetupProblem({ ...hostedEnv, DUA_ALLOW_OPEN_ACCESS: '1' }), /locked/);
  }
  assert.match(ownerSetupProblem({ DUA_ALLOW_OPEN_ACCESS: '0' }), /locked/);
  assert.deepEqual(missingOwnerSettings({ DUA_ALLOWED_EMAIL: ' ', SUPABASE_URL: 'x', SUPABASE_ANON_KEY: 'y' }), ['DUA_ALLOWED_EMAIL']);
});

test('open-access opt-in on Cloud Run is ignored and a chat still answers 503', async () => {
  const response = await handleService(chat(), { env: { ...env, K_SERVICE: 'dua-api', DUA_ALLOW_OPEN_ACCESS: '1' }, fetcher: mockFetch(), logWriter: () => {} });
  assert.equal(response.status, 503);
});

test('startup warning names the missing settings, never their values', () => {
  assert.equal(startupAccessWarning(owner), null);
  assert.match(startupAccessWarning({ SUPABASE_URL: 'https://p.supabase.co' }), /protected routes will answer 503.*missing: DUA_ALLOWED_EMAIL, SUPABASE_ANON_KEY/);
  assert.doesNotMatch(startupAccessWarning({ SUPABASE_URL: 'https://p.supabase.co' }), /p\.supabase\.co/);
  assert.match(startupAccessWarning({ DUA_ALLOW_OPEN_ACCESS: '1' }), /local open mode/);
  assert.match(startupAccessWarning({ K_SERVICE: 'dua-api', DUA_ALLOW_OPEN_ACCESS: '1' }), /ignored on hosted runs.*locked/);
  assert.match(startupAccessWarning({ K_SERVICE: 'dua-api', DUA_ALLOW_OPEN_ACCESS: '1', ...owner }), /ignored on hosted runs\.$/);
});
