import { testProjects } from './project-fixtures.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import { defaultProjects, validateProjects } from '../src/core/projects.mjs';
import { validateCloudSnapshot } from '../src/core/cloud-snapshot.mjs';
const snapshot = () => ({ format: 'dua-cloud-snapshot', version: 1, memory: [], projects: testProjects(), chats: [] });
test('project snapshot round-trip and legacy Personal chats', () => {
  const value = snapshot(); value.chats = [{ id: 'chat-id-1234', model: 'Dua', projectId: 'coding', draft: '', updatedAt: 0, messages: [] }];
  assert.deepEqual(validateCloudSnapshot(value), value);
  delete value.chats[0].projectId; assert.deepEqual(validateCloudSnapshot(value), value);
});
test('invalid metadata and dangling project references are rejected', () => {
  const cases = [s => s.projects[0].instructions = 'x'.repeat(4001), s => s.projects[0].archived = 'false', s => s.projects.push(s.projects[0]),
    s => s.projects = [], s => s.projects.forEach(p => p.archived = true), s => s.projects[0].name = ' ', s => s.projects[0].id = '../personal',
    s => s.projects[0].unexpected = true, s => s.chats = [{ id: 'a', model: 'Dua', projectId: 'missing', draft: '', updatedAt: 0, messages: [] }]];
  for (const mutate of cases) { const s = snapshot(); mutate(s); assert.throws(() => validateCloudSnapshot(s)); }
  assert.throws(() => validateCloudSnapshot({ ...snapshot(), projects: undefined, chats: [{ id: 'a', model: 'Dua', projectId: 'custom', draft: '', updatedAt: 0, messages: [] }] }));
  assert.throws(() => validateProjects(Array.from({ length: 21 }, (_, i) => ({ ...defaultProjects()[0], id: i ? `p${i}` : 'personal' }))));
});
