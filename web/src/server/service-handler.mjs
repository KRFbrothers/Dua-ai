import { handleChat } from '../core/providers.mjs';
import { handleSearch } from '../core/search.mjs';
import { handleAccount, handleCloudRead } from '../core/account.mjs';
import { handleSetup } from '../core/setup.mjs';
import { handleAdvanced } from '../core/advanced-integrations.mjs';
import { guardChatAccess } from '../core/access-policy.mjs';
import { envInteger } from '../core/adapters.mjs';
import { handleFiles } from '../core/files.mjs';
import { handleImagine } from '../core/imagine.mjs';
import { handleVoice } from '../core/voice.mjs';
import { handleBuildPr } from '../core/build-pr.mjs';
import { withRequestLog } from '../core/request-logging.mjs';
import { switchResponse } from '../core/kill-switches.mjs';

/** DUA_CHAT_DEADLINE_SECONDS: whole-request chat deadline (default 280 s, allowed 10–790 s). */
export const chatDeadlineMs = env => envInteger(env, 'DUA_CHAT_DEADLINE_SECONDS', 280, 10, 790) * 1000;

export async function handleService(request, options = {}) {
 return withRequestLog(request, () => routeService(request, options), { logWriter: options.logWriter });
}

async function routeService(request,{env=process.env,fetcher=fetch}={}) {
 const url=new URL(request.url),path=url.pathname;
 const deadlineAt=path==='/api/chat'?Date.now()+chatDeadlineMs(env):undefined;
 // Include authentication/quota work in the chat deadline. Keep it below the Cloud Run request
 // timeout (900 s; deploy-cloudrun.sh sets 780 s) so the reply can end cleanly.
 if(path==='/api/chat')request=new Request(request,{signal:AbortSignal.any([request.signal,AbortSignal.timeout(chatDeadlineMs(env))])});
 if(request.headers.get('origin')&&request.headers.get('origin')!==url.origin)return Response.json({error:'Request origin is not allowed.'},{status:403,headers:{'Cache-Control':'no-store'}});
 // Remote kill switches (DUA_MAINTENANCE, DUA_DISABLE_IMAGINE, DUA_DISABLE_VOICE): refused before any provider work.
 const paused=switchResponse(path,env);
 if(paused)return paused;
 if(path==='/api/web/chat')return handleWebChat(request,{env,fetcher});
 if(path==='/api/setup')return handleSetup(request,{env});
 if(path==='/api/config')return handleChat(request,{env,fetcher});
 // Account and cloud routes: owner-locked by DUA_ALLOWED_EMAIL inside handleAccount (session, features and logout stay open).
 if(path==='/api/cloud'||path==='/api/cloud/chat'||/^\/api\/account\/(session|refresh|logout|features|google-token|email-login|email-signup|password|phone|delete)$/.test(path)) {
  const handler=path==='/api/cloud'&&request.method==='GET'?handleCloudRead:handleAccount;
  return handler(request,{url:env.SUPABASE_URL,key:env.SUPABASE_ANON_KEY,serviceRoleKey:env.SUPABASE_SERVICE_ROLE_KEY,filesBucket:env.DUA_FILES_BUCKET,allowedEmail:env.DUA_ALLOWED_EMAIL,fetcher});
 }
 // Stored files: owner-only (Supabase sign-in + DUA_ALLOWED_EMAIL), checked inside handleFiles.
 if(path.startsWith('/api/files/'))return handleFiles(request,{env,fetcher});
 // Imagine (image + video generation): owner-only, daily caps, checked inside handleImagine.
 if(path.startsWith('/api/imagine/'))return handleImagine(request,{env,fetcher});
 // Voice (reply voices, live voice sessions): owner-only, checked inside handleVoice. Not counted as chat requests.
 if(path.startsWith('/api/voice/'))return handleVoice(request,{env,fetcher});
 if(path==='/api/build/pr')return handleBuildPr(request,{env,fetcher});
 if(['/api/chat','/api/search','/api/connectors/github'].includes(path)) {
  if(request.method!=='POST')return Response.json({error:'Method not allowed.'},{status:405,headers:{'Cache-Control':'no-store'}});
  const advanced=path==='/api/connectors/github';
  if(advanced&&!env.DUA_ALLOWED_EMAIL)return Response.json({error:'Set the allowed personal account email before enabling shared integrations.'},{status:503,headers:{'Cache-Control':'no-store'}});
  const access=await guardChatAccess(request,{env:advanced?{...env,DUA_REQUIRE_LOGIN:'1'}:env,fetcher});
  if(!access.allowed)return access.response;
  const response=path==='/api/chat'?await handleChat(request,{env,fetcher,deadlineAt}):path==='/api/search'?await handleSearch(request,{env,fetcher}):await handleAdvanced(request,{env,fetcher,authorized:true});
  // Keep renewed HttpOnly sessions returned by the access guard.
  if(access.headers){const headers=new Headers(access.headers);for(const [key,value] of headers)if(!['set-cookie','content-type','cache-control'].includes(key))response.headers.set(key,value);for(const cookie of headers.getSetCookie())response.headers.append('Set-Cookie',cookie);}
  return response;
 }
 return Response.json({error:'Not found.'},{status:404,headers:{'Cache-Control':'no-store'}});
}

// Website chat proxy (docs/WEBSITE.md roadmap item 2). The website's owner session lives in HttpOnly
// cookies the page cannot read, so dua-api forwards a same-origin browser request to dua-ai-engine
// /api/chat with that session as the bearer token and streams the SSE reply back unbuffered.
// DUA_ENGINE_URL overrides the engine origin; DUA_APP_TOKEN (optional) is added server-side only.
export const DEFAULT_ENGINE_URL='https://dua-ai-engine-228305912525.us-central1.run.app';
const WEB_CHAT_MAX_BYTES=4*1024*1024;
const noStore=(error,status)=>Response.json({error},{status,headers:{'Cache-Control':'no-store'}});
function engineUrl(env){
 try{const url=new URL(String(env.DUA_ENGINE_URL||DEFAULT_ENGINE_URL));
  if(url.protocol!=='https:'||url.username||url.password||url.search||url.hash||!['','/'].includes(url.pathname))return null;
  return new URL('/api/chat',url.origin);}catch{return null;}
}
export async function handleWebChat(request,{env=process.env,fetcher=fetch}={}){
 if(request.method!=='POST')return noStore('Method not allowed.',405);
 const url=new URL(request.url);
 if(request.headers.get('origin')!==url.origin||!['','same-origin'].includes(request.headers.get('sec-fetch-site')||''))return noStore('Request origin is not allowed.',403);
 const cookie=Object.fromEntries((request.headers.get('cookie')||'').split(';').map(v=>v.trim().split('=')).filter(p=>p.length===2));
 const token=cookie.dua_access;
 if(!token||token.length>10000||!/^[A-Za-z0-9._-]+$/.test(token))return noStore('Sign in to chat.',401);
 const target=engineUrl(env);
 if(!target)return noStore('Chat engine is not configured.',503);
 const body=await request.text();
 if(body.length>WEB_CHAT_MAX_BYTES)return noStore('Conversation too large. Start a new chat.',413);
 let data;try{data=JSON.parse(body);}catch{return noStore('Invalid request.',400);}
 if(!data||typeof data!=='object'||Array.isArray(data)||!Array.isArray(data.messages))return noStore('Invalid request.',400);
 // Plain chat or Agent only from the website; Build/persistence stay Android-only.
 const mode=data.mode==='agent'?'agent':data.mode==='reasoning'?'reasoning':'auto';
 const payload={model:typeof data.model==='string'?data.model:'Dua',messages:data.messages,attachments:[],memory:typeof data.memory==='string'?data.memory:'',stream:true,mode};
 const headers={'Content-Type':'application/json','Accept':'text/event-stream, application/json','Authorization':'Bearer '+token,'x-dua-native':'1'};
 if(env.DUA_APP_TOKEN)headers['X-Dua-App-Token']=env.DUA_APP_TOKEN;
 let upstream;
 try{upstream=await fetcher(target,{method:'POST',headers,body:JSON.stringify(payload),signal:request.signal});}
 catch{return noStore('Chat engine is not reachable. Try again.',502);}
 const type=upstream.headers.get('content-type')||'';
 if(type.includes('text/event-stream')&&upstream.body)
  return new Response(upstream.body,{status:upstream.status,headers:{'Content-Type':'text/event-stream; charset=utf-8','Cache-Control':'no-store','X-Accel-Buffering':'no','X-Content-Type-Options':'nosniff'}});
 // Non-stream answers (errors): pass a short JSON error only, never upstream details beyond `error`.
 let message='Reply failed. Try again.';
 try{const json=await upstream.json();if(typeof json?.error==='string')message=json.error.slice(0,300);}catch{}
 return noStore(message,upstream.ok?502:upstream.status);
}
