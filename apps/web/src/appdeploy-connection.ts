import { ApiError, type Session, type User, type WebConnection } from './api';

/** SDK JSON transport; credentials stay in memory and are never written to browser storage. */
export function createAppDeployConnection(api: {
  get(path: string): Promise<{ data: unknown }>;
  post(path: string, body: unknown): Promise<{ data: unknown }>;
}): WebConnection {
  let credentials: { accessToken: string; refreshToken: string; expiresAt: number } | null = null;
  let user: User | null = null;
  let epoch = 0;
  let refreshing: Promise<void> | null = null;

  function normalize(error: unknown) {
    if (error instanceof ApiError) return error;
    const response = (error as { response?: { status?: unknown; data?: { error?: unknown } } } | null)?.response;
    return new ApiError(typeof response?.data?.error === 'string' ? response.data.error.slice(0, 500) : 'Connection failed. Retry or sign in again.',
      typeof response?.status === 'number' ? response.status : 502);
  }
  async function post(path: string, body: unknown) {
    try { return await api.post(path, body); } catch (error) { throw normalize(error); }
  }

  function accept(raw: unknown) {
    const data = raw as { user?: User; session?: typeof credentials };
    if (!data?.user || typeof data.user.id !== 'string' || typeof data.user.email !== 'string' ||
        typeof data.user.displayName !== 'string' || typeof data.user.avatarUrl !== 'string' || !data.session ||
        typeof data.session.accessToken !== 'string' || !/^[A-Za-z0-9._-]{16,10000}$/.test(data.session.accessToken) ||
        typeof data.session.refreshToken !== 'string' || !/^[A-Za-z0-9._-]{16,10000}$/.test(data.session.refreshToken) ||
        !Number.isSafeInteger(data.session.expiresAt) || data.session.expiresAt <= 0) throw new ApiError('Sign-in could not be verified.', 502);
    credentials = { accessToken: data.session.accessToken, refreshToken: data.session.refreshToken, expiresAt: data.session.expiresAt };
    user = data.user;
  }
  function clear() { epoch++; credentials = null; user = null; refreshing = null; }
  async function token() {
    if (!credentials) throw new ApiError('Sign in to continue.', 401);
    if (credentials.expiresAt * 1000 < Date.now() + 60_000) {
      if (!refreshing) {
        const expected = epoch;
        const refreshToken = credentials.refreshToken;
        refreshing = post('/api/dua/refresh', { refreshToken }).then(response => {
          if (expected !== epoch) throw new ApiError('Account changed. Sign in again.', 401);
          accept(response.data);
        }).catch(error => { if (expected === epoch) clear(); throw error; }).finally(() => { if (expected === epoch) refreshing = null; });
      }
      await refreshing;
    }
    if (!credentials) throw new ApiError('Session expired. Sign in again.', 401);
    return credentials.accessToken;
  }
  return {
    async getSession(): Promise<Session> {
      let response;
      try { response = await api.get('/api/dua/status'); } catch (error) { throw normalize(error); }
      const status = response.data as { configured?: unknown; connected?: unknown };
      if (status?.connected !== true) throw new ApiError('Dua server is not reachable. Retry.', 503);
      return { configured: status.configured === true, user };
    },
    async login(email, password) {
      const expected = ++epoch; credentials = null; user = null; refreshing = null;
      const response = await post('/api/dua/login', { email, password });
      if (expected !== epoch) throw new ApiError('Sign-in was cancelled. Try again.', 409);
      accept(response.data); return { configured: true, user };
    },
    async logout() {
      const accessToken = credentials?.accessToken; clear();
      if (accessToken) return post('/api/dua/logout', { accessToken });
    },
    async cloud() {
      const expected = epoch;
      const accessToken = await token();
      if (expected !== epoch) throw new ApiError('Account changed. Sign in again.', 401);
      const response = await post('/api/dua/history', { accessToken });
      if (expected !== epoch) throw new ApiError('Account changed. History was discarded.', 401);
      return response.data;
    },
    async chat(request, onEvent, signal) {
      if (request.mode !== 'auto') throw new ApiError('Task agents are available in the Android app.', 400);
      const expected = epoch;
      const accessToken = await token();
      if (expected !== epoch) throw new ApiError('Account changed. Sign in again.', 401);
      signal.throwIfAborted();
      onEvent({ type: 'progress', label: 'Waiting for Dua…' });
      // The host transports JSON, so a complete provider answer arrives together.
      // Stopping the UI cannot promise cancellation of an already-running server request.
      const response = await new Promise<{ data: unknown }>((resolve, reject) => {
        const cancel = () => { signal.removeEventListener('abort', cancel); reject(signal.reason || new DOMException('Stopped', 'AbortError')); };
        signal.addEventListener('abort', cancel, { once: true });
        post('/api/dua/chat', { accessToken, model: request.model, messages: request.messages }).then(
          value => { signal.removeEventListener('abort', cancel); resolve(value); },
          error => { signal.removeEventListener('abort', cancel); reject(error); },
        );
      });
      signal.throwIfAborted();
      if (expected !== epoch) throw new ApiError('Account changed. Reply was discarded.', 401);
      const data = response.data as { text?: unknown; notice?: unknown; truncated?: unknown };
      if (typeof data?.text !== 'string' || !data.text.trim()) throw new ApiError('Dua returned no usable reply. Retry.', 502);
      onEvent({ type: 'text', delta: data.text });
      if (typeof data.notice === 'string') onEvent({ type: 'notice', message: data.notice });
      onEvent({ type: 'done', truncated: data.truncated === true });
      if (data.truncated === true) throw new ApiError('This reply is incomplete. Ask a shorter question or retry.', 502);
    },
  };
}
