import { FormEvent, ReactNode, useEffect, useRef, useState } from 'react';
import { streamChat, type ChatMode, type User } from './api';

type Message = { id: number; role: 'user' | 'assistant'; text: string; status?: string; error?: string; streaming?: boolean };

const MODELS = ['Dua', 'Gemini', 'Claude', 'Grok', 'Kimi'] as const;

// Minimal, safe markdown: ``` fenced code blocks, `inline code` and **bold**. Built as React nodes (no HTML injection).
function inline(text: string, key: string): ReactNode[] {
  return text.split(/(`[^`\n]+`|\*\*[^*\n]+\*\*)/g).map((part, i) => {
    if (part.startsWith('`') && part.endsWith('`') && part.length > 2)
      return <code key={key + i} style={{ background: 'rgba(255,255,255,.08)', padding: '1px 5px', borderRadius: 5 }}>{part.slice(1, -1)}</code>;
    if (part.startsWith('**') && part.endsWith('**') && part.length > 4) return <strong key={key + i}>{part.slice(2, -2)}</strong>;
    return part;
  });
}
function Markdown({ text }: { text: string }) {
  const parts = text.split(/```/);
  return <>{parts.map((part, i) => {
    if (i % 2 === 0) return <span key={i}>{inline(part, i + '-')}</span>;
    const newline = part.indexOf('\n');
    const language = newline > 0 ? part.slice(0, newline).trim() : '';
    const code = newline >= 0 ? part.slice(newline + 1) : part;
    return (
      <pre key={i} style={{ background: 'rgba(0,0,0,.35)', border: '1px solid var(--line)', borderRadius: 10, padding: 12, overflowX: 'auto', whiteSpace: 'pre', margin: '8px 0' }}>
        {language && <div className="muted small" style={{ marginBottom: 6 }}>{language}</div>}
        <code>{code.replace(/\n$/, '')}</code>
      </pre>);
  })}</>;
}

export default function ChatShell({ user, onLogout }: { user: User; onLogout: () => void }) {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [model, setModel] = useState<(typeof MODELS)[number]>('Dua');
  const [mode, setMode] = useState<ChatMode>('auto');
  const [draft, setDraft] = useState('');
  const [messages, setMessages] = useState<Message[]>([]);
  const [busy, setBusy] = useState(false);
  const abort = useRef<AbortController | null>(null);
  const bottom = useRef<HTMLDivElement | null>(null);
  const name = user.displayName || user.email.split('@')[0] || 'Owner';

  useEffect(() => { bottom.current?.scrollIntoView({ block: 'end' }); }, [messages]);
  useEffect(() => () => abort.current?.abort(), []);

  function update(id: number, change: (m: Message) => Message) {
    setMessages(list => list.map(m => m.id === id ? change(m) : m));
  }

  function newChat() { abort.current?.abort(); setMessages([]); }

  async function send(event: FormEvent) {
    event.preventDefault();
    const text = draft.trim();
    if (!text || busy) return;
    const history = [...messages.filter(m => !m.error && m.text), { id: 0, role: 'user' as const, text }]
      .slice(-40).map(m => ({ role: m.role === 'user' ? 'user' as const : 'model' as const, text: m.text }));
    const id = Date.now();
    setMessages(list => [...list, { id, role: 'user', text }, { id: id + 1, role: 'assistant', text: '', streaming: true }]);
    setDraft('');
    setBusy(true);
    const controller = new AbortController();
    abort.current = controller;
    try {
      await streamChat({ model, mode, messages: history }, ev => {
        if (ev.type === 'text' && typeof ev.delta === 'string') { const delta = ev.delta; update(id + 1, m => ({ ...m, text: m.text + delta, status: undefined })); }
        else if (ev.type === 'progress' && typeof ev.label === 'string') { const label = ev.label; update(id + 1, m => ({ ...m, status: label })); }
        else if (ev.type === 'thinking') update(id + 1, m => ({ ...m, status: m.status || 'Thinking…' }));
        else if (ev.type === 'error') { const message = typeof ev.message === 'string' ? ev.message : 'Reply failed.'; update(id + 1, m => ({ ...m, error: message })); }
      }, controller.signal);
    } catch (error) {
      if (!controller.signal.aborted) update(id + 1, m => ({ ...m, error: error instanceof Error ? error.message : 'Reply failed. Try again.' }));
    } finally {
      update(id + 1, m => ({ ...m, streaming: false, status: undefined, error: m.error ?? (controller.signal.aborted && !m.text ? 'Stopped.' : undefined) }));
      if (abort.current === controller) abort.current = null;
      setBusy(false);
    }
  }

  return (
    <div className="app">
      <aside className={sidebarOpen ? 'sidebar open' : 'sidebar'} aria-label="Chats">
        <div className="brand"><img src="/dua-logo.webp" alt="" /><span>Dua AI</span></div>
        <nav className="nav">
          <button className="nav-item primary" onClick={() => { newChat(); setSidebarOpen(false); }}><span aria-hidden>✎</span> New chat</button>
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
          <button className="icon-btn" onClick={newChat} aria-label="New chat">✎</button>
        </header>
        <section className="thread" aria-live="polite">
          <div className="col">
            {messages.length === 0 ? (
              <div className="empty"><img src="/dua-logo.webp" alt="" /><h2>What can I help with, {name}?</h2></div>
            ) : messages.map(m => m.role === 'user'
              ? <div key={m.id} className="msg-user"><div>{m.text}</div></div>
              : <div key={m.id} className="msg-ai"><img src="/dua-logo.webp" alt="" /><div className="body">
                  {m.status && <div className="muted small">{m.status}</div>}
                  {m.text ? <Markdown text={m.text} /> : m.streaming && !m.status ? <span className="muted">…</span> : null}
                  {m.error && <div className="banner" role="alert" style={{ marginTop: 8 }}>{m.error}</div>}
                </div></div>)}
            <div ref={bottom} />
          </div>
        </section>
        <form className="composer" onSubmit={send}>
          <div className="box">
            <textarea rows={1} placeholder="Message Dua AI…" aria-label="Message Dua AI" value={draft}
              onChange={e => setDraft(e.target.value)}
              onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(e); } }} />
            <div className="row">
              <button type="button" className="pill" disabled aria-label="Add attachment (coming soon)">+</button>
              <button type="button" className="pill" aria-pressed={mode === 'agent'} disabled={busy}
                style={mode === 'agent' ? { color: 'var(--text)', borderColor: 'var(--accent)' } : undefined}
                onClick={() => setMode(v => v === 'agent' ? 'auto' : 'agent')}>Agent</button>
              <button type="button" className="pill" disabled>Search</button>
              {busy
                ? <button type="button" className="send" aria-label="Stop" title="Stop" onClick={() => abort.current?.abort()}>■</button>
                : <button type="submit" className="send" aria-label="Send" disabled={!draft.trim()}>↑</button>}
            </div>
          </div>
          <p className="note">Dua AI can make mistakes. Check important info.</p>
        </form>
      </main>
    </div>
  );
}
