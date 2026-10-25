import test from 'node:test';
import assert from 'node:assert/strict';
import { handleImagine, imagineConfiguration, publicImagineConfiguration, validateImageRequest, validateVideoRequest } from '../src/core/imagine.mjs';
import { xaiImageRequest, geminiImageRequest, xaiVideoRequest, parseXaiVideoStatus, providerFailure, sniffImageType, POLICY_MESSAGE } from '../src/core/imagine-providers.mjs';
import { signJob, verifyJob } from '../src/core/imagine-jobs.mjs';
import { validateCloudSnapshot } from '../src/core/cloud-snapshot.mjs';
import { handleService } from '../src/server/service-handler.mjs';
import { imagineEnv, world, imagineRequest, PNG, JPEG, MP4, XAI, GEMINI, VIDEO_URL, SUPA, UID } from './imagine-helpers.mjs';

const OTHER = '99999999-2222-4333-8444-555555555555';
const SRC = `${UID}/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.png`;
const call = (fetcher, prefix) => fetcher.find(prefix);

test('config: owner, storage and keys gate each feature with a reason; caps and 1080p come from env', () => {
  const full = imagineConfiguration({ ...imagineEnv, GEMINI_API_KEY: 'g' });
  assert.equal(full.enabled, true);
  assert.equal(full.image.enabled, true);
  assert.deepEqual(full.image.providers.map(p => p.id), ['xai', 'gemini']);
  assert.equal(full.image.defaultProvider, 'xai');
  assert.equal(full.image.fallback, true);
  assert.equal(full.image.dailyLimit, 20);
  assert.equal(full.video.dailyLimit, 60);
  assert.deepEqual(full.video.resolutions, ['480p', '720p']);
  assert.equal(full.video.model, 'grok-imagine-video-1.5');
  assert.deepEqual(imagineConfiguration({ ...imagineEnv, DUA_IMAGINE_VIDEO_1080P: '1' }).video.resolutions, ['480p', '720p', '1080p']);
  assert.deepEqual(imagineConfiguration({ ...imagineEnv, DUA_IMAGINE_VIDEO_1080P: '1', DUA_IMAGINE_XAI_VIDEO_MODEL: 'grok-imagine-video-1.5-lite' }).video.resolutions, ['480p', '720p']);
  assert.equal(imagineConfiguration({ ...imagineEnv, DUA_IMAGINE_IMAGE_PROVIDER: 'gemini', GEMINI_API_KEY: 'g' }).image.defaultProvider, 'gemini');
  assert.equal(imagineConfiguration({ ...imagineEnv, GEMINI_API_KEY: 'g', DUA_IMAGINE_IMAGE_FALLBACK: '0' }).image.fallback, false);
  const off = imagineConfiguration({ ...imagineEnv, DUA_IMAGINE_IMAGE: '0', DUA_IMAGINE_VIDEO: '0' });
  assert.equal(off.enabled, false);
  assert.match(off.image.reason, /DUA_IMAGINE_IMAGE=0/);
  assert.match(off.video.reason, /DUA_IMAGINE_VIDEO=0/);
  assert.match(imagineConfiguration({ ...imagineEnv, DUA_ALLOWED_EMAIL: '' }).image.reason, /DUA_ALLOWED_EMAIL/);
  assert.match(imagineConfiguration({ ...imagineEnv, SUPABASE_URL: '' }).video.reason, /stored files/);
  const geminiOnly = imagineConfiguration({ ...imagineEnv, GROK_API_KEY: '' , GEMINI_API_KEY: 'g' });
  assert.equal(geminiOnly.image.enabled, true);
  assert.match(geminiOnly.video.reason, /GROK_API_KEY/);
  assert.match(imagineConfiguration({ ...imagineEnv, DUA_IMAGINE_DAILY_IMAGES: '0' }).image.reason, /DAILY_IMAGES is 0/);
  assert.deepEqual(imagineConfiguration({ ...imagineEnv, DUA_IMAGINE_VIDEO_MAX_SECONDS: '10' }).video.durations, [5, 8, 10]);
  assert.deepEqual(imagineConfiguration({ ...imagineEnv, DUA_IMAGINE_VIDEO_MAX_SECONDS: '12' }).video.durations, [5, 8, 10, 12]);
  assert.equal('_models' in publicImagineConfiguration(imagineEnv), false);
  assert.doesNotMatch(JSON.stringify(publicImagineConfiguration(imagineEnv)), /test-grok-key|sb_publishable/);
});

test('/api/config advertises imagine for new apps without changing existing fields', async () => {
  const response = await handleService(new Request('https://dua.test/api/config'), { env: imagineEnv, fetcher: world() });
  const data = await response.json();
  assert.equal(data.imagine.image.enabled, true);
  assert.equal(data.files.enabled, true);
  assert.ok(Array.isArray(data.providers));
  const setup = await (await handleService(new Request('https://dua.test/api/setup'), { env: imagineEnv })).json();
  assert.deepEqual(setup.groups.map(g => g.id), ['providers', 'search', 'sync', 'memory', 'connectors', 'computer', 'voice', 'account']);
  assert.match(setup.groups[0].limitations.at(-1), /Imagine/);
});

test('request validation: prompt, count, aspect, provider and owner-folder source images', () => {
  const config = imagineConfiguration({ ...imagineEnv, GEMINI_API_KEY: 'g' });
  assert.deepEqual(validateImageRequest({ prompt: ' a fox ', n: 2, aspectRatio: '16:9' }, config), { prompt: 'a fox', n: 2, aspectRatio: '16:9', provider: 'xai', sourcePaths: [], inline: [] });
  const bad = (body, pattern) => assert.throws(() => validateImageRequest(body, config), error => pattern.test(error.message));
  bad({ prompt: '' }, /Describe/);
  bad({ prompt: 'x', n: 5 }, /1 to 4/);
  bad({ prompt: 'x', aspectRatio: '7:3' }, /aspect/);
  bad({ prompt: 'x', provider: 'openai' }, /not configured/);
  bad({ prompt: 'x', extra: 1 }, /Invalid/);
  bad({ prompt: 'x', sourcePaths: [`${UID}/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.pdf`] }, /JPEG, PNG or WebP/);
  bad({ prompt: 'x', sourcePaths: [SRC, SRC, SRC, SRC] }, /up to 3/);
  bad({ prompt: 'x', sourceImages: [{ mimeType: 'image/gif', data: Buffer.from('GIF89a').toString('base64') }] }, /JPEG, PNG or WebP/);
  assert.equal(validateImageRequest({ prompt: 'x', sourceImages: [{ mimeType: 'image/png', data: PNG.toString('base64') }] }, config).inline[0].mimeType, 'image/png');
  const video = validateVideoRequest({ prompt: 'waves', duration: 8, resolution: '480p' }, config);
  assert.deepEqual({ ...video, inline: undefined }, { prompt: 'waves', duration: 8, aspectRatio: '16:9', resolution: '480p', sourcePath: undefined, inline: undefined });
  assert.equal(validateVideoRequest({ sourcePath: SRC }, config).aspectRatio, 'auto');
  assert.throws(() => validateVideoRequest({ prompt: 'x', duration: 16 }, config), /1 to 15/);
  assert.throws(() => validateVideoRequest({ prompt: 'x', resolution: '1080p' }, config), /DUA_IMAGINE_VIDEO_1080P/);
  assert.throws(() => validateVideoRequest({ duration: 5 }, config), /Describe/);
  assert.throws(() => validateVideoRequest({ prompt: 'x', sourcePath: SRC, sourceImage: { mimeType: 'image/png', data: 'AAAA' } }, config), /one source/);
});

test('provider request building follows the documented xAI and Gemini shapes', () => {
  const src = { bytes: PNG, mimeType: 'image/png' };
  const gen = xaiImageRequest({ key: 'k', model: 'grok-imagine-image-2.0', prompt: 'p', n: 2, aspectRatio: '3:4' });
  assert.equal(gen.url, 'https://api.x.ai/v1/images/generations');
  assert.equal(gen.init.headers.Authorization, 'Bearer k');
  assert.deepEqual(JSON.parse(gen.init.body), { model: 'grok-imagine-image-2.0', prompt: 'p', n: 2, response_format: 'b64_json', aspect_ratio: '3:4' });
  const edit = xaiImageRequest({ key: 'k', model: 'm', prompt: 'p', n: 1, aspectRatio: 'auto', sources: [src] });
  assert.equal(edit.url, 'https://api.x.ai/v1/images/edits');
  const editBody = JSON.parse(edit.init.body);
  assert.equal(editBody.aspect_ratio, undefined);
  assert.equal(editBody.image.url, 'data:image/png;base64,' + PNG.toString('base64'));
  const multi = JSON.parse(xaiImageRequest({ key: 'k', model: 'm', prompt: 'p', n: 1, sources: [src, src] }).init.body);
  assert.equal(multi.images.length, 2);
  assert.equal(multi.image, undefined);
  const gem = geminiImageRequest({ key: 'g', model: 'gemini-nano-banana-2.1', prompt: 'p', aspectRatio: '9:16', sources: [src] });
  assert.equal(gem.url, 'https://generativelanguage.googleapis.com/v1beta/interactions');
  assert.equal(gem.init.headers['x-goog-api-key'], 'g');
  assert.deepEqual(JSON.parse(gem.init.body), { model: 'gemini-nano-banana-2.1', store: false,
    input: [{ type: 'text', text: 'p' }, { type: 'image', data: PNG.toString('base64'), mime_type: 'image/png' }],
    response_format: { type: 'image', mime_type: 'image/jpeg', image_size: '1K', aspect_ratio: '9:16' } });
  const video = JSON.parse(xaiVideoRequest({ key: 'k', model: 'grok-imagine-video-1.5', prompt: 'p', duration: 8, aspectRatio: 'auto', resolution: '720p', source: src }).init.body);
  assert.deepEqual(video, { model: 'grok-imagine-video-1.5', prompt: 'p', duration: 8, resolution: '720p', image: { url: 'data:image/png;base64,' + PNG.toString('base64') } });
  assert.equal(sniffImageType(JPEG), 'image/jpeg');
  assert.equal(sniffImageType(Buffer.from('nope')), null);
});

test('provider errors map to safe messages; content-policy refusals are explicit', () => {
  const policy = providerFailure('xai', 400, { code: 'Client specified an invalid argument', error: 'Generated image rejected by content moderation.' });
  assert.equal(policy.status, 422); assert.equal(policy.code, 'content_policy'); assert.equal(policy.message, POLICY_MESSAGE);
  assert.equal(providerFailure('gemini', 400, { error: { message: 'Request blocked due to SAFETY', status: 'INVALID_ARGUMENT' } }).code, 'content_policy');
  assert.match(providerFailure('xai', 401, {}).message, /GROK_API_KEY/);
  assert.match(providerFailure('gemini', 403, {}).message, /GEMINI_API_KEY/);
  assert.equal(providerFailure('xai', 429, {}).status, 429);
  assert.equal(providerFailure('xai', 503, { error: 'secret upstream detail' }).message.includes('secret'), false);
  assert.equal(providerFailure('xai', 503, {}).retryable, true);
  assert.equal(providerFailure('xai', 400, { error: 'bad aspect' }).retryable, false);
  assert.deepEqual(parseXaiVideoStatus({ status: 'pending', progress: 42 }), { status: 'pending', progress: 42 });
  assert.equal(parseXaiVideoStatus({ status: 'done', video: { url: '', respect_moderation: false } }).code, 'content_policy');
  assert.equal(parseXaiVideoStatus({ status: 'failed', error: { code: 'permission_denied', message: 'x' } }).code, 'content_policy');
  assert.equal(parseXaiVideoStatus({ status: 'expired' }).status, 'expired');
  assert.equal(parseXaiVideoStatus({ status: 'done', video: { url: 'http://insecure/x.mp4' } }).status, 'failed');
});

test('POST /api/imagine/image: xAI images are stored in the owner folder with the owner token, cap charged first', async () => {
  const fetcher = world();
  const response = await handleImagine(imagineRequest('image', { prompt: 'A lighthouse at dawn', n: 2, aspectRatio: '16:9' }), { env: imagineEnv, fetcher });
  assert.equal(response.status, 200);
  const data = await response.json();
  assert.equal(data.provider, 'xai');
  assert.equal(data.model, 'grok-imagine-image-2.0');
  assert.equal(data.images.length, 2);
  assert.match(data.images[0].path, new RegExp(`^${UID}/[0-9a-f-]{36}\\.png$`));
  assert.match(data.images[1].path, /\.jpg$/);
  assert.equal(data.images[0].mimeType, 'image/png');
  assert.equal(data.images[0].size, PNG.length);
  assert.match(data.images[0].url, /^https:\/\/proj\.supabase\.co\/storage\/v1\/object\/sign\/dua-files\/.+\?token=/);
  assert.equal(data.images[0].expiresIn, 3600);
  assert.deepEqual(data.usage, { images: { used: 2, limit: 20 }, resetAt: 1791590400 });
  assert.deepEqual(fetcher.objects[data.images[0].path], PNG);
  const rpc = fetcher.rpcCalls[0];
  assert.deepEqual(rpc.json, { feature: 'image', amount: 2, daily_limit: 20 });
  assert.equal(rpc.headers.get('authorization'), 'Bearer user-token');
  assert.equal(rpc.headers.get('apikey'), 'sb_publishable_test');
  const upload = fetcher.calls.find(c => c.url.startsWith(SUPA + '/storage/v1/object/dua-files/'));
  assert.equal(upload.headers.get('authorization'), 'Bearer user-token');
  assert.equal(upload.headers.get('x-upsert'), 'false');
  assert.equal(upload.headers.get('content-type'), 'image/png');
  const provider = call(fetcher, XAI + '/v1/images/generations')[0];
  assert.equal(provider.headers.get('authorization'), 'Bearer test-grok-key');
  assert.equal(provider.json.response_format, 'b64_json');
  // The charge happens before the provider call.
  assert.ok(fetcher.calls.indexOf(rpc) < fetcher.calls.indexOf(provider));
});

test('image edit: owner source is downloaded and sent as a data URI; foreign paths are refused before any charge', async () => {
  const fetcher = world({ objects: { [SRC]: PNG } });
  const response = await handleImagine(imagineRequest('image', { prompt: 'Make it a pencil sketch', sourcePaths: [SRC] }), { env: imagineEnv, fetcher });
  assert.equal(response.status, 200);
  assert.equal((await response.json()).edit, true);
  const edit = call(fetcher, XAI + '/v1/images/edits')[0];
  assert.equal(edit.json.image.url, 'data:image/png;base64,' + PNG.toString('base64'));
  const foreign = world();
  const refused = await handleImagine(imagineRequest('image', { prompt: 'x', sourcePaths: [`${OTHER}/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.png`] }), { env: imagineEnv, fetcher: foreign });
  assert.equal(refused.status, 404);
  assert.equal(foreign.rpcCalls.length, 0);
  assert.equal(call(foreign, XAI).length, 0);
});

test('daily image cap: 429 with Retry-After and no provider call; caps are durable via the RPC', async () => {
  const fetcher = world({ usage: { image: 19, video: 0 } });
  const response = await handleImagine(imagineRequest('image', { prompt: 'x', n: 2 }), { env: imagineEnv, fetcher });
  assert.equal(response.status, 429);
  assert.ok(Number(response.headers.get('retry-after')) > 0);
  const data = await response.json();
  assert.equal(data.code, 'cap');
  assert.match(data.error, /19 of 20 images used \(1 left, this request needs 2\)/);
  assert.equal(call(fetcher, XAI).length, 0);
  const custom = world({ usage: { image: 4, video: 0 } });
  const limited = await handleImagine(imagineRequest('image', { prompt: 'x' }), { env: { ...imagineEnv, DUA_IMAGINE_DAILY_IMAGES: '4' }, fetcher: custom });
  assert.equal(limited.status, 429);
});

test('missing migration gives a setup message instead of generating uncapped', async () => {
  const fetcher = world({ rpcMissing: true });
  const response = await handleImagine(imagineRequest('image', { prompt: 'x' }), { env: imagineEnv, fetcher });
  assert.equal(response.status, 503);
  assert.match((await response.json()).error, /202610090001_dua_imagine\.sql/);
  assert.equal(call(fetcher, XAI).length, 0);
});

test('content-policy refusals come back as 422 with a clear message', async () => {
  const refused = world({ xai: () => Response.json({ code: 'Client specified an invalid argument', error: 'Prompt violates our usage policy' }, { status: 400 }) });
  const response = await handleImagine(imagineRequest('image', { prompt: 'something not allowed' }), { env: { ...imagineEnv, GEMINI_API_KEY: 'g' }, fetcher: refused });
  assert.equal(response.status, 422);
  const data = await response.json();
  assert.equal(data.code, 'content_policy');
  assert.equal(data.error, POLICY_MESSAGE);
  assert.equal(data.usage.used, 1);
  // Not retried with the fallback provider.
  assert.equal(call(refused, GEMINI).length, 0);
  const empty = world({ xai: () => Response.json({ data: [{ url: null }] }) });
  assert.equal((await handleImagine(imagineRequest('image', { prompt: 'x' }), { env: imagineEnv, fetcher: empty })).status, 422);
});

test('xAI outage falls back to Gemini (one interaction per image) with a notice; Gemini can be the default', async () => {
  const fetcher = world({ xai: () => Response.json({ error: 'overloaded' }, { status: 503 }) });
  const response = await handleImagine(imagineRequest('image', { prompt: 'a cat', n: 2, aspectRatio: '1:1' }), { env: { ...imagineEnv, GEMINI_API_KEY: 'g' }, fetcher });
  assert.equal(response.status, 200);
  const data = await response.json();
  assert.equal(data.provider, 'gemini');
  assert.equal(data.images.length, 2);
  assert.match(data.notice, /Grok Imagine was unavailable/);
  const interactions = call(fetcher, GEMINI);
  assert.equal(interactions.length, 2);
  assert.equal(interactions[0].headers.get('x-goog-api-key'), 'g');
  assert.equal(interactions[0].json.response_format.aspect_ratio, '1:1');
  const noFallback = world({ xai: () => Response.json({ error: 'overloaded' }, { status: 503 }) });
  assert.equal((await handleImagine(imagineRequest('image', { prompt: 'x' }), { env: { ...imagineEnv, GEMINI_API_KEY: 'g', DUA_IMAGINE_IMAGE_FALLBACK: '0' }, fetcher: noFallback })).status, 502);
  const chosen = world();
  const viaGemini = await handleImagine(imagineRequest('image', { prompt: 'x', provider: 'gemini' }), { env: { ...imagineEnv, GEMINI_API_KEY: 'g' }, fetcher: chosen });
  assert.equal((await viaGemini.json()).provider, 'gemini');
  assert.equal(call(chosen, XAI).length, 0);
  const refusal = world({ gemini: () => Response.json({ status: 'completed', steps: [{ type: 'model_output', content: [{ type: 'text', text: 'I cannot create that.' }] }] }) });
  assert.equal((await handleImagine(imagineRequest('image', { prompt: 'x' }), { env: { ...imagineEnv, GROK_API_KEY: '', GEMINI_API_KEY: 'g' }, fetcher: refusal })).status, 422);
});

test('access: owner sign-in required; feature switches return 503 with the reason', async () => {
  assert.equal((await handleImagine(imagineRequest('image', { prompt: 'x' }, { token: null }), { env: imagineEnv, fetcher: world() })).status, 401);
  const stranger = await handleImagine(imagineRequest('image', { prompt: 'x' }), { env: imagineEnv, fetcher: world({ email: 'someone@example.com' }) });
  assert.equal(stranger.status, 403);
  assert.match((await stranger.json()).error, /Imagine/);
  const off = await handleImagine(imagineRequest('image', { prompt: 'x' }), { env: { ...imagineEnv, DUA_IMAGINE_IMAGE: '0' }, fetcher: world() });
  assert.equal(off.status, 503);
  assert.match((await off.json()).error, /DUA_IMAGINE_IMAGE=0/);
  assert.equal((await handleImagine(imagineRequest('video', { prompt: 'x' }), { env: { ...imagineEnv, DUA_IMAGINE_VIDEO: '0' }, fetcher: world() })).status, 503);
  assert.equal((await handleImagine(imagineRequest('image', {}, { method: 'GET' }), { env: imagineEnv, fetcher: world() })).status, 405);
  assert.equal((await handleImagine(imagineRequest('nope', {}), { env: imagineEnv, fetcher: world() })).status, 404);
  // Routed through the service handler too.
  assert.equal((await handleService(imagineRequest('usage', null, { method: 'GET' }), { env: imagineEnv, fetcher: world({ usage: { image: 3, video: 10 } }) })).status, 200);
});

test('video: submit charges seconds and returns a signed job; status polls until done, then stores the MP4 once', async () => {
  let polls = 0;
  const fetcher = world({ objects: { [SRC]: PNG }, videoStatus: () => ++polls === 1 ? Response.json({ status: 'pending', progress: 30 }) : Response.json({ status: 'done', video: { url: VIDEO_URL, duration: 8, respect_moderation: true }, progress: 100 }) });
  const created = await handleImagine(imagineRequest('video', { prompt: 'Waves crash', duration: 8, resolution: '480p', sourcePath: SRC }), { env: imagineEnv, fetcher });
  assert.equal(created.status, 200);
  const job = await created.json();
  assert.equal(job.status, 'pending');
  assert.equal(job.seconds, 8);
  assert.equal(job.aspectRatio, 'auto');
  assert.equal(job.pollAfterSeconds, 5);
  assert.deepEqual(job.usage, { videoSeconds: { used: 8, limit: 60 }, resetAt: 1791590400 });
  const submit = call(fetcher, XAI + '/v1/videos/generations')[0];
  assert.deepEqual({ ...submit.json, image: undefined }, { model: 'grok-imagine-video-1.5', prompt: 'Waves crash', duration: 8, resolution: '480p', image: undefined });
  assert.match(submit.json.image.url, /^data:image\/png;base64,/);
  assert.equal(submit.json.aspect_ratio, undefined);
  assert.deepEqual(fetcher.rpcCalls[0].json, { feature: 'video', amount: 8, daily_limit: 60 });

  const pending = await (await handleImagine(imagineRequest('status', { jobId: job.jobId }), { env: imagineEnv, fetcher })).json();
  assert.deepEqual(pending, { status: 'pending', progress: 30, pollAfterSeconds: 5 });
  assert.equal(call(fetcher, XAI + '/v1/videos/req-1')[0].headers.get('authorization'), 'Bearer test-grok-key');

  const done = await (await handleImagine(imagineRequest('status', { jobId: job.jobId }), { env: imagineEnv, fetcher })).json();
  assert.equal(done.status, 'done');
  assert.match(done.video.path, new RegExp(`^${UID}/[0-9a-f-]{36}\\.mp4$`));
  assert.equal(done.video.mimeType, 'video/mp4');
  assert.equal(done.video.size, MP4.length);
  assert.equal(done.video.duration, 8);
  assert.deepEqual(fetcher.objects[done.video.path], MP4);
  const upload = fetcher.calls.find(c => c.url === `${SUPA}/storage/v1/object/dua-files/${done.video.path}`);
  assert.equal(upload.headers.get('content-type'), 'video/mp4');
  assert.equal(upload.headers.get('authorization'), 'Bearer user-token');
  // A repeated poll reuses the stored object without downloading again.
  const again = await (await handleImagine(imagineRequest('status', { jobId: job.jobId }), { env: imagineEnv, fetcher })).json();
  assert.equal(again.video.path, done.video.path);
  assert.equal(call(fetcher, 'https://vidgen.x.ai/').length, 1);
  // Seconds were charged once, at submit.
  assert.equal(fetcher.rpcCalls.length, 1);
});

test('video status: moderation, failures, expiry, forged or foreign jobs, storage refusal and oversize videos', async () => {
  const secret = imagineEnv.GROK_API_KEY;
  const token = (extra = {}) => signJob({ r: 'req-9', p: `${UID}/bbbbbbbb-bbbb-4ccc-8ddd-eeeeeeeeeeee.mp4`, s: 5, u: UID, t: Math.floor(Date.now() / 1000), m: 'grok-imagine-video-1.5', ...extra }, secret);
  const status = async (fetcher, jobId, env = imagineEnv) => handleImagine(imagineRequest('status', { jobId }), { env, fetcher });
  const moderated = await (await status(world({ videoStatus: () => Response.json({ status: 'done', video: { url: '', respect_moderation: false } }) }), token())).json();
  assert.equal(moderated.status, 'failed'); assert.equal(moderated.code, 'content_policy');
  const failed = await (await status(world({ videoStatus: () => Response.json({ status: 'failed', error: { code: 'internal_error', message: 'boom' } }) }), token())).json();
  assert.equal(failed.status, 'failed'); assert.doesNotMatch(failed.error, /boom/);
  assert.equal((await (await status(world({ videoStatus: () => Response.json({ status: 'expired' }) }), token())).json()).status, 'expired');
  assert.equal((await (await status(world({ videoStatus: () => Response.json({ code: 'not found' }, { status: 404 }) }), token())).json()).status, 'expired');
  assert.equal((await (await status(world(), token({ t: Math.floor(Date.now() / 1000) - 49 * 3600 }))).json()).status, 'expired');
  // Change a middle signature character: the last one carries 2 padding bits that decoders ignore,
  // so editing it could leave the signature bytes unchanged (this made the test flaky).
  const flip = (value, index) => value.slice(0, index) + (value[index] === 'A' ? 'B' : 'A') + value.slice(index + 1);
  const valid = token();
  assert.equal((await status(world(), flip(valid, valid.length - 20))).status, 404);
  assert.equal((await status(world(), flip(valid, 5))).status, 404);
  // A non-canonical spelling of the genuine signature (same bytes, different padding bits) is rejected too.
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';
  const last = alphabet.indexOf(valid.at(-1));
  const respelled = valid.slice(0, -1) + alphabet[last ^ 1];
  assert.deepEqual(Buffer.from(respelled.split('.')[1], 'base64url'), Buffer.from(valid.split('.')[1], 'base64url'));
  assert.equal((await status(world(), respelled)).status, 404);
  assert.equal((await status(world(), signJob({ r: 'x', p: `${OTHER}/bbbbbbbb-bbbb-4ccc-8ddd-eeeeeeeeeeee.mp4`, s: 5, u: OTHER, t: Math.floor(Date.now() / 1000) }, secret))).status, 404);
  assert.equal((await status(world(), signJob({ r: 'x', p: `${UID}/bbbbbbbb-bbbb-4ccc-8ddd-eeeeeeeeeeee.mp4`, s: 5, u: UID, t: Math.floor(Date.now() / 1000) }, 'another-key'))).status, 404);
  const refused = await status(world({ mimeRefused: true }), token());
  assert.equal(refused.status, 503);
  assert.match((await refused.json()).error, /Phase 3 migration/);
  const big = await (await status(world({ videoBytes: Buffer.alloc(2 * 1024 * 1024 + 10) }), token(), { ...imagineEnv, DUA_FILES_MAX_MB: '2' })).json();
  assert.equal(big.status, 'done'); assert.equal(big.video.stored, false); assert.equal(big.video.url, VIDEO_URL); assert.match(big.notice, /2 MB storage limit/);
  // Polling keeps working after video generation is switched off, so paid jobs still land.
  assert.equal((await status(world(), token(), { ...imagineEnv, DUA_IMAGINE_VIDEO: '0' })).status, 200);
});

test('video caps and option gating', async () => {
  const capped = world({ usage: { image: 0, video: 58 } });
  const response = await handleImagine(imagineRequest('video', { prompt: 'x', duration: 5 }), { env: imagineEnv, fetcher: capped });
  assert.equal(response.status, 429);
  assert.match((await response.json()).error, /58 of 60 video seconds/);
  assert.equal(call(capped, XAI).length, 0);
  const hd = world();
  const ok = await handleImagine(imagineRequest('video', { prompt: 'x', duration: 5, resolution: '1080p', aspectRatio: '9:16' }), { env: { ...imagineEnv, DUA_IMAGINE_VIDEO_1080P: '1' }, fetcher: hd });
  assert.equal(ok.status, 200);
  assert.deepEqual(call(hd, XAI + '/v1/videos/generations')[0].json, { model: 'grok-imagine-video-1.5', prompt: 'x', duration: 5, aspect_ratio: '9:16', resolution: '1080p' });
  assert.equal((await handleImagine(imagineRequest('video', { prompt: 'x', resolution: '1080p' }), { env: imagineEnv, fetcher: world() })).status, 400);
});

test('GET /api/imagine/usage reports today against the caps', async () => {
  const response = await handleImagine(imagineRequest('usage', null, { method: 'GET' }), { env: { ...imagineEnv, DUA_IMAGINE_DAILY_IMAGES: '30' }, fetcher: world({ usage: { image: 7, video: 15 } }) });
  assert.deepEqual(await response.json(), { images: { used: 7, limit: 30 }, videoSeconds: { used: 15, limit: 60 }, resetAt: 1791590400 });
});

test('job tokens are bound to the owner and tamper-evident', () => {
  const now = Date.UTC(2026, 9, 8);
  const tokenValue = signJob({ r: 'req', p: SRC, s: 5, u: UID, t: Math.floor(now / 1000) }, 'k');
  assert.equal(verifyJob(tokenValue, 'k', { userId: UID, now }).r, 'req');
  assert.equal(verifyJob(tokenValue, 'k', { userId: OTHER, now }), null);
  assert.equal(verifyJob(tokenValue, 'k2', { userId: UID, now }), null);
  assert.equal(verifyJob('x.y', 'k', { userId: UID, now }), null);
  assert.equal(verifyJob(tokenValue, 'k', { userId: UID, now: now + 49 * 3600 * 1000 }).expired, true);
});

test('cloud snapshots keep Imagine media references on model turns and reject anything else', () => {
  const snapshot = media => ({ format: 'dua-cloud-snapshot', version: 1, memory: [], chats: [{ id: 'c', updatedAt: 1, model: 'Dua', draft: '', messages: [
    { role: 'user', text: 'a fox', state: 'complete' }, { role: 'model', text: 'Image: a fox', state: 'complete', media }] }] });
  const ok = validateCloudSnapshot(snapshot([{ path: `${UID}/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.jpg`, mimeType: 'image/jpeg', size: 10 }, { path: `${UID}/bbbbbbbb-bbbb-4ccc-8ddd-eeeeeeeeeeee.mp4`, mimeType: 'video/mp4', duration: 8 }]));
  assert.equal(ok.chats[0].messages[1].media.length, 2);
  assert.equal(validateCloudSnapshot(snapshot(undefined)).chats[0].messages[1].media, undefined);
  assert.throws(() => validateCloudSnapshot(snapshot([{ path: '../x', mimeType: 'image/jpeg' }])));
  assert.throws(() => validateCloudSnapshot(snapshot([{ path: `${UID}/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.jpg`, mimeType: 'image/jpeg', data: 'bytes' }])));
  assert.throws(() => validateCloudSnapshot(snapshot(new Array(5).fill({ path: `${UID}/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.jpg`, mimeType: 'image/jpeg' }))));
});
