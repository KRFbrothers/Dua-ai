(function (root) {
  const key = 'dua-history-v2';
  let active = null;
  const id = () => root.crypto?.randomUUID?.() || Date.now().toString(36) + Math.random().toString(36).slice(2);
  const normalize = chat => ({
    id: chat.id, updatedAt: Number(chat.updatedAt) || Date.now(),
    model: ['Dua', 'Gemini'].includes(chat.model) ? chat.model : 'Dua',
    draft: typeof chat.draft === 'string' ? chat.draft : '',
    messages: (Array.isArray(chat.messages) ? chat.messages : []).filter(m => m && ['user', 'model'].includes(m.role) && typeof m.text === 'string')
      .map(m => ({role:m.role, text:m.state === 'pending' ? 'Response stopped' : m.text, state:m.state === 'pending' ? 'stopped' : m.state || 'complete'}))
  });
  function read() {
    const raw = root.localStorage.getItem(key);
    if (!raw) return {activeId:null, chats:[]};
    const data = JSON.parse(raw);
    if (!data || !Array.isArray(data.chats)) throw new Error('Saved chat data could not be loaded.');
    return {activeId:data.activeId, chats:data.chats.filter(c => c && typeof c.id === 'string').map(normalize)};
  }
  function write(data) { root.localStorage.setItem(key, JSON.stringify(data)); }
  function migrate() {
    if (root.localStorage.getItem(key)) return;
    const old = JSON.parse(root.sessionStorage.getItem('dua-chat-v1') || 'null');
    if (old && (old.messages?.length || old.draft)) {
      const chat = normalize({...old, id:id()});
      write({activeId:chat.id, chats:[chat]});
    }
  }
  root.DuaHistory = {
    list() { migrate(); return read().chats.sort((a,b) => b.updatedAt-a.updatedAt); },
    load(chatId) {
      migrate(); const data = read(); active = chatId || data.activeId || id();
      const chat = data.chats.find(c => c.id === active);
      if (chatId && !chat) active = id();
      return chat || null;
    },
    newChat() { migrate(); active = id(); const data=read(); data.activeId=active; write(data); },
    save(snapshot) {
      const data=read(); active ||= data.activeId || id();
      const chat=normalize({...snapshot,id:active,updatedAt:Date.now()});
      const chats=data.chats.filter(c=>c.id!==active);
      if(chat.messages.length || chat.draft.trim()) chats.unshift(chat);
      write({activeId:active,chats}); return active;
    },
    remove(chatId) {
      const data=read(); data.chats=data.chats.filter(c=>c.id!==chatId);
      if(data.activeId===chatId) data.activeId=null;
      if(active===chatId) active=null;
      write(data);
      // Do not resurrect deleted legacy data on the next Home visit.
      root.sessionStorage.removeItem('dua-chat-v1');
    }
  };
})(globalThis);
