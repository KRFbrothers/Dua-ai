(function(){
 if(!globalThis.DuaHistory||!globalThis.DuaAccount)return;
 const status=document.querySelector('#automatic-sync-status'),toggle=document.querySelector('#automatic-sync-enabled');
 let working=false;
 const sync=DuaAutoSync.install({onStatus:state=>{if(status)status.textContent=state.message;if(toggle)toggle.checked=state.enabled;}});
 globalThis.DuaSyncController=sync;
 async function run(fn){if(working)return;working=true;try{await fn();}catch(error){if(status)status.textContent=error.message;if(toggle)toggle.checked=sync.enabled;}finally{working=false;}}
 toggle?.addEventListener('change',()=>{if(!toggle.checked){sync.disable();return;}run(()=>sync.enable());});
 document.querySelector('#automatic-sync-retry')?.addEventListener('click',()=>run(()=>sync.retry()));
 document.querySelector('#automatic-sync-resume')?.addEventListener('click',()=>run(()=>sync.resumeAfterReview()));
 window.addEventListener('dua-account',()=>sync.disable());
 run(()=>sync.restore());
 const enabledInput=document.querySelector('#suggestions-enabled'),suggestionStatus=document.querySelector('#suggestions-status'),list=document.querySelector('#suggestions-list');
 if(!enabledInput)return;
 const key='dua-memory-suggestions-enabled';
 function render(candidates){list.replaceChildren();for(const candidate of candidates){const row=document.createElement('div');row.className='local-card';const text=document.createElement('p');text.textContent=candidate;const accept=document.createElement('button');accept.className='local-button';accept.textContent='Save this memory';accept.addEventListener('click',()=>{try{DuaMemorySuggestions.confirm(candidate,DuaPreferences);row.remove();suggestionStatus.textContent='Memory saved. Enable Use local memory in replies to include it in prompts.';}catch(error){suggestionStatus.textContent=error.message;}});const dismiss=document.createElement('button');dismiss.className='local-button';dismiss.textContent='Dismiss';dismiss.addEventListener('click',()=>row.remove());row.append(text,accept,dismiss);list.append(row);}}
 try{enabledInput.checked=localStorage.getItem(key)==='1';}catch{}
 enabledInput.addEventListener('change',()=>{try{localStorage.setItem(key,enabledInput.checked?'1':'0');list.replaceChildren();suggestionStatus.textContent=enabledInput.checked?'Suggestions enabled. Scan recent prompts to review them.':'Suggestions off.';}catch{enabledInput.checked=false;suggestionStatus.textContent='Suggestion settings could not be saved.';}});
 document.querySelector('#suggestions-scan').addEventListener('click',()=>{try{if(!enabledInput.checked)throw Error('Enable suggestions first.');const notes=DuaPreferences.read().memory,candidates=[];for(const chat of DuaHistory.list().slice(0,10))for(const message of chat.messages.filter(m=>m.role==='user').slice(-10))for(const note of DuaMemorySuggestions.propose(message.text,notes))if(!candidates.includes(note)&&candidates.length<10)candidates.push(note);render(candidates);suggestionStatus.textContent=candidates.length?'Review each proposal before saving.':'No explicit remember statements found. Try: Remember that I prefer Hindi replies.';}catch(error){suggestionStatus.textContent=error.message;}});
})();
