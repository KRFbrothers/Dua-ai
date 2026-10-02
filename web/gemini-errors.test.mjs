import {test} from 'node:test';
import assert from 'node:assert/strict';
import handler from './netlify/functions/gemini.mts';
for(const [status,hint] of [[400,'invalid'],[403,'access'],[404,'model'],[429,'quota']]){
 test('Gemini '+status+' provides guidance and redacts API key',async()=>{
  const original=globalThis.fetch;const key='AIza-private-test-key';
  globalThis.Netlify={env:{get:name=>name==='GEMINI_API_KEY'?key:name==='GEMINI_MODEL'?'test-model':undefined}};
  globalThis.fetch=async()=>Response.json({error:{message:'Provider rejected '+key}},{status});
  try{
   const response=await handler(new Request('https://dua.example/api/chat',{method:'POST',body:JSON.stringify({model:'Gemini',messages:[{role:'user',text:'Hello'}]})}));
   const data=await response.json();assert.equal(data.upstreamStatus,status);assert.ok(data.error.includes(hint));assert.ok(!data.error.includes(key));
  }finally{globalThis.fetch=original}
 });
}
