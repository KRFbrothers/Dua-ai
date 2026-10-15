// Test-only writers, independent of the production readers. Nothing here seeds application data.
import { deflateRawSync, crc32 } from 'node:zlib';
import * as CFB from 'cfb';

export const xml = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
export function zip(parts, compressed = true) {
  const local = [], central = [];
  let offset = 0;
  for (const [name, text] of Object.entries(parts)) {
    const n = Buffer.from(name), b = Buffer.from(text), data = compressed ? deflateRawSync(b) : b;
    const h = Buffer.alloc(30); h.writeUInt32LE(0x04034b50); h.writeUInt16LE(20, 4); h.writeUInt16LE(compressed ? 8 : 0, 8);
    h.writeUInt32LE(crc32(b), 14); h.writeUInt32LE(data.length, 18); h.writeUInt32LE(b.length, 22); h.writeUInt16LE(n.length, 26);
    const c = Buffer.alloc(46); c.writeUInt32LE(0x02014b50); c.writeUInt16LE(20, 4); c.writeUInt16LE(20, 6); c.writeUInt16LE(compressed ? 8 : 0, 10);
    c.writeUInt32LE(crc32(b), 16); c.writeUInt32LE(data.length, 20); c.writeUInt32LE(b.length, 24); c.writeUInt16LE(n.length, 28); c.writeUInt32LE(offset, 42);
    local.push(h, n, data); central.push(c, n); offset += h.length + n.length + data.length;
  }
  const directory = Buffer.concat(central), end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50); end.writeUInt16LE(Object.keys(parts).length, 8); end.writeUInt16LE(Object.keys(parts).length, 10);
  end.writeUInt32LE(directory.length, 12); end.writeUInt32LE(offset, 16);
  return Buffer.concat([...local, directory, end]);
}
export function colName(n) { let result = ''; for (n++; n > 0; n = Math.floor((n - 1) / 26)) result = String.fromCharCode(65 + (n - 1) % 26) + result; return result; }
export function xlsxParts(sheets) {
  const parts = {
    'xl/workbook.xml': `<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>${sheets.map((s, i) => `<sheet name="${xml(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>`).join('')}</sheets></workbook>`,
    'xl/_rels/workbook.xml.rels': `<Relationships>${sheets.map((_, i) => `<Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i + 1}.xml"/>`).join('')}</Relationships>`
  };
  sheets.forEach((s, i) => {
    const cols = Math.max(...s.rows.map(r => r.length));
    parts[`xl/worksheets/sheet${i + 1}.xml`] = `<worksheet><dimension ref="A1:${colName(cols - 1)}${s.rows.length}"/><sheetData>${s.rows.map((r, row) => `<row r="${row + 1}">${r.map((v, col) => `<c r="${colName(col)}${row + 1}"${typeof v === 'number' ? '' : ' t="inlineStr"'}>${typeof v === 'number' ? `<v>${v}</v>` : `<is><t>${xml(v)}</t></is>`}</c>`).join('')}</row>`).join('')}</sheetData></worksheet>`;
  });
  return parts;
}
function biff(sheets) {
  const record = (id, data) => { const h = Buffer.alloc(4); h.writeUInt16LE(id); h.writeUInt16LE(data.length, 2); return Buffer.concat([h, data]); };
  const bof = type => { const b = Buffer.alloc(16); b.writeUInt16LE(0x600); b.writeUInt16LE(type, 2); return record(0x809, b); };
  const strings = [...new Set(sheets.flatMap(s => s.rows.flat().filter(v => typeof v !== 'number')))];
  const sstHeader = Buffer.alloc(8); sstHeader.writeUInt32LE(strings.length); sstHeader.writeUInt32LE(strings.length, 4);
  const sst = record(0xfc, Buffer.concat([sstHeader, ...strings.map(s => { const b = Buffer.alloc(3); b.writeUInt16LE(s.length); b[2] = 1; return Buffer.concat([b, Buffer.from(s, 'utf16le')]); })]));
  const contents = sheets.map(s => {
    const dims = Buffer.alloc(14); dims.writeUInt32LE(s.rows.length, 4); dims.writeUInt16LE(Math.max(...s.rows.map(r => r.length)), 10);
    return Buffer.concat([bof(0x10), record(0x200, dims), ...s.rows.flatMap((r, row) => r.map((v, col) => {
      const b = Buffer.alloc(typeof v === 'number' ? 14 : 10); b.writeUInt16LE(row); b.writeUInt16LE(col, 2);
      if (typeof v === 'number') b.writeDoubleLE(v, 6); else b.writeUInt32LE(strings.indexOf(v), 6);
      return record(typeof v === 'number' ? 0x203 : 0xfd, b);
    })), record(0xa, Buffer.alloc(0))]);
  });
  const bounds = sheets.map(s => { const b = Buffer.alloc(8 + s.name.length * 2); b[6] = s.name.length; b[7] = 1; Buffer.from(s.name, 'utf16le').copy(b, 8); return b; });
  let offset = bof(5).length + bounds.reduce((n, b) => n + b.length + 4, 0) + sst.length + 4;
  bounds.forEach((b, i) => { b.writeUInt32LE(offset); offset += contents[i].length; });
  const file = CFB.utils.cfb_new(); CFB.utils.cfb_add(file, 'Workbook', Buffer.concat([bof(5), ...bounds.map(b => record(0x85, b)), sst, record(0xa, Buffer.alloc(0)), ...contents]));
  return CFB.write(file, { type: 'buffer' });
}
export function makeSpreadsheet(sheets, type = 'xlsx') {
  if (type === 'biff8') return biff(sheets);
  if (type === 'ods') return zip({ 'content.xml': `<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"><office:body><office:spreadsheet>${sheets.map(s => `<table:table table:name="${xml(s.name)}">${s.rows.map(r => `<table:table-row>${r.map(v => `<table:table-cell office:value-type="${typeof v === 'number' ? 'float' : 'string'}"${typeof v === 'number' ? ` office:value="${v}"` : ''}><text:p>${xml(v)}</text:p></table:table-cell>`).join('')}</table:table-row>`).join('')}</table:table>`).join('')}</office:spreadsheet></office:body></office:document-content>` });
  return zip(xlsxParts(sheets));
}
