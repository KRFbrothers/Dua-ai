import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, copyFileSync, writeFileSync, readFileSync, rmSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';

const required = ['SUPABASE_URL', 'SUPABASE_ANON_KEY', 'DUA_ALLOWED_EMAIL'];

function deployment({ secrets = required, version = '7', listFailure = false, buildFailure = false, resourcesExist = false, settings = {} } = {}) {
  const dir = mkdtempSync(join(tmpdir(), 'dua-cloudrun-deploy-'));
  try {
    for (const file of ['deploy-cloudrun.sh', '.env.example', 'cloudbuild.yaml']) copyFileSync(new URL('../' + file, import.meta.url), join(dir, file));
    // This would execute if a deployment ever sourced local .env files.
    writeFileSync(join(dir, '.env'), 'touch "$GCLOUD_CALLS.leaked"\nGEMINI_API_KEY=must-not-enter-build-or-command-args\n');
    const log = join(dir, 'gcloud-calls');
    writeFileSync(join(dir, 'gcloud'), `#!/usr/bin/env node
import { appendFileSync } from 'node:fs';
const args = process.argv.slice(2);
appendFileSync(process.env.GCLOUD_CALLS, JSON.stringify(args) + '\\n');
if (args[0] === 'secrets' && args[1] === 'list') {
  if (process.env.MOCK_LIST_FAILURE === '1') process.exit(23);
  console.log(JSON.parse(process.env.MOCK_SECRET_NAMES).map(n => 'projects/123/secrets/' + n).join('\\n'));
}
if (args[0] === 'secrets' && args[1] === 'versions') console.log(process.env.MOCK_SECRET_VERSION);
if (['iam', 'artifacts'].includes(args[0]) && args.includes('describe') && process.env.MOCK_RESOURCES_EXIST !== '1') process.exit(1);
if (args[0] === 'builds' && process.env.MOCK_BUILD_FAILURE === '1') process.exit(17);
if (args[0] === 'run' && args[1] === 'services') console.log('https://dua-api.example.run.app');
`, { mode: 0o755 });
    // .mjs isn't required: the temporary directory opts into ESM for the gcloud stub.
    writeFileSync(join(dir, 'package.json'), '{"type":"module"}');
    const result = spawnSync('bash', [join(dir, 'deploy-cloudrun.sh')], {
      env: { PATH: dir + ':' + process.env.PATH, PROJECT_ID: 'dua-test-project', GCLOUD_CALLS: log,
        MOCK_SECRET_NAMES: JSON.stringify(secrets), MOCK_SECRET_VERSION: version,
        MOCK_LIST_FAILURE: listFailure ? '1' : '0', MOCK_BUILD_FAILURE: buildFailure ? '1' : '0',
        MOCK_RESOURCES_EXIST: resourcesExist ? '1' : '0', ...settings }, encoding: 'utf8', timeout: 15000
    });
    assert.ifError(result.error);
    return { ...result, calls: existsSync(log) ? readFileSync(log, 'utf8').trim().split('\n').map(JSON.parse) : [], leaked: existsSync(log + '.leaked') };
  } finally { rmSync(dir, { recursive: true, force: true }); }
}

test('Cloud Run deployment keeps secrets out of builds and grants runtime access only to selected versions', () => {
  const selected = [...required, 'SUPABASE_SERVICE_ROLE_KEY', 'GEMINI_API_KEY', 'GEMINI_MODEL', 'DUA_FILES_BUCKET', 'DUA_FILES', 'GITHUB_BUILD_WRITE_TOKEN'];
  const result = deployment({ secrets: [...selected, 'UNRELATED_SECRET', 'PORT', 'DUA_REQUIRE_LOGIN'] });
  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.leaked, false, '.env must never execute or supply values');
  assert.doesNotMatch(JSON.stringify(result), /must-not-enter-build-or-command-args/);
  assert.ok(!result.calls.some(args => args.includes('access')), 'secret payloads must never be read by the deployer');
  const deploys = result.calls.filter(args => args[0] === 'run' && args[1] === 'deploy');
  assert.equal(deploys.length, 1);
  const deploy = deploys[0];
  assert.equal(deploy[2], 'dua-api');
  for (const option of ['--timeout=900s', '--min-instances=0', '--port=8080', '--allow-unauthenticated']) assert.ok(deploy.includes(option));
  assert.equal(deploy[deploy.indexOf('--region') + 1], 'us-central1');
  assert.equal(deploy[deploy.indexOf('--service-account') + 1], 'dua-api@dua-test-project.iam.gserviceaccount.com');
  const env = deploy.find(arg => arg.startsWith('--set-env-vars='));
  assert.match(env, /DUA_REQUIRE_LOGIN=1/);
  assert.match(env, /DUA_CHAT_DEADLINE_SECONDS=780/);
  assert.match(env, /DUA_BUILD_PR=0/);
  assert.doesNotMatch(env, /DUA_FILES=/, 'a supplied feature flag is imported as a secret instead');
  const refs = deploy.find(arg => arg.startsWith('--set-secrets=')).slice('--set-secrets='.length).split(',');
  assert.deepEqual(refs.sort(), selected.map(name => `${name}=${name}:7`).sort());
  const bindings = result.calls.filter(args => args[0] === 'secrets' && args[1] === 'add-iam-policy-binding');
  assert.deepEqual(bindings.map(args => args[2]).sort(), [...selected].sort());
  for (const args of bindings) {
    assert.ok(args.includes('--role=roles/secretmanager.secretAccessor'));
    assert.ok(args.includes('--member=serviceAccount:dua-api@dua-test-project.iam.gserviceaccount.com'));
  }
  const projectGrants = result.calls.filter(args => args[0] === 'projects');
  assert.equal(projectGrants.length, 1);
  assert.ok(projectGrants[0].includes('--role=roles/cloudbuild.builds.builder'));
  assert.ok(projectGrants[0].includes('--member=serviceAccount:dua-api-build@dua-test-project.iam.gserviceaccount.com'));
  const build = result.calls.find(args => args[0] === 'builds');
  assert.ok(build.includes('--service-account=projects/dua-test-project/serviceAccounts/dua-api-build@dua-test-project.iam.gserviceaccount.com'));
  assert.ok(build.some(arg => arg.startsWith('--substitutions=_IMAGE=us-central1-docker.pkg.dev/dua-test-project/dua-api/api:')));
  assert.ok(!build.some(arg => arg.includes('SUPABASE') || arg.includes('GEMINI_API_KEY')));
  assert.ok(!result.calls.some(args => args.includes('dua-ai-engine')));
  assert.deepEqual(result.calls.filter(args => args[0] === 'iam' && args[2] === 'create').map(args => args[3]), ['dua-api', 'dua-api-build']);
  assert.ok(result.calls.some(args => args[0] === 'artifacts' && args[2] === 'create' && args[3] === 'dua-api'));
});

test('deployment fails closed on missing secrets, missing versions or a failed inventory request', () => {
  for (const options of [{ secrets: required.slice(0, 2) }, { version: '' }, { listFailure: true }]) {
    const result = deployment(options);
    assert.notEqual(result.status, 0);
    assert.ok(!result.calls.some(args => ['iam', 'builds', 'run'].includes(args[0])));
  }
});

test('deployment validates project/deadline and supports a longer explicit deadline', () => {
  for (const settings of [{ PROJECT_ID: '' }, { PROJECT_ID: 'Display Name' }, { DUA_CHAT_DEADLINE_SECONDS: '900' }, { DUA_CHAT_DEADLINE_SECONDS: 'bad' }]) {
    const result = deployment({ settings });
    assert.notEqual(result.status, 0);
    assert.equal(result.calls.length, 0);
  }
  const result = deployment({ resourcesExist: true, settings: { DUA_CHAT_DEADLINE_SECONDS: '600' } });
  assert.equal(result.status, 0, result.stderr);
  const deploy = result.calls.find(args => args[0] === 'run' && args[1] === 'deploy');
  assert.match(deploy.find(arg => arg.startsWith('--set-env-vars=')), /DUA_CHAT_DEADLINE_SECONDS=600/);
  assert.match(deploy.find(arg => arg.startsWith('--set-env-vars=')), /DUA_FILES=0/);
  assert.ok(!result.calls.some(args => ['iam', 'artifacts'].includes(args[0]) && args.includes('create')));
});

test('a failed container build cannot deploy a stale image', () => {
  const result = deployment({ buildFailure: true });
  assert.equal(result.status, 17);
  assert.ok(result.calls.some(args => args[0] === 'builds'));
  assert.ok(!result.calls.some(args => args[0] === 'run'));
});
