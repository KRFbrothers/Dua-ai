(function(root){
 'use strict';
 const LIMIT=4*1024*1024;
 const clone=value=>JSON.parse(JSON.stringify(value));
 const empty=()=>({format:'dua-cloud-snapshot',version:1,chats:[],memory:[]});
 const canonical=chat=>JSON.stringify({updatedAt:chat.updatedAt,model:chat.model,draft:chat.draft,messages:chat.messages});
 // Stable IDs make a remote conflict the same copy on every reconciliation.
 function fingerprint(text){let a=2166136261,b=5381;for(let i=0;i<text.length;i++){a=Math.imul(a^text.charCodeAt(i),16777619);b=Math.imul(b,33)^text.charCodeAt(i);}return (a>>>0).toString(16)+(b>>>0).toString(16);}
 function validate(value){
  if(!value||value.format!=='dua-cloud-snapshot'||value.version!==1||!Array.isArray(value.chats)||value.chats.length>100||!Array.isArray(value.memory)||value.memory.length>10)throw Error('Snapshot limits require manual review.');
  const ids=new Set();
  const chats=value.chats.map(chat=>{
   if(!chat||typeof chat.id!=='string'||!chat.id.trim()||chat.id.length>200||ids.has(chat.id)||!Number.isSafeInteger(chat.updatedAt)||chat.updatedAt<0||!['Dua','Gemini','ChatGPT','Claude','Grok','Kimi'].includes(chat.model)||typeof chat.draft!=='string'||chat.draft.length>8000||!Array.isArray(chat.messages)||chat.messages.length>500)throw Error('Snapshot limits require manual review.');
   ids.add(chat.id);
   const messages=chat.messages.map(message=>{if(!message||!['user','model'].includes(message.role)||typeof message.text!=='string'||message.text.length>1048576||!['complete','stopped'].includes(message.state))throw Error('Snapshot limits require manual review.');return {role:message.role,text:message.text,state:message.state};});
   return {id:chat.id,updatedAt:chat.updatedAt,model:chat.model,draft:chat.draft,messages};
  });
  const memory=value.memory.map(note=>{if(typeof note!=='string'||!note.trim()||note.length>200)throw Error('Snapshot limits require manual review.');return note;});
  const safe={format:'dua-cloud-snapshot',version:1,chats,memory};
  if(new TextEncoder().encode(JSON.stringify(safe)).length>LIMIT)throw Error('Snapshot exceeds 4 MiB. Review saved data.');
  return safe;
 }
 function summary(value){return {chats:value.chats.map(chat=>({id:chat.id,messages:chat.messages.length,signature:fingerprint(canonical(chat))})).sort((a,b)=>a.id.localeCompare(b.id)),memory:value.memory.slice().sort()};}
 function validSummary(value){return value===null||!!value&&Array.isArray(value.chats)&&value.chats.length<=100&&value.chats.every(chat=>chat&&typeof chat.id==='string'&&chat.id.length<=200&&Number.isSafeInteger(chat.messages)&&chat.messages>=0&&chat.messages<=500&&typeof chat.signature==='string'&&chat.signature.length<=32)&&Array.isArray(value.memory)&&value.memory.length<=10&&value.memory.every(note=>typeof note==='string'&&note.length<=200);}
 function wasDeleted(before,now){return !!before&&(before.chats.some(chat=>{const current=now.chats.find(item=>item.id===chat.id);return !current||current.messages.length<chat.messages;})||before.memory.some(note=>!now.memory.includes(note)));}
 function same(a,b){return JSON.stringify(summary(a))===JSON.stringify(summary(b));}
 function needsContentReview(local,remote,baseline){
  return remote.chats.some(chat=>{
   const here=local.chats.find(current=>current.id===chat.id);
   if(!here||canonical(here)===canonical(chat)||local.chats.some(current=>canonical(current)===canonical(chat)))return false;
   const old=baseline?.chats.find(current=>current.id===chat.id);
   return !old||old.signature!==fingerprint(canonical(here))&&old.signature!==fingerprint(canonical(chat));
  });
 }
 function merge(local,remote,baseline=null){
  local=validate(local);remote=validate(remote);const chats=local.chats.slice(),used=new Set(chats.map(chat=>chat.id));
  for(const chat of remote.chats){
   const signature=fingerprint(canonical(chat));
   // An unchanged cloud reply must not restore the old version after a local retry.
   if(baseline?.chats.some(old=>old.id===chat.id&&old.signature===signature))continue;
   if(chats.some(current=>canonical(current)===canonical(chat)))continue;
   const localChat=chats.find(current=>current.id===chat.id);let next=chat;
   if(localChat){const stem='auto-'+fingerprint(chat.id+'\n'+canonical(chat));let id=stem,n=0;while(used.has(id))id=stem+'-'+(++n);next={...chat,id};}
   used.add(next.id);chats.push(next);
  }
  const memory=Array.from(new Set([...local.memory,...remote.memory]));
  return validate({...local,chats,memory});
 }
 function create(options){
  const account=options.account,local=options.local;
  const timer=options.setTimer||setTimeout,cancelTimer=options.clearTimer||clearTimeout;
  const notify=options.onStatus||(()=>{}),persist=options.persist||(()=>{});
  let enabled=false,owner=null,baseline=null,revision=null,review=false,attempts=0,generation=0,scheduled=null,running=null,queued=false,destroyed=false;
  let state={phase:'disabled',message:'Automatic sync is off.',accountId:null,enabled:false};
  function publish(phase,message){state={phase,message,accountId:owner,enabled};notify({...state});}
  function remember(){try{persist({enabled,accountId:owner,baseline,revision,review});}catch{enabled=false;generation++;cancel();publish('error','Local sync settings could not be saved. Automatic sync is disabled.');throw Error('Local sync settings could not be saved.');}}
  function currentOwner(){return account.state?.user?.id||null;}
  function cancel(){if(scheduled!==null)cancelTimer(scheduled);scheduled=null;}
  function pause(message){review=true;queued=false;cancel();publish('review',message);remember();}
  function schedule(ms){if(!enabled||review||destroyed||attempts>=5)return;cancel();scheduled=timer(()=>{scheduled=null;void flush();},ms);}
  function assertOwner(ticket){
   if(ticket!==generation||!enabled||destroyed)return false;
   if(currentOwner()!==owner){pause('Account changed. Review local data before enabling sync for another account.');return false;}
   if(options.visible?.()===false){queued=false;cancel();publish('paused','Automatic sync pauses while this page is hidden.');return false;}
   if(options.online?.()===false){queued=false;cancel();publish('offline','Offline. Automatic sync will retry when online.');return false;}
   return true;
  }
  async function reconcile(ticket){
   if(!assertOwner(ticket))return;
   if(options.online?.()===false){publish('offline','Offline. Automatic sync will retry when online.');return;}
   if(options.visible?.()===false){publish('paused','Automatic sync pauses while this page is hidden.');return;}
   publish('syncing','Checking cloud changes…');
   let attemptedRevision=null;
   try{
    const session=await account.load();if(!assertOwner(ticket)||session.user?.id!==owner)return;
    const remoteResult=await account.cloud(owner);if(!assertOwner(ticket))return;
    if((remoteResult.ownerId||remoteResult.accountId)&&(remoteResult.ownerId||remoteResult.accountId)!==owner){pause('Cloud account changed. Review before syncing.');return;}
    const remote=validate(remoteResult.snapshot||empty());
    if(!Number.isSafeInteger(remoteResult.revision)||remoteResult.revision<0)throw Error('Invalid cloud revision.');
    let latest=validate(local.read());
    if(wasDeleted(baseline,latest)){pause('Local deletion detected. Use manual cloud review before resuming automatic sync.');return;}
    if(wasDeleted(baseline,remote)){pause('Cloud deletion detected. Use manual cloud review before resuming automatic sync.');return;}
    const contentReview=needsContentReview(latest,remote,baseline),merged=merge(latest,remote,baseline);
    if(!same(latest,merged)){
     if(!assertOwner(ticket))return;
     await local.merge(merged);
     if(!assertOwner(ticket))return;
     latest=validate(local.read());
     // A concurrent local edit may remain, but a concurrent deletion must stop the upload.
     if(wasDeleted(summary(merged),latest)){pause('Local data changed during download. Review before uploading.');return;}
    }
    if(contentReview){pause('This conversation changed on both devices. Both versions were kept locally; review before uploading again.');return;}
    if(!assertOwner(ticket))return;
    if(same(latest,remote)){baseline=summary(latest);revision=remoteResult.revision;attempts=0;remember();publish('synced','Cloud copy is up to date.');return;}
    // The server also verifies this owner against the authenticated account.
    const confirmed=await account.load();if(!assertOwner(ticket)||confirmed.user?.id!==owner)return;
    const toUpload=validate(local.read());
    if(wasDeleted(summary(latest),toUpload)){pause('Local deletion detected before upload. Review cloud data manually.');return;}
    attemptedRevision=remoteResult.revision;
    const result=await account.upload(attemptedRevision,toUpload,owner);
    if(!assertOwner(ticket))return;
    if(!Number.isSafeInteger(result.revision)||result.revision<=attemptedRevision)throw Error('Invalid cloud revision.');
    baseline=summary(toUpload);revision=result.revision;attempts=0;remember();publish('synced','Automatic cloud snapshot saved.');
    if(!same(toUpload,validate(local.read())))queued=true;
   }catch(error){
    if(!assertOwner(ticket))return;
    if(attemptedRevision!==null){
     // Account client need not expose HTTP status: a newer revision proves a CAS conflict.
     try{
      const latestRemote=await account.cloud(owner);if(!assertOwner(ticket))return;
      if((latestRemote.ownerId||latestRemote.accountId)&&(latestRemote.ownerId||latestRemote.accountId)!==owner){pause('Account changed. Review before syncing.');return;}
      if(latestRemote.revision!==attemptedRevision){
       const remote=validate(latestRemote.snapshot||empty()),here=validate(local.read());
       if(!wasDeleted(baseline,remote)&&!wasDeleted(baseline,here)){try{const merged=merge(here,remote,baseline);if(!same(here,merged))await local.merge(merged);}catch{pause('Another device changed the cloud copy. Local merge needs manual review; no upload will be retried.');return;}}
       if(!assertOwner(ticket))return;
       pause('Another device changed the cloud copy. Additive changes were kept locally; review before uploading again.');return;
      }
     }catch{if(!assertOwner(ticket))return;}
    }
    if(/Snapshot|4 MiB|limits|Invalid cloud revision/.test(error.message||'')){pause(error.message);return;}
    attempts++;if(attempts>=5){publish('error','Automatic sync stopped after five failed attempts. Retry explicitly when ready.');return;}
    publish('retrying','Sync unavailable. Local data is kept; retrying shortly.');schedule(Math.min(60000,1000*2**attempts));
   }
  }
  function flush(){
   cancel();if(!enabled||review||destroyed||attempts>=5)return Promise.resolve(state);
   if(running){queued=true;return running;}
   const ticket=generation;queued=false;
   running=reconcile(ticket).finally(()=>{running=null;if(queued&&enabled&&!review){queued=false;schedule(1200);}});
   return running;
  }
  return {
   get status(){return {...state};},get enabled(){return enabled;},
   async enable(saved){
    if(destroyed)throw Error('Sync controller was closed.');
    if(review&&!saved)throw Error('Review cloud data, then use Resume after review.');
    if(saved&&(saved.enabled!==true||typeof saved.accountId!=='string'||!saved.accountId.trim()||saved.accountId.length>200||typeof saved.review!=='boolean'||!(saved.revision===null||Number.isSafeInteger(saved.revision)&&saved.revision>=0)||!validSummary(saved.baseline??null)))throw Error('Saved sync baseline is invalid. Use manual cloud review.');
    const ticket=++generation;cancel();queued=false;
    const session=await account.load();if(ticket!==generation||destroyed)return {...state};if(!session.configured||!session.user?.id)throw Error('Sign in before enabling automatic sync.');
    if(saved?.accountId&&saved.accountId!==session.user.id){owner=saved.accountId;enabled=true;pause('Account changed. Automatic sync requires review for this account.');return state;}
    enabled=true;owner=session.user.id;baseline=saved?.baseline||null;revision=saved?.revision??null;review=saved?.review===true;attempts=0;remember();
    if(review){publish('review','Manual review is required before automatic sync resumes.');return state;}
    if(running)await running;if(ticket!==generation||destroyed)return {...state};await flush();return state;
   },
   disable(persistSetting=true){enabled=false;generation++;queued=false;cancel();if(persistSetting)remember();publish('disabled','Automatic sync is off.');},
   async resumeAfterReview(){review=false;baseline=null;revision=null;enabled=false;return this.enable();},
   changed(){if(!enabled||review||destroyed)return;if(!assertOwner(generation))return;schedule(1200);},
   pauseForLifecycle(){if(!enabled||review||destroyed)return;generation++;queued=false;cancel();publish('paused','Automatic sync pauses while this page is hidden.');},
   retry(){attempts=0;return flush();},flush,
   destroy(){destroyed=true;enabled=false;generation++;cancel();}
  };
 }
 function install(options={}){
  const account=options.account||root.DuaAccount,history=options.history||root.DuaHistory,preferences=options.preferences||root.DuaPreferences;
  if(!account||!history||!preferences)throw Error('Load account, history, and preferences before automatic sync.');
  const key='dua-automation-sync-v1';
  const api=create({account,online:()=>root.navigator?.onLine!==false,visible:()=>!root.document?.hidden,onStatus:options.onStatus,
   persist:value=>root.localStorage.setItem(key,JSON.stringify(value)),
   local:{read:()=>({format:'dua-cloud-snapshot',version:1,chats:JSON.parse(history.exportJson()).chats,memory:preferences.read().memory}),merge(snapshot){
    const previous=preferences.read();preferences.update({memory:snapshot.memory});try{history.mergeCloud(snapshot.chats);}catch(error){preferences.update({memory:previous.memory});throw error;}
   }}
  });
  const changed=()=>api.changed(),visibility=()=>{if(root.document.hidden)api.pauseForLifecycle();else void api.flush();};
  const storage=event=>{if(['dua-history-v2','dua-preferences-v1'].includes(event.key))changed();if(event.key===key){api.disable(false);options.onStatus?.({phase:'disabled',enabled:false,message:'Automatic sync setting changed in another tab. Reload to resume.'});}};
  root.addEventListener?.('dua-history',changed);root.addEventListener?.('dua-settings',changed);root.addEventListener?.('storage',storage);root.addEventListener?.('online',changed);root.document?.addEventListener?.('visibilitychange',visibility);
  root.addEventListener?.('pagehide',event=>{if(event.persisted)api.pauseForLifecycle();else api.destroy();});root.addEventListener?.('pageshow',event=>{if(event.persisted)changed();});
  api.restore=async()=>{let saved;try{saved=JSON.parse(root.localStorage.getItem(key)||'null');}catch{return;}if(saved?.enabled===true)await api.enable(saved);};
  return api;
 }
 root.DuaAutoSync={create,install,validate,merge,summary,wasDeleted,maxBytes:LIMIT};
})(globalThis);
