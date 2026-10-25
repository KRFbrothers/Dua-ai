import test from 'node:test';
import assert from 'node:assert/strict';
import { handleAccount, handleCloudRead } from '../src/core/account.mjs';
import { validateCloudSnapshot } from '../src/core/cloud-snapshot.mjs';
import { mockFetch } from './helpers.mjs';

const owner = '11111111-1111-4111-8111-111111111111';
const snapshot = () => ({ format: 'dua-cloud-snapshot', version: 1, memory: ['Private note'], chats: [
  { id: 'chat', model: 'Dua', draft: 'Draft', updatedAt: 1, title: 'Notes', messages: [{ role: 'user', text: 'Question', state: 'complete' }] }
] });
const env = fetcher => ({ url: 'https://project.supabase.co', key: 'public-anon-key', allowedEmail: 'owner@example.com', fetcher });
const user = email => Response.json({ id: owner, email: email || 'owner@example.com' });
const row = payload => Response.json([{ revision: 3, payload }]);
const req = value => new Request('https://dua.test/api/cloud', { method: 'PUT', headers: {
  'content-type': 'application/json', 'x-dua-native': '1', authorization: 'Bearer fake-access'
}, body: JSON.stringify({ expectedRevision: 3, expectedAccountId: owner, snapshot: value }) });

test('pin flags are optional strict booleans and round-trip without changing content', () => {
  const old = snapshot(); assert.deepEqual(validateCloudSnapshot(old), old);
  for (const pinned of [true, false]) {
    const value = snapshot(); value.chats[0].pinned = pinned;
    assert.deepEqual(validateCloudSnapshot(value), value);
  }
  for (const pinned of ['true', 1, null, {}, []]) {
    const value = snapshot(); value.chats[0].pinned = pinned;
    assert.throws(() => validateCloudSnapshot(value));
  }
});
test('a legacy snapshot upload retains the existing server pin', async () => {
  for (const pinned of [true, false]) {
    const server = snapshot(); server.chats[0].pinned = pinned;
    const incoming = snapshot(); incoming.chats[0].draft = 'New draft from old APK';
    const fetcher = mockFetch(user(), row(server), Response.json({ revision: 4 }));
    assert.equal((await handleAccount(req(incoming), env(fetcher))).status, 200);
    assert.deepEqual(fetcher.calls[2].body.next_payload, { ...incoming, chats: [{ ...incoming.chats[0], pinned }] });
    assert.match(fetcher.calls[1].url, new RegExp('owner_id=eq\\.' + owner));
    assert.equal(fetcher.calls[2].body.expected_revision, 3);
  }
});
test('an explicit unpin is committed while unrelated chats and memory are retained', async () => {
  const server = snapshot(); server.chats[0].pinned = true;
  server.chats.push({ ...server.chats[0], id: 'other' });
  const incoming = structuredClone(server); incoming.chats[0].pinned = false;
  const fetcher = mockFetch(user(), row(server), Response.json({ revision: 4 }));
  assert.equal((await handleAccount(req(incoming), env(fetcher))).status, 200);
  assert.deepEqual(fetcher.calls[2].body.next_payload, incoming);
});
test('read includes pin metadata and a stale revision or non-owner cannot change it', async () => {
  const value = snapshot(); value.chats[0].pinned = true;
  const get = new Request('https://dua.test/api/cloud', { headers: { 'x-dua-native': '1', authorization: 'Bearer fake-access' } });
  const response = await handleCloudRead(get, env(mockFetch(user(), row(value))));
  assert.equal(response.status, 200); assert.deepEqual((await response.json()).snapshot, value);
  const stale = mockFetch(user(), Response.json([{ revision: 4, payload: value }]));
  assert.equal((await handleAccount(req(value), env(stale))).status, 409); assert.equal(stale.calls.length, 2);
  const wrong = mockFetch(user('other@example.com'));
  assert.equal((await handleAccount(req(value), env(wrong))).status, 403); assert.equal(wrong.calls.length, 1);
});
