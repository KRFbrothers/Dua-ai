import { inflateRawSync, crc32 } from 'node:zlib';

const MAX_ARCHIVE = 50 * 1024 * 1024;
const MAX_PART = 16 * 1024 * 1024;
const MAX_INFLATED = 32 * 1024 * 1024;
const utf8 = new TextDecoder('utf-8', { fatal: true });
const invalid = () => { throw Error('Invalid or oversized spreadsheet archive/XML.'); };

/** Read only requested ZIP parts, in memory. No paths are written and inflation is bounded. */
export function spreadsheetZip(input) {
  const bytes = Buffer.from(input);
  if (bytes.length < 22 || bytes.length > MAX_ARCHIVE) invalid();
  let end = bytes.length - 22;
  for (; end >= Math.max(0, bytes.length - 65557); end--)
    if (bytes.readUInt32LE(end) === 0x06054b50 && end + 22 + bytes.readUInt16LE(end + 20) === bytes.length) break;
  if (end < 0 || bytes.readUInt32LE(end) !== 0x06054b50) invalid();
  const count = bytes.readUInt16LE(end + 10), size = bytes.readUInt32LE(end + 12), offset = bytes.readUInt32LE(end + 16);
  if (bytes.readUInt16LE(end + 4) || bytes.readUInt16LE(end + 6) || count !== bytes.readUInt16LE(end + 8) || count > 4096 || offset + size !== end) invalid();
  const entries = new Map();
  let cursor = offset, inflated = 0;
  for (let i = 0; i < count; i++) {
    if (cursor + 46 > end || bytes.readUInt32LE(cursor) !== 0x02014b50) invalid();
    const flags = bytes.readUInt16LE(cursor + 8), method = bytes.readUInt16LE(cursor + 10);
    const checksum = bytes.readUInt32LE(cursor + 16), compressed = bytes.readUInt32LE(cursor + 20), length = bytes.readUInt32LE(cursor + 24);
    const names = bytes.readUInt16LE(cursor + 28), extra = bytes.readUInt16LE(cursor + 30), comment = bytes.readUInt16LE(cursor + 32);
    const local = bytes.readUInt32LE(cursor + 42);
    if (cursor + 46 + names + extra + comment > end || flags & 1 || bytes.readUInt16LE(cursor + 34)) invalid();
    const name = utf8.decode(bytes.subarray(cursor + 46, cursor + 46 + names));
    if (!name || name.startsWith('/') || name.includes('\\') || name.split('/').includes('..') || entries.has(name)) invalid();
    entries.set(name, { flags, method, checksum, compressed, length, local });
    cursor += 46 + names + extra + comment;
  }
  if (cursor !== end) invalid();
  const cache = new Map();
  return {
    has: name => entries.has(name),
    read(name) {
      if (cache.has(name)) return cache.get(name);
      const e = entries.get(name);
      if (!e || e.length > MAX_PART || inflated + e.length > MAX_INFLATED || ![0, 8].includes(e.method) || e.local + 30 > offset) invalid();
      const start = e.local;
      if (bytes.readUInt32LE(start) !== 0x04034b50 || bytes.readUInt16LE(start + 8) !== e.method || bytes.readUInt16LE(start + 6) !== e.flags) invalid();
      const names = bytes.readUInt16LE(start + 26), extra = bytes.readUInt16LE(start + 28);
      const data = start + 30 + names + extra;
      if (data + e.compressed > offset || utf8.decode(bytes.subarray(start + 30, start + 30 + names)) !== name) invalid();
      const compressed = bytes.subarray(data, data + e.compressed);
      const result = e.method === 0 ? compressed : inflateRawSync(compressed, { maxOutputLength: Math.min(MAX_PART, e.length + 1) });
      if (result.length !== e.length || crc32(result) !== e.checksum) invalid();
      inflated += result.length;
      const text = utf8.decode(result);
      cache.set(name, text);
      return text;
    }
  };
}

function entities(text) {
  return text.replace(/&([^;]*);/g, (_, value) => {
    const known = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'" };
    if (Object.hasOwn(known, value)) return known[value];
    if (!/^#(?:[0-9]+|x[0-9a-f]+)$/i.test(value)) invalid();
    const code = value[1]?.toLowerCase() === 'x' ? parseInt(value.slice(2), 16) : Number(value.slice(1));
    if (!Number.isInteger(code) || code > 0x10ffff || code >= 0xd800 && code <= 0xdfff || code < 32 && ![9, 10, 13].includes(code)) invalid();
    return String.fromCodePoint(code);
  });
}

/** Small non-expanding XML reader: namespaces, rich text and CDATA; no DTD/entities/network. */
export function* xmlEvents(xml) {
  if (/<!DOCTYPE|<!ENTITY/i.test(xml)) invalid();
  const tokens = /<!--[\s\S]*?-->|<!\[CDATA\[[\s\S]*?\]\]>|<(?:"[^"]*"|'[^']*'|[^'">])*>|[^<]+/g;
  const stack = [];
  let end = 0, count = 0, roots = 0;
  for (const match of xml.matchAll(tokens)) {
    if (match.index !== end || ++count > 2000000) invalid();
    const token = match[0]; end += token.length;
    if (token.startsWith('<!--') || token.startsWith('<?')) continue;
    if (token.startsWith('<![CDATA[')) { yield { kind: 'text', text: token.slice(9, -3) }; continue; }
    if (token[0] !== '<') {
      if (!stack.length && token.trim()) invalid();
      yield { kind: 'text', text: entities(token) }; continue;
    }
    const tag = /^<(\/)?([A-Za-z_][\w.:-]*)([\s\S]*?)(\/?)>$/.exec(token);
    if (!tag) invalid();
    const [, closing, fullName, attributes, selfClosing] = tag;
    const name = fullName.split(':').at(-1);
    if (closing) {
      if (attributes.trim() || selfClosing || stack.pop() !== fullName) invalid();
      yield { kind: 'end', name }; continue;
    }
    const attrs = {};
    let consumed = 0;
    for (const attr of attributes.matchAll(/\s+([A-Za-z_][\w.:-]*)\s*=\s*(?:"([^"]*)"|'([^']*)')/g)) {
      if (attr.index !== consumed || Object.hasOwn(attrs, attr[1])) invalid();
      attrs[attr[1]] = entities(attr[2] ?? attr[3]); consumed += attr[0].length;
    }
    if (attributes.slice(consumed).trim()) invalid();
    if (!stack.length && ++roots !== 1 || stack.length >= 64) invalid();
    yield { kind: 'start', name, attrs };
    if (selfClosing) yield { kind: 'end', name }; else stack.push(fullName);
  }
  if (end !== xml.length || stack.length || roots !== 1) invalid();
}

export const attribute = (attrs, name) => Object.entries(attrs).find(([key]) => key.split(':').at(-1) === name)?.[1];
