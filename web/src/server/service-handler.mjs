import { handleChat } from '../core/providers.mjs';
import { handleSearch } from '../core/search.mjs';
import { handleAccount, handleCloudRead } from '../core/account.mjs';
import { handleSetup } from '../core/setup.mjs';
import { handleAdvanced } from '../core/advanced-integrations.mjs';
import { guardChatAccess } from '../core/access-policy.mjs';
import { envInteger } from '../core/adapters.mjs';
import { handleFiles } from '../core/files.mjs';

/** DUA_CHAT_DEADLINE_SECONDS: whole-request chat deadline (default 280 s, allowed 10–790 s). */
export const chatDeadlineMs = env => envInteger(env, 'DUA_CHAT_DEADLINE_SECONDS', 280, 10, 790) * 1000;

export async function handleService(request,{env=process.env,fetcher=fetch}={}) {
 const url=new URL(request.url),path=url.pathname;
 // Include authentication/quota work in the chat deadline. Keep it below the function maxDuration
 // (web/vercel.json: 300 s, the Hobby maximum with Fluid compute) so the reply can end cleanly.
 if(path==='/api/chat')request=new Request(request,{signal:AbortSignal.any([request.signal,AbortSignal.timeout(chatDeadlineMs(env))])});
 if(request.headers.get('origin')&&request.headers.get('origin')!==url.origin)return Response.json({error:'Request origin is not allowed.'},{status:403,headers:{'Cache-Control':'no-store'}});
 if(path==='/api/setup')return handleSetup(request,{env});
 if(path==='/api/config')return handleChat(request,{env,fetcher});
 if(path==='/api/cloud'||/^\/api\/account\/(session|signin|signup|refresh|profile|logout|features|reset-password|verify-recovery|password|google-token|delete)$/.test(path)) {
  const handler=path==='/api/cloud'&&request.method==='GET'?handleCloudRead:handleAccount;
  return handler(request,{url:env.SUPABASE_URL,key:env.SUPABASE_ANON_KEY,serviceRoleKey:env.SUPABASE_SERVICE_ROLE_KEY,filesBucket:env.DUA_FILES_BUCKET,fetcher});
 }
 // Stored files: owner-only (Supabase sign-in + DUA_ALLOWED_EMAIL), checked inside handleFiles.
 if(path.startsWith('/api/files/'))return handleFiles(request,{env,fetcher});
 if(['/api/chat','/api/search','/api/connectors/github','/api/voice/audio'].includes(path)) {
  if(request.method!=='POST')return Response.json({error:'Method not allowed.'},{status:405,headers:{'Cache-Control':'no-store'}});
  const advanced=path==='/api/connectors/github'||path==='/api/voice/audio';
  if(advanced&&!env.DUA_ALLOWED_EMAIL)return Response.json({error:'Set the allowed personal account email before enabling shared integrations.'},{status:503,headers:{'Cache-Control':'no-store'}});
  const access=await guardChatAccess(request,{env:advanced?{...env,DUA_REQUIRE_LOGIN:'1'}:env,fetcher});
  if(!access.allowed)return access.response;
  const response=path==='/api/chat'?await handleChat(request,{env,fetcher}):path==='/api/search'?await handleSearch(request,{env,fetcher}):await handleAdvanced(request,{env,fetcher,authorized:true});
  // Keep renewed HttpOnly sessions returned by the access guard.
  if(access.headers){const headers=new Headers(access.headers);for(const [key,value] of headers)if(!['set-cookie','content-type','cache-control'].includes(key))response.headers.set(key,value);for(const cookie of headers.getSetCookie())response.headers.append('Set-Cookie',cookie);}
  return response;
 }
 return Response.json({error:'Not found.'},{status:404,headers:{'Cache-Control':'no-store'}});
}
