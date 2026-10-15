import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { spreadsheetToText, sheetLimits } from '../src/core/sheets.mjs';
import { zip, xlsxParts, makeSpreadsheet } from './spreadsheet-fixtures.mjs';

const parts = () => xlsxParts([{ name: 'Budget & notes', rows: [['unused']] }]);
const parse = (p, compressed = true, extra = {}) => spreadsheetToText(zip(p, compressed), { name: 'book.xlsx', ...extra });
const worksheet = (cells, dimension = 'A1:C3') => `<worksheet><dimension ref="${dimension}"/><sheetData>${cells}</sheetData></worksheet>`;
test('independent openpyxl/Xlwt exports preserve Unicode, dates, booleans, RK numbers and continued SST strings', () => {
  for (const name of ['worksheet-openpyxl.xlsx', 'worksheet-xlwt.xls']) {
    const result = spreadsheetToText(readFileSync(new URL(`./fixtures/${name}`, import.meta.url)), { name });
    assert.match(result.text, /Dua & हैलो,123\.5,2026-10-15,true/);
    assert.match(result.text, /line break,-42,,false/);
    if (name.endsWith('.xls')) { assert.match(result.text, /continued text: क+/); assert.match(result.text, /,2\.5,last SST string/); }
  }
});
test('native XLSX reads shared/rich strings, inline Unicode, sparse cells and cached formulas without evaluating them', () => {
  const p = parts();
  p['xl/sharedStrings.xml'] = '<sst><si><t>Item &amp; cost</t></si><si><r><t>हैलो </t></r><r><t>Dua</t></r><rPh><t>not display text</t></rPh></si></sst>';
  p['xl/worksheets/sheet1.xml'] = worksheet('<row r="1"><c r="A1" t="s"><v>0</v></c><c r="C1" t="s"><v>1</v></c></row><row r="2"><c r="A2" t="inlineStr"><is><t>&quot;Rent&quot;, monthly</t></is></c><c r="B2"><f>SUM(1,2)</f><v>3</v></c><c r="C2" t="b"><v>1</v></c></row><row r="3"><c r="A3" t="str"><f>HYPERLINK(&quot;https://example.invalid&quot;)</f><v>cached only</v></c><c r="B3" t="e"><v>#DIV/0!</v></c></row>');
  for (const compressed of [true, false]) {
    const result = parse(p, compressed);
    assert.match(result.text, /Item & cost,,हैलो Dua/); assert.match(result.text, /""Rent"", monthly"/);
    assert.match(result.text, /,3,true/); assert.match(result.text, /cached only,#DIV\/0!/);
    assert.doesNotMatch(result.text, /HYPERLINK|not display text/);
  }
});
test('numeric/date cells and both Excel epochs retain raw values and ISO dates', () => {
  for (const date1904 of [false, true]) {
    const p = parts(); p['xl/workbook.xml'] = p['xl/workbook.xml'].replace('<sheets>', `<workbookPr date1904="${date1904 ? 1 : 0}"/><sheets>`);
    p['xl/styles.xml'] = '<styleSheet><numFmts><numFmt numFmtId="164" formatCode="yyyy-mm-dd"/></numFmts><cellXfs><xf numFmtId="0"/><xf numFmtId="164"/></cellXfs></styleSheet>';
    p['xl/worksheets/sheet1.xml'] = worksheet('<row r="1"><c r="A1" s="1"><v>1</v></c><c r="B1"><v>1.25E2</v></c><c r="C1" t="d"><v>2026-10-15T09:00:00Z</v></c></row>', 'A1:C1');
    const result = parse(p);
    assert.match(result.text, new RegExp(date1904 ? '1904-01-02' : '1900-01-01'));
    assert.match(result.text, /,125,2026-10-15T09:00:00Z/);
  }
});
test('namespace prefixes, numeric entities and non-A1 sparse ranges are understood', () => {
  const p = parts(); p['xl/worksheets/sheet1.xml'] = '<m:worksheet xmlns:m="urn:test"><m:dimension ref="B10:C11"/><m:sheetData><m:row r="10"><m:c r="B10" t="inlineStr"><m:is><m:t>&#68;&#x75;a</m:t></m:is></m:c></m:row><m:row r="11"><m:c r="C11"><m:v>42</m:v></m:c></m:row></m:sheetData></m:worksheet>';
  const result = parse(p); assert.match(result.text, /Dua\n,42/); assert.equal(result.sheets[0].rows, 2); assert.equal(result.sheets[0].cols, 2);
});
test('worksheet count, row/column/character caps stay bounded without expanding sparse dimensions', () => {
  const book = Array.from({ length: 14 }, (_, i) => ({ name: `Sheet${i}`, rows: [['hello']] }));
  for (const format of ['xlsx', 'ods', 'biff8']) {
    const result = spreadsheetToText(makeSpreadsheet(book, format), { name: `book.${format}` });
    assert.equal(result.sheets.length, 12); assert.equal(result.truncated, true); assert.match(result.text, /2 more sheet/);
  }
  const p = parts(); p['xl/worksheets/sheet1.xml'] = worksheet('<row r="1"><c r="A1"><v>7</v></c></row>', 'A1:XFD1048576');
  const result = parse(p); assert.equal(result.sheets[0].rows, 1048576); assert.ok(result.text.length < 500);
});
test('CSV handles embedded newlines, quotes, empty cells and BOM; malformed CSV fails clearly', () => {
  const result = spreadsheetToText(Buffer.from('\uFEFFname,note,active\r\nDua,"one\ntwo, ""three""",false\r\n'), { name: 'data.csv' });
  assert.match(result.text, /Dua,"one two, ""three""",false/);
  for (const text of ['a,"not closed', 'a,"b"bad', 'a\0,b']) assert.throws(() => spreadsheetToText(Buffer.from(text), { name: 'bad.csv' }), e => e.status === 422);
});
test('malformed XML, external entities, invalid cells/shared refs and external worksheet links fail closed', () => {
  for (const mutate of [
    p => p['xl/sharedStrings.xml'] = '<!DOCTYPE s [<!ENTITY x SYSTEM "file:///etc/passwd">]><sst><si><t>&x;</t></si></sst>',
    p => p['xl/worksheets/sheet1.xml'] = '<worksheet><row></worksheet>',
    p => p['xl/worksheets/sheet1.xml'] = worksheet('<c r="XFE1"><v>1</v></c>'),
    p => p['xl/worksheets/sheet1.xml'] = worksheet('<c r="A1" t="s"><v>999</v></c>'),
    p => p['xl/worksheets/sheet1.xml'] = worksheet('<c r="A1"><v>NaN</v></c>'),
    p => p['xl/_rels/workbook.xml.rels'] = p['xl/_rels/workbook.xml.rels'].replace('Target=', 'TargetMode="External" Target='),
    p => p['xl/_rels/workbook.xml.rels'] = p['xl/_rels/workbook.xml.rels'].replace('worksheets/sheet1.xml', '../../outside.xml')
  ]) { const p = parts(); mutate(p); assert.throws(() => parse(p), e => e.status === 422); }
});
test('truncated/encrypted/corrupt ZIP data and bounded-inflation bombs fail with 422', () => {
  const valid = zip(parts());
  const encrypted = Buffer.from(valid); encrypted.writeUInt16LE(1, encrypted.indexOf(Buffer.from('504b0102', 'hex')) + 8);
  const corrupt = Buffer.from(valid); corrupt[40] ^= 0x7f;
  const huge = parts(); huge['xl/worksheets/sheet1.xml'] = 'x'.repeat(16 * 1024 * 1024 + 1);
  for (const bytes of [valid.subarray(0, 20), valid.subarray(0, -1), encrypted, corrupt, zip(huge), Buffer.from('not an archive')])
    assert.throws(() => spreadsheetToText(bytes, { name: 'bad.xlsx' }), e => e.status === 422);
});
test('ODS repeated cells/rows stay bounded and formulas use cached values', () => {
  const p = { 'content.xml': '<document xmlns:table="urn:table" xmlns:office="urn:office" xmlns:text="urn:text"><table:table table:name="Totals"><table:table-row table:number-rows-repeated="10000"><table:table-cell table:number-columns-repeated="1000" office:value-type="float" office:value="42" table:formula="of:=SUM(1;2)"><text:p>42</text:p></table:table-cell></table:table-row></table:table></document>' };
  const result = spreadsheetToText(zip(p), { name: 'file.ods', limits: sheetLimits({ DUA_SHEET_MAX_ROWS: '10', DUA_SHEET_MAX_COLS: '2' }) });
  assert.deepEqual(result.sheets[0], { name: 'Totals', rows: 10000, cols: 1000, shownRows: 10, shownCols: 2 });
  assert.doesNotMatch(result.text, /SUM/); assert.match(result.text, /42,42/);
});
