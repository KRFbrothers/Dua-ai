import { test } from 'node:test';
import assert from 'node:assert/strict';
import handler, { config as chatConfig } from './netlify/functions/gemini.mts';
import configHandler, { config as configRoute } from './netlify/functions/config.mts';
import rateLimitHandler from './netlify/functions/chat-rate-limit.mts';

test('Netlify configuration never returns the API key', async()=>{
 globalThis.Netlify={env:{get:name=>name==='GEMINI_API_KEY'?'private-test-key':name==='GEMINI_MODEL'?'test-model':undefined}};
 const response=await configHandler(new Request('https://dua.example/api/config'));
 assert.deepEqual(await response.json(),{geminiConfigured:true,model:'test-model'});
});
test('Netlify Gemini endpoint handles message history without exposing secret or reasoning',async()=>{
 const originalFetch=globalThis.fetch;
 globalThis.Netlify={env:{get:name=>name==='GEMINI_API_KEY'?'private-test-key':name==='GEMINI_MODEL'?'test-model':undefined}};
 globalThis.fetch=async(url,options)=>{
  assert.equal(options.headers['x-goog-api-key'],'private-test-key');
  assert.deepEqual(JSON.parse(options.body).contents,[{role:'user',parts:[{text:'Hello'}]}]);
  return Response.json({candidates:[{content:{parts:[{text:'hidden reasoning',thought:true},{text:'Hello, I am Dua.'}]}}]});
 };
 try{
  const response=await handler(new Request('https://dua.example/api/chat',{method:'POST',headers:{'Content-Type':'application/json','Origin':'https://dua.example'},body:JSON.stringify({model:'Gemini',messages:[{role:'user',text:'Hello'}]})}));
  assert.equal(response.status,200);
  assert.deepEqual(await response.json(),{text:'Hello, I am Dua.'});
 }finally{globalThis.fetch=originalFetch;}
});
test('Netlify endpoint rejects missing key and cross-origin requests',async()=>{
 globalThis.Netlify={env:{get:()=>undefined}};
 assert.equal((await handler(new Request('https://dua.example/api/chat',{method:'POST'}))).status,503);
 globalThis.Netlify={env:{get:()=>'test'}};
 assert.equal((await handler(new Request('https://dua.example/api/chat',{method:'POST',headers:{Origin:'https://other.example'}}))).status,403);
});


test('chat edge limit leaves configuration checks outside the allowance', () => {
 assert.equal(chatConfig.path, '/api/chat');
 assert.deepEqual(chatConfig.rateLimit, {
  windowLimit: 5, windowSize: 60, aggregateBy: ['ip', 'domain'],
  action: 'rewrite', to: '/api/chat-rate-limit'
 });
 assert.equal(configRoute.path, '/api/config');
 assert.equal(configRoute.rateLimit, undefined);
});
test('edge rate-limit response is retryable JSON without a provider call', async () => {
 const originalFetch = globalThis.fetch;
 const originalNetlify = globalThis.Netlify;
 globalThis.fetch = () => { throw new Error('Rate limiting must not call Gemini'); };
 globalThis.Netlify = { env: { get: () => { throw new Error('No credentials needed'); } } };
 try {
  const response = await rateLimitHandler();
  assert.equal(response.status, 429);
  assert.equal(response.headers.get('Retry-After'), '60');
  assert.equal(response.headers.get('Cache-Control'), 'no-store');
  assert.match(response.headers.get('Content-Type'), /application\/json/);
  assert.deepEqual(await response.json(), {
   error: 'Bahut jaldi messages bheje gaye. 1 minute baad retry karein.', retryAfterSeconds: 60
  });
 } finally { globalThis.fetch = originalFetch; globalThis.Netlify = originalNetlify; }
});
