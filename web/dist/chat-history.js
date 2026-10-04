(function (root) {
  const key = 'dua-history-v2';
  let active = null;
  let observedGeneration = null;
  const id = () => root.crypto?.randomUUID?.() || Date.now().toString(36) + Math.random().toString(36).slice(2);
  const normalize = chat => ({
    id: chat.id, updatedAt: Number.isFinite(chat.updatedAt) && chat.updatedAt >= 0 ? chat.updatedAt : Date.now(),
    model: ['Dua', 'Gemini', 'ChatGPT', 'Claude', 'Grok', 'Kimi'].includes(chat.model) ? chat.model : 'Dua',
    draft: typeof chat.draft === 'string' ? chat.draft : '',
    messages: (Array.isArray(chat.messages) ? chat.messages : []).filter(m => m && ['user', 'model'].includes(m.role) && typeof m.text === 'string')
      .map(m => ({role:m.role, text:m.state === 'pending' ? 'Response stopped' : m.text, state:m.state === 'pending' ? 'stopped' : m.state || 'complete'}))
  });
  function read() {
    const raw = root.localStorage.getItem(key);
    if (!raw) return {activeId:null, chats:[], generation:"legacy"};
    const data = JSON.parse(raw);
    if (!data || !Array.isArray(data.chats)) throw new Error('Saved chat data could not be loaded.');
    return {generation:typeof data.generation==='string'?data.generation:"legacy", activeId:data.activeId, chats:data.chats.filter(c => c && typeof c.id === 'string').map(normalize)};
  }
  const maxImportBytes = 4 * 1024 * 1024;
  function parseImport(raw) {
    if (typeof raw !== 'string' || new TextEncoder().encode(raw).length > maxImportBytes) throw new Error('Choose a Dua JSON export up to 4 MiB.');
    let depth=0,quoted=false,escaped=false;
    for(const char of raw){
      if(quoted){if(escaped)escaped=false;else if(char==='\\')escaped=true;else if(char==='"')quoted=false;}
      else if(char==='"')quoted=true;
      else if(char==='{'||char==='['){if(++depth>64)throw new Error('Export is too deeply nested. No chats were changed.');}
      else if(char==='}'||char===']')depth--;
    }
    let data;
    try { data = JSON.parse(raw.replace(/^\uFEFF/, '')); } catch { throw new Error('This file is not valid JSON.'); }
    const invalid = () => { throw new Error('Unsupported or invalid Dua export. No chats were changed.'); };
    const integer = value => Number.isSafeInteger(value) && value >= 0;
    if (!data || data.format !== 'dua-chat-export' || data.version !== 1 || !integer(data.exportedAt) || !Array.isArray(data.chats) || data.chats.length > 100) invalid();
    const ids = new Set();
    const records = data.chats.map(chat => {
      if (!chat || typeof chat.id !== 'string' || !chat.id.trim() || chat.id.length > 200 || ids.has(chat.id) || !integer(chat.updatedAt) || !['Dua','Gemini','ChatGPT','Claude','Grok','Kimi'].includes(chat.model) || typeof chat.draft !== 'string' || chat.draft.length > 8000 || !Array.isArray(chat.messages) || chat.messages.length > 500) invalid();
      ids.add(chat.id);
      const messages = chat.messages.map(message => {
        if (!message || !['user','model'].includes(message.role) || typeof message.text !== 'string' || message.text.length > 1048576 || !['complete','stopped'].includes(message.state)) invalid();
        return {role:message.role,text:message.text,state:message.state};
      });
      return {id:chat.id,updatedAt:chat.updatedAt,model:chat.model,draft:chat.draft,messages};
    });
    return records;
  }
  function write(data) { root.localStorage.setItem(key, JSON.stringify(data)); root.dispatchEvent?.(new Event('dua-history')); }
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
      migrate(); const data = read(); observedGeneration=data.generation; active = chatId || data.activeId || id();
      const chat = data.chats.find(c => c.id === active);
      if (chatId && !chat) active = id();
      return chat || null;
    },
    newChat() { migrate(); active = id(); const data=read(); observedGeneration=data.generation; data.activeId=active; write(data); },
    save(snapshot) {
      const data=read();
      if(observedGeneration!==null && observedGeneration!==data.generation) throw new Error('Chat history was cleared in another tab. Reload before saving.');
      observedGeneration=data.generation; active ||= data.activeId || id();
      const chat=normalize({...snapshot,id:active,updatedAt:Date.now()});
      const chats=data.chats.filter(c=>c.id!==active);
      if(chat.messages.length || chat.draft.trim()) chats.unshift(chat);
      write({activeId:active,chats,generation:data.generation}); return active;
    },
    mergeCloud(records) {
      const parsed=parseImport(JSON.stringify({format:'dua-chat-export',version:1,exportedAt:Date.now(),chats:records}));
      migrate();const data=read();const result=root.DuaCloudMerge(data.chats,parsed,id);
      write({...data,chats:result.chats});return {added:result.added,conflicts:result.conflicts};
    },
    exportJson() {
      migrate(); const data=read();
      return JSON.stringify({format:'dua-chat-export',version:1,exportedAt:Date.now(),chats:data.chats.sort((a,b)=>b.updatedAt-a.updatedAt)},null,2);
    },
    maxImportBytes,
    previewImport(raw) { return parseImport(raw).length; },
    importJson(raw) {
      const records=parseImport(raw);
      if (!records.length) return 0;
      // Re-read only when confirmed; retain chats saved while the file was being reviewed.
      migrate(); const data=read();
      const used=new Set(data.chats.map(chat=>chat.id));
      const copies=records.map(chat=>{let next;do{next=id();}while(used.has(next));used.add(next);return {...chat,id:next};});
      write({...data,chats:[...data.chats,...copies]});
      return copies.length;
    },
    clearAll() {
      const generation=id();
      write({activeId:null,chats:[],generation});
      active=null;observedGeneration=generation;
      let cleanupFailed=false;
      for(const key of ['dua-chat-v1','dua-attachments','dua-voice-draft'])try{root.sessionStorage.removeItem(key);}catch{cleanupFailed=true;}
      if(cleanupFailed)throw new Error('Chats deleted; temporary selections could not be cleared.');
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
