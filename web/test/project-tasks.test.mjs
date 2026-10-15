import { testProjects } from './project-fixtures.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import { defaultProjects } from '../src/core/projects.mjs';
import { validateCloudSnapshot } from '../src/core/cloud-snapshot.mjs';

const task = () => ({ id: 'task-1', title: 'Review changes', completed: false });
const snapshot = () => ({ format: 'dua-cloud-snapshot', version: 1, chats: [], memory: [],
  projects: testProjects().map(p => p.id === 'coding' ? { ...p, tasks: [task()] } : p) });
test('cloud validator retains task completion and accepts old project definitions', () => {
  const value = snapshot(); value.projects[1].tasks[0].completed = true;
  assert.deepEqual(validateCloudSnapshot(value), value);
  delete value.projects[1].tasks;
  assert.deepEqual(validateCloudSnapshot(value), value);
  value.projects[1].tasks = [];
  assert.deepEqual(validateCloudSnapshot(value), value);
});
test('task type, IDs, fields and UTF-16 limits are validated before saving', () => {
  const mutations = [p => p.tasks = null, p => p.tasks = {}, p => p.tasks[0].completed = 'true',
    p => p.tasks[0].title = ' ', p => p.tasks[0].title = 'x'.repeat(201), p => p.tasks[0].id = '../bad',
    p => p.tasks[0].unknown = true, p => delete p.tasks[0].completed, p => p.tasks.push(task())];
  for (const mutate of mutations) {
    const value = snapshot(); mutate(value.projects[1]);
    assert.throws(() => validateCloudSnapshot(value));
  }
  const value = snapshot(); value.projects[1].tasks = Array.from({ length: 100 }, (_, i) => ({ ...task(), id: `t${i}`, title: '🙂'.repeat(100) }));
  assert.deepEqual(validateCloudSnapshot(value), value);
  value.projects[1].tasks.push({ ...task(), id: 'last' });
  assert.throws(() => validateCloudSnapshot(value));
});
test('optional due timestamps survive snapshots and invalid values are rejected', () => {
  const value = snapshot(); value.projects[1].tasks[0].dueAt = 1893456000000;
  assert.deepEqual(validateCloudSnapshot(value), value);
  for (const dueAt of [null, '123', 0, -1, 1.5, 253402300800000, true]) {
    value.projects[1].tasks[0].dueAt = dueAt;
    assert.throws(() => validateCloudSnapshot(value));
  }
});
