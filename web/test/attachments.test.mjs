import test from 'node:test';
import assert from 'node:assert/strict';
import {handleGemini} from '../src/core/gemini.mjs';
const png='iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jf0cAAAAASUVORK5CYII=';
const message={model:'Dua',messages:[{role:'user',text:'Read these files'}]};
async function send(attachments,fetcher){return handleGemini(new Request('https://dua.example/api/chat',{method:'POST',body:JSON.stringify({...message,attachments})}),{key:'private-test-key',model:'test-model',fetcher});}
test('image and UTF-8 text arrive in the final user content only',async()=>{
 const response=await send([{name:'photo.png',mimeType:'image/png',data:png},{name:'notes.txt',mimeType:'text/plain',data:Buffer.from('नमस्ते').toString('base64')}],async(_url,options)=>{
  const request=JSON.parse(options.body);assert.equal(options.headers['x-goog-api-key'],'private-test-key');
  assert.deepEqual(request.contents[0].parts[2],{inlineData:{mimeType:'image/png',data:png}});
  assert.match(request.contents[0].parts[3].text,/नमस्ते/);
  return Response.json({candidates:[{content:{parts:[{text:'Read files'}]}}]});
 });assert.equal(response.status,200);assert.deepEqual(await response.json(),{text:'Read files'});
});
test('PDF becomes inline provider data',async()=>{
 const data=Buffer.from('%PDF-1.7\nexample').toString('base64');
 const response=await send([{name:'file.pdf',mimeType:'application/pdf',data}],async(_url,options)=>{
  assert.deepEqual(JSON.parse(options.body).contents[0].parts[2],{inlineData:{mimeType:'application/pdf',data}});
  return Response.json({candidates:[{content:{parts:[{text:'PDF reply'}]}}]});
 });assert.equal(response.status,200);
});
test('invalid media, MIME spoofing, excessive count and invalid base64 never call provider',async()=>{
 for(const attachments of [null,{},Array(4).fill({}),[{name:'file.exe',mimeType:'application/octet-stream',data:'aGk='}],[{name:'fake.png',mimeType:'image/png',data:'aGk='}],[{name:'x.txt',mimeType:'text/plain',data:'!!!!'}]]){
  let called=false;const response=await send(attachments,()=>{called=true});assert.ok(response.status>=400);assert.equal(called,false);
 }
});
test('oversized text and aggregate file bytes are rejected before provider invocation',async()=>{
 for(const files of [
  [{name:'large.txt',mimeType:'text/plain',data:Buffer.alloc(65537,65).toString('base64')}],
  [{name:'large.pdf',mimeType:'application/pdf',data:Buffer.concat([Buffer.from('%PDF-'),Buffer.alloc(2*1024*1024)]).toString('base64')}]
 ]) {let calls=0;const response=await send(files,()=>{calls++});assert.equal(response.status,413);assert.equal(calls,0);}
});
test('attachment bytes are not placed into older conversation messages',async()=>{
 const response=await handleGemini(new Request('https://dua.example/api/chat',{method:'POST',body:JSON.stringify({...message,messages:[{role:'user',text:'Earlier'},{role:'model',text:'Reply'},...message.messages],attachments:[{name:'image.png',mimeType:'image/png',data:png}]})}),{key:'test',model:'model',fetcher:async(_url,options)=>{
  const contents=JSON.parse(options.body).contents;assert.equal(contents[0].parts.length,1);assert.equal(contents[1].parts.length,1);assert.equal(contents[2].parts.length,3);
  return Response.json({candidates:[{content:{parts:[{text:'OK'}]}}]});
 }});assert.equal(response.status,200);
});
