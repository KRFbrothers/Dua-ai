import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, copyFileSync, writeFileSync, readFileSync, rmSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';

const required = ['SUPABASE_URL', 'SUPABASE_ANON_KEY', 'DUA_ALLOWED_EMAIL'];

function deployment({ secrets = required, version = '7', listFailure = false, buildFailure = false, resourcesExist = false, omitTemplateFlags = [], appendTemplate = '', settings = {} } = {}) {
  const dir = mkdtempSync(join(tmpdir(), 'dua-cloudrun-deploy-'));
  try {
    for (const file of ['deploy-cloudrun.sh', '.env.example', 'cloudbuild.yaml']) copyFileSync(new URL('../' + file, import.meta.url), join(dir, file));
    if (omitTemplateFlags.length) {
      const template = join(dir, '.env.example');
      writeFileSync(template, readFileSync(template, 'utf8').split('\n')
        .filter(line => !omitTemplateFlags.some(flag => new RegExp('^#?\\s*' + flag + '=').test(line))).join('\n'));
    }
    if (appendTemplate) writeFileSync(join(dir, '.env.example'), readFileSync(join(dir, '.env.example'), 'utf8') + '\n' + appendTemplate + '\n');
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
      env: { PROJECT_ID: 'dua-test-project', GCLOUD_CALLS: log,
        MOCK_SECRET_NAMES: JSON.stringify(secrets), MOCK_SECRET_VERSION: version,
        MOCK_LIST_FAILURE: listFailure ? '1' : '0', MOCK_BUILD_FAILURE: buildFailure ? '1' : '0',
        MOCK_RESOURCES_EXIST: resourcesExist ? '1' : '0', ...settings, PATH: dir + ':' + (settings.PATH || process.env.PATH) }, encoding: 'utf8', timeout: 15000
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
  const env = deploy.find(arg => arg.startsWith('--update-env-vars='));
  assert.match(env, /DUA_REQUIRE_LOGIN=1/);
  assert.match(env, /DUA_CHAT_DEADLINE_SECONDS=780/);
  assert.match(env, /DUA_BUILD_PR=0/);
  assert.doesNotMatch(env, /DUA_FILES=/, 'a supplied feature flag is imported as a secret instead');
  const refs = deploy.find(arg => arg.startsWith('--update-secrets=')).slice('--update-secrets='.length).split(',');
  assert.deepEqual(refs.sort(), selected.map(name => `${name}=${name}:7`).sort());
  const bindings = result.calls.filter(args => args[0] === 'secrets' && args[1] === 'add-iam-policy-binding');
  assert.deepEqual(bindings.map(args => args[2]).sort(), [...selected].sort());
  for (const args of bindings) {
    assert.ok(args.includes('--role=roles/secretmanager.secretAccessor'));
    assert.ok(args.includes('--member=serviceAccount:dua-api@dua-test-project.iam.gserviceaccount.com'));
  }
  const projectGrants = result.calls.filter(args => args[0] === 'projects');
  assert.deepEqual(projectGrants.map(args => args.find(arg => arg.startsWith('--role='))).sort(), ['--role=roles/cloudbuild.builds.builder', '--role=roles/logging.logWriter']);
  for (const args of projectGrants) assert.ok(args.includes('--member=serviceAccount:dua-api-build@dua-test-project.iam.gserviceaccount.com'));
  const repositoryGrants = result.calls.filter(args => args[0] === 'artifacts' && args[2] === 'add-iam-policy-binding');
  assert.deepEqual(repositoryGrants, [['artifacts', 'repositories', 'add-iam-policy-binding', 'dua-api', '--location', 'us-central1', '--project', 'dua-test-project',
    '--member=serviceAccount:dua-api-build@dua-test-project.iam.gserviceaccount.com', '--role=roles/artifactregistry.writer', '--quiet', '--format=none']]);
  const repositoryCreatedAt = result.calls.findIndex(args => args[0] === 'artifacts' && args[2] === 'create');
  const repositoryGrantedAt = result.calls.indexOf(repositoryGrants[0]);
  const buildAt = result.calls.findIndex(args => args[0] === 'builds');
  assert.ok(repositoryCreatedAt < repositoryGrantedAt && repositoryGrantedAt < buildAt, 'repository writer access is granted after creation and before building');
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

test('existing feature flag secrets must be attached from the template before any IAM, build or deployment', () => {
  const flags = ['DUA_FILES', 'ENABLE_GITHUB_CONNECTOR', 'DUA_BUILD_PR', 'ENABLE_GENERATED_VOICE'];
  for (const flag of flags) {
    const result = deployment({ secrets: [...required, flag], omitTemplateFlags: [flag] });
    assert.notEqual(result.status, 0, flag);
    assert.ok(result.stderr.includes(flag), result.stderr);
    assert.match(result.stderr, /exists in Secret Manager but was not attached from web\/\.env\.example/);
    assert.match(result.stderr, /Restore its NAME=value entry/);
    assert.ok(!result.calls.some(args => ['iam', 'projects', 'artifacts', 'builds', 'run'].includes(args[0])));
    assert.ok(!result.calls.some(args => args.includes('add-iam-policy-binding')));
  }
  const absent = deployment({ omitTemplateFlags: flags });
  assert.equal(absent.status, 0, absent.stderr);
  const env = absent.calls.find(args => args[0] === 'run' && args[1] === 'deploy').find(arg => arg.startsWith('--update-env-vars='));
  for (const flag of flags) assert.ok(env.includes(`${flag}=0`));
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
  assert.match(deploy.find(arg => arg.startsWith('--update-env-vars=')), /DUA_CHAT_DEADLINE_SECONDS=600/);
  assert.match(deploy.find(arg => arg.startsWith('--update-env-vars=')), /DUA_FILES=0/);
  assert.ok(!result.calls.some(args => ['iam', 'artifacts'].includes(args[0]) && args.includes('create')));
  assert.ok(result.calls.some(args => args[0] === 'artifacts' && args[2] === 'add-iam-policy-binding' && args.includes('--role=roles/artifactregistry.writer')),
    'an existing repository still receives the scoped writer grant');
});

test('a failed container build cannot deploy a stale image', () => {
  const result = deployment({ buildFailure: true });
  assert.equal(result.status, 17);
  assert.ok(result.calls.some(args => args[0] === 'builds'));
  assert.ok(!result.calls.some(args => args[0] === 'run'));
});

test('deployment builds the website into web/site before the container build, and fails closed if it breaks', () => {
  const site = mkdtempSync(join(tmpdir(), 'dua-website-'));
  try {
    // Fake npm: `npm run build -- --outDir X` writes X/index.html unless MOCK_SITE_FAIL=1.
    writeFileSync(join(site, 'npm'), `#!/usr/bin/env node
import { appendFileSync, mkdirSync, writeFileSync } from 'node:fs';
const args = process.argv.slice(2);
appendFileSync(process.env.GCLOUD_CALLS, JSON.stringify(['npm', ...args]) + '\\n');
const out = args.indexOf('--outDir');
if (out > 0 && process.env.MOCK_SITE_FAIL !== '1') { mkdirSync(args[out + 1], { recursive: true }); writeFileSync(args[out + 1] + '/index.html', 'ok'); }
`, { mode: 0o755 });
    const env = { DUA_WEBSITE_DIR: site, PATH: site + ':' + process.env.PATH };
    // The npm stub is ESM; mark the folder as a module package.
    writeFileSync(join(site, 'package.json'), '{"type":"module"}');
    const ok = deployment({ settings: env });
    assert.equal(ok.status, 0, ok.stderr);
    const npmAt = ok.calls.findIndex(args => args[0] === 'npm' && args.includes('build'));
    const buildAt = ok.calls.findIndex(args => args[0] === 'builds');
    assert.ok(ok.calls.some(args => args[0] === 'npm' && args[1] === 'ci'));
    assert.ok(npmAt >= 0 && npmAt < buildAt, 'website builds before the container');
    assert.match(ok.calls[npmAt].join(' '), /--outDir \S+\/site --emptyOutDir/);
    const broken = deployment({ settings: { ...env, MOCK_SITE_FAIL: '1' } });
    assert.notEqual(broken.status, 0);
    assert.match(broken.stderr, /did not produce web\/site\/index.html/);
    assert.ok(!broken.calls.some(args => ['iam', 'builds', 'run'].includes(args[0])));
  } finally { rmSync(site, { recursive: true, force: true }); }
});

const KILL_SWITCHES = ['DUA_DISABLE_AGENT', 'DUA_DISABLE_IMAGINE', 'DUA_DISABLE_VOICE', 'DUA_MAINTENANCE', 'DUA_MAINTENANCE_MESSAGE'];
const deployCall = result => result.calls.find(args => args[0] === 'run' && args[1] === 'deploy');
const replacing = /^--(set|clear)-(env-vars|secrets)\b|^--clear-/;

test('owner rule: the deploy script never replaces or clears env vars or secrets', () => {
  const source = readFileSync(new URL('../deploy-cloudrun.sh', import.meta.url), 'utf8');
  const code = source.split('\n').filter(line => !line.trim().startsWith('#')).join('\n');
  assert.doesNotMatch(code, /--(set|clear)-(env-vars|secrets)|--clear-/);
  for (const options of [{}, { resourcesExist: true }, { secrets: [...required, 'DUA_FILES', 'DUA_BUILD_PR'] }, { settings: { DUA_REMOVE_SECRETS: 'OLD_TOKEN' } }]) {
    const result = deployment(options);
    assert.equal(result.status, 0, result.stderr);
    for (const args of result.calls) for (const arg of args) assert.doesNotMatch(arg, replacing, JSON.stringify(args));
    const deploy = deployCall(result);
    assert.ok(deploy.some(arg => arg.startsWith('--update-env-vars=')));
    assert.ok(deploy.some(arg => arg.startsWith('--update-secrets=')));
  }
});

test('kill switches are never passed by a deploy, so values set with gcloud run services update persist', () => {
  const result = deployment({ secrets: [...required, ...KILL_SWITCHES], appendTemplate: 'DUA_DISABLE_AGENT=0\n# DUA_MAINTENANCE=0\nDUA_MAINTENANCE_MESSAGE=\nDUA_DISABLE_FUTURE_THING=0' });
  assert.equal(result.status, 0, result.stderr);
  const deploy = deployCall(result);
  for (const arg of deploy) assert.doesNotMatch(arg, /DUA_DISABLE_|DUA_MAINTENANCE/, arg);
  assert.ok(!result.calls.some(args => args[0] === 'secrets' && args[1] === 'add-iam-policy-binding' && /DUA_DISABLE_|DUA_MAINTENANCE/.test(args[2])));
});

test('a feature flag moved to Secret Manager drops only its old literal', () => {
  const result = deployment({ secrets: [...required, 'DUA_FILES'] });
  assert.equal(result.status, 0, result.stderr);
  const deploy = deployCall(result);
  assert.deepEqual(deploy.filter(arg => arg.startsWith('--remove-')), ['--remove-env-vars=DUA_FILES']);
  assert.doesNotMatch(deploy.find(arg => arg.startsWith('--update-env-vars=')), /DUA_FILES=/);
  // Without such a move nothing is removed at all.
  assert.deepEqual(deployCall(deployment()).filter(arg => arg.startsWith('--remove-')), []);
});

test('DUA_REMOVE_SECRETS removes exactly the named bindings and protects kill switches', () => {
  const result = deployment({ settings: { DUA_REMOVE_SECRETS: 'OLD_TOKEN,LEGACY_KEY' } });
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(deployCall(result).filter(arg => arg.startsWith('--remove-secrets')), ['--remove-secrets=OLD_TOKEN,LEGACY_KEY']);
  for (const bad of ['DUA_MAINTENANCE', 'DUA_DISABLE_AGENT', 'lower', 'A,B C']) {
    const refused = deployment({ settings: { DUA_REMOVE_SECRETS: bad } });
    assert.notEqual(refused.status, 0, bad);
    assert.ok(!refused.calls.some(args => args[0] === 'run'), bad);
  }
});
