import test from 'node:test';
import assert from 'node:assert/strict';
import { handleService, DEFAULT_ENGINE_URL } from '../src/server/service-handler.mjs';
import { mockFetch, sseResponse, sse } from './helpers.mjs';

const origin='https://dua.test';
const env={DUA_ENGINE_URL:'https://engine.test',DUA_REQUIRE_LOGIN:'1'};
const body={model:'Dua',mode:'agent',messages:[{role:'user',text:'hi'}]};
const req=(headers={},method='POST')=>new Request(origin+'/api/web/chat',{method,headers:{'content-type':'application/json',origin,'sec-fetch-site':'same-origin',cookie:'dua_access=fake-access; dua_refresh=fake-refresh',...headers},body:method==='POST'?JSON.stringify(body):undefined});
const run=(request,fetcher,extra={})=>handleService(request,{env:{...env,...extra},fetcher,logWriter:()=>{}});

test('web chat requires the cookie session before contacting the engine',async()=>{
 const fetcher=mockFetch();
 for(const cookie of ['','dua_access=bad token','dua_refresh=only'])
  assert.equal((await run(req({cookie}),fetcher)).status,401);
 assert.equal(fetcher.calls.length,0);
});

test('web chat enforces same-origin browser requests and POST',async()=>{
 const fetcher=mockFetch();
 assert.equal((await run(req({origin:'https://evil.test'}),fetcher)).status,403);
 assert.equal((await run(req({'sec-fetch-site':'cross-site'}),fetcher)).status,403);
 const noOrigin=new Request(origin+'/api/web/chat',{method:'POST',headers:{cookie:'dua_access=fake-access'},body:JSON.stringify(body)});
 assert.equal((await run(noOrigin,fetcher)).status,403);
 assert.equal((await run(req({},'GET'),fetcher)).status,405);
 assert.equal(fetcher.calls.length,0);
});

test('web chat forwards the owner bearer token and streams the engine SSE through',async()=>{
 const fetcher=mockFetch(sseResponse([sse({type:'text',delta:'Hel'},'text'),sse({type:'text',delta:'lo'},'text'),sse({type:'done'},'done')],{delayMs:5}));
 const response=await run(req(),fetcher,{DUA_APP_TOKEN:'fake-app-token'});
 assert.equal(response.status,200);
 assert.match(response.headers.get('content-type'),/text\/event-stream/);
 assert.equal(response.headers.get('x-accel-buffering'),'no');
 const text=await response.text();
 assert.match(text,/"delta":"Hel"/);assert.match(text,/event: done/);
 const call=fetcher.calls[0];
 assert.equal(call.url,'https://engine.test/api/chat');
 assert.equal(call.init.headers.Authorization,'Bearer fake-access');
 assert.equal(call.init.headers['X-Dua-App-Token'],'fake-app-token');
 assert.deepEqual(call.body,{model:'Dua',messages:body.messages,attachments:[],memory:'',stream:true,mode:'agent'});
});

test('web chat passes engine errors without leaking details and validates input',async()=>{
 const fetcher=mockFetch(Response.json({error:'This Dua AI engine is private.',detail:'secret'},{status:401}));
 const response=await run(req(),fetcher);
 assert.equal(response.status,401);assert.deepEqual(await response.json(),{error:'This Dua AI engine is private.'});
 const bad=new Request(origin+'/api/web/chat',{method:'POST',headers:{origin,cookie:'dua_access=fake-access'},body:'{"messages":"x"}'});
 assert.equal((await run(bad,mockFetch())).status,400);
 assert.equal((await run(req(),mockFetch(),{DUA_ENGINE_URL:'http://insecure.test'})).status,503);
 assert.ok(DEFAULT_ENGINE_URL.startsWith('https://'));
});
