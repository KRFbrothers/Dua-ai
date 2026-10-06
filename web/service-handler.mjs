import { handleChat } from './providers.mjs';
import { handleSearch } from './search.mjs';
import { handleAccount, handleCloudRead } from './account.mjs';
import { handleSetup } from './setup.mjs';
import { handleAdvanced } from './advanced-integrations.mjs';
import { guardChatAccess } from './access-policy.mjs';

export async function handleService(request,{env=process.env,fetcher=fetch}={}) {
 const url=new URL(request.url),path=url.pathname;
 // Include authentication/quota work in the chat deadline, before the 60s host limit.
 if(path==='/api/chat')request=new Request(request,{signal:AbortSignal.any([request.signal,AbortSignal.timeout(55000)])});
 if(request.headers.get('origin')&&request.headers.get('origin')!==url.origin)return Response.json({error:'Request origin is not allowed.'},{status:403,headers:{'Cache-Control':'no-store'}});
 if(path==='/api/setup')return handleSetup(request,{env});
 if(path==='/api/config')return handleChat(request,{env,fetcher});
 if(path==='/api/cloud'||/^\/api\/account\/(session|signin|signup|refresh|profile|logout|features|usage|reset-password|verify-recovery|password|google|google-token|delete|oauth\/callback)$/.test(path)) {
  const handler=path==='/api/cloud'&&request.method==='GET'?handleCloudRead:handleAccount;
  return handler(request,{url:env.SUPABASE_URL,key:env.SUPABASE_ANON_KEY,oauthRedirectUrl:env.SUPABASE_AUTH_REDIRECT_URL,serviceRoleKey:env.SUPABASE_SERVICE_ROLE_KEY,dailyRequestLimit:env.DUA_DAILY_REQUEST_LIMIT,fetcher});
 }
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
