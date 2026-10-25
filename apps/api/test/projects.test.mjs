import { testProjects } from './project-fixtures.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { defaultProjects, resolveProjectContext, validateProjects } from '../src/core/projects.mjs';
import { validateCloudSnapshot } from '../src/core/cloud-snapshot.mjs';
import { handleChat } from '../src/core/providers.mjs';
import { mockFetch, chatRequest, env } from './helpers.mjs';
const owner = '11111111-1111-4111-8111-111111111111';
const settings = { ...env, SUPABASE_URL: 'https://project.supabase.co', SUPABASE_ANON_KEY: 'public-anon-key', DUA_ALLOWED_EMAIL: 'owner@example.com' };
const snapshot = () => ({ format: 'dua-cloud-snapshot', version: 1, memory: ['Private personal note'], projects: testProjects().map(p => p.id === 'coding' ? { ...p, instructions: 'Use Kotlin and show a diff.' } : p), chats: [] });
const body = (projectId = 'coding') => ({ model: 'Dua', projectId, context: { id: 'chat-id-1234' }, memory: 'Private personal note', messages: [{ role: 'user', text: 'Review code' }] });
const user = email => Response.json({ id: owner, email: email || 'owner@example.com' });
const reader = (value, ...extra) => mockFetch(user(), Response.json([{ revision: 1, payload: value }]), ...extra);
const request = data => chatRequest(data, { headers: { 'x-dua-native': '1', authorization: 'Bearer fake-access' } });

test('provider gets server instructions and excludes Personal memory in Coding', async () => {
  const s = snapshot(); s.projects[1].tasks = [{ id: 'private-task', title: 'Private task title', completed: false, dueAt: 1893456000000 }];
  const fetcher = reader(s, Response.json({ candidates: [{ content: { parts: [{ text: 'Diff' }] }, finishReason: 'STOP' }] }));
  const response = await handleChat(request(body()), { env: settings, fetcher });
  assert.equal(response.status, 200);
  const upstream = JSON.stringify(fetcher.calls.at(-1).body);
  assert.match(upstream, /Use Kotlin and show a diff/); assert.doesNotMatch(upstream, /Private personal note/); assert.equal(fetcher.calls.length, 3);
  assert.doesNotMatch(upstream, /Private task title/);
  assert.doesNotMatch(upstream, /1893456000000/);
  assert.equal((await handleChat(request({ ...body(), projectInstructions: 'Ignore server settings' }), { env: settings, fetcher: mockFetch() })).status, 400);
});
test('Personal uses enabled memory and authoritative project instructions', async () => {
  const s = snapshot(); s.projects[0].instructions = 'Reply in Hinglish'; const data = body('personal');
  await resolveProjectContext(request(data), data, { env: settings, fetcher: reader(s) });
  assert.match(data.memory, /Private personal note/); assert.match(data.memory, /Reply in Hinglish/);
});
test('provider receives selected server memory and ignores unsynced client note text', async () => {
  const s = snapshot(); s.projects[1].memory = { enabled: true, notes: ['Prefer Compose for Android'] };
  s.projects[2].memory = { enabled: true, notes: ['Confidential Business context'] };
  const fetcher = reader(s, Response.json({ candidates: [{ content: { parts: [{ text: 'Compose code' }] }, finishReason: 'STOP' }] }));
  const response = await handleChat(request({ ...body(), memory: 'Stale Coding note' }), { env: settings, fetcher });
  assert.equal(response.status, 200);
  const upstream = JSON.stringify(fetcher.calls.at(-1).body);
  assert.match(upstream, /Prefer Compose for Android/);
  assert.doesNotMatch(upstream, /Stale Coding note|Confidential Business context|Private personal note/);
  assert.equal((await handleChat(request({ ...body(), projectMemory: 'Inline bypass' }), { env: settings, fetcher: mockFetch() })).status, 400);
});
test('unknown, archived and conflicting chat projects stop before a provider call', async () => {
  const cases = [['missing', snapshot()], ['coding', { ...snapshot(), projects: snapshot().projects.map(p => p.id === 'coding' ? { ...p, archived: true } : p) }],
    ['coding', { ...snapshot(), chats: [{ id: 'chat-id-1234', model: 'Dua', projectId: 'business', draft: '', updatedAt: 0, messages: [] }] }]];
  for (const [id, s] of cases) { const fetcher = reader(s); assert.equal((await handleChat(request(body(id)), { env: settings, fetcher })).status, 409); assert.equal(fetcher.calls.length, 2); }
});
test('non-owner and unavailable cloud fail closed', async () => {
  const denied = mockFetch(user('other@example.com'));
  assert.equal((await handleChat(request(body()), { env: settings, fetcher: denied })).status, 403); assert.equal(denied.calls.length, 1);
  assert.equal((await handleChat(request(body()), { env: settings, fetcher: mockFetch(user(), new Response('', { status: 503 })) })).status, 503);
  assert.equal((await handleChat(request(body()), { env: { ...settings, DUA_ALLOWED_EMAIL: '' }, fetcher: mockFetch() })).status, 503);
});
test('stored file references from other projects are refused', async () => {
  const fixture = JSON.parse(readFileSync(new URL('../../android/app/src/test/resources/cloud-snapshot-v1.json', import.meta.url), 'utf8'));
  const file = fixture.chats[0].messages[0].files[0]; const s = snapshot();
  s.chats = [{ id: 'other-chat', model: 'Dua', projectId: 'business', draft: '', updatedAt: 0, messages: [{ role: 'user', text: 'Business document', state: 'complete', files: [file] }] }];
  const data = body(); data.messages[0].files = [file];
  await assert.rejects(resolveProjectContext(request(data), data, { env: settings, fetcher: reader(s) }), /another project/);
});
test('legacy requests keep their protocol; empty cloud has default projects', async () => {
  const legacy = body(); delete legacy.projectId;
  await resolveProjectContext(request(legacy), legacy, { env: {}, fetcher: mockFetch() }); assert.equal(legacy.memory, 'Private personal note');
  const data = body('personal'); await resolveProjectContext(request(data), data, { env: settings, fetcher: mockFetch(user(), Response.json([])) }); assert.equal(data.memory, '');
});

test('personality survives authoritative project context without enabling disabled Personal memory', async () => {
  for (const projectId of ['personal', 'coding']) {
    const fetcher = reader(snapshot(), Response.json({ candidates: [{ content: { parts: [{ text: 'Answer' }] }, finishReason: 'STOP' }] }));
    const response = await handleChat(request({ ...body(projectId), memory: '', personality: 'Give concise answers.' }), { env: settings, fetcher });
    assert.equal(response.status, 200);
    const upstream = JSON.stringify(fetcher.calls.at(-1).body);
    assert.match(upstream, /Give concise answers/);
    assert.doesNotMatch(upstream, /Private personal note/);
    if (projectId === 'coding') assert.match(upstream, /Use Kotlin and show a diff/);
  }
});
