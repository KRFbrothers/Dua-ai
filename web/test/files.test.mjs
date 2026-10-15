import test from 'node:test';
import assert from 'node:assert/strict';
import { makeSpreadsheet } from './spreadsheet-fixtures.mjs';
import { handleFiles, validateUploadRequest, filesConfiguration } from '../src/core/files.mjs';
import { storedType, ownsPath, validFileRef } from '../src/core/file-types.mjs';
import { storageConfig } from '../src/core/storage.mjs';
import { spreadsheetToText, sheetLimits } from '../src/core/sheets.mjs';
import { handleService } from '../src/server/service-handler.mjs';
import { SUPA, UID, OTHER, filesEnv, path, router, filesRequest } from './files-helpers.mjs';

const MB = 1024 * 1024;

test('signed upload validation: types, extension fallback, size and shape', () => {
  assert.deepEqual(validateUploadRequest({ name: 'q3.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', size: 1000 }, 50 * MB),
    { mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', kind: 'sheet', ext: 'xlsx' });
  assert.equal(validateUploadRequest({ name: 'memo.m4a', mimeType: 'application/octet-stream', size: 10 }, MB).mimeType, 'audio/mp4');
  assert.equal(validateUploadRequest({ name: 'rec.aac', mimeType: 'audio/aac', size: 10 }, MB).kind, 'audio');
  assert.equal(validateUploadRequest({ name: 'a.csv', mimeType: 'text/csv; charset=utf-8', size: 10 }, MB).kind, 'sheet');
  const fails = (body, status) => assert.throws(() => validateUploadRequest(body, 50 * MB), error => error.status === status);
  fails({ name: 'big.pdf', mimeType: 'application/pdf', size: 50 * MB + 1 }, 413);
  fails({ name: 'run.exe', mimeType: 'application/x-msdownload', size: 10 }, 415);
  fails({ name: 'video.mp4', mimeType: 'video/mp4', size: 10 }, 415);
  fails({ name: 'a.pdf', mimeType: 'application/pdf', size: 0 }, 400);
  fails({ name: 'a.pdf', mimeType: 'application/pdf', size: 1.5 }, 400);
  fails({ name: 'a.pdf', mimeType: 'application/pdf', size: 10, path: 'x/../y' }, 400);
  fails({ name: 'bad\nname.pdf', mimeType: 'application/pdf', size: 10 }, 400);
  assert.equal(storedType('image/gif', 'x.gif'), null);
});

test('storage paths are owner-scoped and file refs are strict', () => {
  assert.equal(ownsPath(path(), UID), true);
  assert.equal(ownsPath(path(undefined, 'pdf', OTHER), UID), false);
  assert.equal(ownsPath(`${UID}/../${OTHER}/x.pdf`, UID), false);
  assert.equal(ownsPath(`${UID}/sub/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.pdf`, UID), false);
  assert.equal(validFileRef({ path: path(), name: 'a.pdf', mimeType: 'application/pdf', size: 3 }), true);
  assert.equal(validFileRef({ path: path(), name: 'a.pdf', mimeType: 'application/pdf', extra: 1 }), false);
  assert.equal(validFileRef({ path: path(), name: 'a.exe', mimeType: 'application/x-msdownload' }), false);
  assert.equal(validFileRef({ path: path(undefined, 'm4a'), name: 'r.m4a', mimeType: 'audio/mp4', transcript: 'hi' }), true);
});

test('storage config refuses secret keys and honours DUA_FILES=0 and DUA_FILES_MAX_MB', () => {
  assert.equal(storageConfig(filesEnv).enabled, true);
  assert.equal(storageConfig({ ...filesEnv, SUPABASE_ANON_KEY: 'sb_secret_x' }).enabled, false);
  assert.equal(storageConfig({ ...filesEnv, DUA_FILES: '0' }).enabled, false);
  assert.equal(storageConfig({ ...filesEnv, DUA_FILES_MAX_MB: '200' }).maxBytes, 200 * MB);
  assert.equal(storageConfig(filesEnv).maxBytes, 50 * MB);
  assert.equal(filesConfiguration(filesEnv).enabled, true);
  assert.equal(filesConfiguration({ ...filesEnv, DUA_ALLOWED_EMAIL: '' }).enabled, false);
});

test('POST /api/files/sign issues a Supabase signed upload URL in the owner folder', async () => {
  const fetcher = router([[SUPA + '/storage/v1/object/upload/sign/dua-files/', url => Response.json({ url: new URL(url).pathname.replace('/storage/v1', '') + '?token=up-token' })]]);
  const response = await handleFiles(filesRequest('sign', { name: 'Budget 2026.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', size: 2048 }), { env: filesEnv, fetcher });
  assert.equal(response.status, 200);
  const data = await response.json();
  assert.match(data.path, new RegExp(`^${UID}/[0-9a-f-]{36}\\.xlsx$`));
  assert.equal(data.uploadUrl, `${SUPA}/storage/v1/object/upload/sign/dua-files/${data.path}?token=up-token`);
  assert.deepEqual(data.headers, { 'Content-Type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 'x-upsert': 'false', apikey: 'sb_publishable_test' });
  assert.equal(data.maxBytes, 50 * MB);
  const sign = fetcher.find(SUPA + '/storage/v1/object/upload/sign/')[0];
  assert.equal(sign.method, 'POST');
  assert.equal(sign.headers.get('authorization'), 'Bearer user-token');
  assert.equal(sign.headers.get('apikey'), 'sb_publishable_test');
  assert.ok(!data.path.includes('Budget'), 'file names never go into storage paths');
});

test('file endpoints are owner-only and map storage errors to clear messages', async () => {
  const sign = { name: 'a.pdf', mimeType: 'application/pdf', size: 10 };
  const status = async (request, options) => { const response = await handleFiles(request, options); return [response.status, (await response.json()).error]; };
  assert.equal((await status(filesRequest('sign', sign, { token: null }), { env: filesEnv, fetcher: router([]) }))[0], 401);
  assert.equal((await status(filesRequest('sign', sign), { env: filesEnv, fetcher: router([], { email: 'stranger@example.com' }) }))[0], 403);
  assert.equal((await status(filesRequest('sign', sign), { env: { ...filesEnv, DUA_ALLOWED_EMAIL: '' }, fetcher: router([]) }))[0], 503);
  assert.equal((await status(filesRequest('sign', sign), { env: { ...filesEnv, DUA_FILES: '0' }, fetcher: router([]) }))[0], 503);
  const missing = await status(filesRequest('sign', sign), { env: filesEnv, fetcher: router([[SUPA + '/storage/v1/', () => Response.json({ statusCode: '404', error: 'Bucket not found', message: 'Bucket not found' }, { status: 400 })]]) });
  assert.deepEqual(missing, [503, 'File storage is not set up. Apply the dua-files Supabase migration (see SUPABASE.md).']);
  const rls = await status(filesRequest('sign', sign), { env: filesEnv, fetcher: router([[SUPA + '/storage/v1/', () => Response.json({ statusCode: '403', error: 'Unauthorized', message: 'new row violates row-level security policy' }, { status: 400 })]]) });
  assert.equal(rls[0], 403);
  assert.equal((await status(filesRequest('sign', { ...sign, size: 51 * MB }), { env: filesEnv, fetcher: router([]) }))[0], 413);
  assert.equal((await status(filesRequest('usage', null, { method: 'POST' }), { env: filesEnv, fetcher: router([]) }))[0], 405);
});

test('preview URLs and deletes only touch the caller\'s own folder', async () => {
  const fetcher = router([
    [SUPA + '/rest/v1/dua_cloud_snapshots?', () => Response.json([])],
    [SUPA + '/storage/v1/object/sign/dua-files/', url => Response.json({ signedURL: new URL(url).pathname.replace('/storage/v1', '') + '?token=dl' })],
    [(url, init) => url === SUPA + '/storage/v1/object/dua-files' && init.method === 'DELETE', (url, init) => Response.json(JSON.parse(init.body).prefixes.map(name => ({ name })))]
  ]);
  const foreign = await handleFiles(filesRequest('url', { path: path(undefined, 'pdf', OTHER) }), { env: filesEnv, fetcher });
  assert.equal(foreign.status, 404);
  assert.equal(fetcher.find(SUPA + '/storage/').length, 0);
  const preview = await (await handleFiles(filesRequest('url', { path: path() }), { env: filesEnv, fetcher })).json();
  assert.equal(preview.url, `${SUPA}/storage/v1/object/sign/dua-files/${path()}?token=dl`);
  assert.equal(fetcher.find(SUPA + '/storage/v1/object/sign/')[0].json.expiresIn, 600);
  assert.equal((await handleFiles(filesRequest('delete', { paths: [path(), path(undefined, 'pdf', OTHER)] }), { env: filesEnv, fetcher })).status, 400);
  const deleted = await (await handleFiles(filesRequest('delete', { paths: [path(), path()] }), { env: filesEnv, fetcher })).json();
  assert.deepEqual(deleted, { deleted: 1 });
});

test('usage and delete-all list the owner folder', async () => {
  const items = [{ id: '1', name: 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.pdf', metadata: { size: 1000 } }, { id: '2', name: 'bbbbbbbb-bbbb-4ccc-8ddd-eeeeeeeeeeee.m4a', metadata: { size: 500 } }, { id: null, name: 'folder' }];
  let removed;
  const fetcher = router([
    [SUPA + '/storage/v1/object/list/dua-files', () => Response.json(items)],
    [SUPA + '/storage/v1/object/dua-files', (url, init) => { removed = JSON.parse(init.body).prefixes; return Response.json(removed.map(name => ({ name }))); }]
  ]);
  const usage = await (await handleFiles(filesRequest('usage', null, { method: 'GET' }), { env: filesEnv, fetcher })).json();
  assert.deepEqual(usage, { files: 2, bytes: 1500, maxBytes: 50 * MB, bucket: 'dua-files' });
  assert.equal(fetcher.find(SUPA + '/storage/v1/object/list/')[0].json.prefix, UID + '/');
  const all = await (await handleFiles(filesRequest('delete-all', {}), { env: filesEnv, fetcher })).json();
  assert.deepEqual(all, { deleted: 2 });
  assert.deepEqual(removed, [`${UID}/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.pdf`, `${UID}/bbbbbbbb-bbbb-4ccc-8ddd-eeeeeeeeeeee.m4a`]);
});

test('service handler routes /api/files/* and rejects cross-origin calls', async () => {
  const response = await handleService(filesRequest('usage', null, { method: 'GET', origin: 'https://evil.test' }), { env: filesEnv, fetcher: router([]) });
  assert.equal(response.status, 403);
  const unknown = await handleService(filesRequest('nope', {}), { env: filesEnv, fetcher: router([]) });
  assert.equal(unknown.status, 404);
});

test('spreadsheets become compact CSV with row/column caps and truncation notes', () => {
  const rows = [Array.from({ length: 50 }, (_, index) => 'col' + index)];
  for (let index = 0; index < 400; index++) rows.push(Array.from({ length: 50 }, (_, col) => index * col));
  const book = [{ name: 'Big', rows }, { name: 'Small', rows: [['Name', 'Note'], ['Ali', 'says "hi", ok'], ['Sara', 'line\nbreak']] }];
  const limits = sheetLimits({ DUA_SHEET_MAX_ROWS: '100', DUA_SHEET_MAX_COLS: '10' });
  for (const bookType of ['xlsx', 'biff8', 'ods']) {
    const result = spreadsheetToText(makeSpreadsheet(book, bookType), { name: 'data.' + bookType, limits });
    assert.equal(result.truncated, true);
    assert.deepEqual(result.sheets[0], { name: 'Big', rows: 401, cols: 50, shownRows: 100, shownCols: 10 });
    assert.match(result.text, /Sheet "Big" \(401 rows × 50 columns; showing the first 100 of 401 rows, showing the first 10 of 50 columns\):\ncol0,col1,col2,col3,col4,col5,col6,col7,col8,col9\n/);
    assert.match(result.text, /Sheet "Small" \(3 rows × 2 columns\):\nName,Note\nAli,"says ""hi"", ok"\nSara,line break/);
  }
  const csv = spreadsheetToText(Buffer.from('\uFEFFdate,amount\n2026-01-01,5\n2026-01-02,"1,200"\n'), { name: 'a.csv', mimeType: 'text/csv' });
  assert.equal(csv.truncated, false);
  assert.match(csv.text, /date,amount\n2026-01-01,5\n2026-01-02,"1,200"$/);
  const tight = spreadsheetToText(makeSpreadsheet(book), { name: 'x.xlsx', limits: { ...limits, chars: 2000 } });
  assert.ok(tight.text.length < 2600);
  assert.match(tight.text, /sheet truncated to fit the size limit/);
});

test('library list is read-only, owner-scoped and returns metadata without links or credentials', async () => {
  const name = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.png';
  const fetcher = router([[SUPA + '/storage/v1/object/list/dua-files', (url, init, recorded) => {
    assert.equal(recorded.json.prefix, UID + '/');
    assert.equal(recorded.headers.get('authorization'), 'Bearer user-token');
    return Response.json([{ id: '1', name, metadata: { size: 123, secret: 'never-return' } },
      { id: '2', name: '../private.png', metadata: { size: 99 } }, { id: null, name: 'folder' }]);
  }]]);
  const response = await handleService(filesRequest('list', null, { method: 'GET' }), { env: filesEnv, fetcher });
  assert.equal(response.status, 200);
  assert.equal(response.headers.get('cache-control'), 'no-store');
  assert.deepEqual(await response.json(), { files: [{ path: `${UID}/${name}`, name, mimeType: 'image/png', size: 123 }], truncated: false });
  assert.equal(fetcher.calls.length, 2, 'only owner verification and a bounded private folder listing');
  assert.ok(!fetcher.calls.some(call => call.method === 'DELETE' || call.url.includes('/sign/')));
});

test('library list refuses anonymous, foreign-account, write-method and cross-origin requests', async () => {
  const anonymous = await handleService(filesRequest('list', null, { method: 'GET', token: null }), { env: filesEnv, fetcher: router([]) });
  assert.equal(anonymous.status, 401);
  const foreign = router([], { email: 'other@example.com' });
  assert.equal((await handleService(filesRequest('list', null, { method: 'GET' }), { env: filesEnv, fetcher: foreign })).status, 403);
  assert.equal(foreign.calls.length, 1, 'no Storage access for a non-owner');
  assert.equal((await handleService(filesRequest('list', {}), { env: filesEnv, fetcher: router([]) })).status, 405);
  assert.equal((await handleService(filesRequest('list', null, { method: 'GET', origin: 'https://evil.test' }), { env: filesEnv, fetcher: router([]) })).status, 403);
});
