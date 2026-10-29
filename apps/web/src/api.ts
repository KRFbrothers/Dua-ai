// Same-origin calls to dua-api. Login uses dua-api's email sign-in (the same Supabase
// owner account as the Android app); the session lives in HttpOnly cookies scoped to /api.

export type User = { id: string; email: string; displayName: string; avatarUrl: string };
export type Session = { configured: boolean; user: User | null };

export type CloudChat = { id: string; model: string; title: string; pinned: boolean; updatedAt: number; messages: { role: 'user' | 'model'; text: string }[] };
export type WebConnection = {
  getSession(): Promise<Session>;
  login(email: string, password: string): Promise<Session>;
  logout(): Promise<unknown>;
  chat(request: { model: string; mode: ChatMode; messages: ChatTurn[] }, onEvent: (event: StreamEvent) => void, signal: AbortSignal): Promise<void>;
  cloud(): Promise<unknown>;
};
let connection: WebConnection | null = null;
export function installConnection(value: WebConnection) { connection = value; }
export const supportsAgent = () => !connection;
export const requiresSignInAfterReload = () => Boolean(connection);

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
  if (connection) return connection.getSession();
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
  connection ? connection.login(email, password) : call<Session>('/api/account/email-login', { method: 'POST', body: JSON.stringify({ email, password }) });

export const logout = () => connection ? connection.logout() : call('/api/account/logout', { method: 'POST', body: '{}' });

export async function getCloudHistory(ownerId: string): Promise<CloudChat[]> {
  const raw = (connection ? await connection.cloud() : await call<unknown>('/api/cloud')) as {
    ownerId?: unknown; snapshot?: { format?: unknown; version?: unknown; chats?: unknown };
  };
  if (!raw || raw.ownerId !== ownerId || raw.snapshot?.format !== 'dua-cloud-snapshot' || raw.snapshot.version !== 1 || !Array.isArray(raw.snapshot.chats) || raw.snapshot.chats.length > 100)
    throw new ApiError('Cloud history could not be verified. No data was changed.', 502);
  return raw.snapshot.chats.slice(0, 100).map((value: unknown) => {
    const chat = value as Record<string, unknown>;
    if (!chat || typeof chat.id !== 'string' || typeof chat.model !== 'string' || !Array.isArray(chat.messages)) throw new ApiError('Cloud history needs review.', 502);
    const messages = chat.messages.map((value: unknown) => {
      const message = value as Record<string, unknown>;
      if (!message || !['user', 'model'].includes(String(message.role)) || typeof message.text !== 'string') throw new ApiError('Cloud message could not be verified.', 502);
      return { role: message.role as 'user' | 'model', text: message.text };
    });
    return { id: chat.id, model: chat.model, messages, title: typeof chat.title === 'string' ? chat.title : messages.find(m => m.role === 'user')?.text.slice(0, 70) || 'Untitled chat',
      pinned: chat.pinned === true, updatedAt: typeof chat.updatedAt === 'number' ? chat.updatedAt : 0 };
  }).sort((a, b) => Number(b.pinned) - Number(a.pinned) || b.updatedAt - a.updatedAt);
}

// Website chat: dua-api POST /api/web/chat forwards the cookie session to dua-ai-engine /api/chat
// and streams its SSE events back (see apps/api/src/core/sse.mjs for the event shapes).
export type ChatTurn = { role: 'user' | 'model'; text: string };
export type ChatMode = 'auto' | 'agent';
export type StreamEvent =
  | { type: 'text' | 'thinking'; delta: string }
  | { type: 'progress'; step?: string; label?: string; state?: string }
  | { type: 'notice'; message: string }
  | { type: 'error'; message: string }
  | { type: 'done'; truncated?: boolean }
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
  if (connection) return connection.chat(request, onEvent, signal);
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
  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) throw new ApiError('Reply interrupted before completion. You can retry it.', 502);
      buffer += value;
      if (buffer.length > 2 * 1024 * 1024) throw new ApiError('Reply event is too large.', 502);
      let end: number;
      while ((end = buffer.search(/\r?\n\r?\n/)) >= 0) {
        const block = buffer.slice(0, end);
        buffer = buffer.slice(end).replace(/^\r?\n\r?\n/, '');
        const data = block.split(/\r?\n/).filter(line => line.startsWith('data:')).map(line => line.slice(5).trimStart()).join('\n');
        if (!data) continue;
        let event: StreamEvent;
        try { event = JSON.parse(data) as StreamEvent; }
        catch { throw new ApiError('Reply interrupted by an invalid stream event.', 502); }
        if (!event || typeof event.type !== 'string') throw new ApiError('Invalid reply event.', 502);
        onEvent(event);
        if (event.type === 'error') throw new ApiError(typeof event.message === 'string' ? event.message : 'Reply failed.', 502);
        if (event.type === 'done') {
          if (event.truncated === true) throw new ApiError('Reply reached its output limit and is incomplete.', 502);
          return;
        }
      }
    }
  } finally {
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}

export type Approval = {
  id: string; tool: string; title: string; details: string[]; expiresAt: number;
  diff?: string; diffTruncated?: boolean;
};
export type ActionResult = { status: 'done' | 'rejected' | 'failed'; text: string };

export function approvalFromEvent(event: StreamEvent): Approval | null {
  if (event.type !== 'approval' || typeof event.id !== 'string' || !/^[A-Za-z0-9_-]{16,64}$/.test(event.id) ||
      typeof event.tool !== 'string' || typeof event.title !== 'string' || !Array.isArray(event.details) ||
      !event.details.every((line: unknown) => typeof line === 'string') ||
      typeof event.expiresIn !== 'number' || !Number.isFinite(event.expiresIn) || event.expiresIn < 0) return null;
  return {
    id: event.id, tool: event.tool.slice(0, 100), title: event.title.slice(0, 500),
    details: event.details.slice(0, 12).map((line: string) => line.slice(0, 2000)),
    expiresAt: Date.now() + Math.min(event.expiresIn, 86400) * 1000,
    diff: typeof event.diff === 'string' ? event.diff.slice(0, 24000) : undefined,
    diffTruncated: event.diffTruncated === true
  };
}

export async function decideAction(id: string, decision: 'approve' | 'reject'): Promise<ActionResult> {
  if (connection) throw new ApiError('Agent actions are available in the Android app.', 503);
  // A decision is never automatically retried, even for a 401 or a lost response.
  let response: Response;
  try { response = await fetch('/api/web/agent/action', {
    method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ id, decision })
  }); } catch { throw new ApiError('Action result unknown. Check the destination before asking the agent again.', 502); }
  let data;
  try { data = await response.json(); }
  catch { throw new ApiError('Action result unknown. Check the destination before trying again.', 502); }
  if (!response.ok) throw new ApiError(typeof data?.error === 'string' ? data.error : 'Action result could not be confirmed.', response.status);
  if (!['done', 'rejected', 'failed'].includes(data?.status) || typeof data.text !== 'string')
    throw new ApiError('Action result unknown. Check the destination before trying again.', 502);
  return data as ActionResult;
}
