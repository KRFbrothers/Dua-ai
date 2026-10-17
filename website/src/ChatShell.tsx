import { FormEvent, useState } from 'react';
import type { User } from './api';

type Message = { id: number; role: 'user' | 'assistant'; text: string };

const MODELS = ['Dua', 'Gemini', 'Claude', 'Grok', 'Kimi'] as const;

export default function ChatShell({ user, onLogout }: { user: User; onLogout: () => void }) {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [model, setModel] = useState<(typeof MODELS)[number]>('Dua');
  const [draft, setDraft] = useState('');
  const [messages, setMessages] = useState<Message[]>([]);
  const name = user.displayName || user.email.split('@')[0] || 'Owner';

  function send(event: FormEvent) {
    event.preventDefault();
    const text = draft.trim();
    if (!text) return;
    setMessages(list => [...list,
      { id: Date.now(), role: 'user', text },
      { id: Date.now() + 1, role: 'assistant', text: 'Website chat is coming in the next update. For now, keep chatting in the Dua app — your history will appear here once sync is connected.' }]);
    setDraft('');
  }

  return (
    <div className="app">
      <aside className={sidebarOpen ? 'sidebar open' : 'sidebar'} aria-label="Chats">
        <div className="brand"><img src="/dua-logo.webp" alt="" /><span>Dua AI</span></div>
        <nav className="nav">
          <button className="nav-item primary" onClick={() => { setMessages([]); setSidebarOpen(false); }}><span aria-hidden>✎</span> New chat</button>
          <button className="nav-item" disabled title="Coming soon"><span aria-hidden>⌕</span> Search chats</button>
          <button className="nav-item" disabled title="Coming soon"><span aria-hidden>▣</span> Projects</button>
          <button className="nav-item" disabled title="Coming soon"><span aria-hidden>✦</span> Imagine</button>
        </nav>
        <div className="section">Chats</div>
        <p className="muted small pad">Your synced chats will show here.</p>
        <div className="foot">
          <div className="avatar" aria-hidden>{name.charAt(0).toUpperCase()}</div>
          <div className="who"><div>{name}</div><small>Owner</small></div>
          <button className="icon-btn" onClick={onLogout} aria-label="Sign out" title="Sign out">⎋</button>
        </div>
      </aside>
      {sidebarOpen && <div className="scrim" onClick={() => setSidebarOpen(false)} aria-hidden />}
      <main className="main">
        <header className="top">
          <button className="icon-btn menu" onClick={() => setSidebarOpen(true)} aria-label="Open sidebar">☰</button>
          <label className="picker">
            <span className="sr-only">Model</span>
            <select value={model} onChange={e => setModel(e.target.value as (typeof MODELS)[number])}>
              {MODELS.map(m => <option key={m}>{m}</option>)}
            </select>
          </label>
          <div className="spacer" />
          <button className="icon-btn" onClick={() => setMessages([])} aria-label="New chat">✎</button>
        </header>
        <section className="thread" aria-live="polite">
          <div className="col">
            {messages.length === 0 ? (
              <div className="empty"><img src="/dua-logo.webp" alt="" /><h2>What can I help with, {name}?</h2></div>
            ) : messages.map(m => m.role === 'user'
              ? <div key={m.id} className="msg-user"><div>{m.text}</div></div>
              : <div key={m.id} className="msg-ai"><img src="/dua-logo.webp" alt="" /><div className="body">{m.text}</div></div>)}
          </div>
        </section>
        <form className="composer" onSubmit={send}>
          <div className="box">
            <textarea rows={1} placeholder="Message Dua AI…" aria-label="Message Dua AI" value={draft}
              onChange={e => setDraft(e.target.value)}
              onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(e); } }} />
            <div className="row">
              <button type="button" className="pill" disabled aria-label="Add attachment (coming soon)">+</button>
              <button type="button" className="pill" disabled>Agent</button>
              <button type="button" className="pill" disabled>Search</button>
              <button type="submit" className="send" aria-label="Send" disabled={!draft.trim()}>↑</button>
            </div>
          </div>
          <p className="note">Dua AI can make mistakes. Check important info.</p>
        </form>
      </main>
    </div>
  );
}
