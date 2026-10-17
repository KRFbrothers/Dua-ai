import { useEffect, useState } from 'react';
import ChatShell from './ChatShell';
import Login from './Login';
import { getSession, logout, type User } from './api';

export default function App() {
  const [state, setState] = useState<'loading' | 'signed-out' | 'signed-in'>('loading');
  const [user, setUser] = useState<User | null>(null);
  const [configured, setConfigured] = useState(true);

  useEffect(() => {
    getSession()
      .then(session => { setConfigured(session.configured); setUser(session.user); setState(session.user ? 'signed-in' : 'signed-out'); })
      .catch(() => setState('signed-out'));
  }, []);

  if (state === 'loading') return <div className="center" role="status"><img src="/dua-logo.webp" alt="Loading Dua AI" className="pulse" /></div>;
  if (state === 'signed-in' && user)
    return <ChatShell user={user} onLogout={() => { logout().finally(() => { setUser(null); setState('signed-out'); }); }} />;
  return <Login notConfigured={!configured} onSignedIn={u => { setUser(u); setState('signed-in'); }} />;
}
