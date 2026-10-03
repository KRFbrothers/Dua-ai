(function(){
 const page=document.querySelector('[data-page]'),status=document.querySelector('.local-status');if(!page)return;
 const notice=text=>status.textContent=text;
 function prefs(){try{return DuaPreferences.read();}catch(error){notice(error.message);throw error;}}
 function save(patch){try{DuaPreferences.update(patch);notice('Saved on this device.');render();return true;}catch(error){notice(error.message||'Could not save settings.');render();return false;}}
 function render(){let data;try{data=prefs();}catch{return;}
  for(const input of document.querySelectorAll('input[name=theme]'))input.checked=input.value===data.theme;
  for(const input of document.querySelectorAll('input[name=font]'))input.checked=input.value===data.font;
  const notifications=document.querySelector('#notification-enabled');if(notifications)notifications.checked=data.notifications&&globalThis.Notification?.permission==='granted';
  const memory=document.querySelector('#memory-enabled');if(memory)memory.checked=data.memoryEnabled;
  const list=document.querySelector('#memory-list');if(list){list.replaceChildren();data.memory.forEach((note,index)=>{const row=document.createElement('div');row.className='local-memory';const text=document.createElement('p');text.textContent=note;const remove=document.createElement('button');remove.className='local-button';remove.textContent='Delete';remove.setAttribute('aria-label','Delete memory: '+note);remove.onclick=()=>save({memory:prefs().memory.filter((_,i)=>i!==index)});const edit=document.createElement('button');edit.className='local-button';edit.textContent='Edit';edit.onclick=()=>{const next=prompt('Edit memory (up to 200 characters)',note);if(next===null)return;try{save({memory:prefs().memory.map((value,i)=>i===index?next.trim():value)});}catch{}};row.append(text,edit,remove);list.append(row);});}
 }
 for(const input of document.querySelectorAll('input[name=theme],input[name=font]'))input.addEventListener('change',()=>save({[input.name]:input.value}));
 document.querySelector('#memory-enabled')?.addEventListener('change',event=>save({memoryEnabled:event.target.checked}));
 document.querySelector('#memory-add')?.addEventListener('click',()=>{try{const input=document.querySelector('#memory-note'),note=input.value.trim();if(!note){notice('Write a memory first.');return;}if(save({memory:[...prefs().memory,note]}))input.value='';}catch{}});
 document.querySelector('#memory-clear')?.addEventListener('click',()=>{if(confirm('Delete every local memory?'))save({memory:[]});});
 document.querySelector('#notification-enabled')?.addEventListener('change',async event=>{
  if(!event.target.checked){save({notifications:false});return;}
  try{if(!globalThis.Notification||!navigator.serviceWorker)throw Error('Notifications unavailable in this browser.');const permission=await Notification.requestPermission();if(permission!=='granted')throw Error('Notification access was not granted. Change it in site settings.');await navigator.serviceWorker.register('reply-notifications-sw.js');save({notifications:true});}
  catch(error){notice(error.message);render();}
 });
 document.querySelector('#notification-test')?.addEventListener('click',async()=>{try{if(!prefs().notifications||globalThis.Notification?.permission!=='granted'){notice('Enable notification permission first.');return;}await DuaPreferences.notifyReply(null,true);notice('Test notification requested.');}catch(error){notice(error.message);}});
 async function permissionState(){const output=document.querySelector('#permission-state');if(!output)return;const states=[];for(const name of ['microphone','camera']){try{states.push(name+': '+(await navigator.permissions.query({name})).state);}catch{states.push(name+': state unavailable; requested when used');}}output.textContent=states.join(' · ');}
 for(const button of document.querySelectorAll('[data-permission]'))button.addEventListener('click',async()=>{
  button.disabled=true;let stream;try{if(!navigator.mediaDevices?.getUserMedia)throw Error('Device access unavailable. Use HTTPS and a supported browser.');stream=await navigator.mediaDevices.getUserMedia({[button.dataset.permission]:true});notice('Access granted. Test stream stopped.');}catch(error){notice(error.name==='NotAllowedError'?'Permission denied. Allow access in browser/site settings.':'Device unavailable or permission could not be requested.');}finally{stream?.getTracks().forEach(track=>track.stop());button.disabled=false;permissionState();}
 });
 const picker=document.querySelector('#share-chat'),preview=document.querySelector('#share-preview');let chats=[];
 function selected(){return chats.find(chat=>chat.id===picker.value);}
 function showShare(){const available=!!selected()?.messages.some(m=>m.state==='complete');for(const button of document.querySelectorAll('#share-send,#share-copy'))button.disabled=!available;try{preview.textContent=DuaPreferences.shareText(selected());}catch{preview.textContent='No saved chats to share.';}}
 if(picker){try{chats=DuaHistory.list();for(const chat of chats){const option=document.createElement('option');option.value=chat.id;option.textContent=chat.messages.find(m=>m.role==='user')?.text.slice(0,60)||'Draft';picker.append(option);}showShare();picker.addEventListener('change',showShare);}catch(error){notice(error.message);}}
 document.querySelector('#share-send')?.addEventListener('click',async()=>{try{const text=DuaPreferences.shareText(selected());if(!selected().messages.some(m=>m.state==='complete'))throw Error('This chat has no completed messages.');if(navigator.share)await navigator.share({title:'Dua conversation',text});else{await navigator.clipboard.writeText(text);notice('Copied for sharing.');}}catch(error){if(error.name!=='AbortError')notice(error.message||'Sharing unavailable. Try Copy.');}});
 document.querySelector('#share-copy')?.addEventListener('click',async()=>{try{await navigator.clipboard.writeText(DuaPreferences.shareText(selected()));notice('Copied conversation.');}catch(error){notice(error.message||'Copy unavailable.');}});
 if(new URLSearchParams(location.search).get('from')==='voice')document.querySelector('.local-header a').href='settings.html?from=voice';
 document.querySelector('#reset-settings')?.addEventListener('click',()=>{if(confirm('Reset appearance, notifications and all memories? Chats stay saved.'))try{DuaPreferences.reset();notice('Settings and memories reset.');}catch(error){notice(error.message);}});
 window.addEventListener('storage',event=>{if(event.key==='dua-preferences-v1')render();});render();permissionState();
})();
