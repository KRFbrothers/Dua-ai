import test from 'node:test';
import assert from 'node:assert/strict';
import { handleFiles } from '../src/core/files.mjs';
import { SUPA, UID, filesEnv, router, filesRequest } from './files-helpers.mjs';

const listUrl = SUPA + '/storage/v1/object/list/dua-files';
const deleteUrl = SUPA + '/storage/v1/object/dua-files';

// Apply actual requested deletes before the next offset-based listing. Static list
// fixtures hide the shrinking-inventory bug and cannot verify complete cleanup.
function bucket(count, { partialDelete = false } = {}) {
  let objects = Array.from({ length: count }, (_, i) => ({ id: String(i + 1),
    name: `${i.toString(16).padStart(8, '0')}-bbbb-4ccc-8ddd-eeeeeeeeeeee.pdf`, metadata: { size: 1 } }));
  const fetcher = router([
    [listUrl, (_, init, call) => {
      assert.equal(call.json.prefix, UID + '/');
      assert.equal(call.headers.get('authorization'), 'Bearer user-token');
      return Response.json(objects.slice(call.json.offset, call.json.offset + call.json.limit));
    }],
    [deleteUrl, (_, init, call) => {
      assert.equal(call.method, 'DELETE');
      assert.equal(call.headers.get('authorization'), 'Bearer user-token');
      assert.ok(call.json.prefixes.length <= 1000);
      const paths = new Set(partialDelete ? call.json.prefixes.slice(1) : call.json.prefixes);
      const removed = objects.filter(item => paths.has(UID + '/' + item.name));
      objects = objects.filter(item => !paths.has(UID + '/' + item.name));
      return Response.json(removed.map(item => ({ name: item.name })));
    }]
  ]);
  return { fetcher, remaining: () => objects.length };
}

for (const count of [0, 10000, 10001, 20001]) {
  test(`delete-all confirms empty storage after deleting ${count} objects`, async () => {
    const store = bucket(count);
    const response = await handleFiles(filesRequest('delete-all', {}), { env: filesEnv, fetcher: store.fetcher });
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { deleted: count });
    assert.equal(store.remaining(), 0);
    const lists = store.fetcher.find(listUrl);
    assert.equal(lists.at(-1).json.offset, 0, 'verify from the start of the shrinking inventory');
  });
}

test('delete-all bounds work, reports incomplete cleanup as an error, and resumes on retry', async () => {
  const store = bucket(100001);
  const first = await handleFiles(filesRequest('delete-all', {}), { env: filesEnv, fetcher: store.fetcher });
  assert.equal(first.status, 409, 'older clients must not interpret partial cleanup as success');
  const body = await first.json();
  assert.match(body.error, /incomplete.*Retry/i);
  assert.equal(body.deleted, undefined);
  assert.equal(store.remaining(), 1);
  const retry = await handleFiles(filesRequest('delete-all', {}), { env: filesEnv, fetcher: store.fetcher });
  assert.equal(retry.status, 200);
  assert.deepEqual(await retry.json(), { deleted: 1 });
  assert.equal(store.remaining(), 0);
});

test('partial Storage deletes cannot produce a successful delete-all response', async () => {
  const store = bucket(2, { partialDelete: true });
  const response = await handleFiles(filesRequest('delete-all', {}), { env: filesEnv, fetcher: store.fetcher });
  assert.equal(response.status, 409);
  assert.match((await response.json()).error, /incomplete.*Retry/i);
  assert.equal(store.remaining(), 1);
});

test('usage reports exact totals at the inventory boundary and refuses truncated totals', async () => {
  for (const count of [10000, 10001]) {
    const store = bucket(count);
    const response = await handleFiles(filesRequest('usage', null, { method: 'GET' }), { env: filesEnv, fetcher: store.fetcher });
    const body = await response.json();
    if (count === 10000) {
      assert.equal(response.status, 200);
      assert.equal(body.files, count);
      assert.equal(body.bytes, count);
    } else {
      assert.equal(response.status, 409);
      assert.match(body.error, /exact storage usage/i);
      assert.equal(body.files, undefined);
      assert.equal(body.bytes, undefined);
    }
    assert.equal(store.remaining(), count, 'usage never deletes anything');
  }
});
