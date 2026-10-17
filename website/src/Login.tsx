import { FormEvent, useState } from 'react';
import { ApiError, emailLogin, type User } from './api';

export default function Login({ onSignedIn, notConfigured }: { onSignedIn: (user: User) => void; notConfigured: boolean }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [ownerOnly, setOwnerOnly] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true); setError(''); setOwnerOnly(false);
    try {
      const session = await emailLogin(email, password);
      if (session.user) onSignedIn(session.user);
    } catch (e) {
      if (e instanceof ApiError && e.status === 403) setOwnerOnly(true);
      else setError(e instanceof Error ? e.message : 'Sign-in failed.');
    } finally { setBusy(false); }
  }

  return (
    <main className="login">
      <form className="login-card" onSubmit={submit} aria-labelledby="login-title">
        <img src="/dua-logo.webp" alt="Dua AI logo" className="login-logo" />
        <h1 id="login-title">Welcome to Dua AI</h1>
        <p className="muted">Sign in with your Dua account email and password.</p>
        {notConfigured && <p className="banner" role="alert">Account sign-in is not set up on this server yet.</p>}
        {ownerOnly && <p className="banner" role="alert">Dua AI is a private assistant. Only the owner's account can sign in here.</p>}
        {error && <p className="banner" role="alert">{error}</p>}
        <label>Email<input type="email" autoComplete="username" required value={email} onChange={e => setEmail(e.target.value)} /></label>
        <label>Password<input type="password" autoComplete="current-password" required value={password} onChange={e => setPassword(e.target.value)} /></label>
        <button className="primary-btn" type="submit" disabled={busy || notConfigured}>{busy ? 'Signing in…' : 'Sign in'}</button>
        <p className="muted small">Google accounts: set an email password from the Dua app's Account screen first.</p>
      </form>
    </main>
  );
}
