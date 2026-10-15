import { testProjects } from './project-fixtures.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import { defaultProjects, resolveProjectContext } from '../src/core/projects.mjs';
import { validateCloudSnapshot } from '../src/core/cloud-snapshot.mjs';
import { mockFetch } from './helpers.mjs';

const owner = '11111111-1111-4111-8111-111111111111';
const settings = { SUPABASE_URL: 'https://project.supabase.co', SUPABASE_ANON_KEY: 'public-anon', DUA_ALLOWED_EMAIL: 'owner@example.com' };
const snapshot = () => ({ format: 'dua-cloud-snapshot', version: 1, chats: [], memory: ['Server Personal note'],
  projects: testProjects().map(p => p.id === 'personal' ? p : { ...p, memory: { enabled: true, notes: [`Server ${p.name} note`] } }) });
const request = () => new Request('https://dua.example/api/chat', { headers: { authorization: 'Bearer fake-access' } });
const reader = s => mockFetch(Response.json({ id: owner, email: 'owner@example.com' }), Response.json([{ revision: 1, payload: s }]));
const data = projectId => ({ projectId, context: { id: 'new-chat' }, memory: 'Forged or stale client note', messages: [{ role: 'user', text: 'Hello' }] });

test('project memory survives validation alongside tasks and default legacy projects', () => {
  const s = snapshot(); s.projects[1].tasks = [{ id: 't', title: 'Review', completed: false, dueAt: 1893456000000 }];
  s.projects[1].memory = { enabled: false, notes: ['🙂'.repeat(100), 'Line one\nLine two'] };
  assert.deepEqual(validateCloudSnapshot(s), s);
  s.projects = testProjects(); assert.deepEqual(validateCloudSnapshot(s), s);
  s.projects[1].memory = { enabled: false, notes: [] }; assert.deepEqual(validateCloudSnapshot(s), s);
});
test('invalid types, extra fields and limits fail before snapshot persistence', () => {
  const invalid = [null, [], { enabled: 'true', notes: [] }, { enabled: true }, { notes: [] },
    { enabled: true, notes: [7] }, { enabled: true, notes: [' '] }, { enabled: false, notes: ['x'.repeat(201)] },
    { enabled: true, notes: Array(11).fill('Note') }, { enabled: false, notes: [], unknown: true }];
  for (const memory of invalid) { const s = snapshot(); s.projects[1].memory = memory; assert.throws(() => validateCloudSnapshot(s)); }
  const s = snapshot(); s.projects[0].memory = { enabled: true, notes: ['Duplicate Personal note'] }; assert.throws(() => validateCloudSnapshot(s));
});
test('only selected project server notes reach context; client and other projects are excluded', async () => {
  for (const id of ['coding', 'business', 'personal']) {
    const d = data(id); const s = snapshot(); s.projects.find(p => p.id === id).instructions = `Instructions for ${id}`;
    await resolveProjectContext(request(), d, { env: settings, fetcher: reader(s) });
    assert.match(d.memory, new RegExp(`Server ${id === 'personal' ? 'Personal' : id === 'coding' ? 'Coding' : 'Business'} note`));
    assert.match(d.memory, new RegExp(`Instructions for ${id}`));
    assert.doesNotMatch(d.memory, /Forged or stale/);
    for (const other of ['Personal', 'Coding', 'Business'].filter(n => n.toLowerCase() !== id)) assert.doesNotMatch(d.memory, new RegExp(`Server ${other} note`));
  }
});
test('off retains notes in snapshot but excludes them from prompt; Personal keeps device enable signal', async () => {
  const s = snapshot(); s.projects[1].memory.enabled = false;
  const d = data('coding'); await resolveProjectContext(request(), d, { env: settings, fetcher: reader(s) });
  assert.equal(d.memory, ''); assert.deepEqual(validateCloudSnapshot(s).projects[1].memory.notes, ['Server Coding note']);
  const personal = data('personal'); personal.memory = '';
  await resolveProjectContext(request(), personal, { env: settings, fetcher: reader(s) }); assert.equal(personal.memory, '');
});
test('missing project memory defaults to off without consuming Personal or client notes', async () => {
  const s = snapshot(); delete s.projects[1].memory;
  const d = data('coding'); await resolveProjectContext(request(), d, { env: settings, fetcher: reader(s) }); assert.equal(d.memory, '');
});
test('archived and non-owner context still fail closed before exposing project notes', async () => {
  const s = snapshot(); s.projects[1].archived = true;
  await assert.rejects(resolveProjectContext(request(), data('coding'), { env: settings, fetcher: reader(s) }), /archived/);
  const denied = mockFetch(Response.json({ id: owner, email: 'other@example.com' }));
  await assert.rejects(resolveProjectContext(request(), data('coding'), { env: settings, fetcher: denied }), error => error.status === 403);
  assert.equal(denied.calls.length, 1);
});
