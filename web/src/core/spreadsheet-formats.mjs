import { posix } from 'node:path';
import * as CFB from 'cfb';
import { spreadsheetZip, xmlEvents, attribute } from './spreadsheet-xml.mjs';

const invalid = () => { throw Error('Invalid or unsupported spreadsheet.'); };
const integer = (s, max) => { if (!/^\d+$/.test(String(s)) || Number(s) > max) invalid(); return Number(s); };
function address(ref) {
  const m = /^([A-Z]{1,3})([1-9][0-9]{0,6})$/i.exec(ref || '');
  if (!m) invalid();
  const col = [...m[1].toUpperCase()].reduce((n, c) => n * 26 + c.charCodeAt(0) - 64, 0) - 1;
  const row = Number(m[2]) - 1;
  if (row >= 1048576 || col >= 16384) invalid();
  return [row, col];
}
function collector(name, limits, range) {
  const rows = new Map();
  let minRow = range?.[0][0] ?? Infinity, minCol = range?.[0][1] ?? Infinity;
  let maxRow = range?.[1][0] ?? -1, maxCol = range?.[1][1] ?? -1;
  return {
    add(row, col, value) {
      if (!Number.isInteger(row) || !Number.isInteger(col) || row < 0 || row >= 1048576 || col < 0 || col >= 16384) invalid();
      if (value === '' || value === undefined) return;
      minRow = Math.min(minRow, row); minCol = Math.min(minCol, col); maxRow = Math.max(maxRow, row); maxCol = Math.max(maxCol, col);
      if (row < minRow + limits.rows && col < minCol + limits.cols) {
        if (!rows.has(row)) rows.set(row, new Map());
        rows.get(row).set(col, value);
      }
    },
    result() {
      const shown = [...rows].sort((a, b) => a[0] - b[0]).filter(([r]) => r < minRow + limits.rows).map(([, cells]) => {
        const row = Array(Math.min(maxCol - minCol + 1, limits.cols)).fill('');
        for (const [c, v] of cells) if (c >= minCol && c < minCol + row.length) row[c - minCol] = v;
        return row;
      });
      return { name, rows: shown, totalRows: maxRow < 0 ? 0 : maxRow - minRow + 1, totalCols: maxCol < 0 ? 0 : maxCol - minCol + 1 };
    }
  };
}
function excelDate(value, date1904) {
  const serial = Number(value);
  if (!Number.isFinite(serial) || serial < 0 || serial > 2958465) invalid();
  return new Date((date1904 ? Date.UTC(1904, 0, 1) : Date.UTC(1899, 11, serial < 60 ? 31 : 30)) + Math.round(serial * 86400000));
}
const dateFormat = (id, custom) => id >= 14 && id <= 22 || id >= 45 && id <= 47 || /[ymdhis]/i.test((custom || '').replace(/"[^"]*"|\\.|\[[^\]]*\]/g, ''));
function styles(xml) {
  const custom = new Map(), result = [];
  let inXfs = false;
  for (const e of xmlEvents(xml)) {
    if (e.kind === 'start' && e.name === 'numFmt') custom.set(e.attrs.numFmtId, e.attrs.formatCode);
    if (e.name === 'cellXfs') inXfs = e.kind === 'start';
    if (inXfs && e.kind === 'start' && e.name === 'xf') result.push(dateFormat(Number(e.attrs.numFmtId), custom.get(e.attrs.numFmtId)));
  }
  return result;
}
function sharedStrings(xml) {
  const strings = [];
  let value = '', active = false, text = false, phonetic = false, chars = 0;
  for (const e of xmlEvents(xml)) {
    if (e.kind === 'start' && e.name === 'si') { value = ''; active = true; }
    if (e.name === 'rPh') phonetic = e.kind === 'start';
    if (e.name === 't') text = e.kind === 'start';
    if (e.kind === 'text' && active && text && !phonetic) { value += e.text; chars += e.text.length; if (chars > 8 * 1024 * 1024) invalid(); }
    if (e.kind === 'end' && e.name === 'si') { strings.push(value); active = false; if (strings.length > 200000) invalid(); }
  }
  return strings;
}
export function readXlsx(bytes, limits) {
  const zip = spreadsheetZip(bytes), sheets = [], relationships = new Map();
  let date1904 = false;
  for (const e of xmlEvents(zip.read('xl/_rels/workbook.xml.rels'))) if (e.kind === 'start' && e.name === 'Relationship') {
    if (relationships.has(e.attrs.Id)) invalid();
    relationships.set(e.attrs.Id, e.attrs);
  }
  for (const e of xmlEvents(zip.read('xl/workbook.xml'))) if (e.kind === 'start') {
    if (e.name === 'workbookPr') date1904 = ['1', 'true'].includes(e.attrs.date1904);
    if (e.name === 'sheet') {
      const rel = relationships.get(attribute(e.attrs, 'id'));
      if (!rel || rel.TargetMode === 'External' || !rel.Type?.endsWith('/worksheet')) invalid();
      const path = posix.resolve('/xl', rel.Target || '');
      if (!path.startsWith('/xl/worksheets/') || !path.endsWith('.xml')) invalid();
      sheets.push({ name: e.attrs.name || 'Sheet', path: path.slice(1) });
      if (sheets.length > 1024) invalid();
    }
  }
  if (!sheets.length) invalid();
  const strings = zip.has('xl/sharedStrings.xml') ? sharedStrings(zip.read('xl/sharedStrings.xml')) : [];
  const dates = zip.has('xl/styles.xml') ? styles(zip.read('xl/styles.xml')) : [];
  return { count: sheets.length, sheets: sheets.slice(0, limits.sheets).map(sheet => {
    let output, cell, value = '', inline = '', inV = false, inT = false, row = 0, col = 0;
    for (const e of xmlEvents(zip.read(sheet.path))) {
      if (e.kind === 'start') {
        if (e.name === 'dimension') {
          const parts = (e.attrs.ref || '').split(':');
          const start = address(parts[0]), end = address(parts[1] || parts[0]);
          if (end[0] < start[0] || end[1] < start[1] || output) invalid();
          output = collector(sheet.name, limits, [start, end]);
        }
        if (e.name === 'row') { row = e.attrs.r ? integer(e.attrs.r, 1048576) - 1 : row + 1; col = 0; }
        if (e.name === 'c') { cell = e.attrs; value = ''; inline = ''; if (cell.r) [row, col] = address(cell.r); }
        if (cell && e.name === 'v') inV = true;
        if (cell && e.name === 't') inT = true;
      } else if (e.kind === 'text' && cell) {
        if (inV) value += e.text;
        if (inT) inline += e.text;
        if (value.length + inline.length > 1024 * 1024) invalid();
      } else if (e.kind === 'end') {
        if (e.name === 'v') inV = false;
        if (e.name === 't') inT = false;
        if (e.name === 'c' && cell) {
          let text = value;
          if (cell.t === 's') { const index = integer(value, strings.length - 1); text = strings[index]; }
          else if (cell.t === 'inlineStr') text = inline;
          else if (cell.t === 'b') { if (!['0', '1'].includes(value)) invalid(); text = value === '1'; }
          else if (cell.t === 'd') { text = new Date(value); if (Number.isNaN(text.getTime())) invalid(); }
          else if ((!cell.t || cell.t === 'n') && value) {
            text = Number(value); if (!Number.isFinite(text)) invalid();
            if (dates[Number(cell.s || 0)]) text = excelDate(text, date1904);
          }
          output ||= collector(sheet.name, limits);
          output.add(row, col++, text); cell = null;
        }
      }
    }
    return (output || collector(sheet.name, limits)).result();
  }) };
}

export function readOds(bytes, limits) {
  const xml = spreadsheetZip(bytes).read('content.xml'), sheets = [];
  let output, row = 0, col = 0, repeatRow = 1, repeatCol = 1, cell, text = '', paragraph = false, count = 0, tableOpen = false;
  for (const e of xmlEvents(xml)) {
    if (e.kind === 'start') {
      if (e.name === 'table') { if (tableOpen || ++count > 1024) invalid(); tableOpen = true; output = count <= limits.sheets ? collector(attribute(e.attrs, 'name') || 'Sheet', limits) : null; row = 0; }
      if (output && e.name === 'table-row') { col = 0; repeatRow = integer(attribute(e.attrs, 'number-rows-repeated') || '1', 1048576); if (!repeatRow) invalid(); }
      if (output && ['table-cell', 'covered-table-cell'].includes(e.name)) { cell = e.attrs; text = ''; repeatCol = integer(attribute(cell, 'number-columns-repeated') || '1', 16384); if (!repeatCol) invalid(); }
      if (cell && e.name === 'p') { if (text) text += '\n'; paragraph = true; }
      if (cell && e.name === 's') text += ' '.repeat(Math.min(500, integer(attribute(e.attrs, 'c') || '1', 1000000)));
      if (cell && e.name === 'tab') text += '\t';
      if (cell && e.name === 'line-break') text += '\n';
    } else if (e.kind === 'text' && paragraph) { text += e.text; if (text.length > 1024 * 1024) invalid(); }
    else if (e.kind === 'end') {
      if (e.name === 'p') paragraph = false;
      if (cell && ['table-cell', 'covered-table-cell'].includes(e.name)) {
        let value = attribute(cell, 'string-value') ?? text;
        const type = attribute(cell, 'value-type');
        if (['float', 'currency', 'percentage'].includes(type)) { value = Number(attribute(cell, 'value')); if (!Number.isFinite(value)) invalid(); }
        if (type === 'boolean') value = attribute(cell, 'boolean-value') === 'true';
        if (type === 'date') { value = new Date(attribute(cell, 'date-value')); if (Number.isNaN(value.getTime())) invalid(); }
        if (value !== '') {
          output.add(row + repeatRow - 1, col + repeatCol - 1, value);
          for (let r = 0; r < Math.min(repeatRow, limits.rows); r++) for (let c = 0; c < Math.min(repeatCol, limits.cols); c++) output.add(row + r, col + c, value);
        }
        col += repeatCol; cell = null;
      }
      if (output && e.name === 'table-row') row += repeatRow;
      if (e.name === 'table') { if (output) sheets.push(output.result()); output = null; tableOpen = false; }
    }
  }
  if (!sheets.length) invalid();
  return { count, sheets };
}

/** RFC-style CSV quoting, including embedded line breaks; retain raw strings. */
export function readCsv(bytes, limits) {
  const text = new TextDecoder('utf-8', { fatal: true }).decode(bytes).replace(/^\uFEFF/, '');
  if (text.includes('\u0000') || text.length > 50 * 1024 * 1024) invalid();
  const output = collector('Sheet1', limits);
  let row = 0, col = 0, value = '', quoted = false, afterQuote = false;
  const cell = () => { output.add(row, col++, value); value = ''; afterQuote = false; };
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (c === '"') {
      if (quoted && text[i + 1] === '"') { value += '"'; i++; }
      else if (quoted) { quoted = false; afterQuote = true; }
      else if (!value && !afterQuote) quoted = true;
      else invalid();
    } else if (!quoted && (c === ',' || c === '\n' || c === '\r')) {
      cell(); if (c !== ',') { row++; col = 0; if (c === '\r' && text[i + 1] === '\n') i++; }
    } else { if (afterQuote && c !== ' ') invalid(); if (!afterQuote && value.length < 1024 * 1024) value += c; }
  }
  if (quoted) invalid();
  cell();
  return { count: 1, sheets: [output.result()] };
}

/** Legacy BIFF8 uses the official npm-hosted CFB container reader, never the removed XLSX package. */
export function readXls(bytes, limits) {
  if (bytes.length > 50 * 1024 * 1024 || !Buffer.from(bytes).subarray(0, 8).equals(Buffer.from('d0cf11e0a1b11ae1', 'hex'))) invalid();
  const container = CFB.read(bytes, { type: 'buffer' });
  const stream = CFB.find(container, 'Workbook') || CFB.find(container, 'Book');
  if (!stream || stream.size > 50 * 1024 * 1024) invalid();
  const data = Buffer.from(stream.content), records = [];
  for (let p = 0; p < data.length;) {
    if (p + 4 > data.length || records.length > 500000) invalid();
    const id = data.readUInt16LE(p), size = data.readUInt16LE(p + 2);
    if (id === 0 && size === 0 && data.subarray(p).every(b => b === 0)) break; // Writers may pad the Workbook stream.
    if (p + 4 + size > data.length) invalid();
    if (id === 0x2f) invalid(); // Encrypted workbook.
    records.push({ id, offset: p, b: data.subarray(p + 4, p + 4 + size) }); p += 4 + size;
  }
  if (records[0]?.id !== 0x809 || records[0].b.readUInt16LE(0) !== 0x600) invalid();
  const sheets = [], strings = [], xfs = [], formats = new Map();
  const unicode = (b, offset) => {
    const length = b.readUInt16LE(offset), flags = b[offset + 2], wide = flags & 1;
    const start = offset + 3 + (flags & 8 ? 2 : 0) + (flags & 4 ? 4 : 0), end = start + length * (wide ? 2 : 1);
    if (end > b.length) invalid();
    return b.subarray(start, end).toString(wide ? 'utf16le' : 'latin1');
  };
  let date1904 = false;
  for (let i = 0; i < records.length; i++) {
    const { id, b } = records[i];
    if (id === 0x85) {
      if (b.length < 8 || b[5] !== 0) continue;
      const count = b[6], wide = b[7] & 1;
      if (8 + count * (wide ? 2 : 1) > b.length) invalid();
      sheets.push({ offset: b.readUInt32LE(0), name: b.subarray(8, 8 + count * (wide ? 2 : 1)).toString(wide ? 'utf16le' : 'latin1') });
    }
    if (id === 0x22) date1904 = !!b.readUInt16LE(0);
    if (id === 0x41e) formats.set(b.readUInt16LE(0), unicode(b, 2));
    if (id === 0xe0) xfs.push(b.readUInt16LE(2));
    if (id === 0xfc) {
      const segments = [b]; while (records[i + 1]?.id === 0x3c) segments.push(records[++i].b);
      let seg = 0, p = 8;
      const byte = () => { while (seg < segments.length && p >= segments[seg].length) { seg++; p = 0; } if (seg >= segments.length) invalid(); return segments[seg][p++]; };
      const u16 = () => byte() | byte() << 8;
      const u32 = () => (u16() | u16() << 16) >>> 0;
      const count = b.readUInt32LE(4); if (count > 200000) invalid();
      for (let n = 0; n < count; n++) {
        const length = u16(), flags = byte(), runs = flags & 8 ? u16() : 0, extra = flags & 4 ? u32() : 0;
        let wide = !!(flags & 1), value = '';
        for (let c = 0; c < length; c++) {
          if (p === segments[seg]?.length) { seg++; p = 0; wide = !!(byte() & 1); }
          value += String.fromCharCode(wide ? u16() : byte());
        }
        for (let skip = 0; skip < runs * 4 + extra; skip++) byte();
        strings.push(value);
      }
    }
  }
  if (!sheets.length) invalid();
  const rk = n => { let value; if (n & 2) value = n >> 2; else { const b = Buffer.alloc(8); b.writeUInt32LE((n & ~3) >>> 0, 4); value = b.readDoubleLE(); } return n & 1 ? value / 100 : value; };
  return { count: sheets.length, sheets: sheets.slice(0, limits.sheets).map(sheet => {
    const first = records.findIndex(r => r.offset === sheet.offset); if (first < 0) invalid();
    let output = collector(sheet.name, limits);
    const add = (b, value, col = b.readUInt16LE(2), xf = b.readUInt16LE(4)) => output.add(b.readUInt16LE(0), col, typeof value === 'number' && dateFormat(xfs[xf], formats.get(xfs[xf])) ? excelDate(value, date1904) : value);
    for (let i = first + 1; i < records.length; i++) {
      const { id, b } = records[i];
      if (id === 0xa) break;
      if (id === 0x200 && b.length >= 12 && b.readUInt32LE(4) && b.readUInt16LE(10)) output = collector(sheet.name, limits, [[b.readUInt32LE(0), b.readUInt16LE(8)], [b.readUInt32LE(4) - 1, b.readUInt16LE(10) - 1]]);
      if (id === 0xfd) { const index = b.readUInt32LE(6); if (index >= strings.length) invalid(); add(b, strings[index]); }
      if (id === 0x203) add(b, b.readDoubleLE(6));
      if (id === 0x27e) add(b, rk(b.readUInt32LE(6)));
      if (id === 0xbd) for (let j = 4, col = b.readUInt16LE(2); j + 6 <= b.length - 2; j += 6, col++) add(b, rk(b.readUInt32LE(j + 2)), col, b.readUInt16LE(j));
      if (id === 0x205) add(b, b[7] ? '#ERROR!' : !!b[6]);
      if (id === 0x204) add(b, unicode(b, 6));
      if (id === 0x6) { // Cached formulas only; never evaluate workbook expressions.
        if (b.readUInt16LE(12) !== 0xffff) add(b, b.readDoubleLE(6));
        else if (b[6] === 1) add(b, !!b[8]);
        else if (b[6] === 2) add(b, '#ERROR!');
        else if (b[6] === 0 && records[i + 1]?.id === 0x207) add(b, unicode(records[++i].b, 0));
      }
    }
    return output.result();
  }) };
}
