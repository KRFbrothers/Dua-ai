// Same-origin calls to dua-api. Login uses dua-api's email sign-in (the same Supabase
// owner account as the Android app); the session lives in HttpOnly cookies scoped to /api.

export type User = { id: string; email: string; displayName: string; avatarUrl: string };
export type Session = { configured: boolean; user: User | null };

export class ApiError extends Error {
  constructor(message: string, readonly status: number) { super(message); }
}

async function call<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(path, {
    credentials: 'same-origin',
    ...init,
    headers: { Accept: 'application/json', ...(init.body ? { 'Content-Type': 'application/json' } : {}), ...init.headers }
  });
  let data: unknown = null;
  try { data = await response.json(); } catch { /* empty body */ }
  if (!response.ok) {
    const message = (data && typeof data === 'object' && 'error' in data && typeof data.error === 'string')
      ? data.error : 'Connection failed. Try again.';
    throw new ApiError(message, response.status);
  }
  return data as T;
}

export async function getSession(): Promise<Session> {
  try { return await call<Session>('/api/account/session'); }
  catch (error) {
    // An expired access cookie: try the refresh cookie once.
    if (error instanceof ApiError && error.status === 401) {
      await call('/api/account/refresh', { method: 'POST', body: '{}' });
      return call<Session>('/api/account/session');
    }
    throw error;
  }
}

export const emailLogin = (email: string, password: string) =>
  call<Session>('/api/account/email-login', { method: 'POST', body: JSON.stringify({ email, password }) });

export const logout = () => call('/api/account/logout', { method: 'POST', body: '{}' });

// Website chat: dua-api POST /api/web/chat forwards the cookie session to dua-ai-engine /api/chat
// and streams its SSE events back (see web/src/core/sse.mjs for the event shapes).
export type ChatTurn = { role: 'user' | 'model'; text: string };
export type ChatMode = 'auto' | 'agent';
export type StreamEvent =
  | { type: 'text' | 'thinking'; delta: string }
  | { type: 'progress'; step?: string; label?: string; state?: string }
  | { type: 'notice'; message: string }
  | { type: 'error'; message: string }
  | { type: 'done' }
  | { type: string; [key: string]: unknown };

async function postChat(body: string, signal: AbortSignal) {
  return fetch('/api/web/chat', {
    method: 'POST', credentials: 'same-origin', signal, body,
    headers: { Accept: 'text/event-stream, application/json', 'Content-Type': 'application/json' }
  });
}

export async function streamChat(
  request: { model: string; mode: ChatMode; messages: ChatTurn[] },
  onEvent: (event: StreamEvent) => void,
  signal: AbortSignal
): Promise<void> {
  const body = JSON.stringify(request);
  let response = await postChat(body, signal);
  if (response.status === 401) {
    // Expired access cookie: refresh once and retry.
    try { await call('/api/account/refresh', { method: 'POST', body: '{}' }); } catch { /* keep the 401 */ }
    response = await postChat(body, signal);
  }
  if (!response.ok || !(response.headers.get('content-type') || '').includes('text/event-stream') || !response.body) {
    let message = 'Reply failed. Try again.';
    try { const data = await response.json(); if (typeof data?.error === 'string') message = data.error; } catch { /* empty */ }
    throw new ApiError(message, response.status);
  }
  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
  let buffer = '';
  for (;;) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += value;
    let end: number;
    while ((end = buffer.search(/\r?\n\r?\n/)) >= 0) {
      const block = buffer.slice(0, end);
      buffer = buffer.slice(end).replace(/^\r?\n\r?\n/, '');
      const data = block.split(/\r?\n/).filter(line => line.startsWith('data:')).map(line => line.slice(5).trimStart()).join('\n');
      if (!data) continue; // heartbeat or comment
      try { onEvent(JSON.parse(data) as StreamEvent); } catch { /* ignore malformed event */ }
    }
  }
}
