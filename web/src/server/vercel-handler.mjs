import { createBurstGuard } from './vercel-burst.mjs';
import { handleService } from './service-handler.mjs';
export function createVercelHandler({ env = process.env, fetcher = fetch, guard = createBurstGuard() } = {}) {
 return {async fetch(request) {
  const path=new URL(request.url).pathname;
  try {
   const group=['/api/chat','/api/search','/api/voice/audio','/api/connectors/github'].includes(path)&&request.method==='POST'?'chat':path.startsWith('/api/account/')||path.startsWith('/api/files/')||path==='/api/cloud'?'account':null;
   const wait=group?guard(request,group,group==='chat'?5:20):0;
   if(wait)return Response.json({error:'Too many requests. Retry shortly.',retryAfterSeconds:wait},{status:429,headers:{'Cache-Control':'no-store','Retry-After':String(wait)}});
   return await handleService(request,{env,fetcher});
  } catch {return Response.json({error:'Connection failed. Retry without changing local data.'},{status:502,headers:{'Cache-Control':'no-store'}});}
 }};
}
