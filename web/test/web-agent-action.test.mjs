import test from 'node:test';
import assert from 'node:assert/strict';
import { handleWebAgentAction } from '../src/core/web-agent-action.mjs';
const origin = 'https://dua.test';
const env = { DUA_ALLOWED_EMAIL: process.env.DUA_TEST_OWNER_EMAIL || 'owner@example.invalid', DUA_ENGINE_URL: 'https://engine.test' };
const id = 'a'.repeat(24);
const req = (data = { id, decision: 'approve' }, headers = {}) => new Request(origin + '/api/web/agent/action', {
  method: 'POST', headers: { origin, 'sec-fetch-site': 'same-origin', 'content-type': 'application/json', cookie: 'dua_access=old-access', ...headers }, body: JSON.stringify(data)
});
const session = () => Response.json({ configured: true, user: { email: env.DUA_ALLOWED_EMAIL } });
const run = (request, options = {}) => handleWebAgentAction(request, { env, sessionHandler: async () => session(), ...options });

test('rejects cross-origin requests before auth or execution', async () => {
  let calls = 0;
  const sessionHandler = async () => { calls++; return session(); };
  for (const headers of [{ origin: 'https://other.test' }, { 'sec-fetch-site': 'cross-site' }, { origin: '' }]) {
    assert.equal((await run(req(undefined, headers), { sessionHandler })).status, 403);
  }
  assert.equal(calls, 0);
});
test('requires owner configuration, authenticated session, and matching owner', async () => {
  let calls = 0;
  const fetcher = async () => { calls++; throw Error(); };
  assert.equal((await run(req(), { env: { ...env, DUA_ALLOWED_EMAIL: '' }, fetcher })).status, 503);
  assert.equal((await run(req(), { sessionHandler: async () => Response.json({ configured: true, user: null }), fetcher })).status, 401);
  assert.equal((await run(req(), { sessionHandler: async () => Response.json({ configured: true, user: { email: 'other@example.invalid' } }), fetcher })).status, 403);
  assert.equal(calls, 0);
});
test('maintenance and Agent pause block decisions before authentication', async () => {
  const sessionHandler = async () => { throw Error('must not authenticate'); };
  for (const switchName of ['DUA_MAINTENANCE', 'DUA_DISABLE_AGENT']) {
    const response = await run(req(), { env: { ...env, [switchName]: '1' }, sessionHandler });
    assert.equal(response.status, 503);
  }
});
test('rejects injected arguments, invalid decisions, oversized bodies, and unsafe engine URLs', async () => {
  for (const body of [{ id, decision: 'approve', args: { text: 'injected' } }, { id: '../bad', decision: 'approve' }, { id, decision: 'maybe' }]) {
    assert.equal((await run(req(body))).status, 400);
  }
  assert.equal((await run(req({ id, decision: 'approve', extra: 'x'.repeat(5000) }))).status, 413);
  assert.equal((await run(req(), { env: { ...env, DUA_ENGINE_URL: 'http://engine.test' } })).status, 503);
});
test('forwards fresh cookie token and only the stored action id and decision', async () => {
  const response = await run(req(), {
    sessionHandler: async request => {
      assert.equal(request.headers.get('authorization'), null);
      const result = session(); result.headers.append('Set-Cookie', 'dua_access=new-access; HttpOnly; Secure'); return result;
    },
    fetcher: async (url, init) => {
      assert.equal(String(url), 'https://engine.test/api/agent/action');
      assert.equal(init.headers.Authorization, 'Bearer new-access');
      assert.equal(init.redirect, 'error');
      assert.deepEqual(JSON.parse(init.body), { id, decision: 'approve' });
      return Response.json({ status: 'done', text: 'Note saved.', internal: 'not forwarded' });
    }
  });
  assert.equal(response.status, 200);
  assert.equal(response.headers.getSetCookie().length, 1);
  assert.deepEqual(await response.json(), { status: 'done', text: 'Note saved.' });
});
test('preserves rejection and expiry results without repeating execution', async () => {
  for (const [status, body] of [[200, { status: 'rejected', text: 'Nothing changed.' }], [410, { error: 'Expired approval' }]]) {
    const response = await run(req({ id, decision: 'reject' }), { fetcher: async () => Response.json(body, { status }) });
    assert.equal(response.status, status); assert.deepEqual(await response.json(), body);
  }
});
test('lost or invalid execution responses have unknown outcome and are never retried', async () => {
  for (const result of [null, Response.json({ error: 'private detail' }, { status: 500 }), Response.json({ status: 'surprise' })]) {
    let calls = 0;
    const response = await run(req(), { fetcher: async () => { calls++; if (!result) throw Error('private detail'); return result; } });
    assert.equal(response.status, 502);
    const body = await response.json(); assert.equal(body.code, 'ACTION_OUTCOME_UNKNOWN');
    assert.ok(!body.error.includes('private detail')); assert.equal(calls, 1);
  }
});
