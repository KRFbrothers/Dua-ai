import { createBurstGuard } from './vercel-burst.mjs';
import { handleService } from './service-handler.mjs';
export function createVercelHandler({ env = process.env, fetcher = fetch, guard = createBurstGuard() } = {}) {
 return {async fetch(request) {
  const path=new URL(request.url).pathname;
  try {
   const group=['/api/chat','/api/search','/api/connectors/github','/api/build/pr'].includes(path)&&request.method==='POST'?'chat':path.startsWith('/api/account/')||path.startsWith('/api/files/')||path==='/api/cloud'?'account':path.startsWith('/api/imagine/')?'imagine':path.startsWith('/api/voice/')?'voice':null;
   // Imagine allows video status polls every 5 s; daily caps bound the spend. Reply voices fetch a long reply in
   // several short chunks (plus voice previews), so voice gets its own, larger window.
   const wait=group?guard(request,group,group==='chat'?5:group==='imagine'?30:group==='voice'?40:20):0;
   if(wait)return Response.json({error:'Too many requests. Retry shortly.',retryAfterSeconds:wait},{status:429,headers:{'Cache-Control':'no-store','Retry-After':String(wait)}});
   return await handleService(request,{env,fetcher});
  } catch {return Response.json({error:'Connection failed. Retry without changing local data.'},{status:502,headers:{'Cache-Control':'no-store'}});}
 }};
}
