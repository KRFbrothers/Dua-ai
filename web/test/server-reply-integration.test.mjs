import test from 'node:test';
import assert from 'node:assert/strict';
import { handleService } from '../src/server/service-handler.mjs';
import { messagesHash } from '../src/core/reply-persistence.mjs';
import { chatRequest, readEvents, sseResponse, sse, env } from './helpers.mjs';

const owner = '11111111-1111-4111-8111-111111111111';
const settings = { ...env, DUA_REQUIRE_LOGIN: '1', DUA_ALLOWED_EMAIL: 'owner@example.com', SUPABASE_URL: 'https://project.supabase.co', SUPABASE_ANON_KEY: 'public-key' };
const prompt = () => ({ model: 'Dua', stream: true, context: { id: 'chat-12345' }, projectId: 'personal', messages: [{ role: 'user', text: 'Five tips' }], persistence: {
  ownerId: owner, chatId: 'chat-12345', baseHash: messagesHash([]), user: { id: 'new-user', role: 'user', state: 'complete', text: 'Five tips' },
  replyId: 'new-reply', replaceReply: false, turnIds: ['new-user'], submittedDraft: 'Five tips'
} });
function fixture(email = settings.DUA_ALLOWED_EMAIL) {
  let payload = { format: 'dua-cloud-snapshot', version: 1, chats: [], memory: [] }, revision = 0;
  const calls = [];
  const fetcher = async (url, init = {}) => {
    calls.push(url);
    if (url.endsWith('/auth/v1/user')) return Response.json({ id: owner, email });
    if (url.includes('/rest/v1/dua_cloud_snapshots?')) return Response.json(revision ? [{ revision, payload }] : []);
    if (url.endsWith('/rpc/dua_write_cloud_snapshot')) {
      const data = JSON.parse(init.body); assert.equal(data.expected_revision, revision);
      payload = data.next_payload; revision++; return Response.json({ revision });
    }
    assert.match(url, /:streamGenerateContent\?alt=sse$/);
    return sseResponse([sse({ candidates: [{ content: { parts: [{ text: 'Five useful tips.' }] }, finishReason: 'STOP' }] })]);
  };
  return { calls, handler: { fetch: request => handleService(request, { env: settings, fetcher }) }, stored: () => payload };
}
const req = data => chatRequest(data, { headers: { 'x-dua-native': '1', authorization: 'Bearer fake-access' } });
test('native owner chat streams through the actual service handler and saves before completion without cloud sync', async () => {
  const state = fixture(); const response = await state.handler.fetch(req(prompt()));
  const events = await readEvents(response);
  assert.deepEqual(events.map(event => event.type), ['text', 'saved', 'done']);
  assert.equal(events[1].ownerId, owner); assert.equal(events[1].revision, 1);
  assert.deepEqual(state.stored().chats[0].messages.map(message => message.id), ['new-user', 'new-reply']);
  assert.equal(state.stored().chats[0].messages[1].text, 'Five useful tips.');
  assert.equal(state.calls.filter(url => url.includes('generativelanguage.googleapis.com')).length, 1);
  assert.equal(state.calls.filter(url => url.includes('/rpc/')).length, 1);
});
test('owner or stale-baseline failures cannot reach generation or storage writes', async () => {
  const foreign = fixture('stranger@example.com');
  assert.equal((await foreign.handler.fetch(req(prompt()))).status, 403);
  assert.equal(foreign.calls.length, 1);
  const stale = fixture(); const value = prompt(); value.persistence.baseHash = '0'.repeat(64);
  assert.equal((await stale.handler.fetch(req(value))).status, 409);
  assert.ok(stale.calls.every(url => !url.includes('generativelanguage.googleapis.com') && !url.includes('/rpc/')));
});
