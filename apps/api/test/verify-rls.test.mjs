import test from 'node:test';
import assert from 'node:assert/strict';
import { verifyRls, main, DEFAULT_TABLES } from '../scripts/verify-rls.mjs';

const env = { SUPABASE_URL: 'https://project.supabase.co', SUPABASE_PUBLISHABLE_KEY: 'sb_publishable_mock_public_key' };
function mock(read = () => Response.json([]), insert = () => new Response(null, { status: 403 })) {
  const calls = [];
  const fetcher = async (url, init) => {
    calls.push({ url, init });
    if (url.endsWith('/auth/v1/settings')) return Response.json({ external: {} });
    return init.method === 'POST' ? insert(url) : read(url);
  };
  return Object.assign(fetcher, { calls });
}
test('missing configuration skips cleanly without any network request', async () => {
  const result = await verifyRls({ env: {}, fetcher: () => assert.fail('must not fetch') });
  assert.equal(result.skipped, true); assert.equal(result.ok, true); assert.match(result.reason, /SKIP.*SUPABASE_URL/);
  assert.equal(await main([], { env: {}, log: () => {} }), 0);
});
test('default tables accept empty anonymous reads or 401/403 and require denied inserts', async () => {
  for (const status of [200, 401, 403]) {
    const fetcher = mock(() => status === 200 ? Response.json([]) : new Response(null, { status }));
    const result = await verifyRls({ env, fetcher });
    assert.equal(result.ok, true); assert.deepEqual(result.checks.map(c => c.table), DEFAULT_TABLES);
    for (const { url, init } of fetcher.calls) {
      assert.equal(new URL(url).origin, env.SUPABASE_URL); assert.equal(init.headers.apikey, env.SUPABASE_PUBLISHABLE_KEY);
      assert.equal(new Headers(init.headers).has('authorization'), false); assert.equal(init.redirect, 'error');
      if (init.method === 'POST') { assert.deepEqual(JSON.parse(init.body), []); assert.match(init.headers.Prefer, /tx=rollback/); }
    }
  }
});
test('visible data, successful insert admission, missing tables and unrelated failures never pass', async () => {
  for (const [read, insert] of [
    [() => Response.json([{ private: 'do not log' }]), () => new Response(null, { status: 403 })],
    [() => Response.json([]), () => new Response(null, { status: 201 })],
    [() => new Response(null, { status: 404 }), () => new Response(null, { status: 404 })],
    [() => Response.json({ error: 'unexpected' }), () => new Response(null, { status: 403 })],
    [() => Response.json([]), () => new Response(null, { status: 400 })],
    [() => { throw Error('secret network detail'); }, () => new Response(null, { status: 403 })]
  ]) {
    const result = await verifyRls({ env, fetcher: mock(read, insert) });
    assert.equal(result.ok, false); assert.doesNotMatch(JSON.stringify(result), /do not log|secret network detail/);
  }
});
test('invalid or privileged keys, user tokens and invalid project URLs are refused before fetching', async () => {
  const jwt = role => `eyJhbGciOiJIUzI1NiJ9.${Buffer.from(JSON.stringify({ role })).toString('base64url')}.signature`;
  for (const key of ['sb_secret_do_not_use', jwt('service_role'), jwt('authenticated'), 'garbage']) {
    const result = await verifyRls({ env: { ...env, SUPABASE_ANON_KEY: key }, fetcher: () => assert.fail() });
    assert.equal(result.ok, false); assert.doesNotMatch(JSON.stringify(result), new RegExp(key.replaceAll('.', '\\.')));
  }
  assert.equal((await verifyRls({ env: { ...env, SUPABASE_ANON_KEY: jwt('anon') }, fetcher: mock() })).ok, true);
  for (const url of ['http://project.supabase.co', env.SUPABASE_URL + '/rest/v1/', 'https://user:pass@project.supabase.co', env.SUPABASE_URL + '?token=bad'])
    assert.equal((await verifyRls({ env: { ...env, SUPABASE_URL: url }, fetcher: () => assert.fail() })).ok, false);
});
test('invalid public credentials are not mistaken for protected tables', async () => {
  let calls = 0;
  const result = await verifyRls({ env, fetcher: async () => { calls++; return new Response(null, { status: 401 }); } });
  assert.equal(result.ok, false); assert.equal(calls, 1); assert.equal(result.checks.length, 0);
});
test('repo snapshot table can be checked explicitly; malformed table options and CLI failures exit nonzero', async () => {
  const fetcher = mock();
  assert.equal(await main(['--tables=dua_cloud_snapshots'], { env, fetcher, log: () => {} }), 0);
  assert.equal(fetcher.calls.length, 3); assert.ok(fetcher.calls[1].url.includes('/dua_cloud_snapshots?'));
  assert.equal(await main(['--tables=../../secret'], { env, fetcher: () => assert.fail(), log: () => {} }), 1);
  assert.equal(await main(['--unknown'], { log: () => {} }), 1);
  assert.equal(await main([], { env, fetcher: mock(() => new Response(null, { status: 404 })), log: () => {} }), 1);
});
