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
