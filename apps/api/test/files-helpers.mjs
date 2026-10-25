// Fakes for stored-file tests: a Supabase project (auth + storage) and provider endpoints, routed by URL.
import { env } from './helpers.mjs';
export const SUPA = 'https://proj.supabase.co';
export const UID = '11111111-2222-4333-8444-555555555555';
export const OTHER = '99999999-2222-4333-8444-555555555555';
export const filesEnv = { ...env, SUPABASE_URL: SUPA, SUPABASE_ANON_KEY: 'sb_publishable_test', DUA_ALLOWED_EMAIL: 'owner@example.com' };
export const path = (name = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee', ext = 'pdf', uid = UID) => `${uid}/${name}.${ext}`;

/** routes: [[predicate(url, init) | string prefix, handler(url, init, call)]]. Records {url, method, headers, body, json}. */
export function router(routes, { email = 'owner@example.com' } = {}) {
  const calls = [];
  const all = [[url => url === SUPA + '/auth/v1/user', () => Response.json({ id: UID, email })], ...routes];
  const fetcher = async (url, init = {}) => {
    url = String(url);
    const call = { url, method: init.method || 'GET', headers: new Headers(init.headers), body: init.body };
    if (typeof init.body === 'string') { try { call.json = JSON.parse(init.body); } catch {} }
    calls.push(call);
    for (const [match, handler] of all) if (typeof match === 'string' ? url.startsWith(match) : match(url, init)) return handler(url, init, call);
    throw new Error('Unexpected fetch ' + url);
  };
  fetcher.calls = calls;
  fetcher.find = prefix => calls.filter(call => call.url.startsWith(prefix));
  return fetcher;
}
/** Storage routes: signed download URLs (POST /object/sign) + byte downloads (GET ?token=) for {path: Buffer}. */
export function storageRoutes(objects) {
  const prefix = SUPA + '/storage/v1/object/sign/dua-files/';
  return [
    [(url, init) => init.method === 'POST' && url.startsWith(prefix), url => {
      const objectPath = decodeURIComponent(url.slice(prefix.length));
      return objects[objectPath] ? Response.json({ signedURL: `/object/sign/dua-files/${objectPath}?token=signed-${objectPath.split('/')[1]}` }) : Response.json({ statusCode: '404', error: 'not_found', message: 'Object not found' }, { status: 400 });
    }],
    [(url, init) => (init.method || 'GET') === 'GET' && url.startsWith(prefix) && url.includes('?token=signed-'), url => {
      const objectPath = decodeURIComponent(new URL(url).pathname.slice('/storage/v1/object/sign/dua-files/'.length));
      return new Response(objects[objectPath], { headers: { 'content-length': String(objects[objectPath].length) } });
    }]
  ];
}
export function filesRequest(action, body, { method = 'POST', token = 'user-token', origin } = {}) {
  return new Request('https://dua.test/api/files/' + action, { method, headers: { 'content-type': 'application/json', ...(token ? { authorization: 'Bearer ' + token } : {}), ...(origin ? { origin } : {}) }, ...(method === 'POST' ? { body: JSON.stringify(body ?? {}) } : {}) });
}
export function authedChat(body) {
  return new Request('https://dua.test/api/chat', { method: 'POST', headers: { 'content-type': 'application/json', authorization: 'Bearer user-token' }, body: JSON.stringify(body) });
}
/** Builds a small valid one-page PDF containing `text` (Helvetica), with a correct xref table. */
export function makePdf(text) {
  const stream = `BT /F1 18 Tf 72 720 Td (${text}) Tj ET`;
  const objects = ['<< /Type /Catalog /Pages 2 0 R >>', '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
    `<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`, '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>'];
  let out = '%PDF-1.4\n';
  const offsets = objects.map((object, index) => { const at = out.length; out += `${index + 1} 0 obj\n${object}\nendobj\n`; return at; });
  const xref = out.length;
  out += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n` + offsets.map(at => String(at).padStart(10, '0') + ' 00000 n \n').join('');
  out += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(out, 'latin1');
}
