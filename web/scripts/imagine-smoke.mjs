// Local smoke test for /api/imagine with fake providers and a fake Supabase project (no keys, no network).
// Starts the real local HTTP server and drives image generation, editing, a video job and usage over HTTP.
// Run: node scripts/imagine-smoke.mjs
import { makeServer } from '../src/server/server.mjs';
import { imagineEnv, world, UID } from '../test/imagine-helpers.mjs';

const SOURCE = `${UID}/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.png`;
let polls = 0;
const fake = world({
  objects: { [SOURCE]: Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3]) },
  videoStatus: () => ++polls < 2 ? Response.json({ status: 'pending', progress: 50 }) : Response.json({ status: 'done', video: { url: 'https://vidgen.x.ai/fake/smoke.mp4', duration: 5, respect_moderation: true }, progress: 100 })
});
const env = { ...imagineEnv, GEMINI_API_KEY: 'fake-gemini-key', DUA_IMAGINE_DAILY_IMAGES: '5' };
const server = makeServer({ env, key: env.GEMINI_API_KEY, supabaseUrl: env.SUPABASE_URL, supabaseKey: env.SUPABASE_ANON_KEY, fetcher: fake });
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const base = `http://127.0.0.1:${server.address().port}`;
const headers = { 'content-type': 'application/json', authorization: 'Bearer smoke-token' };
const post = async (path, body) => { const r = await fetch(base + path, { method: 'POST', headers, body: JSON.stringify(body) }); return [r.status, await r.json()]; };
const check = (label, ok, detail) => { console.log(`${ok ? 'PASS' : 'FAIL'} ${label}${detail ? ' — ' + detail : ''}`); if (!ok) process.exitCode = 1; };
try {
  const config = await (await fetch(base + '/api/config')).json();
  check('config advertises imagine', config.imagine?.image?.enabled && config.imagine?.video?.enabled, `providers ${config.imagine.image.providers.map(p => p.id)}`);
  let [status, data] = await post('/api/imagine/image', { prompt: 'A watercolor fox', n: 2, aspectRatio: '1:1' });
  check('image generate', status === 200 && data.images.length === 2, `${status} ${data.provider} ${data.images?.map(i => i.path).join(', ')}`);
  [status, data] = await post('/api/imagine/image', { prompt: 'Make it night', sourcePaths: [SOURCE], provider: 'gemini' });
  check('image edit (Gemini)', status === 200 && data.edit === true, `${status} ${data.provider}`);
  [status, data] = await post('/api/imagine/video', { prompt: 'Waves at sunset', duration: 5, resolution: '480p', aspectRatio: '16:9' });
  check('video submit', status === 200 && data.status === 'pending', `${status} ${data.seconds}s ${data.resolution}`);
  const jobId = data.jobId;
  [status, data] = await post('/api/imagine/status', { jobId });
  check('video poll pending', status === 200 && data.status === 'pending', `progress ${data.progress}`);
  [status, data] = await post('/api/imagine/status', { jobId });
  check('video poll done + stored', status === 200 && data.status === 'done' && data.video?.path?.endsWith('.mp4'), data.video?.path);
  const usage = await (await fetch(base + '/api/imagine/usage', { headers })).json();
  check('usage', usage.images.used === 3 && usage.images.limit === 5 && usage.videoSeconds.used === 5, JSON.stringify(usage));
  [status, data] = await post('/api/imagine/image', { prompt: 'x', n: 4 });
  check('daily cap enforced', status === 429 && data.code === 'cap', data.error);
  console.log(`Stored objects: ${Object.keys(fake.objects).length}`);
} finally { server.close(); }
