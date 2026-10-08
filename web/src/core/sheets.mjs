// Spreadsheet (XLSX, XLS, ODS, CSV) -> compact CSV text for any provider, using SheetJS
// (installed from the SheetJS CDN, cdn.sheetjs.com, as its docs recommend; the npm registry copy is outdated).
import * as XLSX from 'xlsx';
import { envInteger } from './adapters.mjs';

/** DUA_SHEET_MAX_ROWS (default 300 per sheet), DUA_SHEET_MAX_COLS (default 40), DUA_SHEET_MAX_CHARS (default 120000 total). */
export function sheetLimits(env = {}) {
  return { rows: envInteger(env, 'DUA_SHEET_MAX_ROWS', 300, 10, 20000), cols: envInteger(env, 'DUA_SHEET_MAX_COLS', 40, 2, 500),
    chars: envInteger(env, 'DUA_SHEET_MAX_CHARS', 120000, 2000, 1000000), sheets: 12 };
}

const cellText = value => {
  if (value === null || value === undefined) return '';
  const text = value instanceof Date ? value.toISOString().replace(/T00:00:00\.000Z$/, '').replace(/\.000Z$/, 'Z') : String(value);
  const flat = text.replace(/\r\n?|\n/g, ' ').replace(/[\u0000-\u0008\u000b-\u001f\u007f]/g, '').slice(0, 500);
  return /[",]/.test(flat) ? '"' + flat.replace(/"/g, '""') + '"' : flat;
};

/**
 * Parses a spreadsheet buffer into labelled CSV blocks with truncation notes.
 * Returns {text, sheets:[{name, rows, cols, shownRows, shownCols}], truncated}.
 */
export function spreadsheetToText(buffer, { name = 'spreadsheet', mimeType = '', limits = sheetLimits() } = {}) {
  const csv = mimeType === 'text/csv' || /\.csv$/i.test(name);
  let workbook;
  try {
    workbook = csv
      ? XLSX.read(new TextDecoder('utf-8').decode(buffer).replace(/^\uFEFF/, ''), { type: 'string', dense: true, sheetRows: limits.rows + 1, raw: true })
      : XLSX.read(buffer, { type: 'buffer', dense: true, cellDates: true, cellFormula: false, cellHTML: false, sheetRows: limits.rows + 1 });
  } catch {
    throw Object.assign(new Error(`"${name}" could not be read as a spreadsheet.`), { status: 422 });
  }
  const parts = [];
  const sheets = [];
  let used = 0;
  let truncated = false;
  for (const sheetName of workbook.SheetNames.slice(0, limits.sheets)) {
    const sheet = workbook.Sheets[sheetName];
    const ref = sheet?.['!fullref'] || sheet?.['!ref'];
    const range = ref ? XLSX.utils.decode_range(ref) : null;
    const totalRows = range ? range.e.r - range.s.r + 1 : 0;
    const totalCols = range ? range.e.c - range.s.c + 1 : 0;
    const rows = XLSX.utils.sheet_to_json(sheet, { header: 1, raw: true, defval: '', blankrows: false });
    const shown = rows.slice(0, limits.rows).map(row => row.slice(0, limits.cols));
    const shownCols = Math.min(totalCols, limits.cols);
    let block = shown.map(row => row.map(cellText).join(',').replace(/,+$/, '')).join('\n');
    const notes = [];
    if (totalRows > shown.length) notes.push(`showing the first ${shown.length} of ${totalRows} rows`);
    if (totalCols > limits.cols) notes.push(`showing the first ${limits.cols} of ${totalCols} columns`);
    const header = `Sheet "${String(sheetName).slice(0, 100)}" (${totalRows} rows × ${totalCols} columns${notes.length ? '; ' + notes.join(', ') : ''}):`;
    if (used + header.length + block.length > limits.chars) {
      block = block.slice(0, Math.max(0, limits.chars - used - header.length - 80));
      block = block.slice(0, block.lastIndexOf('\n') > 0 ? block.lastIndexOf('\n') : block.length) + '\n[…sheet truncated to fit the size limit]';
      truncated = true;
    }
    if (notes.length) truncated = true;
    parts.push(header + '\n' + block);
    used += header.length + block.length + 2;
    sheets.push({ name: String(sheetName), rows: totalRows, cols: totalCols, shownRows: shown.length, shownCols });
    if (used >= limits.chars) break;
  }
  if (workbook.SheetNames.length > sheets.length) { truncated = true; parts.push(`[${workbook.SheetNames.length - sheets.length} more sheet(s) not shown]`); }
  return { text: `Spreadsheet "${name}" as CSV (untrusted file content):\n` + parts.join('\n\n'), sheets, truncated };
}
