import test from 'node:test';
import assert from 'node:assert/strict';
import { handleAccount, handleCloudRead } from '../src/core/account.mjs';
import { validateCloudSnapshot } from '../src/core/cloud-snapshot.mjs';
import { mockFetch } from './helpers.mjs';

const owner = '11111111-1111-4111-8111-111111111111';
const env = fetcher => ({ url: 'https://project.supabase.co', key: 'public-anon-key', allowedEmail: 'owner@example.com', fetcher });
const user = (email = 'owner@example.com') => Response.json({ id: owner, email });
const chat = id => ({ id, model: 'Dua', draft: 'Keep this draft', updatedAt: 1, title: id, messages: [{ id: id + '-m', role: 'user', text: 'Question', state: 'complete' }] });
const snapshot = () => ({ format: 'dua-cloud-snapshot', version: 1, memory: ['Prefer Hindi'], chats: [chat('delete-me'), chat('keep-me')] });
const row = (payload, revision = 3) => Response.json([{ revision, payload }]);
const req = (path, method, body) => new Request('https://dua.test' + path, {
  method, headers: { 'content-type': 'application/json', 'x-dua-native': '1', authorization: 'Bearer fake-access' },
  ...(method === 'GET' ? {} : { body: JSON.stringify(body) })
});
const deletion = (extra = {}) => req('/api/cloud/chat', 'DELETE', { expectedRevision: 3, expectedAccountId: owner, chatId: 'delete-me', confirmation: 'DELETE CHAT', ...extra });

test('delete preserves other chats and memory and records a durable deletion ID', async () => {
  const value = snapshot(); const fetcher = mockFetch(user(), row(value), Response.json({ revision: 4 }));
  const response = await handleAccount(deletion(), env(fetcher));
  assert.equal(response.status, 200);
  const saved = fetcher.calls[2].body.next_payload;
  assert.deepEqual(saved, { ...value, chats: [value.chats[1]], deletedChatIds: ['delete-me'] });
  assert.deepEqual(await response.json(), { ownerId: owner, chatId: 'delete-me', revision: 4, deletedChatIds: ['delete-me'], deleted: true });
  assert.match(fetcher.calls[1].url, new RegExp('owner_id=eq\\.' + owner));
  assert.ok(fetcher.calls.every(call => call.init.headers.Authorization === 'Bearer fake-access'));
  assert.ok(fetcher.calls.every(call => !call.url.includes('/storage/')));
});
test('a concurrent change is rejected by both the pre-read and atomic RPC', async () => {
  const before = mockFetch(user(), row(snapshot(), 4));
  assert.equal((await handleAccount(deletion(), env(before))).status, 409);
  assert.equal(before.calls.length, 2);
  const during = mockFetch(user(), row(snapshot()), Response.json({ conflict: true, revision: 4 }));
  assert.equal((await handleAccount(deletion(), env(during))).status, 409);
});
test('an acknowledged deletion is idempotent, including retry after a lost response', async () => {
  const value = { ...snapshot(), chats: [chat('keep-me')], deletedChatIds: ['delete-me'] };
  const fetcher = mockFetch(user(), row(value, 4));
  const response = await handleAccount(deletion({ expectedRevision: 4 }), env(fetcher));
  assert.equal(response.status, 200); assert.equal((await response.json()).revision, 4);
  assert.equal(fetcher.calls.length, 2, 'retry does not write another revision');
});
test('a response lost after the RPC commit can be retried without another deletion write', async () => {
  let saved;
  const lost = mockFetch(user(), row(snapshot()), (_url, init) => {
    saved = JSON.parse(init.body).next_payload;
    throw new DOMException('Response lost after commit', 'TimeoutError');
  });
  assert.equal((await handleAccount(deletion(), env(lost))).status, 502);
  assert.deepEqual(saved.deletedChatIds, ['delete-me']);
  const retry = mockFetch(user(), row(saved, 4));
  assert.equal((await handleAccount(deletion({ expectedRevision: 4 }), env(retry))).status, 200);
  assert.equal(retry.calls.length, 2);
});
test('old clients cannot resurrect a deleted ID or erase markers on upload or clear', async () => {
  const server = { ...snapshot(), chats: [chat('keep-me')], deletedChatIds: ['delete-me'] };
  const stale = mockFetch(user(), row(server));
  const rejected = await handleAccount(req('/api/cloud', 'PUT', { expectedRevision: 3, snapshot: snapshot() }), env(stale));
  assert.equal(rejected.status, 409); assert.equal(stale.calls.length, 2);
  for (const method of ['PUT', 'DELETE']) {
    const fetcher = mockFetch(user(), row(server), Response.json({ revision: 4 }));
    const body = { expectedRevision: 3, snapshot: { ...snapshot(), chats: [chat('keep-me')] } };
    assert.equal((await handleAccount(req('/api/cloud', method, body), env(fetcher))).status, 200);
    assert.deepEqual(fetcher.calls[2].body.next_payload.deletedChatIds, ['delete-me']);
  }
});
test('client-supplied deletion IDs cannot remove a live server chat', async () => {
  const incoming = { ...snapshot(), deletedChatIds: ['forged'] };
  const fetcher = mockFetch(user(), row(snapshot()), Response.json({ revision: 4 }));
  assert.equal((await handleAccount(req('/api/cloud', 'PUT', { expectedRevision: 3, snapshot: incoming }), env(fetcher))).status, 200);
  assert.deepEqual(fetcher.calls[2].body.next_payload, snapshot());
});
test('confirmation, account binding and owner lock fail before reading cloud data', async () => {
  for (const extra of [{ confirmation: '' }, { expectedAccountId: 'another-account' }, { chatId: '' }, { chatId: 'x'.repeat(201) }]) {
    const fetcher = mockFetch(user());
    assert.ok((await handleAccount(deletion(extra), env(fetcher))).status >= 400);
    assert.equal(fetcher.calls.length, 1);
  }
  const stranger = mockFetch(user('someone@example.com'));
  assert.equal((await handleAccount(deletion(), env(stranger))).status, 403);
  assert.equal(stranger.calls.length, 1);
  const missing = mockFetch(user());
  assert.equal((await handleAccount(deletion(), { ...env(missing), allowedEmail: undefined })).status, 503);
});
test('corrupt stored deletion metadata blocks a write and a partially stripped read', async () => {
  const value = { ...snapshot(), deletedChatIds: ['delete-me'] };
  const write = mockFetch(user(), row(value));
  assert.equal((await handleAccount(deletion(), env(write))).status, 503);
  assert.equal(write.calls.length, 2);
  const read = mockFetch(user(), row(value));
  assert.equal((await handleCloudRead(req('/api/cloud', 'GET'), env(read))).status, 502);
});
test('deletion metadata is bounded, unique, typed and disjoint from live records', () => {
  for (const ids of [null, 'id', [1], [''], ['gone', 'gone'], ['x'.repeat(201)], Array.from({ length: 10_001 }, (_, i) => 'id-' + i), ['delete-me']]) {
    assert.throws(() => validateCloudSnapshot({ ...snapshot(), deletedChatIds: ids }));
  }
  const value = { ...snapshot(), deletedChatIds: ['z', 'a'] };
  assert.deepEqual(validateCloudSnapshot(value).deletedChatIds, ['a', 'z']);
});
