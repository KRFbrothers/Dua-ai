import {test} from 'node:test';
import assert from 'node:assert/strict';
import {handleGemini} from './gemini.mjs';

const payload={model:'Dua',messages:[{role:'user',text:'Hi'}]};
const request=data=>new Request('https://dua.example/api/chat',{method:'POST',body:JSON.stringify(data)});
const config={key:'fake-configured-key',model:'test-model'};
for(const [name,input] of [
 ['null root',null],['array root',[]],['string root','hello'],
 ['null message',{...payload,messages:[null]}],
 ['array message',{...payload,messages:[[]]}]
])test('invalid shape returns 400: '+name,async()=>{
 let calls=0;
 const response=await handleGemini(request(input),{...config,fetcher:async()=>{calls++;throw Error('Unexpected provider call')}});
 assert.equal(response.status,400);
 assert.equal(calls,0);
});
test('long assistant reply can be followed by a short user prompt',async()=>{
 let calls=0;
 const response=await handleGemini(request({...payload,messages:[{role:'model',text:'a'.repeat(8001)},...payload.messages]}),{
  ...config,fetcher:async()=>{calls++;return Response.json({candidates:[{content:{parts:[{text:'ok'}]}}]})}
 });
 assert.equal(response.status,200);assert.equal(calls,1);
});
test('oversized user message is still rejected',async()=>{
 const response=await handleGemini(request({...payload,messages:[{role:'user',text:'a'.repeat(8001)}]}),{
  ...config,fetcher:async()=>{throw Error('Unexpected provider call')}
 });
 assert.equal(response.status,400);
});
test('raw provider messages and alternate synthetic keys never reach clients',async()=>{
 const otherKey='AIza'+'Z'.repeat(35);
 const response=await handleGemini(request(payload),{...config,fetcher:async()=>Response.json({
  error:{message:'sensitive diagnostic '+config.key+' '+otherKey}
 },{status:403})});
 const body=JSON.stringify(await response.json());
 assert.equal(response.status,502);
 for(const secret of [config.key,otherKey,'sensitive diagnostic'])assert.ok(!body.includes(secret));
 assert.match(body,/access/);
});
test('quota retry metadata and header propagate without retrying',async()=>{
 let calls=0;
 const response=await handleGemini(request(payload),{...config,fetcher:async()=>{
  calls++;return Response.json({error:{details:[
   {'@type':'type.googleapis.com/google.rpc.RetryInfo',retryDelay:'65s'}
  ]}},{status:429,headers:{'Retry-After':'30'}});
 }});
 assert.equal(response.status,429);
 assert.equal(response.headers.get('retry-after'),'65');
 assert.equal((await response.json()).retryAfterSeconds,65);
 assert.equal(calls,1);
});
test('malformed quota retry metadata does not invent a wait time',async()=>{
 const response=await handleGemini(request(payload),{...config,fetcher:async()=>Response.json({
  error:{details:[{'@type':'type.googleapis.com/google.rpc.RetryInfo',retryDelay:'-1s'}]}
 },{status:429,headers:{'Retry-After':'not-a-time'}})});
 assert.equal(response.headers.get('retry-after'),null);
 assert.equal((await response.json()).retryAfterSeconds,undefined);
});
test('provider timeout is distinct from invalid input and network failure',async()=>{
 const response=await handleGemini(request(payload),{...config,fetcher:async()=>{throw new DOMException('synthetic','TimeoutError')}});
 assert.equal(response.status,504);
 assert.match((await response.json()).error,/timeout/);
});
