// Fakes for Imagine tests: Supabase (auth, storage, usage RPCs) and xAI / Gemini endpoints, routed by URL.
import { SUPA, UID, filesEnv, router } from './files-helpers.mjs';

export const imagineEnv = { ...filesEnv };
export const PNG = Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), Buffer.from('fake-png-body')]);
export const JPEG = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), Buffer.from('fake-jpeg-body')]);
export const MP4 = Buffer.concat([Buffer.from([0, 0, 0, 0x18]), Buffer.from('ftypmp42 fake video bytes')]);
export const XAI = 'https://api.x.ai';
export const GEMINI = 'https://generativelanguage.googleapis.com/v1beta/interactions';
export const VIDEO_URL = 'https://vidgen.x.ai/xai-vidgen-bucket/xai-video-req-1.mp4';

/**
 * A stateful fake world. Options: usage (initial {image, video}), objects (path -> Buffer), xai/gemini/video handlers.
 * Returns the router fetcher with `.objects`, `.usage` and `.rpcCalls`.
 */
export function world({ usage = { image: 0, video: 0 }, objects = {}, xai, gemini, videoStatus, videoBytes = MP4, rpcMissing = false, mimeRefused = false, email } = {}) {
  const state = { ...usage };
  const store = { ...objects };
  const rpcCalls = [];
  const storagePrefix = SUPA + '/storage/v1/object/';
  const fetcher = router([
    [SUPA + '/rest/v1/rpc/dua_consume_imagine', (url, init, call) => {
      rpcCalls.push(call);
      if (rpcMissing) return Response.json({ code: 'PGRST202', message: 'Could not find the function' }, { status: 404 });
      const { feature, amount, daily_limit } = call.json;
      const used = state[feature];
      if (used + amount > daily_limit) return Response.json({ allowed: false, used, resetAt: 1791590400 });
      state[feature] = used + amount;
      return Response.json({ allowed: true, used: used + amount, resetAt: 1791590400 });
    }],
    [SUPA + '/rest/v1/rpc/dua_read_imagine_usage', () => Response.json({ images: state.image, videoSeconds: state.video, resetAt: 1791590400 })],
    [(url, init) => init.method === 'POST' && url.startsWith(storagePrefix + 'sign/dua-files/'), url => {
      const path = decodeURIComponent(url.slice((storagePrefix + 'sign/dua-files/').length));
      return store[path] ? Response.json({ signedURL: `/object/sign/dua-files/${path}?token=signed-${path.split('/')[1]}` }) : Response.json({ statusCode: '404', error: 'not_found', message: 'Object not found' }, { status: 400 });
    }],
    [(url, init) => (init.method || 'GET') === 'GET' && url.startsWith(storagePrefix + 'sign/dua-files/') && url.includes('?token=signed-'), url => {
      const path = decodeURIComponent(new URL(url).pathname.slice('/storage/v1/object/sign/dua-files/'.length));
      return new Response(store[path], { headers: { 'content-length': String(store[path].length) } });
    }],
    [(url, init) => init.method === 'POST' && url.startsWith(storagePrefix + 'dua-files/'), (url, init) => {
      const path = decodeURIComponent(url.slice((storagePrefix + 'dua-files/').length));
      if (mimeRefused && new Headers(init.headers).get('content-type') === 'video/mp4') return Response.json({ statusCode: '415', error: 'invalid_mime_type', message: 'mime type video/mp4 is not supported' }, { status: 400 });
      if (store[path]) return Response.json({ statusCode: '409', error: 'Duplicate', message: 'The resource already exists' }, { status: 400 });
      store[path] = Buffer.from(init.body);
      return Response.json({ Key: 'dua-files/' + path });
    }],
    [XAI + '/v1/images/', (url, init, call) => xai ? xai(url, init, call) : Response.json({ data: [{ b64_json: PNG.toString('base64'), mime_type: 'image/png' }, { b64_json: JPEG.toString('base64'), mime_type: 'image/jpeg' }].slice(0, call.json.n), usage: { cost_in_usd_ticks: 800000000 } })],
    [XAI + '/v1/videos/generations', (url, init, call) => xai ? xai(url, init, call) : Response.json({ request_id: 'req-1' })],
    [XAI + '/v1/videos/', (url, init, call) => videoStatus ? videoStatus(url, init, call) : Response.json({ status: 'done', video: { url: VIDEO_URL, duration: 5, respect_moderation: true }, progress: 100, model: 'grok-imagine-video-1.5' })],
    [GEMINI, (url, init, call) => gemini ? gemini(url, init, call) : Response.json({ status: 'completed', steps: [{ type: 'model_output', content: [{ type: 'image', data: JPEG.toString('base64'), mime_type: 'image/jpeg' }] }] })],
    ['https://vidgen.x.ai/', () => new Response(videoBytes, { headers: { 'content-length': String(videoBytes.length), 'content-type': 'video/mp4' } })]
  ], email ? { email } : undefined);
  fetcher.objects = store;
  fetcher.usage = state;
  fetcher.rpcCalls = rpcCalls;
  return fetcher;
}

export function imagineRequest(action, body, { method = 'POST', token = 'user-token' } = {}) {
  return new Request('https://dua.test/api/imagine/' + action, { method, headers: { 'content-type': 'application/json', ...(token ? { authorization: 'Bearer ' + token } : {}) }, ...(method === 'POST' ? { body: JSON.stringify(body ?? {}) } : {}) });
}
export { SUPA, UID };
