import { validateCloudSnapshot } from './cloud-snapshot.mjs';
const cookieNames={access:'dua_access',refresh:'dua_refresh'};
function configuration(env){
 const url=String(env.url||'').replace(/\/$/,'');const key=env.key;
 if(!url&&!key)return null;
 let parsed;try{parsed=new URL(url);}catch{return null;}
 if(parsed.protocol!=='https:'||parsed.username||parsed.password||parsed.search||parsed.hash||parsed.pathname!=='/'||!key||key.startsWith('sb_secret_'))return null;
 // Do not permit a privileged legacy JWT key to bypass row-level security.
 try{if(JSON.parse(Buffer.from(key.split('.')[1]||'','base64url').toString()).role==='service_role')return null;}catch{}
 return {url,key};
}
function privilegedKey(value){
 if(typeof value!=='string'||value.length>10000)return null;
 if(/^sb_secret_[A-Za-z0-9_-]+$/.test(value))return value;
 try{if(/^[A-Za-z0-9._-]+$/.test(value)&&JSON.parse(Buffer.from(value.split('.')[1]||'','base64url').toString()).role==='service_role')return value;}catch{}
 return null;
}
export function accountCapabilities(env={}){
 const configured=!!configuration(env);
 return {configured,deletionConfigured:configured&&!!privilegedKey(env.serviceRoleKey)};
}
/** DUA_ALLOWED_EMAIL owner lock: '' (no lock), the normalized owner email, or null when the setting is malformed. */
export function ownerEmail(value){const owner=String(value||'').trim().toLowerCase();if(!owner)return '';return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(owner)?owner:null;}
function cookies(req){return Object.fromEntries((req.headers.get('cookie')||'').split(';').map(value=>value.trim().split('=' )).filter(pair=>pair.length===2));}
// Greeting/profile name: the Google account name (full_name, then name) wins; an older saved display_name is the fallback.
function metadataName(metadata){for(const key of ['full_name','name','display_name']){const value=metadata?.[key];if(typeof value==='string'&&value.trim())return value.trim().slice(0,80);}return '';}
function publicUser(user){if(!user||typeof user.id!=='string'||! /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(user.id))throw Error('Invalid account response');return {id:user.id,email:typeof user.email==='string'?user.email:'',displayName:metadataName(user.user_metadata)};}
function sessionCookies(session,clear=false){return [cookieNames.access,cookieNames.refresh].map((name,index)=>`${name}=${clear?'':index===0?session.access_token:session.refresh_token}; Path=/api; HttpOnly; Secure; SameSite=Strict; Max-Age=${clear?0:index===0?Math.min(86400,Math.max(60,session.expires_in||3600)):2592000}`);}
function validSession(data){if(!data||typeof data.access_token!=='string'||typeof data.refresh_token!=='string'||! /^[A-Za-z0-9._-]+$/.test(data.access_token)||! /^[A-Za-z0-9._-]+$/.test(data.refresh_token)||data.access_token.length>10000||data.refresh_token.length>10000)throw Error('Invalid session response');publicUser(data.user);return data;}
export async function handleAccount(req,env={}){
 const fetcher=env.fetcher||fetch;const config=configuration(env),url=new URL(req.url);const native=req.headers.get('x-dua-native')==='1'&&!req.headers.get('origin');let extraCookies=[];
 const json=(value,status=200)=>{const headers=new Headers({'Cache-Control':'no-store','Content-Type':'application/json'});extraCookies.forEach(value=>headers.append('Set-Cookie',value));return new Response(JSON.stringify(value),{status,headers});};
 if(req.headers.get('origin')&&req.headers.get('origin')!==url.origin)return json({error:'Request origin is not allowed.'},403);
 if(!config)return url.pathname==='/api/account/session'?json({configured:false,user:null}):json({error:'Supabase account setup is not configured.'},503);
 const headers=token=>({'apikey':config.key,'Authorization':'Bearer '+(token||config.key),'Content-Type':'application/json'});
 async function request(path,method,body,token){const response=await fetcher(config.url+path,{method,headers:headers(token),...(body===undefined?{}:{body:JSON.stringify(body)}),signal:AbortSignal.any([req.signal,AbortSignal.timeout(15000)]),redirect:'error'});let data=null;if(response.status!==204){const text=await response.text();if(Buffer.byteLength(text)>5*1024*1024)throw Error('Response too large');try{data=JSON.parse(text);}catch{}}return {response,data};}
 function deliver(session){validSession(session);if(!native){extraCookies=sessionCookies(session);return {configured:true,user:publicUser(session.user)};}return {configured:true,user:publicUser(session.user),session:{accessToken:session.access_token,refreshToken:session.refresh_token,expiresAt:Math.floor(Date.now()/1000)+(session.expires_in||3600)}};}
 // DUA_ALLOWED_EMAIL: only the owner may sign in, refresh, use cloud snapshots or delete. Session, features and logout stay open.
 const owner=ownerEmail(env.allowedEmail);const ownerAllowed=user=>!owner||user.email.toLowerCase()===owner;
 const ownerDenied=()=>{console.warn('[dua-access] Account request from non-allowed account rejected (403).');return json({error:'Only the configured personal account can use this Dua server.'},403);};
 const stored=cookies(req);let token=req.headers.get('authorization')?.match(/^Bearer ([A-Za-z0-9._-]+)$/)?.[1]||stored[cookieNames.access];
 async function authenticated(){
  if(token){const result=await request('/auth/v1/user','GET',undefined,token);if(result.response.ok)return publicUser(result.data);if(result.response.status!==401&&result.response.status!==403)throw Error('Account unavailable');}
  if(!native&&stored[cookieNames.refresh]){const result=await request('/auth/v1/token?grant_type=refresh_token','POST',{refresh_token:stored[cookieNames.refresh]});if(result.response.ok){const session=validSession(result.data);token=session.access_token;extraCookies=sessionCookies(session);return publicUser(session.user);}}
  return null;
 }
 try{
  if(url.pathname==='/api/account/session'&&req.method==='GET')return json({configured:true,user:await authenticated()});
  if(url.pathname==='/api/account/features'&&req.method==='GET')return json({passwordReset:false,accountDeletion:!!privilegedKey(env.serviceRoleKey)});
  if(!['POST','PUT','DELETE'].includes(req.method))return json({error:'Method not allowed.'},405);
  if(owner===null&&url.pathname!=='/api/account/logout')return json({error:'Allowed account email setup is invalid.'},503);
  if(!(req.headers.get('content-type')||'').startsWith('application/json'))return json({error:'Use JSON.'},415);
  const raw=await req.text();if(Buffer.byteLength(raw)> (url.pathname==='/api/cloud'?4*1024*1024+1024:16384))return json({error:'Request too large.'},413);
  let body;try{body=JSON.parse(raw||'{}');}catch{return json({error:'Invalid request.'},400);}if(!body||typeof body!=='object'||Array.isArray(body))return json({error:'Invalid request.'},400);
  if(url.pathname==='/api/account/google-token'&&req.method==='POST'){
   if(!native)return json({error:'Native Google sign-in is only available from the Android app.'},400);
   if(typeof body.idToken!=='string'||! /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(body.idToken)||body.idToken.length>8192||body.nonce!==undefined&&(typeof body.nonce!=='string'||! /^[A-Za-z0-9_-]{16,128}$/.test(body.nonce)))return json({error:'Google sign-in could not be verified. Try again.'},400);
   const result=await request('/auth/v1/token?grant_type=id_token','POST',{provider:'google',id_token:body.idToken,...(body.nonce?{nonce:body.nonce}:{})});
   if(!result.response.ok){const reason=String(result.data?.error_description||result.data?.msg||result.data?.message||result.data?.error||'').replace(/[^\x20-\x7e]/g,' ').slice(0,200);console.warn('[dua-auth] Google ID token rejected ('+result.response.status+')'+(reason?': '+reason:'.'));return json({error:'Google sign-in failed. Check that this Google account is allowed and try again.'},result.response.status===429?429:401);}
   const signedIn=validSession(result.data);
   if(!ownerAllowed(publicUser(signedIn.user))){try{await request('/auth/v1/logout?scope=local','POST',{},signedIn.access_token);}catch{}return ownerDenied();}
   console.info('[dua-auth] Google native sign-in succeeded.');return json(deliver(signedIn));
  }
  if(url.pathname==='/api/account/refresh'&&req.method==='POST'){
   const refresh=native?body.refreshToken:stored[cookieNames.refresh];if(typeof refresh!=='string'||refresh.length>10000||! /^[A-Za-z0-9._-]+$/.test(refresh))return json({error:'Sign in again.'},401);
   const result=await request('/auth/v1/token?grant_type=refresh_token','POST',{refresh_token:refresh});if(!result.response.ok)return json({error:'Session expired. Sign in again.'},401);if(!ownerAllowed(publicUser(validSession(result.data).user)))return ownerDenied();return json(deliver(result.data));
  }
  if(url.pathname==='/api/account/logout'&&req.method==='POST'){
   let revoked=false;try{if(token){const result=await request('/auth/v1/logout?scope=local','POST',{},token);revoked=result.response.ok;}}catch{}
   if(!native)extraCookies=sessionCookies(null,true);return json({user:null,revoked,message:revoked?'Signed out on this device.':'Signed out locally; server revocation could not be confirmed.'});
  }
  const user=await authenticated();if(!user)return json({error:'Sign in from Profile to continue.'},401);if(!ownerAllowed(user))return ownerDenied();
  if(url.pathname==='/api/account/delete'&&req.method==='DELETE'){
   if(body.expectedAccountId!==user.id)return json({error:'Account changed. Review the account shown in Profile before deleting.'},409);
   if(body.confirmation!=='DELETE MY ACCOUNT')return json({error:'Type DELETE MY ACCOUNT to confirm permanent account and cloud data deletion.'},400);const serviceKey=privilegedKey(env.serviceRoleKey);if(!serviceKey)return json({error:'Account deletion is not configured. The server needs its Supabase admin key.'},503);
   // Best effort: remove this user's stored files first (Storage objects are not removed with the auth user).
   try{const storage=await import('./storage.mjs');const files=storage.storageConfig({SUPABASE_URL:config.url,SUPABASE_ANON_KEY:config.key,DUA_FILES_BUCKET:env.filesBucket});if(files.enabled&&token){const list=await storage.listOwnerFiles(files,token,user.id.toLowerCase(),{fetcher,signal:req.signal});if(list.length)await storage.removeFiles(files,token,list.map(file=>file.path),{fetcher,signal:req.signal});}}catch{}
   // Only this verified user's id is used. Never accept an owner id from request JSON.
   const result=await fetcher(config.url+'/auth/v1/admin/users/'+encodeURIComponent(user.id),{method:'DELETE',headers:{apikey:serviceKey,Authorization:'Bearer '+serviceKey,'Content-Type':'application/json'},body:JSON.stringify({should_soft_delete:false}),signal:AbortSignal.any([req.signal,AbortSignal.timeout(15000)]),redirect:'error'});if(!result.ok)return json({error:'Account deletion failed. The account and local data may still be available. Check Profile before retrying.'},502);if(!native)extraCookies=sessionCookies(null,true);return json({user:null,deleted:true,message:'Account deleted. Its server snapshot and request usage were deleted. The phone cache remains until cleared.'});
  }
  if((url.pathname==='/api/cloud'&&(req.method==='PUT'||req.method==='DELETE'))||(url.pathname==='/api/cloud/chat'&&req.method==='DELETE')){
   const deleteChat=url.pathname==='/api/cloud/chat';
   if(deleteChat&&!owner)return json({error:'Owner account setup is required for chat deletion.'},503);
   if(deleteChat&&(body.expectedAccountId!==user.id||body.confirmation!=='DELETE CHAT'))return json({error:'Confirm chat deletion from the current signed-in account.'},409);
   if(deleteChat&&(typeof body.chatId!=='string'||!body.chatId.trim()||body.chatId.length>200))return json({error:'Choose a valid chat to delete.'},400);
   if(body.expectedAccountId!==undefined&&body.expectedAccountId!==user.id)return json({error:'Account changed. Review the cloud snapshot again.'},409);
   if(!Number.isSafeInteger(body.expectedRevision)||body.expectedRevision<0||body.expectedRevision>2147483647)return json({error:'Review the cloud version before changing it.'},400);
   let snapshot;try{if(!deleteChat)snapshot=validateCloudSnapshot(req.method==='DELETE'?{format:'dua-cloud-snapshot',version:1,chats:[],memory:[]}:body.snapshot);}catch(error){return json({error:error.message},400);}
   // Read through this verified account's RLS before the revision-checked RPC. The RPC still
   // rejects a concurrent change between this read and write; no other chats are overwritten.
   const stored=await request('/rest/v1/dua_cloud_snapshots?select=revision,payload,updated_at&owner_id=eq.'+encodeURIComponent(user.id),'GET',undefined,token);
   if(!stored.response.ok)return json({error:'Cloud history could not be checked. Retry without changing the phone cache.'},503);
   let remote;try{
    if(!Array.isArray(stored.data)||stored.data.length>1)throw Error();
    const row=stored.data[0];if(row&&(!Number.isSafeInteger(row.revision)||row.revision<1||row.revision>2147483647))throw Error();
    remote={revision:row?.revision||0,snapshot:row?validateCloudSnapshot(row.payload):{format:'dua-cloud-snapshot',version:1,chats:[],memory:[]}};
   }catch{return json({error:'Cloud history needs review. No server data was changed.'},503);}
   if(remote.revision!==body.expectedRevision)return json({error:'Cloud changed on another device. Refresh and review before retrying.',revision:remote.revision},409);
   const deleted=new Set(remote.snapshot.deletedChatIds||[]);
   if(deleteChat){
    if(deleted.has(body.chatId))return json({ownerId:user.id,chatId:body.chatId,revision:remote.revision,deletedChatIds:[...deleted].sort(),deleted:true});
    if(deleted.size>=10_000)return json({error:'Deletion history limit reached. No chat was changed.'},409);
    deleted.add(body.chatId);
    snapshot={...remote.snapshot,chats:remote.snapshot.chats.filter(chat=>chat.id!==body.chatId)};
   }else if(snapshot.chats.some(chat=>deleted.has(chat.id))){
    return json({error:'This upload contains a deleted chat. Download current cloud history before uploading.',revision:remote.revision},409);
   }
   // Legacy clients cannot express unpin: preserve the server flag when they omit it.
   if(!deleteChat&&req.method==='PUT'){
    const serverPins=new Map(remote.snapshot.chats.map(chat=>[chat.id,chat.pinned]));
    snapshot.chats=snapshot.chats.map(chat=>chat.pinned===undefined&&serverPins.get(chat.id)!==undefined?{...chat,pinned:serverPins.get(chat.id)}:chat);
   }
   // Only the server creates deletion IDs. Older clients may omit them, but cannot remove them.
   delete snapshot.deletedChatIds;
   if(deleted.size)snapshot.deletedChatIds=[...deleted].sort();
   try{snapshot=validateCloudSnapshot(snapshot);}catch(error){return json({error:error.message},400);}
   const result=await request('/rest/v1/rpc/dua_write_cloud_snapshot','POST',{expected_revision:body.expectedRevision,next_payload:snapshot},token);if(!result.response.ok)return json({error:'Cloud database setup or saving failed. Local data was kept.'},503);
   if(result.data?.conflict)return json({error:'Cloud changed on another device. Download and review before uploading.',revision:result.data.revision},409);
   if(!Number.isSafeInteger(result.data?.revision)||result.data.revision<=remote.revision||result.data.revision>2147483647)throw Error('Invalid revision');
   return deleteChat?json({ownerId:user.id,chatId:body.chatId,revision:result.data.revision,deletedChatIds:[...deleted].sort(),deleted:true}):json({revision:result.data.revision});
  }
  return json({error:'Not found.'},404);
 }catch(error){return json({error:error?.name==='AbortError'||error?.name==='TimeoutError'?'Account request timed out. Retry.':'Account service unavailable. Retry without changing local data.'},502);}
}
export async function handleCloudRead(req,env={}){
 // Reuse authenticated account validation; then forward the same access token through RLS.
 const session=await handleAccount(new Request(new URL('/api/account/session',req.url),{headers:req.headers,signal:req.signal}),env);const details=await session.json();
 if(!session.ok)return Response.json(details,{status:session.status,headers:{'Cache-Control':'no-store'}});
 if(!details.configured)return Response.json({error:'Supabase account setup is not configured.'},{status:503,headers:{'Cache-Control':'no-store'}});
 if(!session.ok||!details.user)return Response.json({error:'Sign in from Profile to continue.'},{status:401,headers:{'Cache-Control':'no-store'}});
 const owner=ownerEmail(env.allowedEmail);
 if(owner===null)return Response.json({error:'Allowed account email setup is invalid.'},{status:503,headers:{'Cache-Control':'no-store'}});
 if(owner&&String(details.user.email).toLowerCase()!==owner){console.warn('[dua-access] Cloud read from non-allowed account rejected (403).');return Response.json({error:'Only the configured personal account can use this Dua server.'},{status:403,headers:{'Cache-Control':'no-store'}});}
 const config=configuration(env);let token=req.headers.get('authorization')?.replace(/^Bearer /,'')||cookies(req)[cookieNames.access];const fresh=session.headers.getSetCookie?.()||[];for(const value of fresh)if(value.startsWith(cookieNames.access+'='))token=value.split(';')[0].slice(cookieNames.access.length+1);
 const headers=new Headers({'Cache-Control':'no-store','Content-Type':'application/json'});fresh.forEach(value=>headers.append('Set-Cookie',value));
 try{const response=await (env.fetcher||fetch)(config.url+'/rest/v1/dua_cloud_snapshots?select=revision,payload,updated_at&owner_id=eq.'+encodeURIComponent(details.user.id),{headers:{apikey:config.key,Authorization:'Bearer '+token},signal:AbortSignal.any([req.signal,AbortSignal.timeout(15000)]),redirect:'error'});if(!response.ok)return new Response(JSON.stringify({error:'Cloud database setup is not finished.'}),{status:503,headers});const rows=await response.json();if(!Array.isArray(rows)||rows.length>1)throw Error('Invalid snapshot');const row=rows[0];if(row&&(!Number.isSafeInteger(row.revision)||row.revision<1||row.revision>2147483647))throw Error('Invalid revision');const snapshot=row?validateCloudSnapshot(row.payload):{format:'dua-cloud-snapshot',version:1,chats:[],memory:[]};return new Response(JSON.stringify({ownerId:details.user.id,revision:row?.revision||0,snapshot}),{headers});}catch{return new Response(JSON.stringify({error:'Cloud could not be loaded. Local data was kept.'}),{status:502,headers});}
}
