import test from 'node:test';
import assert from 'node:assert/strict';
import { attachmentParts, isTextType, providerType, MAX_TEXT_FILE_BYTES } from '../src/core/attachments.mjs';
import { b64 } from './helpers.mjs';

const file = (name, mimeType, data) => ({ name, mimeType, data });

test('text and code files become inline UTF-8 text parts', () => {
  for (const [name, type] of [['data.csv', 'text/csv'], ['notes.md', 'text/markdown'], ['cfg.json', 'application/json'], ['a.yaml', 'application/yaml'], ['Main.kt', 'text/plain'], ['app.log', 'text/plain'], ['page.xml', 'text/xml']]) {
    assert.deepEqual(attachmentParts([file(name, type, b64('\uFEFFhello,world'))]), [{ text: `Attached text file (${name}):\nhello,world` }]);
  }
  assert.equal(providerType('text/csv'), 'text/plain');
  assert.equal(providerType('image/png'), 'image/png');
  assert.equal(isTextType('application/zip'), false);
});

test('text files are limited to 256 KB, must be UTF-8 and not binary', () => {
  assert.doesNotThrow(() => attachmentParts([file('ok.txt', 'text/plain', b64('a'.repeat(MAX_TEXT_FILE_BYTES - 1)))]));
  assert.throws(() => attachmentParts([file('big.csv', 'text/csv', b64('a'.repeat(MAX_TEXT_FILE_BYTES + 3)))]), e => e.status === 413 && /256 KB/.test(e.message));
  assert.throws(() => attachmentParts([file('latin1.txt', 'text/plain', Buffer.from([0x63, 0x61, 0x66, 0xe9]).toString('base64'))]), /not valid UTF-8/);
  assert.throws(() => attachmentParts([file('bin.txt', 'text/plain', b64('a\u0000b'))]), e => e.status === 415);
});

test('unsupported types and mismatched image bytes are rejected with clear messages', () => {
  assert.throws(() => attachmentParts([file('a.zip', 'application/zip', b64('PK'))]), e => e.status === 415 && /UTF-8 text\/code file/.test(e.message));
  assert.throws(() => attachmentParts([file('a.png', 'image/png', b64('not png'))]), /match/);
  assert.throws(() => attachmentParts([1, 2, 3, 4]), /Maximum 3/);
  const png = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10, 0]).toString('base64');
  assert.deepEqual(attachmentParts([file('p.png', 'image/png', png)])[1], { inlineData: { mimeType: 'image/png', data: png } });
});
