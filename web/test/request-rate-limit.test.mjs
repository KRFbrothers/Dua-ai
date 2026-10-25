import test from 'node:test';
import assert from 'node:assert/strict';
import { RequestRateLimiter } from '../src/core/request-rate-limit.mjs';
import { guardChatAccess } from '../src/core/access-policy.mjs';

test('burst bucket isolates accounts, refills gradually and expires idle entries', () => {
  let now = 0;
  const limiter = new RequestRateLimiter({ clock: () => now });
  assert.equal(limiter.consume('one', 2).allowed, true);
  assert.equal(limiter.consume('one', 2).allowed, true);
  assert.deepEqual(limiter.consume('one', 2), { allowed: false, retryAfterSeconds: 30 });
  assert.equal(limiter.consume('two', 2).allowed, true);
  now = 15000;
  assert.deepEqual(limiter.consume('one', 2), { allowed: false, retryAfterSeconds: 15 });
  now = 30000;
  assert.equal(limiter.consume('one', 2).allowed, true);
  assert.equal(limiter.consume('one', 2).allowed, false);
  now = 90000;
  assert.equal(limiter.consume('one', 2).allowed, true);
  assert.equal(limiter.accounts.has('two'), false);
});

test('full limiter rejects new accounts without evicting exhausted buckets', () => {
  const limiter = new RequestRateLimiter({ clock: () => 0, maxAccounts: 1 });
  assert.equal(limiter.consume('one', 1).allowed, true);
  assert.equal(limiter.consume('two', 1).allowed, false);
  assert.equal(limiter.consume('one', 1).allowed, false);
});

test('access guard rejects bursts before charging daily quota and preserves refreshed cookies', async () => {
  const env = {
    DUA_REQUIRE_LOGIN: '1', DUA_MINUTE_REQUEST_LIMIT: '1', DUA_DAILY_REQUEST_LIMIT: '5',
    SUPABASE_URL: 'https://project.supabase.co', SUPABASE_ANON_KEY: 'public-key',
    SUPABASE_SERVICE_ROLE_KEY: 'sb_secret_fake',
  };
  const request = () => new Request('https://dua.test/api/chat', {
    headers: { cookie: 'dua_access=old-access; dua_refresh=old-refresh' },
  });
  const rateLimiter = new RequestRateLimiter({ clock: () => 0 });
  const user = { id: '11111111-1111-4111-8111-111111111111', email: 'owner@example.test' };
  let quotaCalls = 0;
  const fetcher = async (url) => {
    if (url.includes('/auth/v1/user')) return Response.json({ error: 'expired' }, { status: 401 });
    if (url.includes('/auth/v1/token')) return Response.json({
      access_token: 'new-access', refresh_token: 'new-refresh', expires_in: 3600, user,
    });
    assert.ok(url.endsWith('/rest/v1/rpc/dua_consume_daily_request'));
    quotaCalls++;
    return Response.json({ allowed: true, used: quotaCalls, resetAt: Math.floor(Date.now() / 1000) + 3600 });
  };
  assert.equal((await guardChatAccess(request(), { env, fetcher, rateLimiter })).allowed, true);
  const denied = await guardChatAccess(request(), { env, fetcher, rateLimiter });
  assert.equal(denied.response.status, 429);
  assert.equal(denied.response.headers.get('Retry-After'), '60');
  assert.ok(denied.response.headers.getSetCookie().some(cookie => cookie.startsWith('dua_access=new-access')));
  assert.equal(quotaCalls, 1);
});

test('invalid minute limit fails closed before authentication', async () => {
  for (const value of ['', '0', '-1', '1.5', 'NaN', '100001']) {
    const result = await guardChatAccess(new Request('https://dua.test/api/chat'), {
      env: { DUA_MINUTE_REQUEST_LIMIT: value },
      fetcher: () => { throw Error('Must not contact auth'); },
    });
    assert.equal(result.response.status, 503);
  }
});
