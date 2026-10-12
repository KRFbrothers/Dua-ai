import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { validateCloudSnapshot } from '../src/core/cloud-snapshot.mjs';
import { handleAccount, handleCloudRead } from '../src/core/account.mjs';
import { mockFetch } from './helpers.mjs';

// Android decodes/re-encodes this same fixture in CloudSnapshotTest.
const fixture = JSON.parse(readFileSync(new URL('../../app/src/test/resources/cloud-snapshot-v1.json', import.meta.url), 'utf8'));
const fresh = () => structuredClone(fixture);
const owner = '11111111-1111-4111-8111-111111111111';
const user = () => Response.json({ id: owner, email: 'owner@example.com' });
const environment = fetcher => ({ url: 'https://project.supabase.co', key: 'public-anon-key', allowedEmail: 'owner@example.com', fetcher });
const request = (method, snapshot) => new Request('https://dua.test/api/cloud', {
  method, headers: { 'content-type': 'application/json', 'x-dua-native': '1', authorization: 'Bearer fake-access' },
  ...(method === 'GET' ? {} : { body: JSON.stringify({ expectedRevision: 0, expectedAccountId: owner, snapshot }) })
});

test('the shared Android snapshot retains every extended field through repeated normalization', () => {
  const value = fresh();
  assert.deepEqual(validateCloudSnapshot(value), value);
  assert.deepEqual(validateCloudSnapshot(validateCloudSnapshot(value)), value);
});

test('cloud PUT writes the complete snapshot and GET returns it with its stable message IDs', async () => {
  const value = fresh();
  const writer = mockFetch(user(), Response.json([]), Response.json({ revision: 1 }));
  const upload = await handleAccount(request('PUT', value), environment(writer));
  assert.equal(upload.status, 200);
  assert.deepEqual(writer.calls[2].body, { expected_revision: 0, next_payload: value });
  const reader = mockFetch(user(), Response.json([{ revision: 1, payload: writer.calls[2].body.next_payload }]));
  const download = await handleCloudRead(request('GET'), environment(reader));
  assert.equal(download.status, 200);
  assert.deepEqual(await download.json(), { ownerId: owner, revision: 1, snapshot: value });
});

test('older version 1 snapshots without metadata still normalize unchanged', () => {
  const old = { format: 'dua-cloud-snapshot', version: 1, memory: [], chats: [{
    id: 'old', model: 'Dua', draft: '', updatedAt: 0,
    messages: [{ role: 'user', text: 'Hi', state: 'complete' }, { role: 'model', text: 'Hello', state: 'stopped', thinkingSeconds: 0 }]
  }] };
  assert.deepEqual(validateCloudSnapshot(old), old);
  assert.deepEqual(validateCloudSnapshot({ ...old, chats: [] }), { ...old, chats: [] });
});

test('invalid extended metadata is rejected before a cloud write RPC', async () => {
  const invalid = [
    s => { s.chats[0].title = 'x'.repeat(121); },
    s => { s.chats[0].title = ' '; },
    s => { s.chats[0].title = 'a\nb'; },
    s => { s.chats[0].title = 'a\u2028b'; },
    s => { s.chats[0].title = 'a\u0085b'; },
    s => { s.chats[0].title = null; },
    s => { s.chats[0].title = 123; },
    s => { s.chats[0].title = ' padded '; },
    s => { s.chats[0].summary.text = 'x'.repeat(16_001); },
    s => { s.chats[0].summary.throughId = ''; },
    s => { s.chats[0].buildRepository = '../repo'; },
    s => { s.chats[0].messages[0].id = 'x'.repeat(101); },
    s => { s.chats[0].messages[1].id = s.chats[0].messages[0].id; },
    s => { s.chats[0].messages[0].attachmentText = 'x'.repeat(32_001); },
    s => { s.chats[0].messages[0].attachmentText = 1; },
    s => { s.chats[0].messages[0].files = Array(21).fill(s.chats[0].messages[0].files[0]); },
    s => { s.chats[0].messages[0].files[0].path = '../private.csv'; },
    s => { s.chats[0].messages[0].files[1].transcript = 'x'.repeat(100_001); },
    s => { s.chats[0].messages[0].files[0].data = 'raw-base64'; },
    s => { s.chats[0].messages[1].files = []; },
    s => { s.chats[0].messages[0].build = s.chats[0].messages[1].build; },
    s => { s.chats[0].messages[1].codeRuns[0].code = 'x'.repeat(20_001); },
    s => { s.chats[0].messages[1].codeRuns[0].output = 'x'.repeat(20_001); },
    s => { s.chats[0].messages[1].codeRuns[0].outcome = 'unknown'; },
    s => { s.chats[0].messages[1].codeRuns = Array(11).fill(s.chats[0].messages[1].codeRuns[0]); },
    s => { s.chats[0].messages[1].build.files[0].path = '../secrets'; },
    s => { s.chats[0].messages[1].build.files = Array(101).fill(s.chats[0].messages[1].build.files[0]); },
    s => { s.chats[0].messages[1].proposal.files[0].path = '.github/workflows/run.yml'; },
    s => { s.chats[0].messages[1].proposal.files[0].path = '.git/config'; },
    s => { s.chats[0].messages[1].proposal.files[0].content = 'x'.repeat(512 * 1024 + 1); },
    s => { s.chats[0].messages[1].proposal.files[0].diff = 'x'.repeat(20_001); },
    s => { s.chats[0].messages[1].proposal.files[1].baseSha = 'bad'; },
    s => { s.chats[0].messages[1].proposal.files[1].path = s.chats[0].messages[1].proposal.files[0].path; },
    s => { s.chats[0].messages[2].proposal.pullRequest.url = 'https://evil.example/pull/123'; },
    s => { s.chats[0].messages[2].proposal.pullRequest.number = 124; },
    s => { s.chats[0].messages[2].imagine.progress = 101; },
    s => { s.chats[0].messages[2].imagine.jobId = 'x'.repeat(2001); },
    s => { s.chats[0].messages[2].imagine.status = 'unknown'; },
    s => { s.chats[0].messages[1].media[0].url = 'https://temporary.example/image.png'; }
  ];
  for (const [index, mutate] of invalid.entries()) {
    const value = fresh(); mutate(value);
    const fetcher = mockFetch(user());
    const response = await handleAccount(request('PUT', value), environment(fetcher));
    assert.equal(response.status, 400, 'case ' + index);
    assert.match((await response.json()).error, /Invalid cloud snapshot/);
    assert.equal(fetcher.calls.length, 1, 'case ' + index + ': no RPC after invalid metadata');
  }
});

test('metadata caps match Android characters and the whole snapshot uses UTF-8 bytes', () => {
  const value = fresh();
  const chat = value.chats[0];
  chat.title = '💡'.repeat(60);
  chat.summary.text = 'x'.repeat(16_000);
  chat.messages[0].attachmentText = 'x'.repeat(32_000);
  chat.messages[0].files[1].transcript = 'x'.repeat(100_000);
  chat.messages[1].codeRuns[0].code = 'x'.repeat(20_000);
  chat.messages[1].codeRuns[0].output = 'x'.repeat(20_000);
  chat.messages[1].proposal.files[0].diff = 'x'.repeat(20_000);
  chat.messages[1].proposal.files[0].content = 'x'.repeat(512 * 1024 - chat.messages[1].proposal.files[1].content.length);
  assert.deepEqual(validateCloudSnapshot(value), value);
  const large = { format: 'dua-cloud-snapshot', version: 1, memory: [], chats: [0, 1].map(i => ({
    id: 'large-' + i, model: 'Dua', draft: '', updatedAt: 0,
    messages: [{ role: 'user', text: '界'.repeat(800_000), state: 'complete' }]
  })) };
  assert.throws(() => validateCloudSnapshot(large), /Invalid cloud snapshot/);
  chat.messages[1].proposal.files[0].content += 'x';
  assert.throws(() => validateCloudSnapshot(value), /Invalid cloud snapshot/);
});

test('unknown fields are not copied into normalized snapshots', () => {
  const value = fresh();
  value.accessToken = 'never-store-token';
  value.chats[0].providerKey = 'never-store-key';
  value.chats[0].messages[0].attachments = [{ data: 'raw-bytes' }];
  value.chats[0].messages[1].proposal.privateKey = 'never-store-key';
  const normalized = validateCloudSnapshot(value);
  assert.deepEqual(normalized, fixture);
  assert.doesNotMatch(JSON.stringify(normalized), /never-store|raw-bytes/);
});

test('cloud reads refuse corrupt metadata without returning a partially stripped snapshot', async () => {
  const value = fresh(); value.chats[0].messages[1].codeRuns[0].code = 4;
  const fetcher = mockFetch(user(), Response.json([{ revision: 1, payload: value }]));
  const response = await handleCloudRead(request('GET'), environment(fetcher));
  assert.equal(response.status, 502);
  assert.equal((await response.json()).snapshot, undefined);
});

test('non-owner access is denied before extended snapshot data reaches storage', async () => {
  const denied = () => Response.json({ id: owner, email: 'other@example.com' });
  for (const method of ['PUT', 'GET']) {
    const fetcher = mockFetch(denied());
    const handler = method === 'GET' ? handleCloudRead : handleAccount;
    const response = await handler(request(method, fresh()), environment(fetcher));
    assert.equal(response.status, 403);
    assert.equal(fetcher.calls.length, 1);
  }
});
