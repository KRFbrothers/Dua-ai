import { test } from 'node:test';
import assert from 'node:assert/strict';
import handler from './netlify/functions/gemini.mts';

test('Netlify configuration never returns the API key', async()=>{
 globalThis.Netlify={env:{get:name=>name==='GEMINI_API_KEY'?'private-test-key':undefined}};
 const response=await handler(new Request('https://dua.example/api/config'));
 assert.deepEqual(await response.json(),{geminiConfigured:true,model:'gemini-2.5-flash'});
});
test('Netlify Gemini endpoint handles message history without exposing secret or reasoning',async()=>{
 const originalFetch=globalThis.fetch;
 globalThis.Netlify={env:{get:name=>name==='GEMINI_API_KEY'?'private-test-key':undefined}};
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
