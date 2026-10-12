import test from 'node:test';
import assert from 'node:assert/strict';
import { messagesHash, prepareReplySave, savedReplyStream, validateReplySave } from '../src/core/reply-persistence.mjs';
import { eventStreamResponse } from '../src/core/sse.mjs';
import { readEvents } from './helpers.mjs';
import { readFileSync } from 'node:fs';

const owner = '11111111-1111-4111-8111-111111111111';
const env = { SUPABASE_URL: 'https://project.supabase.co', SUPABASE_ANON_KEY: 'public-key', DUA_ALLOWED_EMAIL: 'owner@example.com' };
const turn = (id, role, text) => ({ id, role, text, state: 'complete' });
const original = () => ({ id: 'chat-12345', model: 'Dua', projectId: 'personal', draft: 'Five tips', updatedAt: 1, pinned: true, title: 'Keep title',
  messages: [turn('old-user', 'user', 'Hello'), turn('old-reply', 'model', 'Hi')] });
const snapshot = () => ({ format: 'dua-cloud-snapshot', version: 1, chats: [original(), { ...original(), id: 'other-chat' }], memory: ['Keep notes'], deletedChatIds: ['gone'] });
const request = signal => new Request('https://dua.test/api/chat', { headers: { 'x-dua-native': '1', authorization: 'Bearer fake-access' }, signal });
function fixture(payload = snapshot(), revision = 3) {
  const state = { payload: structuredClone(payload), revision, calls: [], writes: [], rpc: null };
  state.fetcher = async (url, init = {}) => {
    state.calls.push({ url, init });
    if (url.endsWith('/auth/v1/user')) return Response.json({ id: owner, email: env.DUA_ALLOWED_EMAIL });
    if (url.includes('/rest/v1/dua_cloud_snapshots?')) return Response.json(state.revision ? [{ revision: state.revision, payload: structuredClone(state.payload) }] : []);
    assert.match(url, /\/rpc\/dua_write_cloud_snapshot$/);
    assert.equal(init.headers.Authorization, 'Bearer fake-access'); assert.equal(init.headers.apikey, 'public-key');
    const body = JSON.parse(init.body); state.writes.push(body);
    if (state.rpc) return state.rpc(body);
    if (body.expected_revision !== state.revision) return Response.json({ conflict: true, revision: state.revision });
    state.payload = body.next_payload; state.revision++; return Response.json({ revision: state.revision });
  };
  return state;
}
function data(base = original().messages, replacing = false) {
  const user = replacing ? base.at(-2) : turn('new-user', 'user', 'Five tips');
  return { model: 'Dua', stream: true, projectId: 'personal', context: { id: 'chat-12345' }, messages: [{ role: 'user', text: user.text }],
    persistence: { chatId: 'chat-12345', ownerId: owner, baseHash: messagesHash(base), replyId: replacing ? base.at(-1).id : 'new-reply',
      user, replaceReply: replacing, turnIds: [user.id], submittedDraft: replacing ? '' : 'Five tips', ...(replacing ? { prefixHash: messagesHash(base.slice(0, -2)) } : {}) } };
}
async function prepare(state, value = data(), req = request()) {
  assert.equal(validateReplySave(value), null);
  return prepareReplySave(req, value, { env, fetcher: state.fetcher });
}
const source = events => signal => eventStreamResponse(async send => events.forEach(send), { signal });
const success = [{ type: 'text', delta: 'A complete reply.' }, { type: 'done', truncated: false }];
const complete = (prepared, events = success, signal = request().signal) => readEvents(savedReplyStream(source(events), prepared, { signal }));

test('the shared Android digest fixture matches regardless of object key order', () => {
  const fixture = JSON.parse(readFileSync(new URL('../../app/src/test/resources/reply-digest-v1.json', import.meta.url), 'utf8'));
  assert.equal(messagesHash(fixture.messages), fixture.digest);
  assert.equal(messagesHash([{ x: 2, y: '✨' }]), messagesHash([{ y: '✨', x: 2.0 }]));
});

test('a completed reply is saved before saved/done and retains unrelated history, memory and metadata', async () => {
  const state = fixture(); const prepared = await prepare(state);
  const events = await complete(prepared);
  assert.deepEqual(events.map(event => event.type), ['text', 'saved', 'done']);
  assert.equal(state.writes.length, 1);
  const chat = state.payload.chats.find(chat => chat.id === 'chat-12345');
  assert.deepEqual(chat.messages.map(message => message.id), ['old-user', 'old-reply', 'new-user', 'new-reply']);
  assert.equal(chat.title, 'Keep title'); assert.equal(chat.pinned, true); assert.equal(chat.draft, '');
  assert.deepEqual(state.payload.chats.find(chat => chat.id === 'other-chat'), { ...original(), id: 'other-chat' });
  assert.deepEqual(state.payload.memory, ['Keep notes']); assert.deepEqual(state.payload.deletedChatIds, ['gone']);
  assert.equal(events[1].revision, 4); assert.equal(events[1].replyHash, messagesHash([chat.messages.at(-1)]));
});
test('fresh concurrent draft, title, pin and memory edits survive the targeted save', async () => {
  const state = fixture(); const prepared = await prepare(state);
  const changed = state.payload.chats[0]; changed.draft = 'Other device draft'; changed.title = 'New title'; changed.pinned = false;
  state.payload.memory = ['New server note']; state.revision++;
  const events = await complete(prepared);
  const chat = state.payload.chats.find(chat => chat.id === 'chat-12345');
  assert.equal(chat.draft, 'Other device draft'); assert.equal(chat.title, 'New title'); assert.equal(chat.pinned, false);
  assert.deepEqual(state.payload.memory, ['New server note']); assert.equal(events[1].chat.title, 'New title'); assert.equal(events[1].original.title, 'Keep title');
});
test('a concurrent edit, deletion or cloud clear blocks saving without resurrecting data', async () => {
  for (const change of ['edit', 'delete', 'clear']) {
    const state = fixture(); const prepared = await prepare(state);
    if (change === 'edit') state.payload.chats[0].messages[1].text = 'Other device answer';
    else { state.payload.chats = []; if (change === 'delete') state.payload.deletedChatIds.push('chat-12345'); }
    state.revision++;
    const events = await complete(prepared);
    assert.deepEqual(events.map(event => event.type), ['text', 'error']); assert.equal(events[1].status, 409); assert.equal(state.writes.length, 0);
  }
});
test('CAS retries once for an unrelated change without calling the provider again', async () => {
  const state = fixture(); const prepared = await prepare(state); let calls = 0;
  state.rpc = body => {
    if (state.writes.length === 1) { state.payload.memory = ['Concurrent note']; state.revision++; return Response.json({ conflict: true, revision: state.revision }); }
    state.payload = body.next_payload; state.revision++; return Response.json({ revision: state.revision });
  };
  const events = await readEvents(savedReplyStream(signal => { calls++; return source(success)(signal); }, prepared, { signal: request().signal }));
  assert.equal(calls, 1); assert.equal(state.writes.length, 2); assert.deepEqual(state.payload.memory, ['Concurrent note']); assert.equal(events.at(-1).type, 'done');
});
test('CAS cannot overwrite a target chat changed during the write', async () => {
  const state = fixture(); const prepared = await prepare(state);
  state.rpc = () => { state.payload.chats[0].messages[1].text = 'Concurrent answer'; state.revision++; return Response.json({ conflict: true, revision: state.revision }); };
  const events = await complete(prepared);
  assert.equal(state.writes.length, 1); assert.equal(events.at(-1).status, 409);
});
test('an RPC error or lost acknowledgement retains text and never claims completion', async () => {
  for (const lost of [false, true]) {
    const state = fixture(); const prepared = await prepare(state);
    state.rpc = body => {
      if (!lost) return Response.json({ error: 'private upstream detail' }, { status: 503 });
      state.payload = body.next_payload; state.revision++; throw new Error('private connection detail');
    };
    const events = await complete(prepared);
    assert.deepEqual(events.map(event => event.type), ['text', 'error']); assert.equal(events.at(-1).status, 503);
    assert.doesNotMatch(events.at(-1).message, /private/);
    if (lost) { await assert.rejects(prepare(state), { status: 409 }); assert.equal(state.writes.length, 1); }
  }
});
test('provider errors, missing completion or empty answers are never stored', async () => {
  for (const events of [[{ type: 'text', delta: 'Partial' }, { type: 'error', message: 'Provider failed', status: 502 }], [{ type: 'text', delta: 'Partial' }], [{ type: 'done', truncated: false }]]) {
    const state = fixture(); const prepared = await prepare(state);
    const result = await complete(prepared, events);
    assert.equal(result.at(-1).type, 'error'); assert.equal(state.writes.length, 0);
  }
});
test('cancellation before provider completion does not write a server reply', async () => {
  const state = fixture(); const stop = new AbortController(); const req = request(stop.signal); const prepared = await prepare(state, data(), req);
  const response = savedReplyStream(signal => eventStreamResponse(async send => {
    send({ type: 'text', delta: 'Partial' });
    await new Promise(resolve => signal.addEventListener('abort', resolve, { once: true }));
  }, { signal }), prepared, { signal: stop.signal });
  const reader = response.body.getReader(); await reader.read(); await reader.cancel();
  assert.equal(state.writes.length, 0);
});
test('retry replaces only the last response and can recover an unsaved stopped turn', async () => {
  const state = fixture(); const events = await complete(await prepare(state, data(original().messages, true)));
  const chat = state.payload.chats.find(chat => chat.id === 'chat-12345');
  assert.deepEqual(chat.messages.map(message => message.id), ['old-user', 'old-reply']); assert.equal(chat.messages[1].text, 'A complete reply.'); assert.equal(events.at(-1).type, 'done');
  const empty = fixture({ format: 'dua-cloud-snapshot', version: 1, chats: [], memory: [] }, 0);
  const stopped = [turn('new-user', 'user', 'Five tips'), { ...turn('new-reply', 'model', ''), state: 'stopped' }];
  await complete(await prepare(empty, data(stopped, true)));
  assert.deepEqual(empty.payload.chats[0].messages.map(message => message.id), ['new-user', 'new-reply']);
});
test('preflight rejects stale history, wrong owner, deleted IDs and invalid metadata before any write', async () => {
  for (const change of [value => value.persistence.baseHash = '0'.repeat(64), value => value.persistence.ownerId = 'other-owner', value => value.persistence.chatId = 'gone']) {
    const state = fixture(); const value = data(); change(value);
    if (value.persistence.chatId === 'gone') value.context.id = 'gone';
    await assert.rejects(prepare(state, value)); assert.equal(state.writes.length, 0);
  }
  for (const change of [value => value.stream = false, value => value.persistence.replyId = value.persistence.user.id, value => value.persistence.extra = true,
    value => value.persistence.turnIds = [], value => value.persistence.user.role = 'model']) {
    const value = data(); change(value); assert.deepEqual(validateReplySave(value), ['Invalid server reply save request.', 400]);
  }
});

test('expired auth during commit is a save error and cannot request automatic regeneration', async () => {
  const captured = fixture(); const originalFetcher = captured.fetcher; let expired = false;
  captured.fetcher = (url, init) => expired && url.endsWith('/auth/v1/user') ? Response.json({ error: 'expired' }, { status: 401 }) : originalFetcher(url, init);
  const ready = await prepare(captured); expired = true;
  const events = await complete(ready);
  assert.equal(events.at(-1).status, 503); assert.equal(captured.writes.length, 0);
});

test('thinking, code, source footers, repository metadata and summary are persisted with their IDs', async () => {
  const state = fixture(); const value = data(); value.persistence.turnIds = ['old-user', 'new-user'];
  value.messages = [{ role: 'user', text: 'Hello' }, { role: 'user', text: 'Five tips' }];
  const ready = await prepare(state, value);
  const events = await complete(ready, [
    { type: 'context', summary: 'Earlier greeting', covered: 1 },
    { type: 'thinking', delta: 'Consider tips' },
    { type: 'code', index: 0, language: 'PYTHON', code: 'print(1)' }, { type: 'code', index: 0, output: '1', outcome: 'ok' },
    { type: 'build', repository: 'owner/repo', branch: 'main', files: [{ path: 'README.md', bytes: 42 }], rounds: 1 },
    { type: 'text', delta: 'Tips\n\nSources:\n[1] Example\nhttps://example.com/' }, { type: 'done', truncated: false }
  ]);
  const chat = state.payload.chats.find(chat => chat.id === 'chat-12345'); const reply = chat.messages.at(-1);
  assert.equal(reply.thinking, 'Consider tips'); assert.equal(reply.thinkingSeconds, 1);
  assert.deepEqual(reply.codeRuns, [{ language: 'python', code: 'print(1)', output: '1', outcome: 'ok' }]);
  assert.equal(reply.build.repository, 'owner/repo'); assert.equal(chat.summary.throughId, 'old-user');
  assert.match(reply.text, /Sources:/); assert.equal(events.at(-2).replyHash, messagesHash([reply]));
});

test('thinking whitespace matches Android blank handling without trimming real thought content', async () => {
  for (const thinking of [' \n\t\u001c\u00a0\u2007\u202f\u3000', ' \nConsider tips\n ', '\ufeff']) {
    const state = fixture();
    const events = await complete(await prepare(state), [{ type: 'thinking', delta: thinking }, ...success]);
    const reply = state.payload.chats.find(chat => chat.id === 'chat-12345').messages.at(-1); const receipt = events.at(-2);
    if (thinking.startsWith(' \n\t')) {
      assert.equal(reply.thinking, undefined); assert.equal(reply.thinkingSeconds, undefined);
      assert.equal(receipt.thinkingSeconds, undefined);
      assert.equal(receipt.replyHash, messagesHash([turn('new-reply', 'model', 'A complete reply.')]));
    } else {
      assert.equal(reply.thinking, thinking); assert.equal(reply.thinkingSeconds, 1);
      assert.equal(receipt.replyHash, messagesHash([reply]));
    }
    assert.equal(events.at(-1).type, 'done');
  }
});

test('generation reserve cancels a hanging model without consuming the independent save signal', async () => {
  const stop = new AbortController(); let generationSignal; let writes = 0;
  const response = savedReplyStream(signal => {
    generationSignal = signal;
    return new Promise(resolve => signal.addEventListener('abort', () => resolve(Response.json({ error: 'Generation time limit reached' }, { status: 504 })), { once: true }));
  }, { commit: async () => { writes++; } }, { signal: stop.signal, deadlineAt: Date.now() + 100 });
  const keepAlive = setTimeout(() => {}, 1000);
  try {
    const events = await readEvents(response);
    assert.equal(events.at(-1).status, 504); assert.equal(generationSignal.aborted, true);
    assert.equal(stop.signal.aborted, false); assert.equal(writes, 0);
  } finally { clearTimeout(keepAlive); }
});

test('stored-file references, bounded text context and fresh transcripts survive completion', async () => {
  const shared = JSON.parse(readFileSync(new URL('../../app/src/test/resources/cloud-snapshot-v1.json', import.meta.url), 'utf8'));
  const reference = shared.chats.flatMap(chat => chat.messages).find(message => message.role === 'user' && message.files?.length).files[0];
  const state = fixture(); const value = data();
  value.persistence.user.files = [reference]; value.persistence.user.attachmentText = 'Attached text context';
  value.messages[0].files = [reference];
  const ready = await prepare(state, value);
  await complete(ready, [{ type: 'files', files: [{ path: reference.path, transcript: 'Updated transcript' }] }, ...success]);
  const user = state.payload.chats.find(chat => chat.id === 'chat-12345').messages.at(-2);
  assert.equal(user.attachmentText, 'Attached text context'); assert.equal(user.files[0].path, reference.path); assert.equal(user.files[0].transcript, 'Updated transcript');
});
