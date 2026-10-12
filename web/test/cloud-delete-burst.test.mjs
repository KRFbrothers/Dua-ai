import test from 'node:test';
import assert from 'node:assert/strict';
import { createVercelHandler } from '../src/server/vercel-handler.mjs';
import { createBurstGuard } from '../src/server/vercel-burst.mjs';

const owner = '11111111-1111-4111-8111-111111111111';
const env = {
  SUPABASE_URL: 'https://project.supabase.co',
  SUPABASE_ANON_KEY: 'public-anon-key',
  DUA_REQUIRE_LOGIN: '1',
  DUA_ALLOWED_EMAIL: 'owner@example.com'
};
const snapshot = { format: 'dua-cloud-snapshot', version: 1, chats: [], memory: [], deletedChatIds: ['deleted'] };
const req = (method = 'DELETE', ip = '203.0.113.10') => new Request('https://dua.test/api/cloud' + (method === 'DELETE' ? '/chat' : ''), {
  method,
  headers: { 'content-type': 'application/json', 'x-dua-native': '1', authorization: 'Bearer fake-access', 'x-forwarded-for': ip },
  ...(method === 'DELETE' ? { body: JSON.stringify({ chatId: 'deleted', expectedAccountId: owner, expectedRevision: 3, confirmation: 'DELETE CHAT' }) } : {})
});
function fixture() {
  let time = 1000;
  const calls = [];
  const fetcher = async (url, init) => {
    calls.push({ url, init });
    if (url === env.SUPABASE_URL + '/auth/v1/user') return Response.json({ id: owner, email: env.DUA_ALLOWED_EMAIL });
    if (url.startsWith(env.SUPABASE_URL + '/rest/v1/dua_cloud_snapshots?')) return Response.json([{ revision: 3, payload: snapshot }]);
    throw new Error('Unexpected fetch: ' + url);
  };
  const handler = createVercelHandler({ env, fetcher, guard: createBurstGuard(() => time) });
  return { handler, calls, advance: ms => { time += ms; } };
}
async function deleted(handler) {
  const response = await handler.fetch(req());
  assert.equal(response.status, 200);
  assert.equal((await response.json()).deleted, true);
}

test('cloud chat deletion stops before auth/storage at the account burst limit and recovers after expiry', async () => {
  const { handler, calls, advance } = fixture();
  for (let i = 0; i < 20; i++) await deleted(handler);
  assert.equal(calls.length, 40, 'idempotent deletes authenticate and read, without writing');
  advance(1500);
  const blocked = await handler.fetch(req());
  assert.equal(blocked.status, 429);
  assert.equal(blocked.headers.get('Retry-After'), '59');
  assert.equal(blocked.headers.get('Cache-Control'), 'no-store');
  assert.equal((await blocked.json()).retryAfterSeconds, 59);
  assert.equal(calls.length, 40, 'blocked calls must not reach Supabase');
  advance(58_500);
  await deleted(handler);
  assert.equal(calls.length, 42);
});

test('cloud reads and chat deletes share the account quota while other IPs keep their own window', async () => {
  const { handler, calls } = fixture();
  for (let i = 0; i < 19; i++) {
    const response = await handler.fetch(req('GET'));
    assert.equal(response.status, 200);
    assert.equal((await response.json()).revision, 3);
  }
  await deleted(handler);
  assert.equal((await handler.fetch(req())).status, 429);
  assert.equal(calls.length, 40);
  assert.equal((await handler.fetch(req('DELETE', '203.0.113.11'))).status, 200);
  assert.equal(calls.length, 42);
});
