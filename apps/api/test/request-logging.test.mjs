import test from 'node:test';
import assert from 'node:assert/strict';
import { setTimeout as delay } from 'node:timers/promises';
import { withRequestLog, loggedStage } from '../src/core/request-logging.mjs';
import { handleService } from '../src/server/service-handler.mjs';
import { prepareRepository } from '../src/core/build-chat.mjs';
import { listRepositories } from '../src/core/github.mjs';
import { prLimits, proposeChanges } from '../src/core/build-pr.mjs';
import { filesEnv, router } from './files-helpers.mjs';

const repo = 'private-owner/private-repository';
const content = 'private file contents not for logs';
const request = (action, extra = {}) => new Request('https://dua.test/api/connectors/github?private-url=value', {
  method: 'POST', headers: { 'content-type': 'application/json', authorization: 'Bearer private-session-token', 'x-request-id': 'client-supplied-id' },
  body: JSON.stringify({ action, repository: repo, ...extra }),
});
const capture = () => {
  const records = [];
  return { records, logWriter: line => records.push(JSON.parse(line)) };
};
const finished = records => records.filter(r => r.event === 'stage.finished');
const assertSafeSchema = records => {
  for (const r of records) {
    assert.equal(r.source, 'dua-github');
    assert.match(r.requestId, /^[a-f\d]{8}-(?:[a-f\d]{4}-){3}[a-f\d]{12}$/);
    assert.ok(Object.keys(r).every(k => ['source', 'requestId', 'route', 'event', 'stage', 'outcome', 'durationMs', 'status'].includes(k)));
    if ('durationMs' in r) assert.ok(Number.isInteger(r.durationMs) && r.durationMs >= 0);
  }
};

test('concurrent owner connector reads keep separate IDs and never log credentials or repository data', async () => {
  const { records, logWriter } = capture();
  const env = { ...filesEnv, ENABLE_GITHUB_CONNECTOR: '1', GITHUB_CONNECTOR_TOKEN: 'private-connector-token' };
  const fetcher = router([[url => url.startsWith('https://api.github.com'), async url => {
    await delay(url.includes('/contents/') ? 5 : 1);
    return url.includes('/contents/')
      ? Response.json({ type: 'file', path: 'private-path.md', size: content.length, encoding: 'base64', content: Buffer.from(content).toString('base64') })
      : Response.json({ total_count: 1, items: [{ path: 'private-path.md', repository: { full_name: repo } }] });
  }]]);
  const responses = await Promise.all([
    handleService(request('get-file', { path: 'private-path.md' }), { env, fetcher, logWriter }),
    handleService(request('search-code', { query: 'private search text' }), { env, fetcher, logWriter }),
  ]);
  assert.equal((await responses[0].json()).text, content);
  assert.equal((await responses[1].json()).results.length, 1);
  const starts = records.filter(r => r.event === 'request.started');
  assert.equal(starts.length, 2);
  assert.notEqual(starts[0].requestId, starts[1].requestId);
  for (const start of starts) {
    const group = records.filter(r => r.requestId === start.requestId);
    assert.equal(finished(group).length, 1);
    assert.equal(group.at(-1).event, 'request.response');
    assert.equal(group.at(-1).status, 200);
  }
  assert.deepEqual(finished(records).map(r => r.stage).sort(), ['file_read', 'repository_search']);
  for (const secret of [repo, content, 'private-path.md', 'private search text', 'private-session-token', 'private-connector-token', 'owner@example.com', 'private-url', 'client-supplied-id'])
    assert.ok(!JSON.stringify(records).includes(secret), secret);
  assertSafeSchema(records);
});

test('repository preparation/listing and synchronous proposal outcomes share the request context', async () => {
  const { records, logWriter } = capture();
  const options = { env: { ENABLE_GITHUB_CONNECTOR: '1', GITHUB_CONNECTOR_TOKEN: 'secret' }, fetcher: async url => {
    if (url.includes('/user/repos')) return Response.json([{ full_name: repo }]);
    if (url.includes('/git/trees/')) return Response.json({ tree: [] });
    return Response.json({ full_name: repo, default_branch: 'main' });
  } };
  await withRequestLog(request('get-tree'), async () => {
    assert.equal((await prepareRepository(repo, options)).branch, 'main');
    assert.equal((await listRepositories(options)).length, 1);
    const context = { repository: repo, branch: 'main', read: new Map(), tree: { files: [] }, limits: prLimits({}) };
    const result = proposeChanges({ title: 'private title', summary: 'private summary', changes: [{ path: 'new.md', action: 'create', content }] }, context);
    assert.ok(!(result instanceof Promise), 'proposal validation remains synchronous');
    assert.equal(result.proposal.files[0].content, content);
    assert.ok(proposeChanges({ changes: [] }, context).response.error);
    return new Response('unchanged response');
  }, { logWriter });
  assert.deepEqual(finished(records).map(r => [r.stage, r.outcome]).sort(), [
    ['repository_load', 'success'], ['repository_tree', 'success'], ['repository_list', 'success'],
    ['proposal_creation', 'success'], ['proposal_creation', 'refused'],
  ].sort());
  assert.equal(new Set(records.map(r => r.requestId)).size, 1);
  assert.ok(!JSON.stringify(records).includes('private'));
  assertSafeSchema(records);
});

test('thrown failures keep their original identity and expose only bounded numeric status', async () => {
  const { records, logWriter } = capture();
  const failure = Object.assign(new Error('private error detail'), { name: 'private error type', status: 429 });
  await assert.rejects(withRequestLog(request('get-file'),
    () => loggedStage('file_read', async () => { throw failure; })(), { logWriter }), e => e === failure);
  assert.equal(finished(records)[0].status, 429);
  assert.equal(finished(records)[0].outcome, 'error');
  assert.equal(records.at(-1).event, 'request.failed');
  assert.ok(!JSON.stringify(records).includes('private'));
  assertSafeSchema(records);
});

test('failed log sinks cannot change results, refused access or thrown errors', async () => {
  const logWriter = () => { throw new Error('sink unavailable'); };
  const response = new Response('stream content');
  assert.equal(await withRequestLog(request('get-tree'), () => loggedStage('repository_tree', async () => response)(), { logWriter }), response);
  assert.equal(response.bodyUsed, false, 'logging does not consume response bodies');
  const failure = new Error('unchanged failure');
  await assert.rejects(withRequestLog(request('get-tree'), () => loggedStage('repository_tree', () => { throw failure; })(), { logWriter }), e => e === failure);
  const refused = await handleService(request('get-tree'), { env: {}, logWriter, fetcher: () => assert.fail('must not access GitHub') });
  assert.equal(refused.status, 503);
});

test('unrelated routes have no GitHub logs and response creation is not called stream completion', async () => {
  const { records, logWriter } = capture();
  const response = await handleService(new Request('https://dua.test/api/setup'), { env: {}, logWriter });
  assert.equal(response.status, 200);
  assert.deepEqual(records, []);
  const stream = new Response('data: private-stream-payload\n\n', { headers: { 'Content-Type': 'text/event-stream' } });
  assert.equal(await withRequestLog(new Request('https://dua.test/api/chat'), () => stream, { logWriter }), stream);
  assert.equal(stream.bodyUsed, false);
  assert.deepEqual(records.map(r => r.event), ['request.started', 'request.response']);
});
