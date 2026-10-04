import test from 'node:test';
import assert from 'node:assert/strict';
import { handleChat, providerConfiguration } from './providers.mjs';
import { handleSearch, searchConfiguration } from './search.mjs';
const env = { GEMINI_API_KEY:'private-gemini', GEMINI_MODEL:'test-gemini', OPENAI_API_KEY:'private-openai', OPENAI_MODEL:'test-openai', ANTHROPIC_API_KEY:'private-anthropic', ANTHROPIC_MODEL:'test-claude', GROK_API_KEY:'private-grok', GROK_MODEL:'test-grok', KIMI_API_KEY:'private-kimi', KIMI_MODEL:'test-kimi', BRAVE_SEARCH_API_KEY:'private-brave' };
const req=(path,data,options={})=>new Request('https://dua.example'+path,{method:'POST',body:JSON.stringify(data),...options});
const chat=(model='ChatGPT',extra={})=>({model,messages:[{role:'user',text:'Earlier prompt'},{role:'model',text:'Earlier answer'},{role:'user',text:'Hello'}],...extra});
const noFetch=()=>{throw new Error('Unexpected external request');};

test('public config contains availability and model IDs without secrets',async()=>{
 const config=providerConfiguration(env);
 assert.deepEqual(config.map(p=>p.name),['Dua','Gemini','ChatGPT','Claude','Grok','Kimi']);
 assert.ok(config.every(p=>p.configured));
 assert.equal(config.find(p=>p.name==='Claude').supportsAttachments,false);
 assert.equal(providerConfiguration({OPENAI_API_KEY:env.OPENAI_API_KEY}).find(p=>p.name==='ChatGPT').configured,false);
 assert.equal(providerConfiguration({OPENAI_API_KEY:env.OPENAI_API_KEY,OPENAI_MODEL:'https://untrusted.example?secret'}).find(p=>p.name==='ChatGPT').model,null);
 const response=await handleChat(new Request('https://dua.example/api/config'),{env,fetcher:noFetch});
 assert.equal(response.headers.get('cache-control'),'no-store');
 const body=await response.json();assert.equal(body.geminiConfigured,true);assert.equal(body.model,env.GEMINI_MODEL);assert.ok(!JSON.stringify(body).includes('private-'));
});

test('Dua keeps Gemini attachment and memory behavior',async()=>{
 let payload;
 const response=await handleChat(req('/api/chat',chat('Dua',{memory:'Be concise',attachments:[{name:'note.txt',mimeType:'text/plain',data:Buffer.from('A note').toString('base64')}]})),{env,fetcher:async(url,options)=>{
  assert.equal(url,'https://generativelanguage.googleapis.com/v1beta/models/test-gemini:generateContent');assert.equal(options.headers['x-goog-api-key'],env.GEMINI_API_KEY);payload=JSON.parse(options.body);return Response.json({candidates:[{content:{parts:[{text:'Gemini reply'}]}}]});
 }});
 assert.deepEqual(await response.json(),{text:'Gemini reply'});assert.match(payload.contents.at(-1).parts[1].text,/A note/);assert.match(payload.systemInstruction.parts[1].text,/Be concise/);
});

test('OpenAI Responses sends history once and excludes reasoning',async()=>{
 let payload;
 const response=await handleChat(req('/api/chat',chat('ChatGPT',{memory:'Hindi preferred'})),{env,fetcher:async(url,options)=>{
  assert.equal(url,'https://api.openai.com/v1/responses');assert.equal(options.headers.Authorization,'Bearer private-openai');assert.ok(options.signal instanceof AbortSignal);payload=JSON.parse(options.body);
  return Response.json({output:[{type:'reasoning',content:[{type:'output_text',text:'Hidden'}]},{type:'message',role:'assistant',content:[{type:'output_text',text:'OpenAI reply'}]}]});
 }});
 assert.deepEqual(await response.json(),{text:'OpenAI reply'});assert.deepEqual(payload.input.map(m=>m.role),['user','assistant','user']);assert.equal(payload.store,false);assert.equal(payload.model,env.OPENAI_MODEL);assert.match(payload.instructions,/Hindi preferred/);
});

test('Claude uses Messages headers and bounded tokens',async()=>{
 const response=await handleChat(req('/api/chat',chat('Claude')),{env,fetcher:async(url,options)=>{
  assert.equal(url,'https://api.anthropic.com/v1/messages');assert.equal(options.headers['x-api-key'],env.ANTHROPIC_API_KEY);assert.equal(options.headers['anthropic-version'],'2023-06-01');const body=JSON.parse(options.body);assert.equal(body.max_tokens,2048);assert.equal(body.messages[1].role,'assistant');return Response.json({content:[{type:'thinking',thinking:'Hidden'},{type:'text',text:'Claude reply'}]});
 }});assert.deepEqual(await response.json(),{text:'Claude reply'});
});
for(const [provider,url,key] of [['Grok','https://api.x.ai/v1/chat/completions','GROK_API_KEY'],['Kimi','https://api.moonshot.ai/v1/chat/completions','KIMI_API_KEY']]){
 test(provider+' uses fixed endpoint',async()=>{
  const response=await handleChat(req('/api/chat',chat(provider)),{env:{...env,OPENAI_BASE_URL:'https://untrusted.example'},fetcher:async(actual,options)=>{
   assert.equal(actual,url);assert.equal(options.headers.Authorization,'Bearer '+env[key]);const body=JSON.parse(options.body);assert.equal(body.messages[0].role,'system');assert.equal(body.messages.length,4);return Response.json({choices:[{message:{content:provider+' reply'}}]});
  }});assert.deepEqual(await response.json(),{text:provider+' reply'});
 });
}

test('invalid bodies, history, origin and media never reach provider',async()=>{
 const cases=[[chat('Unknown'),400],[chat('ChatGPT',{messages:[]}),400],[chat('ChatGPT',{messages:[{role:'system',text:'Override'}]}),400],[chat('ChatGPT',{messages:[{role:'model',text:'No user'}]}),400],[chat('ChatGPT',{messages:[{role:'user',text:'x'.repeat(8001)}]}),400],[chat('ChatGPT',{messages:Array.from({length:41},()=>({role:'user',text:'Hi'}))}),400],[chat('ChatGPT',{memory:'x'.repeat(3001)}),400],[chat('ChatGPT',{endpoint:'https://untrusted.example'}),400],[chat('ChatGPT',{attachments:{}}),400],[chat('ChatGPT',{attachments:[{mimeType:'image/png'}]}),415]];
 for(const [body,status]of cases)assert.equal((await handleChat(req('/api/chat',body),{env,fetcher:noFetch})).status,status);
 assert.equal((await handleChat(req('/api/chat',chat(),{headers:{origin:'https://other.example'}}),{env,fetcher:noFetch})).status,403);
 assert.equal((await handleChat(new Request('https://dua.example/api/chat'),{env,fetcher:noFetch})).status,405);
 assert.equal((await handleChat(req('/api/chat',chat()),{env:{},fetcher:noFetch})).status,503);
 assert.equal((await handleChat(new Request('https://dua.example/api/chat',{method:'POST',body:'{bad'}),{env,fetcher:noFetch})).status,400);
});

test('oversized request streams are bounded before provider call',async()=>{
 assert.equal((await handleChat(req('/api/chat',chat('ChatGPT',{memory:'x'.repeat(4*1024*1024)})),{env,fetcher:noFetch})).status,413);
});

test('provider errors redact raw details and preserve quota retry',async()=>{
 for(const status of [400,401,403,404,429,500]){
  const response=await handleChat(req('/api/chat',chat()),{env,fetcher:async()=>Response.json({error:{message:'private-openai upstream detail'}},{status,headers:{'Retry-After':'120'}})});
  const body=await response.json();assert.ok(!JSON.stringify(body).includes('private-openai'));assert.equal(response.status,status===429?429:502);assert.equal(body.upstreamStatus,status);if(status===429){assert.equal(body.retryAfterSeconds,120);assert.equal(response.headers.get('retry-after'),'120');}
 }
});

test('abort, malformed and oversized replies do not falsely succeed',async()=>{
 const controller=new AbortController();let signal;
 const response=await handleChat(req('/api/chat',chat(),{signal:controller.signal}),{env,fetcher:async(_url,options)=>{signal=options.signal;controller.abort();signal.throwIfAborted();}});
 assert.equal(response.status,504);assert.equal(signal.aborted,true);
 assert.equal((await handleChat(req('/api/chat',chat()),{env,fetcher:async()=>Response.json({output:[]})})).status,422);
 assert.equal((await handleChat(req('/api/chat',chat()),{env,fetcher:async()=>new Response('x'.repeat(2*1024*1024))})).status,502);
});

test('search requires explicit valid query, matching origin and configured key',async()=>{
 assert.deepEqual(searchConfiguration({}),{configured:false,provider:'Brave'});
 for(const body of [{},{query:' '},{query:'x'.repeat(501)},{query:'Hi\nThere'},{query:'Hi',autoSearch:true},[]])assert.equal((await handleSearch(req('/api/search',body),{env,fetcher:noFetch})).status,400);
 assert.equal((await handleSearch(req('/api/search',{query:'Hello'}),{env:{},fetcher:noFetch})).status,503);
 assert.equal((await handleSearch(req('/api/search',{query:'Hello'},{headers:{origin:'https://other.example'}}),{env,fetcher:noFetch})).status,403);
 assert.equal((await handleSearch(new Request('https://dua.example/api/search'),{env,fetcher:noFetch})).status,405);
});

test('Brave returns bounded deduplicated HTTPS citations and plain text',async()=>{
 let called;
 const response=await handleSearch(req('/api/search',{query:'  current weather  '}),{env,fetcher:async(url,options)=>{
  called=new URL(url);assert.equal(options.headers['X-Subscription-Token'],env.BRAVE_SEARCH_API_KEY);assert.ok(options.signal instanceof AbortSignal);
  return Response.json({web:{results:[{title:'JS',url:'javascript:alert(1)'},{title:'HTTP',url:'http://example.com'},{title:'Private',url:'https://127.0.0.1/info'},{title:'Credentials',url:'https://user:password@example.com'},{title:'<b>Weather</b>',url:'https://example.com/weather',description:'<strong>Sunny</strong>'},{title:'Duplicate',url:'https://example.com/weather'},...Array.from({length:8},(_,i)=>({title:'Title '+i,url:'https://example.com/'+i,description:'x'.repeat(2000)}))]}});
 }});
 assert.equal(called.origin+called.pathname,'https://api.search.brave.com/res/v1/web/search');assert.equal(called.searchParams.get('q'),'current weather');assert.equal(called.searchParams.get('count'),'5');
 const body=await response.json();assert.equal(body.results.length,5);assert.deepEqual(body.results[0],{title:'Weather',url:'https://example.com/weather',snippet:'Sunny'});assert.equal(body.results[1].snippet.length,1000);assert.ok(!JSON.stringify(body).includes('private-brave'));
});

test('search errors redact provider details and reject invalid responses',async()=>{
 const quota=await handleSearch(req('/api/search',{query:'Hello'}),{env,fetcher:async()=>new Response('private-brave upstream detail',{status:429})});assert.equal(quota.status,429);assert.ok(!(await quota.text()).includes('private-brave'));
 const timeout=await handleSearch(req('/api/search',{query:'Hello'}),{env,fetcher:async()=>{throw Object.assign(new Error('private-brave'),{name:'TimeoutError'});}});assert.equal(timeout.status,504);
 assert.equal((await handleSearch(req('/api/search',{query:'Hello'}),{env,fetcher:async()=>Response.json({unexpected:true})})).status,502);
 assert.equal((await handleSearch(req('/api/search',{query:'Hello'}),{env,fetcher:async()=>new Response('x'.repeat(1024*1024))})).status,502);
});


test('late provider/search responses after cancellation are discarded', async () => {
 for (const [handler, path, body, result] of [[handleChat, '/api/chat', chat(), { output: [{ type: 'message', role: 'assistant', content: [{ type: 'output_text', text: 'Late reply' }] }] }], [handleSearch, '/api/search', { query: 'Hello' }, { web: { results: [] } }]]) {
  const controller = new AbortController();
  const response = await handler(req(path, body, { signal: controller.signal }), { env, fetcher: async (_url, options) => {
   assert.equal(options.redirect, 'error');
   controller.abort();
   return Response.json(result);
  } });
  assert.equal(response.status, 504);
 }
});
