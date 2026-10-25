import test from 'node:test';
import assert from 'node:assert/strict';
import { handleChat, validateChat } from '../src/core/providers.mjs';
import { chatRequest, mockFetch, env } from './helpers.mjs';

const body = extra => ({ model: 'Dua', messages: [{ role: 'user', text: 'Review this' }], ...extra });
const reply = () => Response.json({ candidates: [{ content: { parts: [{ text: 'Answer' }] }, finishReason: 'STOP' }] });

test('personality is a bounded optional instruction and older requests remain valid', () => {
  assert.equal(validateChat(body()), null);
  assert.equal(validateChat(body({ personality: 'x'.repeat(1000) })), null);
  for (const personality of [null, false, 12, [], {}, '', '   ', 'x'.repeat(1001)]) {
    assert.deepEqual(validateChat(body({ personality })), ['Invalid personality instruction.', 400]);
  }
});

test('style is placed in system context before memory without rewriting user messages', async () => {
  const fetcher = mockFetch(reply());
  const response = await handleChat(chatRequest(body({ personality: 'Use production standards.', memory: 'Prefer Kotlin' })), { env, fetcher });
  assert.equal(response.status, 200);
  const upstream = fetcher.calls[0].body;
  const system = upstream.systemInstruction.parts.map(part => part.text).join('\n');
  assert.ok(system.indexOf('Use production standards.') < system.indexOf('Prefer Kotlin'));
  assert.match(system, /never override system instructions/);
  assert.equal(upstream.contents[0].parts[0].text, 'Review this');
});

test('personality support is advertised by config', async () => {
  const response = await handleChat(new Request('https://dua.test/api/config'), { env });
  assert.deepEqual((await response.json()).personalization, { personality: true });
});
