import { FormEvent, ReactNode, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { approvalFromEvent, getCloudHistory, streamChat, supportsAgent, type Approval, type ChatMode, type ChatTurn, type CloudChat, type User } from './api';
import ApprovalCard from './ApprovalCard';
import Icon from './Icon';

type ChatRequest = { model: string; mode: ChatMode; messages: ChatTurn[] };
type Message = { id: number; role: 'user' | 'assistant'; text: string; status?: string; error?: string; streaming?: boolean; notice?: string; approvals?: Approval[]; request?: ChatRequest };

const MODELS = ['Dua', 'Gemini'] as const;

function resizeComposer(field: HTMLTextAreaElement) {
  // Measure without a scrollbar; CSS owns the existing min/max height and overflow cap.
  field.style.overflowY = 'hidden';
  field.style.height = 'auto';
  field.style.height = `${field.scrollHeight}px`;
  field.style.overflowY = 'auto';
}

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
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [theme, setTheme] = useState(() => { try { return localStorage.getItem('dua-web-theme') || 'light'; } catch { return 'light'; } });
  const [cloud, setCloud] = useState<CloudChat[]>([]);
  const [cloudError, setCloudError] = useState('');
  const [cloudBusy, setCloudBusy] = useState(false);
  const [cloudRevision, setCloudRevision] = useState(0);
  const [readOnly, setReadOnly] = useState(false);
  const [search, setSearch] = useState('');
  const [localChats, setLocalChats] = useState<{ id: string; title: string; messages: Message[] }[]>([]);
  const activeId = useRef<string>(crypto.randomUUID());
  const abort = useRef<AbortController | null>(null);
  const running = useRef(false);
  const bottom = useRef<HTMLDivElement | null>(null);
  const composer = useRef<HTMLTextAreaElement | null>(null);
  const name = user.displayName || user.email.split('@')[0] || 'Owner';
  const firstName = user.displayName.trim().split(/\s+/)[0];
  const agentAvailable = supportsAgent();
  const settings = useRef<HTMLDialogElement | null>(null);

  useLayoutEffect(() => { if (composer.current) resizeComposer(composer.current); }, [draft]);
  useLayoutEffect(() => {
    const field = composer.current;
    if (!field) return;
    let width = -1;
    let active = true;
    const observer = new ResizeObserver(([entry]) => {
      if (entry.contentRect.width !== width) {
        width = entry.contentRect.width;
        resizeComposer(field);
      }
    });
    observer.observe(field);
    document.fonts.ready.then(() => { if (active) resizeComposer(field); });
    return () => { active = false; observer.disconnect(); };
  }, []);

  useEffect(() => {
    const media = matchMedia('(prefers-color-scheme: dark)');
    const apply = () => { document.documentElement.dataset.theme = theme === 'system' ? media.matches ? 'dark' : 'light' : theme === 'dark' ? 'dark' : 'light'; };
    apply(); media.addEventListener('change', apply);
    try { localStorage.setItem('dua-web-theme', theme); } catch { /* Theme still applies for the current page. */ }
    return () => media.removeEventListener('change', apply);
  }, [theme]);
  useEffect(() => {
    if (settingsOpen) settings.current?.showModal(); else settings.current?.close();
  }, [settingsOpen]);
  useEffect(() => {
    let cancelled = false; setCloudBusy(true); setCloudError('');
    getCloudHistory(user.id).then(chats => { if (!cancelled) setCloud(chats); })
      .catch(() => { if (!cancelled) setCloudError('Cloud history could not be loaded. Your Android data was kept.'); })
      .finally(() => { if (!cancelled) setCloudBusy(false); });
    return () => { cancelled = true; };
  }, [user.id, cloudRevision]);
  useEffect(() => {
    if (!sidebarOpen) return;
    const escape = (event: KeyboardEvent) => { if (event.key === 'Escape') setSidebarOpen(false); };
    document.addEventListener('keydown', escape);
    return () => document.removeEventListener('keydown', escape);
  }, [sidebarOpen]);

  useEffect(() => { bottom.current?.scrollIntoView({ block: 'end' }); }, [messages]);
  useEffect(() => () => abort.current?.abort(), []);

  function update(id: number, change: (m: Message) => Message) {
    setMessages(list => list.map(m => m.id === id ? change(m) : m));
  }

  function newChat() {
    keepLocalChat();
    abort.current?.abort(); abort.current = null; running.current = false;
    setBusy(false); setMessages([]); setReadOnly(false); setDraft(''); setMode('auto');
    activeId.current = crypto.randomUUID();
  }
  function keepLocalChat() {
    if (readOnly || !messages.some(message => message.role === 'user')) return;
    const entry = { id: activeId.current, title: messages.find(message => message.role === 'user')!.text.slice(0, 70),
      messages: messages.map(message => ({ ...message, streaming: false,
        ...(message.streaming ? { error: 'This reply was interrupted before completion.' } : {}) })) };
    setLocalChats(chats => [entry, ...chats.filter(chat => chat.id !== entry.id)]);
  }
  function openSettings() { setSidebarOpen(false); setSettingsOpen(true); }
  function openCloud(chat: CloudChat) {
    keepLocalChat(); abort.current?.abort(); abort.current = null; running.current = false; setBusy(false);
    setMessages(chat.messages.map((message, index) => ({ id: index, role: message.role === 'user' ? 'user' : 'assistant', text: message.text })));
    if (MODELS.includes(chat.model as typeof MODELS[number])) setModel(chat.model as typeof MODELS[number]);
    setReadOnly(true); setSidebarOpen(false);
  }

  async function reply(id: number, request: ChatRequest) {
    if (running.current) return;
    running.current = true;
    setBusy(true);
    const controller = new AbortController();
    abort.current = controller;
    update(id, m => ({ ...m, text: '', error: undefined, notice: undefined, status: undefined, streaming: true }));
    try {
      await streamChat(request, ev => {
        if (abort.current !== controller) return;
        if (ev.type === 'text' && typeof ev.delta === 'string') { const delta = ev.delta; update(id, m => ({ ...m, text: m.text + delta, status: undefined })); }
        else if (ev.type === 'progress' && typeof ev.label === 'string') { const label = ev.label; update(id, m => ({ ...m, status: label })); }
        else if (ev.type === 'thinking') update(id, m => ({ ...m, status: m.status || 'Thinking…' }));
        else if (ev.type === 'notice' && typeof ev.message === 'string') { const notice = ev.message; update(id, m => ({ ...m, notice })); }
        else if (ev.type === 'approval') {
          const action = approvalFromEvent(ev);
          if (!action) throw new Error('The action preview could not be validated. Ask the agent again.');
          update(id, m => ({ ...m, approvals: m.approvals?.some(a => a.id === action.id) ? m.approvals : [...(m.approvals || []), action].slice(0, 3) }));
        }
      }, controller.signal);
    } catch (error) {
      if (abort.current === controller) update(id, m => ({ ...m,
        error: controller.signal.aborted ? agentAvailable ? 'Stopped. This reply is incomplete.' : 'Stopped waiting. The server may still finish this request.' : error instanceof Error ? error.message : 'Reply interrupted. Try again.' }));
    } finally {
      if (abort.current === controller) {
        update(id, m => ({ ...m, streaming: false, status: undefined }));
        abort.current = null; running.current = false; setBusy(false);
      }
    }
  }

  async function send(event: FormEvent) {
    event.preventDefault();
    const text = draft.trim();
    if (!text || running.current || readOnly) return;
    const history = [...messages.filter(m => !m.error && m.text), { id: 0, role: 'user' as const, text }]
      .slice(-40).map(m => ({ role: m.role === 'user' ? 'user' as const : 'model' as const, text: m.text }));
    const id = Date.now();
    const request: ChatRequest = { model, mode, messages: history };
    setMessages(list => [...list, { id, role: 'user', text }, { id: id + 1, role: 'assistant', text: '', streaming: true, request }]);
    setDraft('');
    await reply(id + 1, request);
  }

  const history = cloud.filter(chat => chat.title.toLowerCase().includes(search.toLowerCase()));
  return (
    <div className="app">
      <aside className={sidebarOpen ? 'sidebar open' : 'sidebar'} aria-label="Chats">
        <div className="brand"><span>Dua AI</span><span className="spacer" /><button className="icon-btn" onClick={() => setSidebarOpen(false)} aria-label="Close history"><Icon name="close" /></button></div>
        <nav className="nav">
          <button className="nav-item primary" onClick={() => { newChat(); setSidebarOpen(false); }}><Icon name="plus" /> New chat</button>
          <label className="history-search"><Icon name="search" /><input aria-label="Search chats" placeholder="Search chats" value={search} onChange={event => setSearch(event.target.value)} /></label>
          <button className="nav-item" disabled title="Use Projects in the Android app"><Icon name="folder" /> Projects <small>Android app</small></button>
        </nav>
        <div className="history-scroll">
          {localChats.length > 0 && <div className="section">This tab</div>}
          {localChats.filter(chat => chat.title.toLowerCase().includes(search.toLowerCase())).map(chat => <button className="chat-row" key={chat.id} onClick={() => {
            keepLocalChat(); abort.current?.abort(); abort.current = null; running.current = false; setBusy(false);
            activeId.current = chat.id; setMessages(chat.messages); setReadOnly(false); setSidebarOpen(false);
          }}>{chat.title}</button>)}
          <div className="section">Cloud history <button className="refresh-btn" aria-label="Refresh cloud history" disabled={cloudBusy} onClick={() => setCloudRevision(value => value + 1)}>Refresh</button></div>
          {cloudBusy && <p className="muted small pad" role="status">Loading history…</p>}
          {cloudError && <p className="history-error" role="alert">{cloudError}</p>}
          {!cloudBusy && !cloudError && history.length === 0 && <p className="muted small pad">{search ? 'No matching chats.' : 'No cloud chats yet.'}</p>}
          {history.map(chat => <button key={chat.id} className="chat-row" onClick={() => openCloud(chat)}>{chat.pinned && <span aria-label="Pinned">☆ </span>}{chat.title}</button>)}
        </div>
        <p className="muted sidebar-note">New web replies stay in this tab. Cloud history is read-only.</p>
        <div className="foot">
          <div className="avatar" aria-hidden>{name.charAt(0).toUpperCase()}</div>
          <div className="who"><div>{name}</div><small>Owner</small></div>
          <button className="icon-btn" onClick={openSettings} aria-label="Settings"><Icon name="settings" /></button>
        </div>
      </aside>
      {sidebarOpen && <button className="scrim" onClick={() => setSidebarOpen(false)} aria-label="Close history backdrop" />}
      <main className="main">
        <header className="top">
          <button className="icon-btn menu" onClick={() => setSidebarOpen(true)} aria-label="Open history"><Icon name="menu" /></button>
          <span className="app-name">Dua AI</span>
          <label className="picker">
            <span className="sr-only">Model</span>
            <select value={model} disabled={busy || readOnly} onChange={e => setModel(e.target.value as (typeof MODELS)[number])}>
              {MODELS.map(m => <option key={m}>{m}</option>)}
            </select>
          </label>
          <div className="spacer" />
          {messages.length > 0 ? <button className="icon-btn" onClick={newChat} aria-label="New chat"><Icon name="plus" /></button> : <button className="icon-btn" onClick={openSettings} aria-label="Profile and settings"><span className="avatar">{name.charAt(0).toUpperCase()}</span></button>}
        </header>
        <section className="thread" aria-live="polite">
          <div className="col">
            {messages.length === 0 ? (
              <div className="empty"><img className="hero-logo logo-light" src="/dua-light.svg" alt="Dua AI" /><img className="hero-logo logo-dark" src="/dua-dark.svg" alt="Dua AI" /><h2>{mode === 'agent' ? 'What can I tackle for you?' : `Hi${firstName ? ' ' + firstName : ''}, what’s on your mind?`}</h2>
                {mode === 'agent' && <div className="task-starters">{['Nudge me to back up my photos', 'Learn React by building an app', 'Compare views on a topic I follow'].map(prompt => <button key={prompt} onClick={() => { setDraft(prompt); composer.current?.focus(); }}>{prompt}</button>)}</div>}
              </div>
            ) : messages.map(m => m.role === 'user'
              ? <div key={m.id} className="msg-user"><div>{m.text}</div></div>
              : <div key={m.id} className="msg-ai"><div className="body">
                  {m.notice && <p className="muted small">{m.notice}</p>}
                  {m.status && <div className="muted small">{m.status}</div>}
                  {m.text ? <Markdown text={m.text} /> : m.streaming && !m.status ? <span className="muted">…</span> : null}
                  {m.approvals?.map(action => <ApprovalCard key={action.id} action={action} disabled={busy} />)}
                  {m.error && <div className="banner" role="alert" style={{ marginTop: 8 }}>{m.error}</div>}
                  {m.error && m.request && !m.approvals?.length && messages[messages.length - 1]?.id === m.id &&
                    <div className="retry-row">
                      <button type="button" className="pill" disabled={busy} onClick={() => m.request && reply(m.id, m.request)}>Retry request</button>
                      {m.request.mode === 'agent' && <span className="muted small">Starts the Agent request again.</span>}
                    </div>}
                  {m.error && !!m.approvals?.length && <p className="muted small">Review the action cards before asking the agent again.</p>}
                </div></div>)}
            <div ref={bottom} />
          </div>
        </section>
        {readOnly && <div className="readonly-note">Cloud conversation · read-only. <button className="text-btn" onClick={newChat}>Start a new chat</button></div>}
        {messages.length === 0 && <div className="home-modes" role="group" aria-label="Home mode"><button aria-pressed={mode === 'auto'} onClick={() => setMode('auto')}>Chat</button><button aria-pressed={mode === 'agent'} disabled={!agentAvailable} title={!agentAvailable ? 'Task agents are available in the Android app' : undefined} onClick={() => setMode('agent')}>Task</button></div>}
        <form className="composer" onSubmit={send}>
          <div className="box">
            <button type="button" className="icon-btn" disabled aria-label="Attachments are available in the Android app"><Icon name="plus" /></button>
            <textarea ref={composer} rows={1} maxLength={8000} disabled={readOnly} placeholder={readOnly ? 'Cloud history is read-only' : mode === 'agent' ? 'Describe…' : 'Ask Dua AI…'} aria-label="Message Dua AI" value={draft}
              onChange={e => setDraft(e.target.value)}
              onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(e); } }} />
            <div className="row">
              <button type="button" className="icon-btn" disabled aria-label="Dictation is available in the Android app"><Icon name="mic" /></button>
              {busy
                ? <button type="button" className="send" aria-label={agentAvailable ? 'Stop' : 'Stop waiting'} title={agentAvailable ? 'Stop' : 'Stop waiting; server work may continue'} onClick={() => abort.current?.abort()}><Icon name="stop" /></button>
                : draft.trim() ? <button type="submit" className="send" aria-label="Send" disabled={readOnly}><Icon name="send" /></button>
                  : <button type="button" className="send" disabled aria-label="Live voice is available in the Android app"><Icon name="wave" /></button>}
            </div>
          </div>
          <p className="note">Dua AI can make mistakes. Check important info.</p>
        </form>
      </main>
      <dialog ref={settings} className="settings-dialog" onCancel={() => setSettingsOpen(false)}>
        <div className="settings-title"><h2>Your Dua account</h2><button className="icon-btn" aria-label="Close settings" onClick={() => setSettingsOpen(false)}><Icon name="close" /></button></div>
        <p>{name}</p><p className="muted account-email">{user.email}</p>
        <label className="theme-label">Appearance<select aria-label="Appearance" value={theme} onChange={event => setTheme(event.target.value)}><option value="system">Match system</option><option value="light">Light</option><option value="dark">Dark</option></select></label>
        <p className="muted small">Connected to your Dua backend. Web replies stay in this tab; Android cloud history is read-only.</p>
        <button className="primary-btn" onClick={onLogout}>Sign out</button>
      </dialog>
    </div>
  );
}
