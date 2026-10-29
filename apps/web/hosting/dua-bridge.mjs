/** Fixed-origin gateway to the owner's existing Dua API. No provider keys or account data are configured here. */
export const DUA_ORIGIN = 'https://dua-api-228305912525.us-central1.run.app';
const record = value => value && typeof value === 'object' && !Array.isArray(value);
const validToken = value => typeof value === 'string' && value.length >= 16 && value.length <= 10000 && /^[A-Za-z0-9._-]+$/.test(value);
const result = (data, status = 200) => ({ data, status });
const fail = (message, status) => result({ error: message }, status);

export function createDuaBridge({ fetcher = fetch } = {}) {
  async function request(path, { method = 'GET', body, accessToken, timeout = 20000 } = {}) {
    const headers = { Accept: 'application/json', 'x-dua-native': '1' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
    const response = await fetcher(DUA_ORIGIN + path, {
      method, headers, redirect: 'error', signal: AbortSignal.timeout(timeout),
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    });
    if (!(response.headers.get('content-type') || '').includes('application/json')) return fail('Dua server returned an unexpected response.', 502);
    const reader = response.body.getReader(); let bytes = 0; const chunks = [];
    try {
      for (;;) {
        const { done, value } = await reader.read(); if (done) break;
        bytes += value.byteLength; if (bytes > 4 * 1024 * 1024 + 4096) throw Error('oversize'); chunks.push(value);
      }
    } finally { await reader.cancel().catch(() => {}); reader.releaseLock(); }
    const merged = new Uint8Array(bytes); let offset = 0;
    for (const chunk of chunks) { merged.set(chunk, offset); offset += chunk.byteLength; }
    const data = JSON.parse(new TextDecoder().decode(merged));
    if (!response.ok) return fail(typeof data?.error === 'string' ? data.error.slice(0, 500) : 'Dua request failed. Retry.', response.status);
    return result(data);
  }
  return async function handle(operation, input = {}) {
    if (!record(input)) return fail('Invalid request.', 400);
    try {
      if (operation === 'status') {
        const response = await request('/api/account/session');
        return response.status === 200
          ? result({ connected: true, configured: response.data.configured === true })
          : fail('Dua account service is unavailable. Retry.', 503);
      }
      if (operation === 'login') {
        if (typeof input.email !== 'string' || input.email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(input.email.trim()) ||
            typeof input.password !== 'string' || !input.password.trim() || input.password.length > 128) return fail('Enter your Dua account email and password.', 400);
        return await request('/api/account/email-login', { method: 'POST', body: { email: input.email.trim(), password: input.password } });
      }
      if (operation === 'refresh') {
        if (!validToken(input.refreshToken)) return fail('Sign in again.', 401);
        return await request('/api/account/refresh', { method: 'POST', body: { refreshToken: input.refreshToken } });
      }
      if (!['logout', 'history', 'chat'].includes(operation)) return fail('Not found.', 404);
      if (!validToken(input.accessToken)) return fail('Sign in to continue.', 401);
      if (operation === 'logout') return await request('/api/account/logout', { method: 'POST', accessToken: input.accessToken, body: {} });
      if (operation === 'history') return await request('/api/cloud', { accessToken: input.accessToken });
      if (!['Dua', 'Gemini'].includes(input.model) || !Array.isArray(input.messages) || !input.messages.length || input.messages.length > 40 ||
          input.messages.some(message => !record(message) || !['user', 'model'].includes(message.role) || typeof message.text !== 'string' || !message.text.trim() || message.text.length > (message.role === 'user' ? 8000 : 80000)) ||
          input.messages.at(-1).role !== 'user' || new TextEncoder().encode(JSON.stringify(input.messages)).length > 256 * 1024)
        return fail('Conversation is invalid or too large. Start a new chat.', 400);
      // Existing native endpoint performs owner verification, kill switches and request quotas.
      return await request('/api/chat', { method: 'POST', accessToken: input.accessToken,
        body: { model: input.model, messages: input.messages.map(({ role, text }) => ({ role, text })), attachments: [], stream: false, mode: 'auto' }, timeout: 25000 });
    } catch {
      return fail('Dua connection failed or timed out. Retry without changing your account settings.', 502);
    }
  };
}
