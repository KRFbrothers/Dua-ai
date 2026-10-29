import test from 'node:test';
import assert from 'node:assert/strict';
import { handleFiles } from '../src/core/files.mjs';
import { SUPA, UID, OTHER, filesEnv, path, router, filesRequest } from './files-helpers.mjs';

const upload = path();
const image = path('bbbbbbbb-bbbb-4ccc-8ddd-eeeeeeeeeeee', 'png');
const video = path('cccccccc-bbbb-4ccc-8ddd-eeeeeeeeeeee', 'mp4');
const orphan = path('dddddddd-bbbb-4ccc-8ddd-eeeeeeeeeeee', 'txt');
const snapshot = () => ({ format: 'dua-cloud-snapshot', version: 1, memory: [], chats: [{
  id: 'server-chat', model: 'Dua', draft: '', updatedAt: 1, messages: [
    { id: 'user', role: 'user', text: 'Test upload', state: 'complete', files: [{ path: upload, name: 'test.pdf', mimeType: 'application/pdf', size: 10 }] },
    { id: 'reply', role: 'model', text: 'Generated media', state: 'complete', media: [
      { path: image, mimeType: 'image/png' }, { path: video, mimeType: 'video/mp4' }
    ] }
  ]
}] });
const cloudUrl = SUPA + '/rest/v1/dua_cloud_snapshots?';
const storageUrl = SUPA + '/storage/v1/object/dua-files';
const deleteRoute = [storageUrl, (_, init) => Response.json(JSON.parse(init.body).prefixes.map(name => ({ name })))];

test('legacy cache-deletion requests retain uploaded files and generated media in the server snapshot', async () => {
  const fetcher = router([[cloudUrl, () => Response.json([{ revision: 1, payload: snapshot() }])], deleteRoute]);
  const response = await handleFiles(filesRequest('delete', { paths: [upload, image, video, upload] }), { env: filesEnv, fetcher });
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { deleted: 0, retained: 3 });
  assert.equal(fetcher.find(storageUrl).length, 0);
  const read = fetcher.find(cloudUrl)[0];
  assert.match(read.url, new RegExp('owner_id=eq\\.' + UID));
  assert.equal(read.headers.get('authorization'), 'Bearer user-token');
  assert.equal(read.headers.get('apikey'), filesEnv.SUPABASE_ANON_KEY);
});

test('mixed cleanup deletes only unreferenced paths, deduplicated and under the owner token', async () => {
  const fetcher = router([[cloudUrl, () => Response.json([{ revision: 1, payload: snapshot() }])], deleteRoute]);
  const response = await handleFiles(filesRequest('delete', { paths: [upload, orphan, orphan] }), { env: filesEnv, fetcher });
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { deleted: 1, retained: 1 });
  assert.deepEqual(fetcher.find(storageUrl)[0].json.prefixes, [orphan]);
  assert.equal(fetcher.find(storageUrl)[0].headers.get('authorization'), 'Bearer user-token');
});

test('cleanup retains project-only sources in active and archived projects while deleting orphans', async () => {
  for (const archived of [false, true]) {
    const payload = { format: 'dua-cloud-snapshot', version: 1, memory: [], chats: [], projects: [
      { id: 'personal', name: 'Personal', instructions: '', archived: false },
      { id: 'research', name: 'Research', instructions: '', archived,
        sources: [{ path: upload, name: 'source.pdf', mimeType: 'application/pdf', size: 10 }] }
    ] };
    const fetcher = router([[cloudUrl, () => Response.json([{ revision: 1, payload }])], deleteRoute]);
    const response = await handleFiles(filesRequest('delete', { paths: [upload, orphan, upload] }), { env: filesEnv, fetcher });
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { deleted: 1, retained: 1 });
    assert.deepEqual(fetcher.find(storageUrl)[0].json.prefixes, [orphan]);
  }
});

test('cleanup permits unreferenced uploads when no snapshot row exists or when the server snapshot is cleared', async () => {
  for (const rows of [[], [{ revision: 2, payload: { format: 'dua-cloud-snapshot', version: 1, chats: [], memory: [] } }]]) {
    const fetcher = router([[cloudUrl, () => Response.json(rows)], deleteRoute]);
    const response = await handleFiles(filesRequest('delete', { paths: [orphan] }), { env: filesEnv, fetcher });
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { deleted: 1 });
  }
});

test('unavailable or corrupt cloud state fails closed before any Storage delete', async () => {
  for (const result of [
    () => Response.json({ error: 'unavailable' }, { status: 503 }),
    () => Response.json([{ revision: 1, payload: { format: 'corrupt' } }]),
    () => Response.json([{ revision: 0, payload: snapshot() }]),
    () => { throw Error('Network unavailable'); }
  ]) {
    const fetcher = router([[cloudUrl, result], deleteRoute]);
    const response = await handleFiles(filesRequest('delete', { paths: [orphan] }), { env: filesEnv, fetcher });
    assert.ok(response.status >= 500);
    assert.match((await response.json()).error, /No files were deleted/);
    assert.equal(fetcher.find(storageUrl).length, 0);
  }
});

test('the reviewed delete-all action still permanently deletes referenced files', async () => {
  let remaining = [{ id: 'file', name: upload.split('/')[1], metadata: { size: 10 } }];
  const fetcher = router([
    [SUPA + '/storage/v1/object/list/dua-files', () => Response.json(remaining)],
    [storageUrl, (_, init) => { remaining = []; return Response.json(JSON.parse(init.body).prefixes.map(name => ({ name }))); }]
  ]);
  const response = await handleFiles(filesRequest('delete-all', {}), { env: filesEnv, fetcher });
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { deleted: 1 });
  assert.deepEqual(fetcher.find(storageUrl).filter(call => call.method === 'DELETE')[0].json.prefixes, [upload]);
});

test('foreign paths and non-owner accounts cannot use guarded cleanup', async () => {
  const fetcher = router([]);
  const foreign = await handleFiles(filesRequest('delete', { paths: [path(undefined, 'pdf', OTHER)] }), { env: filesEnv, fetcher });
  assert.equal(foreign.status, 400);
  assert.equal(fetcher.find(cloudUrl).length, 0);
  assert.equal(fetcher.find(storageUrl).length, 0);
  const other = router([], { email: 'someone@example.com' });
  assert.equal((await handleFiles(filesRequest('delete', { paths: [upload] }), { env: filesEnv, fetcher: other })).status, 403);
  assert.equal(other.find(cloudUrl).length, 0);
  assert.equal(other.find(storageUrl).length, 0);
});

test('an account identity change during reference validation prevents Storage deletion', async () => {
  const backend = router([[cloudUrl, () => Response.json([])], deleteRoute]);
  let authenticated = 0;
  const fetcher = (url, init) => {
    if (String(url) === SUPA + '/auth/v1/user' && ++authenticated === 2) {
      return Response.json({ id: OTHER, email: 'owner@example.com' });
    }
    return backend(url, init);
  };
  const response = await handleFiles(filesRequest('delete', { paths: [orphan] }), { env: filesEnv, fetcher });
  assert.equal(response.status, 409);
  assert.match((await response.json()).error, /Cloud account changed/);
  assert.equal(backend.find(storageUrl).length, 0);
});
