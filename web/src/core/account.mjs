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
const validEmail=value=>typeof value==='string'&&value.length<=254&&/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
function cookies(req){return Object.fromEntries((req.headers.get('cookie')||'').split(';').map(value=>value.trim().split('=' )).filter(pair=>pair.length===2));}
function publicUser(user){if(!user||typeof user.id!=='string'||! /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(user.id))throw Error('Invalid account response');return {id:user.id,email:typeof user.email==='string'?user.email:'',displayName:typeof user.user_metadata?.display_name==='string'?user.user_metadata.display_name.slice(0,80):''};}
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
 const stored=cookies(req);let token=req.headers.get('authorization')?.match(/^Bearer ([A-Za-z0-9._-]+)$/)?.[1]||stored[cookieNames.access];
 async function authenticated(){
  if(token){const result=await request('/auth/v1/user','GET',undefined,token);if(result.response.ok)return publicUser(result.data);if(result.response.status!==401&&result.response.status!==403)throw Error('Account unavailable');}
  if(!native&&stored[cookieNames.refresh]){const result=await request('/auth/v1/token?grant_type=refresh_token','POST',{refresh_token:stored[cookieNames.refresh]});if(result.response.ok){const session=validSession(result.data);token=session.access_token;extraCookies=sessionCookies(session);return publicUser(session.user);}}
  return null;
 }
 try{
  if(url.pathname==='/api/account/session'&&req.method==='GET')return json({configured:true,user:await authenticated()});
  if(url.pathname==='/api/account/features'&&req.method==='GET')return json({passwordReset:true,accountDeletion:!!privilegedKey(env.serviceRoleKey)});
  if(!['POST','PATCH','PUT','DELETE'].includes(req.method))return json({error:'Method not allowed.'},405);
  if(!(req.headers.get('content-type')||'').startsWith('application/json'))return json({error:'Use JSON.'},415);
  const raw=await req.text();if(Buffer.byteLength(raw)> (url.pathname==='/api/cloud'?4*1024*1024+1024:16384))return json({error:'Request too large.'},413);
  let body;try{body=JSON.parse(raw||'{}');}catch{return json({error:'Invalid request.'},400);}if(!body||typeof body!=='object'||Array.isArray(body))return json({error:'Invalid request.'},400);
  if(url.pathname==='/api/account/google-token'&&req.method==='POST'){
   if(!native)return json({error:'Native Google sign-in is only available from the Android app.'},400);
   if(typeof body.idToken!=='string'||! /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(body.idToken)||body.idToken.length>8192||body.nonce!==undefined&&(typeof body.nonce!=='string'||! /^[A-Za-z0-9_-]{16,128}$/.test(body.nonce)))return json({error:'Google sign-in could not be verified. Try again.'},400);
   const result=await request('/auth/v1/token?grant_type=id_token','POST',{provider:'google',id_token:body.idToken,...(body.nonce?{nonce:body.nonce}:{})});
   if(!result.response.ok){const reason=String(result.data?.error_description||result.data?.msg||result.data?.message||result.data?.error||'').replace(/[^\x20-\x7e]/g,' ').slice(0,200);console.warn('[dua-auth] Google ID token rejected ('+result.response.status+')'+(reason?': '+reason:'.'));return json({error:'Google sign-in failed. Check that this Google account is allowed and try again.'},result.response.status===429?429:401);}
   console.info('[dua-auth] Google native sign-in succeeded.');return json(deliver(result.data));
  }
  if(url.pathname==='/api/account/reset-password'&&req.method==='POST'){
   if(!validEmail(body.email))return json({error:'Enter a valid email address.'},400);
   const result=await request('/auth/v1/recover','POST',{email:body.email.trim()});if(!result.response.ok)return json({error:result.response.status===429?'Password reset limit reached. Wait and retry.':'Password reset could not be sent. Check email setup and retry.',...(result.response.status===429?{retryAfterSeconds:60}:{})},result.response.status===429?429:502);
   return json({message:'If this account exists, a recovery email was sent. Enter its recovery code to choose a new password.'});
  }
  if(url.pathname==='/api/account/verify-recovery'&&req.method==='POST'){
   if(!validEmail(body.email)||typeof body.token!=='string'||! /^[0-9]{6,10}$/.test(body.token))return json({error:'Enter the email address and numeric recovery code.'},400);
   const result=await request('/auth/v1/verify','POST',{email:body.email.trim(),token:body.token,type:'recovery'});if(!result.response.ok)return json({error:'Recovery code is invalid or expired. Request a new code.'},401);return json({...deliver(result.data),recoveryVerified:true});
  }
  if(url.pathname==='/api/account/signin'||url.pathname==='/api/account/signup'){
   const signup=url.pathname.endsWith('signup');if(req.method!=='POST')return json({error:'Method not allowed.'},405);
   if(typeof body.email!=='string'||body.email.length>254||! /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(body.email)||typeof body.password!=='string'||body.password.length>256||body.password.length<(signup?8:1)||body.displayName!==undefined&&(typeof body.displayName!=='string'||body.displayName.length>80))return json({error:'Enter a valid email and password (at least 8 characters for signup).'},400);
   const result=await request(signup?'/auth/v1/signup':'/auth/v1/token?grant_type=password','POST',{email:body.email.trim(),password:body.password,...(signup?{data:{display_name:body.displayName?.trim()||''}}:{})});
   if(!result.response.ok)return json({error:signup?'Account could not be created. Check details and retry.':'Sign-in failed. Check your details and confirm your email.',...(result.response.status===429?{retryAfterSeconds:60}:{})},result.response.status===429?429:401);
   return json(result.data?.access_token?deliver(result.data):{configured:true,user:null,confirmationRequired:true,message:'Check your email, confirm your account, then sign in.'});
  }
  if(url.pathname==='/api/account/refresh'&&req.method==='POST'){
   const refresh=native?body.refreshToken:stored[cookieNames.refresh];if(typeof refresh!=='string'||refresh.length>10000||! /^[A-Za-z0-9._-]+$/.test(refresh))return json({error:'Sign in again.'},401);
   const result=await request('/auth/v1/token?grant_type=refresh_token','POST',{refresh_token:refresh});if(!result.response.ok)return json({error:'Session expired. Sign in again.'},401);return json(deliver(result.data));
  }
  if(url.pathname==='/api/account/logout'&&req.method==='POST'){
   let revoked=false;try{if(token){const result=await request('/auth/v1/logout?scope=local','POST',{},token);revoked=result.response.ok;}}catch{}
   if(!native)extraCookies=sessionCookies(null,true);return json({user:null,revoked,message:revoked?'Signed out on this device.':'Signed out locally; server revocation could not be confirmed.'});
  }
  const user=await authenticated();if(!user)return json({error:'Sign in from Profile to continue.'},401);
  if(url.pathname==='/api/account/password'&&req.method==='PUT'){
   if(typeof body.password!=='string'||body.password.length<8||body.password.length>256||body.currentPassword!==undefined&&(typeof body.currentPassword!=='string'||body.currentPassword.length>256))return json({error:'Use a new password between 8 and 256 characters.'},400);
   const result=await request('/auth/v1/user','PUT',{password:body.password,...(body.currentPassword?{current_password:body.currentPassword}:{})},token);if(!result.response.ok)return json({error:'Password could not be changed. Verify a fresh recovery code and retry.'},401);return json({user:publicUser(result.data),message:'Password updated. Sign out other devices from your account security settings if needed.'});
  }
  if(url.pathname==='/api/account/delete'&&req.method==='DELETE'){
   if(body.expectedAccountId!==user.id)return json({error:'Account changed. Review the account shown in Profile before deleting.'},409);
   if(body.confirmation!=='DELETE MY ACCOUNT')return json({error:'Type DELETE MY ACCOUNT to confirm permanent account and cloud data deletion.'},400);const serviceKey=privilegedKey(env.serviceRoleKey);if(!serviceKey)return json({error:'Account deletion is not configured. The server needs its Supabase admin key.'},503);
   // Best effort: remove this user's stored files first (Storage objects are not removed with the auth user).
   try{const storage=await import('./storage.mjs');const files=storage.storageConfig({SUPABASE_URL:config.url,SUPABASE_ANON_KEY:config.key,DUA_FILES_BUCKET:env.filesBucket});if(files.enabled&&token){const list=await storage.listOwnerFiles(files,token,user.id.toLowerCase(),{fetcher,signal:req.signal});if(list.length)await storage.removeFiles(files,token,list.map(file=>file.path),{fetcher,signal:req.signal});}}catch{}
   // Only this verified user's id is used. Never accept an owner id from request JSON.
   const result=await fetcher(config.url+'/auth/v1/admin/users/'+encodeURIComponent(user.id),{method:'DELETE',headers:{apikey:serviceKey,Authorization:'Bearer '+serviceKey,'Content-Type':'application/json'},body:JSON.stringify({should_soft_delete:false}),signal:AbortSignal.any([req.signal,AbortSignal.timeout(15000)]),redirect:'error'});if(!result.ok)return json({error:'Account deletion failed. The account and local data may still be available. Check Profile before retrying.'},502);if(!native)extraCookies=sessionCookies(null,true);return json({user:null,deleted:true,message:'Account deleted. Its cloud snapshot and request usage were deleted. Local chats and memories stay on this device.'});
  }
  if(url.pathname==='/api/account/profile'&&req.method==='PATCH'){
   if(typeof body.displayName!=='string'||!body.displayName.trim()||body.displayName.length>80)return json({error:'Enter a display name up to 80 characters.'},400);
   const result=await request('/auth/v1/user','PUT',{data:{display_name:body.displayName.trim()}},token);return result.response.ok?json({user:publicUser(result.data)}):json({error:'Profile could not be updated.'},502);
  }
  if(url.pathname==='/api/cloud'&&(req.method==='PUT'||req.method==='DELETE')){
   if(body.expectedAccountId!==undefined&&body.expectedAccountId!==user.id)return json({error:'Account changed. Review the cloud snapshot again.'},409);
   if(!Number.isSafeInteger(body.expectedRevision)||body.expectedRevision<0||body.expectedRevision>2147483647)return json({error:'Review the cloud version before changing it.'},400);
   let snapshot;try{snapshot=validateCloudSnapshot(req.method==='DELETE'?{format:'dua-cloud-snapshot',version:1,chats:[],memory:[]}:body.snapshot);}catch(error){return json({error:error.message},400);}
   const result=await request('/rest/v1/rpc/dua_write_cloud_snapshot','POST',{expected_revision:body.expectedRevision,next_payload:snapshot},token);if(!result.response.ok)return json({error:'Cloud database setup or saving failed. Local data was kept.'},503);
   if(result.data?.conflict)return json({error:'Cloud changed on another device. Download and review before uploading.',revision:result.data.revision},409);
   if(!Number.isSafeInteger(result.data?.revision))throw Error('Invalid revision');return json({revision:result.data.revision});
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
 const config=configuration(env);let token=req.headers.get('authorization')?.replace(/^Bearer /,'')||cookies(req)[cookieNames.access];const fresh=session.headers.getSetCookie?.()||[];for(const value of fresh)if(value.startsWith(cookieNames.access+'='))token=value.split(';')[0].slice(cookieNames.access.length+1);
 const headers=new Headers({'Cache-Control':'no-store','Content-Type':'application/json'});fresh.forEach(value=>headers.append('Set-Cookie',value));
 try{const response=await (env.fetcher||fetch)(config.url+'/rest/v1/dua_cloud_snapshots?select=revision,payload,updated_at&owner_id=eq.'+encodeURIComponent(details.user.id),{headers:{apikey:config.key,Authorization:'Bearer '+token},signal:AbortSignal.any([req.signal,AbortSignal.timeout(15000)]),redirect:'error'});if(!response.ok)return new Response(JSON.stringify({error:'Cloud database setup is not finished.'}),{status:503,headers});const rows=await response.json();if(!Array.isArray(rows)||rows.length>1)throw Error('Invalid snapshot');const row=rows[0];if(row&&(!Number.isSafeInteger(row.revision)||row.revision<1||row.revision>2147483647))throw Error('Invalid revision');const snapshot=row?validateCloudSnapshot(row.payload):{format:'dua-cloud-snapshot',version:1,chats:[],memory:[]};return new Response(JSON.stringify({ownerId:details.user.id,revision:row?.revision||0,snapshot}),{headers});}catch{return new Response(JSON.stringify({error:'Cloud could not be loaded. Local data was kept.'}),{status:502,headers});}
}
