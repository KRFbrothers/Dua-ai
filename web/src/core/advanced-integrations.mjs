// Optional personal integrations. Credentials never leave this server module.
// Reply voice audio moved to voice.mjs (/api/voice/*); the legacy `generatedAudio` flag stays for older apps.
import { legacyAudioProvider } from './voice.mjs';
import { boundedBytes, githubReady, GitHubError, listRepositories, repositoryFile, repositoryTree, searchRepository } from './github.mjs';
const noStore = { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' };
const json = (value, status = 200) => Response.json(value, { status, headers: noStore });

// Phase 5B adds read-only tree/file/search actions (also used by Build mode). There are no write actions.
const GITHUB_ACTIONS = ['list-repositories', 'get-tree', 'get-file', 'search-code'];
const ACTION_KEYS = { 'list-repositories': [], 'get-tree': ['repository'], 'get-file': ['repository', 'path'], 'search-code': ['repository', 'query'] };

export function advancedConfiguration(env = {}) {
  return {
    connectors: { github: { configured: githubReady(env), actions: GITHUB_ACTIONS, access: 'read-only' } },
    voice: { generatedAudio: { configured: Boolean(legacyAudioProvider(env)), provider: legacyAudioProvider(env) || 'elevenlabs', maximumCharacters: 2000 }, foregroundInterrupt: true, backgroundConversation: false, fullDuplex: false },
    media: { localCameraPreview: true, localScreenPreview: true, uploads: false, recording: false, remoteComputerControl: false }
  };
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
    const keys = ACTION_KEYS[body.action];
    if (!keys || Object.keys(body).some(key => key !== 'action' && !keys.includes(key))) return json({ error: 'Choose a supported read-only repository action.' }, 400);
    if (!githubReady(env)) return json({ error: 'GitHub connector setup is not enabled.' }, 503);
    const options = { env, fetcher, signal: req.signal };
    if (body.action === 'list-repositories') return json({ repositories: await listRepositories(options), maximumResults: 30, readOnly: true });
    if (body.action === 'get-tree') {
      const tree = await repositoryTree(body.repository, { ...options, maxEntries: 2000 });
      return json({ repository: body.repository, files: tree.files, total: tree.total, truncated: tree.truncated, readOnly: true });
    }
    if (body.action === 'get-file') {
      const file = await repositoryFile(body.repository, body.path, { ...options, maxBytes: 100 * 1024 });
      return json({ repository: body.repository, path: file.path, size: file.size, text: file.text, truncated: file.truncated, readOnly: true });
    }
    const found = await searchRepository(body.repository, body.query, options);
    return json({ repository: body.repository, ...found, readOnly: true });
  } catch (error) {
    if (error instanceof GitHubError) return json({ error: error.message }, error.status === 404 || error.status === 400 || error.status === 413 || error.status === 415 ? error.status : 502);
    return json({ error: error?.name === 'AbortError' || error?.name === 'TimeoutError' ? 'Integration request timed out. Retry.' : 'Integration is temporarily unavailable.' }, 502);
  }
}
