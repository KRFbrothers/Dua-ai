(function(){
 if(location.hash.includes('access_token='))history.replaceState(null,'',location.pathname+location.search);
 window.addEventListener('pagehide',()=>{for(const input of document.querySelectorAll('input[type=password]'))input.value='';});
 const status=document.querySelector('.local-status');const notice=text=>status.textContent=text;let busy=false,review=null,accountId=null;
 const buttons=()=>document.querySelectorAll('button,input');
 function render(){const state=DuaAccount.state;document.querySelector('#signed-out')?.toggleAttribute('hidden',!!state.user);document.querySelector('#signed-in')?.toggleAttribute('hidden',!state.user);document.querySelector('#account-setup')?.toggleAttribute('hidden',state.configured);const email=document.querySelector('#account-email');if(email)email.textContent=state.user?.email||'Not signed in';const name=document.querySelector('#display-name');if(name&&!busy)name.value=state.user?.displayName||'';for(const control of buttons())control.disabled=busy||!state.configured||!!document.querySelector('#cloud-upload')&&!state.user;}
 async function action(fn){if(busy)return;busy=true;render();try{await fn();}catch(error){notice(error.message||'Request failed. Local data was kept.');}finally{busy=false;render();}}
 const signin=document.querySelector('#signin-form');signin?.addEventListener('submit',event=>{event.preventDefault();action(async()=>{await DuaAccount.signin(signin.elements.email.value.trim(),signin.elements.password.value);signin.elements.password.value='';notice('Signed in. Local data was not uploaded.');});});
 document.querySelector('#signup')?.addEventListener('click',()=>action(async()=>{const value=await DuaAccount.signup(signin.elements.email.value.trim(),signin.elements.password.value,'');signin.elements.password.value='';notice(value.confirmationRequired?'Check your email, confirm your account, then sign in.':'Account created.');}));
 document.querySelector('#profile-form')?.addEventListener('submit',event=>{event.preventDefault();action(async()=>{await DuaAccount.profile(document.querySelector('#display-name').value.trim());notice('Profile updated.');});});
 document.querySelector('#logout')?.addEventListener('click',()=>action(async()=>{const result=await DuaAccount.logout();notice(result.message);document.querySelector('#logout').hidden=true;}));
 function preview(kind,snapshot,revision){review={kind,snapshot,revision,accountId};document.querySelector('#cloud-review').hidden=false;document.querySelector('#review-title').textContent={upload:'Upload local snapshot?',download:'Merge cloud snapshot?',delete:'Clear cloud snapshot?'}[kind];document.querySelector('#cloud-preview').textContent=snapshot?JSON.stringify({chats:snapshot.chats.map(chat=>({title:chat.messages.find(m=>m.role==='user')?.text.slice(0,80)||'Draft',draft:chat.draft,messages:chat.messages.length})),memory:snapshot.memory},null,2):'Cloud snapshot becomes empty. Local chats and memories stay saved.';document.querySelector('#cloud-version').textContent='Reviewed cloud revision '+revision;}
 document.querySelector('#cloud-upload')?.addEventListener('click',()=>action(async()=>{const remote=await DuaAccount.cloud();const snapshot={format:'dua-cloud-snapshot',version:1,chats:JSON.parse(DuaHistory.exportJson()).chats,memory:DuaPreferences.read().memory};if(new TextEncoder().encode(JSON.stringify(snapshot)).length>4*1024*1024)throw Error('Cloud snapshot limit is 4 MiB. Reduce saved data first.');preview('upload',snapshot,remote.revision);}));
 document.querySelector('#cloud-download')?.addEventListener('click',()=>action(async()=>{const remote=await DuaAccount.cloud();preview('download',remote.snapshot,remote.revision);}));
 document.querySelector('#cloud-delete')?.addEventListener('click',()=>action(async()=>{const remote=await DuaAccount.cloud();preview('delete',null,remote.revision);}));
 document.querySelector('#cloud-cancel')?.addEventListener('click',()=>{review=null;document.querySelector('#cloud-review').hidden=true;notice('Cancelled. Data was not changed.');});
 document.querySelector('#cloud-confirm')?.addEventListener('click',()=>action(async()=>{
  if(!review)return;const current=await DuaAccount.load();if(current.user?.id!==review.accountId)throw Error('Account changed. Review again.');
  if(review.kind==='upload'){const result=await DuaAccount.upload(review.revision,review.snapshot);notice('Uploaded cloud revision '+result.revision+'. Local data stays unchanged.');}
  else if(review.kind==='delete'){await DuaAccount.clearCloud(review.revision);notice('Cloud snapshot cleared. Local data stays unchanged.');}
  else {const old=DuaPreferences.read(),notes=Array.from(new Set([...old.memory,...review.snapshot.memory]));if(notes.length>10)throw Error('Merged memories exceed 10 notes. Remove some local notes and review again.');
   // Validate both before changing local data. Memory first; roll it back if history persistence fails.
   DuaPreferences.update({memory:notes});try{const result=DuaHistory.mergeCloud(review.snapshot.chats);notice(result.added+' chats merged; '+result.conflicts+' conflicts kept as copies.');}catch(error){try{DuaPreferences.update({memory:old.memory});}catch{throw Error('Chat import failed and memory rollback failed. Inspect local memories before retrying.');}throw error;}
  }
  review=null;document.querySelector('#cloud-review').hidden=true;
 }));
 action(async()=>{await DuaAccount.load();accountId=DuaAccount.state.user?.id||null;if(!DuaAccount.state.configured)notice('Supabase setup is required. Local data is unchanged.');});
 if(new URLSearchParams(location.search).get('from')==='voice')document.querySelector('.local-header a').href='settings.html?from=voice';
})();
