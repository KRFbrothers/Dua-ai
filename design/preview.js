'use strict';
(() => {
  const screens = [
    ['home', 'Chat home', 'A quiet start, with Dua’s original calligraphy and a single, readable greeting.', ['Switch between Chat and Task.', 'Choose a model or type a message.', 'Open history from the menu.']],
    ['task', 'Task home', 'A focused starting point for getting something done. A starter fills the draft without sending it.', ['Choose a starter and edit the draft.', 'Send to open a sample conversation.', 'Switch back to Chat without losing your draft.']],
    ['chat', 'Conversation', 'User messages, readable assistant replies and a composer that stays within reach.', ['Send a message for a clearly marked preview reply.', 'Copy a reply or open the sample Canvas.', 'Start a new chat from the top bar.']],
    ['history', 'Chat history', 'A searchable history drawer with pinned chats and a fixed settings entry.', ['Search by title; try a query with no matches.', 'Filter to pinned conversations.', 'Open a chat or start a fresh one.']],
    ['models', 'Model picker', 'One model selector, with letter badges and clear selection states.', ['Choose Dua or Gemini for the preview.', 'Check the selected radio state.', 'Return to the same screen with your draft intact.']],
    ['projects', 'Projects', 'Keep related work together in a compact, personal workspace.', ['Open a sample project in Canvas.', 'Create a project with your own title.', 'Return to Chat from the back arrow.']],
    ['library', 'Library', 'A simple inventory of documents and local file selections.', ['Choose a file from your device.', 'Inspect its name and size.', 'Files are listed locally; nothing is uploaded.']],
    ['canvas', 'Canvas', 'A document you can edit alongside your conversation.', ['Edit the morning plan.', 'Save and return: edits stay in this tab.', 'Use the back arrow to return to your previous screen.']],
    ['voice', 'Voice', 'A visual voice experience, using Dua’s restrained orb treatment.', ['Start and pause the orb preview.', 'Toggle the preview microphone state.', 'No audio is recorded or sent.']],
    ['profile', 'Profile', 'A personal workspace identity, without a copied reference account.', ['Enter a display name.', 'Save it and revisit the Chat greeting.', 'The name is stored only in this browser.']],
    ['settings', 'Settings', 'Grouped settings keep appearance, account and data controls easy to find.', ['Open Appearance and change the theme.', 'Try the notification preferences.', 'Inspect the preview’s storage summary.']],
    ['appearance', 'Appearance', 'System, Light and Dark share the same surfaces and original Dua artwork.', ['Select a theme and inspect the whole page.', 'Reload to check the saved choice.', 'System follows your browser’s color preference.']],
    ['notifications', 'Notifications', 'Clearly labelled, keyboard-accessible notification preferences.', ['Toggle the master preference.', 'Change individual reply and task preferences.', 'These switches do not request device permissions.']],
    ['storage', 'Storage', 'Honest counts of the sample chats and files in this preview.', ['Add a file in Library and revisit this count.', 'Clear the sample chats with confirmation.', 'Reset preview restores the starting sample content.']],
  ];
  const byId = new Map(screens.map((screen, index) => [screen[0], { screen, index }]));
  const readSaved = key => { try { return localStorage.getItem(key); } catch { return null; } };
  const save = (key, value) => { try { localStorage.setItem(key, value); } catch { toast('Browser storage is unavailable; changes last for this tab.'); } };
  const escape = value => String(value).replace(/[&<>"']/g, character => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[character]));
  const icon = name => `<svg class="icon" aria-hidden="true"><use href="#i-${name}"/></svg>`;
  const iconButton = (name, label, attributes = '') => `<button type="button" class="icon-button" aria-label="${label}" ${attributes}>${icon(name)}</button>`;
  const logo = () => '<img class="dua-logo logo-light" src="assets/dua-light.svg" alt="Dua AI"><img class="dua-logo logo-dark" src="assets/dua-dark.svg" alt="Dua AI">';
  const seedMessages = () => [
    { user: true, text: 'Help me plan a focused morning.' },
    { user: false, text: 'Start with one thing that matters. Give it your first uninterrupted hour, then leave a little room to reset.\n\nI’ve put a simple plan in Canvas so you can make it your own.' },
  ];
  const seedHistory = () => [
    { id: 'morning', title: 'A more focused morning', pinned: true, messages: seedMessages() },
    { id: 'ideas', title: 'Ideas for my next project', pinned: false, messages: [{ user: true, text: 'Let’s brainstorm a small project.' }, { user: false, text: 'Try a reading journal, a weekly planner or a simple recipe collection. Pick something you would enjoy using every day.' }] },
    { id: 'react', title: 'Learning React, one step at a time', pinned: false, messages: [{ user: true, text: 'Where should I start with React?' }, { user: false, text: 'Start with a small component, add a button and use state to change what it shows. Build one working interaction before adding the next.' }] },
  ];
  const state = {
    route: byId.has(location.hash.slice(1)) ? location.hash.slice(1) : 'home', stack: [],
    theme: ['system', 'light', 'dark'].includes(readSaved('dua-preview-theme')) ? readSaved('dua-preview-theme') : 'light',
    name: (readSaved('dua-preview-name') || '').slice(0, 48), model: 'Gemini', draft: '',
    messages: seedMessages(), history: seedHistory(), currentChat: 'morning', search: '', pinned: false,
    projects: ['A calmer morning', 'My next app'], project: '', files: [], attachments: [],
    document: '01  Make room for your best work\n\nChoose one meaningful task. Silence distractions and give yourself a full hour to make progress.\n\n02  Take a real break\n\nStep away from the screen. Stretch, get some water and come back with a fresh perspective.\n\n03  Leave a little breathing room\n\nKeep the last half hour open for whatever the morning brings.',
    notifications: { all: true, replies: true, tasks: true, sound: false }, voicePlaying: false, muted: false,
  };
  const content = document.getElementById('phone-content');
  const modal = document.getElementById('modal');
  const media = matchMedia('(prefers-color-scheme: dark)');
  let toastTimer;
  function toast(message) {
    const element = document.getElementById('toast'); element.textContent = message;
    element.classList.add('visible'); clearTimeout(toastTimer);
    toastTimer = setTimeout(() => element.classList.remove('visible'), 2600);
  }
  function theme() { return state.theme === 'system' ? (media.matches ? 'dark' : 'light') : state.theme; }
  function applyTheme() {
    document.documentElement.dataset.theme = theme();
    document.querySelector('meta[name="theme-color"]').content = theme() === 'dark' ? '#151515' : '#f9f9f9';
    document.querySelectorAll('.toolbar [data-theme-mode]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.themeMode === state.theme)));
    document.getElementById('theme-caption').textContent = theme() === 'dark' ? 'Dark' : 'Light';
    document.getElementById('token-theme').textContent = state.theme === 'system' ? 'Match system' : theme() === 'dark' ? 'Dark' : 'Light';
  }
  media.addEventListener('change', () => { if (state.theme === 'system') applyTheme(); });
  function setTheme(mode, restoreFocus = false) {
    if (!['system', 'light', 'dark'].includes(mode)) return;
    state.theme = mode; save('dua-preview-theme', mode); applyTheme();
    if (state.route === 'appearance') {
      renderPhone();
      if (restoreFocus) content.querySelector(`[data-theme-mode="${mode}"]`).focus();
    }
  }
  function go(route, remember = true) {
    if (!byId.has(route)) return;
    if (remember && route !== state.route) state.stack.push(state.route);
    state.route = route; render();
    content.querySelector('#screen-title')?.focus({ preventScroll: true });
  }
  function back() { go(state.stack.pop() || 'home', false); }
  function newChat() { state.messages = []; state.draft = ''; state.attachments = []; state.currentChat = null; state.stack = []; go('home', false); }
  function appbar() {
    return `<header class="appbar">${iconButton('menu', 'Open chat history', 'data-go="history"')}<span class="brand">Dua AI</span><button type="button" class="model" data-go="models" aria-label="Choose model, ${state.model}"><span>${state.model}</span><svg class="icon chevron-down" aria-hidden="true"><use href="#i-chev"/></svg></button><span class="spacer"></span>${state.route === 'chat' ? iconButton('plus', 'New chat', 'data-action="new-chat"') : `<button type="button" class="icon-button" data-go="profile" aria-label="Open profile"><span class="avatar">${escape(state.name.charAt(0).toUpperCase() || 'D')}</span></button>`}</header>`;
  }
  const detailbar = (title, action = '') => `<header class="detailbar">${iconButton('back', 'Back', 'data-action="back"')}<h2 id="screen-title" tabindex="-1">${title}</h2>${action}</header>`;
  const row = (name, description, glyph, attributes = '') => `<button type="button" class="row" ${attributes}>${icon(glyph)}<span class="row-label"><b>${escape(name)}</b>${description ? `<small>${escape(description)}</small>` : ''}</span>${icon('chev')}</button>`;
  function composerAction() {
    return state.draft.trim()
      ? `<button type="submit" class="icon-button send" aria-label="Send message">${icon('send')}</button>`
      : '<button type="button" class="icon-button send" aria-label="Voice mode" data-go="voice"><span class="wave" aria-hidden="true"><i></i><i></i><i></i></span></button>';
  }
  function composer() {
    const task = state.route === 'task';
    return `<div class="composer-wrap">${state.attachments.length ? `<div class="attachment-indicator">${state.attachments.length} file${state.attachments.length === 1 ? '' : 's'} selected · preview only</div>` : ''}<form class="composer" id="composer-form">${iconButton('plus', 'Add files', 'data-action="attach"')}<textarea id="message-input" rows="1" aria-label="Message" maxlength="4000" placeholder="${task ? 'Describe…' : 'Ask Dua AI…'}">${escape(state.draft)}</textarea>${iconButton('mic', 'Dictate', 'data-action="dictate"')}<span id="composer-action">${composerAction()}</span></form></div>`;
  }
  function home() {
    const task = state.route === 'task';
    const greeting = task ? 'What can I tackle for you?' : `Hi${state.name ? ' ' + state.name.split(/\s+/)[0] : ''}, what’s on your mind?`;
    const starters = [['attach', 'Nudge me to back up my photos'], ['filetext', 'Learn React by building an app'], ['globe', 'Compare views on a topic I follow']];
    return appbar() + `<div class="home-main"><section class="hero">${logo()}<h2 id="screen-title" tabindex="-1">${escape(greeting)}</h2></section>${task ? `<div class="starters">${starters.map(([glyph, text]) => `<button type="button" class="starter" data-starter="${escape(text)}">${icon(glyph)}<span>${text}</span></button>`).join('')}</div>` : ''}</div><div class="mode-selector" role="group" aria-label="Home mode"><button type="button" data-mode="home" aria-pressed="${!task}">Chat</button><button type="button" data-mode="task" aria-pressed="${task}">Task</button></div>` + composer();
  }
  function chat() {
    const messages = state.messages.map((message, index) => message.user ? `<div class="message user">${escape(message.text)}</div>` : `<div class="message assistant"><span class="sample-label">Preview response</span>${escape(message.text)}<div class="message-actions"><button type="button" class="text-button" data-copy="${index}">Copy</button><button type="button" class="text-button" data-action="retry">Retry</button><button type="button" class="text-button" data-go="canvas">Canvas</button></div></div>`).join('');
    return appbar() + `<div class="scroll message-list" role="log" aria-label="Sample conversation"><h2 id="screen-title" class="sr-only" tabindex="-1">Conversation</h2>${messages || '<div class="empty"><h3>A fresh conversation</h3><p>What would you like to explore?</p></div>'}${state.messages.length ? `<button type="button" class="artifact" data-go="canvas">${icon('filetext')}<span><b>Your morning, a little clearer</b><small>Canvas · editable document</small></span>${icon('chev')}</button>` : ''}</div>` + composer();
  }
  function historyItems() {
    const chats = state.history.filter(chat => (!state.pinned || chat.pinned) && chat.title.toLowerCase().includes(state.search.toLowerCase()));
    return chats.length ? chats.map(chat => `<button type="button" class="row" data-chat="${chat.id}">${icon(chat.pinned ? 'star' : 'chats')}<span class="row-label"><b>${escape(chat.title)}</b><small>${chat.pinned ? 'Pinned · ' : ''}${chat.messages.length} messages</small></span>${icon('chev')}</button>`).join('') : `<div class="empty"><h3>${state.search ? 'No matching chats' : state.pinned ? 'No pinned chats' : 'Your next idea starts here'}</h3><p>${state.search ? 'Try another search or clear the current one.' : 'Start a conversation and it will appear here.'}</p>${state.search || state.pinned ? '<button type="button" class="text-button" data-action="clear-search">Show all chats</button>' : '<button type="button" class="text-button" data-action="new-chat">Start a chat</button>'}</div>`;
  }
  function history() {
    return detailbar('Your chats', iconButton('plus', 'New chat', 'data-action="new-chat"')) + `<div class="scroll"><label class="search">${icon('search')}<input id="history-search" type="search" aria-label="Search chats" placeholder="Search your chats" value="${escape(state.search)}"></label><div class="chips" role="group" aria-label="History filter"><button type="button" data-filter="all" aria-pressed="${!state.pinned}">All chats</button><button type="button" data-filter="pinned" aria-pressed="${state.pinned}">Pinned</button></div><div class="group">${row('Projects', 'A place for ongoing work', 'file', 'data-go="projects"')}${row('Library', 'Your documents and files', 'filetext', 'data-go="library"')}</div><p class="section-label">RECENT CONVERSATIONS</p><div id="history-list">${historyItems()}</div></div><div class="history-footer"><span class="avatar">${escape(state.name.charAt(0).toUpperCase() || 'D')}</span><span>${escape(state.name || 'Personal workspace')}</span>${iconButton('settings', 'Open settings', 'data-go="settings"')}</div>`;
  }
  function models() {
    return detailbar('Choose a model') + `<div class="scroll"><p class="page-description">Pick who responds in this chat.</p><div class="group" role="radiogroup" aria-label="Model">${['Dua', 'Gemini', 'ChatGPT', 'Claude', 'Grok', 'Kimi'].map(name => {
      const available = ['Dua', 'Gemini'].includes(name);
      return `<button type="button" class="row ${state.model === name ? 'selected' : ''}" role="radio" tabindex="${state.model === name ? 0 : -1}" aria-checked="${state.model === name}" data-model="${name}" ${available ? '' : 'disabled'}><span class="model-letter">${name.charAt(0)}</span><span class="row-label"><b>${name === 'Dua' ? 'Dua · Auto' : name}</b><small>${available ? 'Available in this preview' : 'Setup required'}</small></span>${state.model === name ? icon('check') : ''}</button>`;
    }).join('')}</div><p class="page-description" style="margin-top:20px">Model selection changes the preview label. This preview does not connect to a provider.</p><button type="button" class="primary-button wide" data-action="back">Done</button></div>`;
  }
  function projects() {
    return detailbar('Projects', iconButton('plus', 'Create project', 'data-action="new-project"')) + `<div class="scroll"><p class="page-description">A little context for the things you’re working on.</p><div class="project-grid">${state.projects.map((name, index) => `<button type="button" class="project-card" data-project="${index}">${icon('file')}<b>${escape(name)}</b><small>Personal workspace</small></button>`).join('')}</div><p class="page-description" style="margin-top:24px">Your ideas, notes and conversations, kept together.</p></div>`;
  }
  const fileSize = size => size < 1024 ? `${size} B` : size < 1048576 ? `${Math.ceil(size / 1024)} KB` : `${(size / 1048576).toFixed(1)} MB`;
  function library() {
    return detailbar('Library', iconButton('plus', 'Add files to library', 'data-action="attach"')) + `<div class="scroll"><p class="page-description">Documents and files for your workspace.</p><div class="group">${row('Your morning, a little clearer', 'Canvas · editable document', 'filetext', 'data-go="canvas"')}${state.files.map((file, index) => row(file.name, `${fileSize(file.size)} · selected on this device`, 'file', `data-file="${index}"`)).join('')}</div><p class="page-description" style="margin-top:24px">Choose a file to see how it appears here. Files stay on your device and are not uploaded.</p><button type="button" class="primary-button wide" data-action="attach">${icon('plus')}Add files</button></div>`;
  }
  function canvas() {
    return detailbar('Canvas', '<button type="button" class="text-button" data-action="save-canvas">Save</button>') + `<div class="scroll"><div class="document"><h3>${escape(state.project || 'Your morning, a little clearer')}</h3><label class="sr-only" for="canvas-editor">Document text</label><textarea id="canvas-editor" maxlength="12000">${escape(state.document)}</textarea></div></div>`;
  }
  function voice() {
    return detailbar('Dua Voice', '<span class="pill">Preview</span>') + `<div class="voice-main"><div class="voice-orb ${state.voicePlaying ? 'playing' : ''}" aria-hidden="true">${icon('mic')}</div><h3>${state.voicePlaying ? 'A little space to talk' : 'Ready when you are'}</h3><p>${state.voicePlaying ? 'The orb is playing a visual preview. No audio is recorded.' : 'Try the visual voice experience, at your own pace.'}</p><button type="button" class="primary-button" data-action="voice-play">${state.voicePlaying ? 'Pause preview' : 'Start preview'}</button></div><div class="voice-controls">${iconButton('mic', state.muted ? 'Unmute preview microphone' : 'Mute preview microphone', `data-action="voice-mute" aria-pressed="${state.muted}"`)}${iconButton('back', 'Close voice preview', 'data-action="back"')}</div>`;
  }
  function profile() {
    return detailbar('Your profile') + `<div class="scroll"><div class="profile-hero"><span class="avatar">${escape(state.name.charAt(0).toUpperCase() || 'D')}</span><h3>${escape(state.name || 'Make it yours')}</h3><p>Personal workspace</p></div><form id="profile-form"><label class="field-label" for="display-name">Display name</label><input class="field" id="display-name" name="name" autocomplete="nickname" maxlength="48" value="${escape(state.name)}" placeholder="Your name"><button type="submit" class="primary-button wide">Save display name</button></form><p class="section-label">YOUR WORKSPACE</p><div class="group">${row('Settings', 'Make Dua feel right for you', 'settings', 'data-go="settings"')}${row('Projects', 'Continue your work', 'file', 'data-go="projects"')}</div></div>`;
  }
  function settings() {
    return detailbar('Settings') + `<div class="scroll"><div class="group">${row(state.name || 'Personal workspace', 'Your profile and display name', 'profile', 'data-go="profile"')}</div><p class="section-label">LOOK AND FEEL</p><div class="group">${row('Appearance', state.theme === 'system' ? 'Match system' : state.theme === 'dark' ? 'Dark' : 'Light', 'sun', 'data-go="appearance"')}${row('Voice', 'A space to talk', 'mic', 'data-go="voice"')}</div><p class="section-label">GENERAL AND DATA</p><div class="group">${row('Notifications', state.notifications.all ? 'On' : 'Off', 'bell', 'data-go="notifications"')}${row('Storage', `${state.history.length} chats · ${state.files.length + 1} documents`, 'db', 'data-go="storage"')}${row('History and data', 'Explore your conversations', 'chats', 'data-go="history"')}</div><p class="section-label">ABOUT</p><div class="group">${row('About Dua AI', 'Your personal assistant', 'info', 'data-action="about"')}</div></div>`;
  }
  function appearance() {
    const options = [['system', 'Match system', 'Follow your device appearance'], ['light', 'Light', 'A clear, quiet canvas'], ['dark', 'Dark', 'A little easier on the eyes']];
    return detailbar('Appearance') + `<div class="scroll"><p class="page-description">The same Dua, in the light that suits you.</p><div role="radiogroup" aria-label="Appearance">${options.map(([mode, name, description]) => `<button type="button" class="theme-option" role="radio" tabindex="${state.theme === mode ? 0 : -1}" aria-checked="${state.theme === mode}" data-theme-mode="${mode}"><span class="theme-mini ${mode}"><i></i><i></i><i></i></span><span><b>${name}</b><small>${description}</small></span>${state.theme === mode ? icon('check') : ''}</button>`).join('')}</div><div class="group">${row('Inter', 'Dua’s familiar typeface', 'type', 'data-action="font-info"')}</div></div>`;
  }
  function notifications() {
    const switches = [['all', 'Allow notifications', 'Your master notification preference'], ['replies', 'Assistant replies', 'When a response is ready'], ['tasks', 'Task updates', 'Progress on things you asked for'], ['sound', 'Notification sounds', 'A gentle cue for new updates']];
    return detailbar('Notifications') + `<div class="scroll"><p class="page-description">Choose what you’d like to hear about.</p><div class="group">${switches.map(([key, name, description]) => `<div class="row"><span class="row-label"><b>${name}</b><small>${description}</small></span><button type="button" class="switch" role="switch" aria-label="${name}" aria-checked="${state.notifications[key]}" data-switch="${key}" ${key !== 'all' && !state.notifications.all ? 'disabled' : ''}></button></div>`).join('')}</div><p class="page-description" style="margin-top:24px">These preview preferences do not change your phone’s notification permissions.</p></div>`;
  }
  function storage() {
    return detailbar('Storage') + `<div class="scroll"><div class="storage-summary"><h3>${state.history.length} chats</h3><p>${state.files.length + 1} documents in this preview</p></div><p class="section-label">YOUR CONTENT</p><div class="group">${row('Documents', `${state.files.length + 1} · Canvas and selected files`, 'filetext', 'data-go="library"')}${row('Chat history', `${state.history.length} sample conversations`, 'chats', 'data-go="history"')}</div><p class="section-label">MANAGE DATA</p><div class="group">${row('Clear preview chats', 'Only removes sample conversations in this tab', 'trash', 'data-action="clear-history"')}</div><p class="page-description" style="margin-top:24px">Sample chats, documents and switches live in this tab. Theme and display name are saved in your browser.</p></div>`;
  }
  const renderers = { home, task: home, chat, history, models, projects, library, canvas, voice, profile, settings, appearance, notifications, storage };
  function renderPhone() { content.innerHTML = renderers[state.route](); }
  function render() {
    renderPhone(); applyTheme();
    const { screen, index } = byId.get(state.route);
    document.getElementById('screen-select').value = state.route;
    document.querySelectorAll('.rail [data-route]').forEach(button => {
      if (button.dataset.route === state.route) button.setAttribute('aria-current', 'page'); else button.removeAttribute('aria-current');
    });
    document.getElementById('screen-number').textContent = String(index + 1).padStart(2, '0');
    document.getElementById('screen-caption').textContent = screen[1];
    document.getElementById('note-title').textContent = screen[1];
    document.getElementById('note-description').textContent = screen[2];
    document.getElementById('note-actions').innerHTML = screen[3].map(text => `<li>${escape(text)}</li>`).join('');
    document.body.dataset.screen = state.route;
    try { window.history.replaceState(null, '', `#${state.route}`); } catch { /* Also works when opened as a local file. */ }
  }
  function openModal(title, body, actions = '<button type="button" class="primary-button" data-action="close-modal">Done</button>') {
    document.getElementById('modal-content').innerHTML = `<h2 id="modal-title">${title}</h2>${body}<div class="dialog-actions">${actions}</div>`;
    modal.showModal();
  }
  function send() {
    const text = state.draft.trim(); if (!text) return;
    if (state.route === 'home' || state.route === 'task') { state.messages = []; state.currentChat = null; }
    state.messages.push({ user: true, text });
    state.messages.push({ user: false, text: 'This is a preview reply, so you can try the conversation layout. In the connected Dua app, your selected model would respond here.\n\nYou can keep chatting, copy this reply or open Canvas to edit your plan.' });
    state.draft = ''; state.attachments = [];
    if (!state.currentChat) { state.currentChat = `chat-${Date.now()}`; state.history.unshift({ id: state.currentChat, title: text.slice(0, 60), pinned: false, messages: [] }); }
    const saved = state.history.find(chat => chat.id === state.currentChat);
    if (saved) saved.messages = state.messages.map(message => ({ ...message }));
    if (state.route !== 'chat') go('chat'); else renderPhone();
    const list = content.querySelector('.message-list'); list.scrollTop = list.scrollHeight;
    content.querySelector('#message-input').focus();
  }
  document.getElementById('screen-navigation').innerHTML = screens.map(([id, name], index) => `${index === 0 ? '<div class="nav-group">Assistant</div>' : index === 5 ? '<div class="nav-group">Workspace</div>' : index === 9 ? '<div class="nav-group">Preferences</div>' : ''}<button type="button" data-route="${id}"><span>${String(index + 1).padStart(2, '0')}</span>${name}</button>`).join('');
  document.getElementById('screen-select').innerHTML = screens.map(([id, name], index) => `<option value="${id}">${String(index + 1).padStart(2, '0')} · ${name}</option>`).join('');
  document.getElementById('screen-select').addEventListener('change', event => { state.stack = []; go(event.target.value, false); });
  document.getElementById('reset-preview').addEventListener('click', () => openModal('Reset this preview?', '<p>Restore the sample chats, projects and document. Clear the saved theme and display name. Your actual Dua account is unaffected.</p>', '<button type="button" class="text-button" data-action="close-modal">Cancel</button><button type="button" class="primary-button" data-action="confirm-reset">Reset preview</button>'));
  document.addEventListener('input', event => {
    if (event.target.id === 'message-input') {
      state.draft = event.target.value;
      document.getElementById('composer-action').innerHTML = composerAction();
    }
    if (event.target.id === 'canvas-editor') state.document = event.target.value;
    if (event.target.id === 'history-search') { state.search = event.target.value; document.getElementById('history-list').innerHTML = historyItems(); }
  });
  document.addEventListener('keydown', event => {
    if (event.target.id === 'message-input' && event.key === 'Enter' && !event.shiftKey && !event.isComposing) { event.preventDefault(); send(); }
    if (event.target.matches('.theme-option') && ['ArrowDown', 'ArrowUp', 'ArrowLeft', 'ArrowRight'].includes(event.key)) {
      event.preventDefault(); const modes = ['system', 'light', 'dark'];
      const next = (modes.indexOf(state.theme) + (['ArrowDown', 'ArrowRight'].includes(event.key) ? 1 : 2)) % 3;
      setTheme(modes[next], true);
    }
    if (event.target.matches('[data-model]') && ['ArrowDown', 'ArrowUp', 'ArrowLeft', 'ArrowRight'].includes(event.key)) {
      event.preventDefault(); state.model = state.model === 'Dua' ? 'Gemini' : 'Dua'; renderPhone();
      content.querySelector(`[data-model="${state.model}"]`).focus();
    }
  });
  document.addEventListener('submit', event => {
    event.preventDefault();
    if (event.target.id === 'composer-form') send();
    if (event.target.id === 'profile-form') { state.name = document.getElementById('display-name').value.trim().slice(0, 48); save('dua-preview-name', state.name); renderPhone(); toast('Display name saved.'); }
    if (event.target.id === 'project-form') {
      const name = document.getElementById('project-name').value.trim(); if (!name) return;
      state.projects.push(name.slice(0, 80)); modal.close(); renderPhone(); toast('Project created in this preview.');
    }
  });
  document.getElementById('file-picker').addEventListener('change', event => {
    const files = Array.from(event.target.files).map(file => ({ name: file.name, size: file.size }));
    state.files.push(...files); if (['home', 'task', 'chat'].includes(state.route)) state.attachments.push(...files);
    event.target.value = ''; renderPhone(); if (files.length) toast(`${files.length} file${files.length === 1 ? '' : 's'} selected. Nothing was uploaded.`);
  });
  document.addEventListener('click', async event => {
    const button = event.target.closest('button'); if (!button || button.disabled) return;
    const data = button.dataset;
    if (data.route) { state.stack = []; go(data.route, false); }
    else if (data.go) go(data.go);
    else if (data.mode) { go(data.mode, false); }
    else if (data.themeMode) setTheme(data.themeMode, button.classList.contains('theme-option'));
    else if (data.starter) { state.draft = data.starter; renderPhone(); content.querySelector('#message-input').focus(); }
    else if (data.filter) { state.pinned = data.filter === 'pinned'; renderPhone(); }
    else if (data.chat) { const chat = state.history.find(item => item.id === data.chat); if (chat) { state.currentChat = chat.id; state.messages = chat.messages.map(message => ({ ...message })); go('chat'); } }
    else if (data.model) { state.model = data.model; renderPhone(); content.querySelector(`[data-model="${state.model}"]`).focus(); }
    else if (data.project !== undefined) { state.project = state.projects[Number(data.project)]; go('canvas'); }
    else if (data.file !== undefined) { const file = state.files[Number(data.file)]; openModal(escape(file.name), `<p>${fileSize(file.size)} · selected on your device. This preview stores the file name and size in memory, without reading or uploading its contents.</p>`); }
    else if (data.copy !== undefined) {
      try { await navigator.clipboard.writeText(state.messages[Number(data.copy)].text); toast('Reply copied.'); } catch { toast('Copy is unavailable here. Select the reply text to copy it.'); }
    }
    else if (data.switch) {
      state.notifications[data.switch] = !state.notifications[data.switch]; renderPhone();
      content.querySelector(`[data-switch="${data.switch}"]`).focus();
    }
    else if (data.action === 'back') back();
    else if (data.action === 'new-chat') newChat();
    else if (data.action === 'attach') document.getElementById('file-picker').click();
    else if (data.action === 'dictate') openModal('Voice input', '<p>Dictation is available in the connected Dua app. This design preview does not listen to your microphone.</p>', '<button type="button" class="text-button" data-action="close-modal">Close</button><button type="button" class="primary-button" data-action="try-voice">Try voice preview</button>');
    else if (data.action === 'try-voice') { modal.close(); go('voice'); }
    else if (data.action === 'close-modal') modal.close();
    else if (data.action === 'clear-search') { state.search = ''; state.pinned = false; renderPhone(); content.querySelector('#history-search').focus(); }
    else if (data.action === 'retry') toast('This is a sample reply. Live regeneration is available in the connected app.');
    else if (data.action === 'save-canvas') toast('Document kept in this preview tab.');
    else if (data.action === 'voice-play') { state.voicePlaying = !state.voicePlaying; renderPhone(); content.querySelector('[data-action="voice-play"]').focus(); }
    else if (data.action === 'voice-mute') { state.muted = !state.muted; renderPhone(); content.querySelector('[data-action="voice-mute"]').focus(); }
    else if (data.action === 'new-project') openModal('A new place for your ideas', '<form id="project-form"><label class="field-label" for="project-name">Project name</label><input class="field" id="project-name" required maxlength="80" autofocus placeholder="What are you working on?"><div class="dialog-actions"><button type="button" class="text-button" data-action="close-modal">Cancel</button><button type="submit" class="primary-button">Create project</button></div></form>', '');
    else if (data.action === 'clear-history') openModal('Clear preview chats?', '<p>This removes the sample conversations from this tab. Your real Dua history is unaffected.</p>', '<button type="button" class="text-button" data-action="close-modal">Cancel</button><button type="button" class="primary-button" data-action="confirm-clear">Clear preview chats</button>');
    else if (data.action === 'confirm-clear') { state.history = []; state.messages = []; state.currentChat = null; modal.close(); renderPhone(); toast('Preview chats cleared.'); }
    else if (data.action === 'confirm-reset') {
      try { localStorage.removeItem('dua-preview-theme'); localStorage.removeItem('dua-preview-name'); } catch { /* Tab reset still works without storage. */ }
      location.hash = ''; location.reload();
    }
    else if (data.action === 'about') openModal('Dua AI', `<div class="about-logo">${logo()}</div><p>Your personal assistant, by KRFbrothers.</p><p>This interactive design preview is separate from the connected app. It makes no API calls and does not sign in to your account.</p>`);
    else if (data.action === 'font-info') toast('Inter · licensed under the SIL Open Font License. See the footer for the full license.');
  });
  window.addEventListener('hashchange', () => { const route = location.hash.slice(1); if (byId.has(route) && route !== state.route) { state.stack = []; go(route, false); } });
  render();
})();
