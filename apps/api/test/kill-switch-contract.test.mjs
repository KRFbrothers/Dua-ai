import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { killSwitches, maintenanceMessage, DEFAULT_MAINTENANCE_MESSAGE, PAUSED } from '../src/core/kill-switches.mjs';

const document = readFileSync(new URL('../../../packages/contracts/KILL_SWITCHES.md', import.meta.url), 'utf8');
const contract = JSON.parse(document.match(/```json\n([\s\S]*?)\n```/)[1]);
const flags = contract.flags;
const names = text => [...new Set(text.match(/\bDUA_[A-Z_]+\b/g) || [])].sort();
const field = (state, path) => path.split('.').reduce((value, key) => value[key], state);

test('API documents the complete contract and implements its declared flag ownership', () => {
  const source = readFileSync(new URL('../src/core/kill-switches.mjs', import.meta.url), 'utf8');
  const header = source.match(/^\/\*\*([\s\S]*?)\*\//)[1];
  assert.deepEqual(names(header), Object.keys(flags).sort());
  assert.ok(header.includes('packages/contracts/KILL_SWITCHES.md'));
  const implemented = [...new Set([...source.matchAll(/\benv\.(DUA_[A-Z_]+)\b/g)].map(match => match[1]))].sort();
  const expected = Object.entries(flags).filter(([, flag]) => flag.implementedBy.includes('api')).map(([name]) => name).sort();
  assert.deepEqual(implemented, expected);
});

test('API switch defaults and truthy values follow the shared contract', () => {
  for (const [name, spec] of Object.entries(flags)) {
    if (spec.kind !== 'switch' || !spec.implementedBy.includes('api')) continue;
    assert.equal(field(killSwitches({}), spec.apiField), spec.default, name);
    for (const value of contract.truthy) {
      assert.equal(field(killSwitches({ [name]: ` ${value.toUpperCase()} ` }), spec.apiField), true, `${name}: ${value}`);
    }
    for (const value of ['', '0', 'false', 'no', 'off', 'unexpected']) {
      assert.equal(field(killSwitches({ [name]: value }), spec.apiField), false, `${name}: ${value}`);
    }
  }
});

test('API messages and existing cleanup rules follow the shared contract', () => {
  assert.equal(DEFAULT_MAINTENANCE_MESSAGE, contract.messages.maintenance);
  assert.deepEqual(PAUSED, { agent: contract.messages.agent, imagine: contract.messages.imagine, voice: contract.messages.voice });
  assert.equal(maintenanceMessage({}), contract.messages.maintenance);
  assert.equal(maintenanceMessage({ DUA_MAINTENANCE_MESSAGE: ' \u0000Back\nsoon\u007f ' }), 'Back soon');
  assert.equal(maintenanceMessage({ DUA_MAINTENANCE_MESSAGE: 'x'.repeat(500) }).length, contract.maintenanceMessageMaxLength);
  assert.deepEqual(killSwitches({ DUA_MAINTENANCE_MESSAGE: 'Back soon' }).maintenance, { enabled: false });
});
