(function(root){
 const key='dua-preferences-v1',defaults={theme:'dark',font:'default',haptic:false,notifications:false,memoryEnabled:false,memory:[]};
 function validate(data){
  if(!data||!['dark','light','system'].includes(data.theme)||!['default','system','serif','monospace'].includes(data.font)||['haptic','notifications','memoryEnabled'].some(key=>typeof data[key]!=='boolean')||!Array.isArray(data.memory)||data.memory.length>10||data.memory.some(note=>typeof note!=='string'||!note.trim()||note.length>200))throw Error('Saved preferences could not be read.');
  return {...defaults,...data,memory:data.memory.slice()};
 }
 function read(){const raw=root.localStorage.getItem(key);return raw?validate(JSON.parse(raw)):{...defaults,memory:[]};}
 function apply(){let data;try{data=read();}catch{data={...defaults,memory:[]};}const dark=data.theme==='dark'||data.theme==='system'&&root.matchMedia?.('(prefers-color-scheme: dark)').matches;root.document.documentElement.dataset.theme=dark?'dark':'light';root.document.documentElement.dataset.font=data.font;root.document.documentElement.style.colorScheme=dark?'dark':'light';return data;}
 const api={read,reset(){const next={...defaults,memory:[]};root.localStorage.setItem(key,JSON.stringify(next));apply();return next;},update(patch){const next=validate({...read(),...patch});root.localStorage.setItem(key,JSON.stringify(next));apply();root.dispatchEvent?.(new Event('dua-settings'));return next;},memoryText(){const data=read();return data.memoryEnabled?data.memory.map(note=>'- '+note).join('\n'):'';},shareText(chat){if(!chat)throw Error('Choose a saved chat first.');return 'Dua conversation\n\n'+chat.messages.filter(m=>m.state==='complete').map(m=>(m.role==='user'?'You':'Dua')+': '+m.text).join('\n\n');},async notifyReply(chatId,test=false){
   const data=read();if(!data.notifications||root.Notification?.permission!=='granted'||!test&&!root.document.hidden)return;
   if(!root.navigator.serviceWorker)throw Error('Notifications unavailable in this browser.');
   const registration=await root.navigator.serviceWorker.register('reply-notifications-sw.js');
   await root.navigator.serviceWorker.ready;
   await registration.showNotification('Dua',{body:'Reply ready. Open Dua to read it.',tag:'dua-reply',data:{chatId:chatId||null}});
  }};
 root.DuaPreferences=api;
 root.document.addEventListener?.('click',event=>{const control=event.target.closest?.('button,a,summary');if(!control||control.disabled)return;try{if(read().haptic)root.navigator.vibrate?.(15);}catch{}});
 apply();root.matchMedia?.('(prefers-color-scheme: dark)').addEventListener?.('change',apply);root.addEventListener?.('storage',event=>{if(event.key===key)apply();});
})(globalThis);
