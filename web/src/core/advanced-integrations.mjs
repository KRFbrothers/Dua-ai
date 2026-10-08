// Optional personal integrations. Credentials never leave this server module.
// Reply voice audio moved to voice.mjs (/api/voice/*); the legacy `generatedAudio` flag stays for older apps.
import { legacyAudioProvider } from './voice.mjs';
const noStore = { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' };
const json = (value, status = 200) => Response.json(value, { status, headers: noStore });

function githubReady(env) {
  return env.ENABLE_GITHUB_CONNECTOR === '1' && typeof env.GITHUB_CONNECTOR_TOKEN === 'string' && !!env.GITHUB_CONNECTOR_TOKEN.trim();
}
export function advancedConfiguration(env = {}) {
  return {
    connectors: { github: { configured: githubReady(env), actions: ['list-repositories'], access: 'read-only' } },
    voice: { generatedAudio: { configured: Boolean(legacyAudioProvider(env)), provider: legacyAudioProvider(env) || 'elevenlabs', maximumCharacters: 2000 }, foregroundInterrupt: true, backgroundConversation: false, fullDuplex: false },
    media: { localCameraPreview: true, localScreenPreview: true, uploads: false, recording: false, remoteComputerControl: false }
  };
}

async function boundedBytes(response, limit) {
  const declared = Number(response.headers.get('content-length'));
  if (declared > limit) { await response.body?.cancel(); throw Error('Response too large'); }
  if (!response.body) return new Uint8Array();
  const reader = response.body.getReader();
  let size = 0;
  const chunks = [];
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > limit) { await reader.cancel(); throw Error('Response too large'); }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
  return bytes;
}

// The router must verify a signed-in allowed personal account before setting authorized.
export async function handleAdvanced(req, { env = {}, fetcher = fetch, authorized = false } = {}) {
  const url = new URL(req.url);
  if (url.pathname !== '/api/connectors/github') return json({ error: 'Not found.' }, 404);
  if (req.headers.get('origin') && req.headers.get('origin') !== url.origin) return json({ error: 'Request origin is not allowed.' }, 403);
  if (!authorized) return json({ error: 'Sign in with the allowed personal account to continue.' }, 401);
  if (req.method !== 'POST') return json({ error: 'Method not allowed.' }, 405);
  if (!(req.headers.get('content-type') || '').startsWith('application/json')) return json({ error: 'Use JSON.' }, 415);
  let body;
  try {
    const bytes = await boundedBytes(req, 12000);
    body = JSON.parse(new TextDecoder().decode(bytes));
  } catch { return json({ error: 'Invalid request or request too large.' }, 400); }
  if (!body || typeof body !== 'object' || Array.isArray(body)) return json({ error: 'Invalid request.' }, 400);
  try {
    if (body.action !== 'list-repositories' || Object.keys(body).some(key => key !== 'action')) return json({ error: 'Choose the supported read-only repository action.' }, 400);
    if (!githubReady(env)) return json({ error: 'GitHub connector setup is not enabled.' }, 503);
    const response = await fetcher('https://api.github.com/user/repos?per_page=30&sort=updated&affiliation=owner', {
      method: 'GET', redirect: 'error', signal: AbortSignal.any([req.signal, AbortSignal.timeout(15000)]),
      headers: { Authorization: 'Bearer ' + env.GITHUB_CONNECTOR_TOKEN.trim(), Accept: 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'Dua-Personal-Connector' }
    });
    if (!response.ok) { await response.body?.cancel(); return json({ error: response.status === 429 || response.status === 403 ? 'GitHub access or rate limit prevented this request.' : 'GitHub connector request failed.' }, 502); }
    const repositories = JSON.parse(new TextDecoder().decode(await boundedBytes(response, 1024 * 1024)));
    if (!Array.isArray(repositories) || repositories.length > 30) throw Error('Invalid repository response');
    const safe = repositories.map(repo => {
      if (!repo || !/^[A-Za-z0-9_-]{1,100}\/[A-Za-z0-9_.-]{1,100}$/.test(repo.full_name || '')) throw Error('Invalid repository');
      return { name: repo.full_name.split('/')[1], fullName: repo.full_name, private: repo.private === true, description: typeof repo.description === 'string' ? repo.description.slice(0, 240) : '', url: 'https://github.com/' + repo.full_name };
    });
    return json({ repositories: safe, maximumResults: 30, readOnly: true });
  } catch (error) {
    return json({ error: error?.name === 'AbortError' || error?.name === 'TimeoutError' ? 'Integration request timed out. Retry.' : 'Integration is temporarily unavailable.' }, 502);
  }
}
