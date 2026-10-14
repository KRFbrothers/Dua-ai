import test from 'node:test';
import assert from 'node:assert/strict';
import { handleAccount } from '../src/core/account.mjs';
import { mockFetch } from './helpers.mjs';
import { handleService } from '../src/server/service-handler.mjs';

const env = fetcher => ({ url: 'https://project.supabase.co', key: 'public-anon-key', fetcher });
const id = '11111111-1111-4111-8111-111111111111';
const session = metadata => Response.json({ access_token: 'access', refresh_token: 'refresh', expires_in: 3600, user: { id, email: 'fareed@example.com', user_metadata: metadata } });
const googleSignIn = fetcher => handleAccount(new Request('https://dua.example/api/account/google-token', {
  method: 'POST', headers: { 'content-type': 'application/json', 'x-dua-native': '1' },
  body: JSON.stringify({ idToken: 'header.payload.signature', nonce: 'raw-nonce-0123456789abcdef' })
}), env(fetcher));

test('Google sign-in names the account from Google metadata, falling back to a saved display name', async () => {
  const cases = [
    [{ full_name: 'Fareed Ahmad', name: 'Other', display_name: 'Old' }, 'Fareed Ahmad'],
    [{ name: ' Fareed ', display_name: 'Old' }, 'Fareed'],
    [{ full_name: '  ', display_name: 'Saved Name' }, 'Saved Name'],
    [{}, ''],
    [undefined, ''],
  ];
  for (const [metadata, expected] of cases) {
    const fetcher = mockFetch(session(metadata));
    const response = await googleSignIn(fetcher);
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.user.displayName, expected);
    assert.equal(body.user.email, 'fareed@example.com');
    assert.match(fetcher.calls[0].url, /\/auth\/v1\/token\?grant_type=id_token$/);
    assert.equal(fetcher.calls[0].body.nonce, 'raw-nonce-0123456789abcdef');
  }
});

test('the removed profile-name route is no longer served', async () => {
  const response = await handleAccount(new Request('https://dua.example/api/account/profile', {
    method: 'PATCH', headers: { 'content-type': 'application/json', 'x-dua-native': '1' }, body: JSON.stringify({ displayName: 'New' })
  }), env(mockFetch()));
  assert.equal(response.status, 405);
});

// Owner lock (DUA_ALLOWED_EMAIL) for account and cloud routes, through the real service router.
const serviceEnv = { SUPABASE_URL: 'https://project.supabase.co', SUPABASE_ANON_KEY: 'public-anon-key', DUA_ALLOWED_EMAIL: ' Fareed@Example.com ' };
const strangerId = '22222222-2222-4222-8222-222222222222';
const userJson = (email, userId = id) => Response.json({ id: userId, email, user_metadata: {} });
const sessionFor = email => Response.json({ access_token: 'access', refresh_token: 'refresh', expires_in: 3600, user: { id: email.startsWith('fareed') ? id : strangerId, email, user_metadata: {} } });
const call = (path, { method = 'GET', body, token = 'access', env = serviceEnv } = {}, fetcher) => handleService(new Request('https://dua.example' + path, {
  method, headers: { 'x-dua-native': '1', ...(token ? { authorization: 'Bearer ' + token } : {}), ...(body === undefined ? {} : { 'content-type': 'application/json' }) },
  ...(body === undefined ? {} : { body: JSON.stringify(body) })
}), { env, fetcher });
const googleBody = { idToken: 'header.payload.signature', nonce: 'raw-nonce-0123456789abcdef' };

test('Google sign-in is limited to the allowed email and revokes a stranger session', async () => {
  const owner = mockFetch(sessionFor('fareed@example.com'));
  const ok = await call('/api/account/google-token', { method: 'POST', body: googleBody, token: null }, owner);
  assert.equal(ok.status, 200);
  assert.equal((await ok.json()).session.accessToken, 'access');

  const stranger = mockFetch(sessionFor('someone@else.com'), new Response(null, { status: 204 }));
  const denied = await call('/api/account/google-token', { method: 'POST', body: googleBody, token: null }, stranger);
  assert.equal(denied.status, 403);
  const body = await denied.json();
  assert.equal(body.session, undefined);
  assert.match(body.error, /Only the configured personal account/);
  assert.match(stranger.calls[1].url, /\/auth\/v1\/logout\?scope=local$/);
  assert.equal(stranger.calls[1].init.headers.Authorization, 'Bearer access');
});

test('refresh, cloud read/write and delete reject a non-owner account', async () => {
  const refresh = await call('/api/account/refresh', { method: 'POST', body: { refreshToken: 'refresh' }, token: null }, mockFetch(sessionFor('someone@else.com')));
  assert.equal(refresh.status, 403);
  assert.equal((await refresh.json()).session, undefined);

  const write = mockFetch(userJson('someone@else.com', strangerId));
  const put = await call('/api/cloud', { method: 'PUT', body: { expectedRevision: 0, snapshot: { format: 'dua-cloud-snapshot', version: 1, chats: [], memory: [] } } }, write);
  assert.equal(put.status, 403);
  assert.equal(write.calls.length, 1, 'no cloud RPC for a non-owner');

  const read = mockFetch(userJson('someone@else.com', strangerId));
  const get = await call('/api/cloud', {}, read);
  assert.equal(get.status, 403);
  assert.equal(read.calls.length, 1, 'no snapshot query for a non-owner');

  const del = await call('/api/account/delete', { method: 'DELETE', body: { expectedAccountId: strangerId, confirmation: 'DELETE MY ACCOUNT' } }, mockFetch(userJson('someone@else.com', strangerId)));
  assert.equal(del.status, 403);
});

test('the owner keeps refresh, cloud read/write; session, features and logout stay open', async () => {
  const refresh = await call('/api/account/refresh', { method: 'POST', body: { refreshToken: 'refresh' }, token: null }, mockFetch(sessionFor('fareed@example.com')));
  assert.equal(refresh.status, 200);

  const put = await call('/api/cloud', { method: 'PUT', body: { expectedRevision: 0, snapshot: { format: 'dua-cloud-snapshot', version: 1, chats: [], memory: [] } } },
    mockFetch(userJson('FAREED@example.com'), Response.json([]), Response.json({ revision: 1 })));
  assert.equal(put.status, 200);
  assert.equal((await put.json()).revision, 1);

  const get = await call('/api/cloud', {}, mockFetch(userJson('fareed@example.com'), Response.json([])));
  assert.equal(get.status, 200);
  assert.equal((await get.json()).revision, 0);

  const session = await call('/api/account/session', {}, mockFetch(userJson('someone@else.com', strangerId)));
  assert.equal(session.status, 200);
  assert.equal((await session.json()).user.email, 'someone@else.com');

  const features = await call('/api/account/features', { token: null }, mockFetch());
  assert.deepEqual(await features.json(), { passwordReset: false, emailAuth: true, accountDeletion: false });

  const logout = await call('/api/account/logout', { method: 'POST', body: {} }, mockFetch(new Response(null, { status: 204 })));
  assert.equal(logout.status, 200);
  assert.equal((await logout.json()).revoked, true);
});

test('without DUA_ALLOWED_EMAIL any signed-in account may use cloud; a malformed owner setting fails closed', async () => {
  const open = await call('/api/cloud', { env: { ...serviceEnv, DUA_ALLOWED_EMAIL: '' } }, mockFetch(userJson('someone@else.com', strangerId), Response.json([])));
  assert.equal(open.status, 200);
  const broken = { ...serviceEnv, DUA_ALLOWED_EMAIL: 'not-an-email' };
  assert.equal((await call('/api/account/google-token', { method: 'POST', body: googleBody, token: null, env: broken }, mockFetch())).status, 503);
  assert.equal((await call('/api/cloud', { env: broken }, mockFetch(userJson('fareed@example.com')))).status, 503);
  assert.equal((await call('/api/account/logout', { method: 'POST', body: {}, env: broken }, mockFetch(new Response(null, { status: 204 })))).status, 200);
});

test('legacy sign-in/signup and recovery routes remain unserved', async () => {
  for (const route of ['signin', 'signup', 'reset-password', 'verify-recovery']) {
    const fetcher = mockFetch();
    const response = await call('/api/account/' + route, { method: 'POST', body: { email: 'fareed@example.com', password: 'secret-password', token: '123456' }, token: null }, fetcher);
    assert.equal(response.status, 404, route);
    assert.equal(fetcher.calls.length, 0, route + ' must not reach Supabase');
  }
});

test('Google sign-in returns the https profile photo and drops unsafe ones', async () => {
  for (const [metadata, expected] of [
    [{ avatar_url: 'https://lh3.googleusercontent.com/a/photo' }, 'https://lh3.googleusercontent.com/a/photo'],
    [{ picture: 'https://lh3.googleusercontent.com/a/pic' }, 'https://lh3.googleusercontent.com/a/pic'],
    [{ avatar_url: 'http://insecure.example/p.png' }, ''],
    [{}, ''],
  ]) {
    const body = await (await googleSignIn(mockFetch(session(metadata)))).json();
    assert.equal(body.user.avatarUrl, expected);
  }
});
