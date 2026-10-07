import {test} from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';

const readAloudSource=await readFile(new URL('../dist/read-aloud.js',import.meta.url),'utf8');
const voiceSource=await readFile(new URL('../dist/voice-input.js',import.meta.url),'utf8');
const source=(await readFile(new URL('../dist/chat.html',import.meta.url),'utf8')).split('<script>')[1].split('</script>')[0];
function setup({deferChat=false,replyError=false,replyText='Hello',files=[],voiceDraft=null,speechSupported=false,model='Dua',savedChat=null}={}) {
 const synthesis={spoken:[],speak(utterance){this.spoken.push(utterance)},cancel(){},getVoices(){return[]}};
 class Utterance{constructor(text){this.text=text;}}
 const windowEvents={};let redirected=null;
 const events={};const configuration=[];const replies=[];let chatCalls=0;let signal;let payload;
 const node=(tag='div')=>{
  const n={tagName:tag.toUpperCase(),hidden:false,value:'',dataset:{},children:[],queries:{},attributes:{},events:{},style:{setProperty(){}},
   classList:{toggle(){},remove(){},contains(name){return (n.className||'').split(' ').includes(name)}},
   setAttribute(k,v){this.attributes[k]=v},getAttribute(k){return this.attributes[k]},removeAttribute(k){delete this.attributes[k]},toggleAttribute(){},focus(){},
   addEventListener(type,handler){this.events[type]=handler},querySelector(selector){return this.queries[selector]||(this.queries[selector]=node())},querySelectorAll(selector){return this.children.flatMap(child=>[child,...child.querySelectorAll('*')]).filter(child=>selector==='*'||child.classList.contains(selector.slice(1)))},
   append(child){this.children.push(child);child.parent=this},remove(){if(this.parent)this.parent.children=this.parent.children.filter(n=>n!==this)}
  };
  n.contains=child=>n===child||n.children.some(c=>c.contains(child));
  n.closest=selector=>n.classList.contains(selector.slice(1))?n:n.parent?.closest(selector);
  Object.defineProperties(n,{lastElementChild:{get(){return n.children.at(-1)}},previousElementSibling:{get(){return n.parent?.children[n.parent.children.indexOf(n)-1]}},textContent:{get(){return (n.text||'')+n.children.map(child=>child.textContent).join('')},set(value){n.text=value;n.children=[]}}});
  return n;
 };
 const message=node(),action=node(),picker=node(),composer=node(),error=node(),empty=node();message.value='Hi';
 composer.addEventListener=(type,handler)=>{events[type]=handler};
 const elements={'textarea':message,'#composer-action':action,'.model-picker':picker,'.composer':composer,'.chat-messages':node(),'.chat-error':error,'.empty':empty,'.model-chip-label':node(),'.model-sheet-footer':node()};
 const storage=new Map();if(voiceDraft)storage.set('dua-voice-draft',JSON.stringify({text:voiceDraft}));if(files.length)storage.set('dua-attachments',JSON.stringify(files));
 vm.runInNewContext(readAloudSource+voiceSource+source,{
  document:{querySelectorAll:s=>Object.values(elements).flatMap(n=>[n,...n.querySelectorAll('*')]).filter(n=>n.classList.contains(s.slice(1))),querySelector:s=>elements[s]||(elements[s]=node()),createElement:node,addEventListener(){},documentElement:node()},
  location:{protocol:'https:',search:'',pathname:'/chat.html',replace(url){redirected=url}},
  navigator:{clipboard:{async writeText(text){storage.set('copied',text)}}},window:{speechSynthesis:speechSupported?synthesis:null,SpeechSynthesisUtterance:speechSupported?Utterance:null,navigator:{language:"hi-IN"},addEventListener(event,handler){windowEvents[event]=handler},history:{replaceState(){}}},URL,URLSearchParams,AbortController,setTimeout,clearTimeout,
  DuaHistory:{save(){},load(){return savedChat||(model==='Dua'?null:{model,draft:'Hi',messages:[]})},newChat(){}},
  sessionStorage:{getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)},
  fetch:async (url,options)=>{
   if(url==='/api/config')return new Promise(resolve=>configuration.push(config=>resolve({ok:true,json:async()=>typeof config==='object'?config:{geminiConfigured:config}})));
   chatCalls++;signal=options.signal;payload=JSON.parse(options.body);
   if(deferChat)return new Promise(resolve=>replies.push(resolve));
   return {ok:!replyError,json:async()=>replyError?{error:'Gemini quota limit aa gayi.'}:{text:replyText}};
  }
 });
 return {windowEvents,redirected:()=>redirected,synthesis,message,error,empty,configuration,replies,storage,fileCard:elements['.selected-files'],fileCopy:elements['.selected-file-copy'],messages:elements['.chat-messages'],signal:()=>signal,
  webMode:elements['#web-mode'],webHelp:elements['#web-mode-help'],chooseWeb(mode){elements['#web-mode'].value=mode;elements['#web-mode'].events.change()},
  payload:()=>payload,calls:()=>chatCalls,submit:()=>events.submit({preventDefault(){}}),stop:()=>action.events.click({preventDefault(){}})};
}
const flush=async()=>{for(let i=0;i<10;i++)await Promise.resolve()};
test('double send while config loads produces one chat request',async()=>{
 const page=setup();
 const first=page.submit();const second=page.submit();
 page.configuration.splice(0).forEach(resolve=>resolve(true));
 await Promise.all([first,second]);
 assert.equal(page.calls(),1);
 assert.equal(page.message.disabled,false);
 assert.equal(page.message.value,'');
});
test('unconfigured send unlocks and keeps draft for retry',async()=>{
 const page=setup();const failed=page.submit();
 page.configuration.splice(0).forEach(resolve=>resolve(false));
 await failed;
 assert.equal(page.calls(),0);
 assert.equal(page.message.disabled,false);
 assert.equal(page.message.value,'Hi');
 assert.equal(page.empty.hidden,true);
 const retry=page.submit();
 page.configuration.splice(0).forEach(resolve=>resolve(true));
 await retry;
 assert.equal(page.calls(),1);
});
test('double send after config loads produces one chat request',async()=>{
 const page=setup();page.configuration.splice(0).forEach(resolve=>resolve(true));await flush();
 await Promise.all([page.submit(),page.submit()]);
 assert.equal(page.calls(),1);
});

test('provider errors restore the draft and remove pending bubbles',async()=>{
 const page=setup({replyError:true});page.configuration.splice(0).forEach(resolve=>resolve(true));await flush();
 await page.submit();assert.equal(page.message.value,'Hi');assert.equal(page.message.disabled,false);
 assert.match(page.error.textContent,/quota/);assert.equal(page.messages.children.length,0);
});
test('Stop aborts the request and ignores its late response',async()=>{
 const page=setup({deferChat:true});page.configuration.splice(0).forEach(resolve=>resolve(true));await flush();
 const send=page.submit();await flush();assert.equal(page.calls(),1);assert.equal(page.message.value,'');
 page.stop();assert.equal(page.signal().aborted,true);assert.equal(page.message.disabled,false);
 page.replies[0]({ok:true,json:async()=>({text:'Late response'})});await send;
 assert.deepEqual(page.messages.children.map(n=>n.textContent),['Hi','Response stopped']);
 const saved=JSON.parse(page.storage.get('dua-chat-v1'));assert.equal(saved.messages[1].state,'stopped');
});
test('Stop while configuration loads never sends a provider request',async()=>{
 const page=setup();const send=page.submit();page.stop();
 page.configuration.splice(0).forEach(resolve=>resolve(true));await send;
 assert.equal(page.calls(),0);assert.equal(page.message.value,'Hi');assert.equal(page.message.disabled,false);
});

test('attachments are sent once and clear only after a successful reply',async()=>{
 const files=[{name:'notes.txt',mimeType:'text/plain',data:'aGk='}];
 const page=setup({files});page.configuration.splice(0).forEach(resolve=>resolve(true));await flush();
 await page.submit();assert.equal(page.calls(),1);assert.deepEqual(page.payload().attachments,files);
 assert.match(page.messages.children[0].textContent,/notes.txt/);
 assert.equal(page.storage.has('dua-attachments'),false);
});
test('failed attachment request keeps file selection and draft for retry',async()=>{
 const files=[{name:'notes.txt',mimeType:'text/plain',data:'aGk='}];
 const page=setup({files,replyError:true});page.configuration.splice(0).forEach(resolve=>resolve(true));await flush();
 await page.submit();assert.equal(page.message.value,'Hi');assert.deepEqual(JSON.parse(page.storage.get('dua-attachments')),files);
});

test('provider MIME restrictions reject mixed files before draft, history or pending UI changes',async()=>{
 const files=[{name:'notes.txt',mimeType:'text/plain',data:'aGk='},{name:'paper.pdf',mimeType:'application/pdf',data:'JVBERi0='}];
 const page=setup({model:'ChatGPT',files});page.configuration.splice(0).forEach(resolve=>resolve({providers:[{name:'ChatGPT',configured:true,supportsAttachments:true,attachmentTypes:['image/jpeg','image/png','image/webp','text/plain']}]}));await flush();
 page.storage.set('dua-chat-v1','saved history');const fileCopy=page.fileCopy.textContent;
 await page.submit();assert.equal(page.calls(),0);assert.equal(page.message.value,'Hi');assert.equal(Boolean(page.message.disabled),false);assert.equal(page.messages.children.length,0);assert.equal(page.empty.hidden,false);
 assert.equal(page.storage.get('dua-chat-v1'),'saved history');assert.deepEqual(JSON.parse(page.storage.get('dua-attachments')),files);assert.equal(page.fileCard.hidden,false);assert.equal(page.fileCopy.textContent,fileCopy);
 assert.match(page.error.textContent,/ChatGPT supports \.jpg\/\.jpeg, \.png, \.webp, \.txt attachments/);
});
test('capabilities are loaded before an unsupported file can change pending UI',async()=>{
 const files=[{name:'photo.webp',mimeType:'image/webp',data:'UklGRg=='}];const page=setup({model:'Kimi',files});page.storage.set('dua-chat-v1','saved history');
 const send=page.submit();assert.equal(Boolean(page.message.disabled),false);assert.equal(page.message.value,'Hi');assert.equal(page.messages.children.length,0);
 page.configuration.splice(0).forEach(resolve=>resolve({providers:[{name:'Kimi',configured:true,supportsAttachments:true,attachmentTypes:['image/jpeg','image/png','text/plain']}]}));await send;
 assert.equal(page.calls(),0);assert.equal(page.storage.get('dua-chat-v1'),'saved history');assert.deepEqual(JSON.parse(page.storage.get('dua-attachments')),files);assert.match(page.error.textContent,/Kimi supports \.jpg\/\.jpeg, \.png, \.txt attachments/);
});
test('provider exact MIME lists permit supported files and explicitly empty or disabled lists reject files',async()=>{
 const file={name:'paper.pdf',mimeType:'application/pdf',data:'JVBERi0='};
 const page=setup({model:'Claude',files:[file]});page.configuration.splice(0).forEach(resolve=>resolve({providers:[{name:'Claude',configured:true,supportsAttachments:true,attachmentTypes:['application/pdf']}]}));await flush();await page.submit();
 assert.equal(page.calls(),1);assert.deepEqual(page.payload().attachments,[file]);
 for(const capability of [{supportsAttachments:true,attachmentTypes:[]},{supportsAttachments:false,attachmentTypes:['application/pdf']}]){
  const blocked=setup({model:'Claude',files:[file]});blocked.configuration.splice(0).forEach(resolve=>resolve({providers:[{name:'Claude',configured:true,...capability}]}));await flush();await blocked.submit();
  assert.equal(blocked.calls(),0);assert.equal(blocked.message.value,'Hi');assert.deepEqual(JSON.parse(blocked.storage.get('dua-attachments')),[file]);assert.match(blocked.error.textContent,/Claude does not support attachments/);
 }
});
test('provider gating uses exact MIME values rather than filenames or MIME families',async()=>{
 for(const mimeType of ['image/png','IMAGE/JPEG','image/*']){
  const file={name:'photo.jpg',mimeType,data:'aGk='};const page=setup({model:'ChatGPT',files:[file]});page.configuration.splice(0).forEach(resolve=>resolve({providers:[{name:'ChatGPT',configured:true,supportsAttachments:true,attachmentTypes:['image/jpeg']}]}));await flush();await page.submit();
  assert.equal(page.calls(),0);assert.deepEqual(JSON.parse(page.storage.get('dua-attachments')),[file]);assert.match(page.error.textContent,/ChatGPT supports \.jpg\/\.jpeg attachments/);
 }
});
test('unknown and malformed capabilities are ignored without enabling legacy attachment fallback',async()=>{
 const file={name:'photo.png',mimeType:'image/png',data:'aGk='};
 for(const attachmentTypes of [null,'image/png',3,true,{},['application/octet-stream','image/*','IMAGE/PNG',null,3]]){
  const page=setup({files:[file]});page.configuration.splice(0).forEach(resolve=>resolve({providers:[{name:'Dua',configured:true,supportsAttachments:true,attachmentTypes}]}));await flush();await page.submit();
  assert.equal(page.calls(),0);assert.deepEqual(JSON.parse(page.storage.get('dua-attachments')),[file]);assert.match(page.error.textContent,/Dua does not support attachments/);
 }
 const page=setup({model:'ChatGPT',files:[{name:'paper.pdf',mimeType:'application/pdf',data:'JVBERi0='}]});page.configuration.splice(0).forEach(resolve=>resolve({providers:[{name:'ChatGPT',configured:true,supportsAttachments:true,attachmentTypes:['application/octet-stream','image/png','image/png',null]}]}));await flush();await page.submit();
 assert.equal(page.calls(),0);assert.match(page.error.textContent,/ChatGPT supports \.png attachments/);assert.doesNotMatch(page.error.textContent,/application\/octet-stream/);
});
test('missing MIME metadata preserves Gemini files and limits other attachment providers to TXT',async()=>{
 for(const model of ['Dua','Gemini','ChatGPT','Claude','Grok','Kimi']){
  for(const mimeType of ['image/jpeg','image/png','image/webp','application/pdf','text/plain']){
   const file={name:'selected-file',mimeType,data:'aGk='};const page=setup({model,files:[file]});page.configuration.splice(0).forEach(resolve=>resolve({providers:[{name:model,configured:true,supportsAttachments:true}]}));await flush();await page.submit();
   const supported=['Dua','Gemini'].includes(model)||mimeType==='text/plain';assert.equal(page.calls(),supported?1:0,model+' '+mimeType);
   if(!supported){assert.match(page.error.textContent,new RegExp(model+' supports \\.txt attachments'));assert.deepEqual(JSON.parse(page.storage.get('dua-attachments')),[file]);}
  }
 }
});

test('reviewed voice appends to composer once without sending an API request',()=>{
 const page=setup({voiceDraft:'नमस्ते Dua'});
 assert.equal(page.message.value,'Hi\nनमस्ते Dua');assert.equal(page.calls(),0);
 assert.equal(page.storage.has('dua-voice-draft'),false);
});
test('oversize voice handoff preserves the existing draft and pending transcript',()=>{
 const page=setup({voiceDraft:'x'.repeat(8000)});
 assert.equal(page.message.value,'Hi');assert.equal(page.calls(),0);
 assert.equal(page.storage.has('dua-voice-draft'),true);assert.match(page.error.textContent,/8000/);
});

test('read-aloud controls never enter saved history or a later API request',async()=>{
 const page=setup({speechSupported:true});page.configuration.splice(0).forEach(resolve=>resolve(true));await flush();
 await page.submit();assert.equal(page.synthesis.spoken.length,0);
 const reply=page.messages.children[1],read=reply.children[0].children[2];read.events.click();
 assert.equal(page.synthesis.spoken[0].text,'Hello');
 const snapshot=JSON.parse(page.storage.get('dua-chat-v1'));assert.equal(snapshot.messages[1].text,'Hello');
 page.message.value='Next question';await page.submit();
 assert.equal(page.payload().messages[1].text,'Hello');assert.ok(!page.payload().messages.some(message=>message.text.includes('Read aloud')));
});

test('deletion in another tab stops pending work and exits without resaving deleted chats',async()=>{
 const page=setup({deferChat:true});page.configuration.splice(0).forEach(resolve=>resolve(true));await flush();
 const sending=page.submit();await flush();const snapshot=page.storage.get('dua-chat-v1');
 page.windowEvents.storage({key:'dua-history-v2',oldValue:JSON.stringify({generation:'old'}),newValue:JSON.stringify({generation:'new',chats:[]})});
 assert.equal(page.signal().aborted,true);assert.equal(page.redirected(),'index.html');assert.equal(page.storage.get('dua-chat-v1'),snapshot);
 page.replies[0]({ok:true,json:async()=>({text:'Late'})});await sending;
});

test('copy icon writes only response text and three actions have accessible labels',async()=>{
 const p=setup({speechSupported:true});p.configuration.splice(0).forEach(r=>r(true));await flush();await p.submit();
 const buttons=p.messages.children[1].children[0].children;
 assert.deepEqual(buttons.map(b=>b.attributes['aria-label']),['Copy response','Retry response','Speak response']);
 await buttons[0].events.click();assert.equal(p.storage.get('copied'),'Hello');
});
test('retry replaces only latest reply, sends prompt once and preserves the draft',async()=>{
 const p=setup();p.configuration.splice(0).forEach(r=>r(true));await flush();await p.submit();p.message.value='Unsent draft';
 await p.messages.children[1].children[0].children[1].events.click();await flush();
 assert.equal(p.calls(),2);assert.equal(p.messages.children.length,2);assert.equal(p.payload().messages.length,1);assert.equal(p.payload().messages[0].text,'Hi');assert.equal(p.message.value,'Unsent draft');
});
test('stopped regeneration keeps original reply and ignores late completion',async()=>{
 const p=setup({deferChat:true});p.configuration.splice(0).forEach(r=>r(true));await flush();const send=p.submit();await flush();p.replies.shift()({ok:true,json:async()=>({text:'Original'})});await send;
 p.messages.children[1].children[0].children[1].events.click();await flush();p.stop();p.replies.shift()({ok:true,json:async()=>({text:'Late'})});await flush();
 assert.equal(p.messages.children[1].dataset.messageText,'Original');assert.equal(p.message.disabled,false);
});
test('file-backed retry requests reattachment instead of silently sending text only',async()=>{
 const p=setup({files:[{name:'note.txt',mimeType:'text/plain',data:'SGk='}]});p.configuration.splice(0).forEach(r=>r(true));await flush();await p.submit();
 p.messages.children[1].children[0].children[1].events.click();await flush();assert.equal(p.calls(),1);assert.match(p.error.textContent,/Reattach/);
});

test('failed regeneration retains the original reply and unsent draft',async()=>{
 const p=setup({deferChat:true});p.configuration.splice(0).forEach(r=>r(true));await flush();const send=p.submit();await flush();p.replies.shift()({ok:true,json:async()=>({text:'Original'})});await send;p.message.value='Draft';
 p.messages.children[1].children[0].children[1].events.click();await flush();p.replies.shift()({ok:false,json:async()=>({error:'Quota reached'})});await flush();
 assert.equal(p.messages.children[1].dataset.messageText,'Original');assert.equal(p.message.value,'Draft');assert.match(p.error.textContent,/Original response kept/);
});

const webConfig={geminiConfigured:true,web:{searchConfigured:true,pageReadingAvailable:true}};
test('web defaults Off even with configured search or a stored mode and is omitted from requests and saved chats',async()=>{
 const page=setup({savedChat:{model:'Dua',draft:'Hi',web:'search',messages:[]}});
 page.configuration.splice(0).forEach(resolve=>resolve(webConfig));await flush();
 assert.equal(page.webMode.value,'off');await page.submit();assert.equal(Object.hasOwn(page.payload(),'web'),false);
 const snapshot=JSON.parse(page.storage.get('dua-chat-v1'));assert.equal(Object.hasOwn(snapshot,'web'),false);
 assert.ok(snapshot.messages.every(item=>!Object.hasOwn(item,'web')));
 assert.match(page.webHelp.textContent,/each time this page opens/);
});
test('only opt-in Search and Read link requests include their exact mode, and choosing Off removes it again',async()=>{
 for(const mode of ['search','page']){
  const page=setup();page.configuration.splice(0).forEach(resolve=>resolve(webConfig));await flush();
  page.chooseWeb(mode);page.message.value='Read https://example.com/first and https://example.com/second';await page.submit();
  assert.equal(page.payload().web,mode);assert.equal(page.payload().messages.at(-1).text,'Read https://example.com/first and https://example.com/second');
  page.chooseWeb('off');page.message.value='Continue';await page.submit();assert.equal(Object.hasOwn(page.payload(),'web'),false);
 }
});
test('Search without a configured key is disabled and preserves the draft and files when requested',async()=>{
 const files=[{name:'notes.txt',mimeType:'text/plain',data:'aGk='}],page=setup({files});
 page.configuration.splice(0).forEach(resolve=>resolve({geminiConfigured:true,web:{searchConfigured:false,pageReadingAvailable:true}}));await flush();
 const option=page.webMode.querySelector('option[value="search"]');assert.equal(option.disabled,true);assert.match(option.textContent,/setup needed/);assert.match(page.webHelp.textContent,/Brave Search API key/);
 page.chooseWeb('search');await page.submit();assert.equal(page.calls(),0);assert.equal(page.message.value,'Hi');assert.equal(page.message.disabled,false);
 assert.deepEqual(JSON.parse(page.storage.get('dua-attachments')),files);assert.equal(page.messages.children.length,0);assert.match(page.error.textContent,/Choose Web: Off or Read link/);
});
test('Read link stays disabled unless configuration explicitly advertises support',async()=>{
 for(const web of [undefined,null,true,{}, {pageReadingAvailable:false}, {pageReadingAvailable:'true'}, {pageReadingAvailable:1}]){
  const page=setup();assert.equal(page.webMode.querySelector('option[value="page"]').disabled,true);
  page.configuration.splice(0).forEach(resolve=>resolve({geminiConfigured:true,...(web===undefined?{}:{web})}));await flush();
  assert.equal(page.webMode.querySelector('option[value="page"]').disabled,true);assert.match(page.webHelp.textContent,/Read link is unavailable/);
  page.chooseWeb('page');page.message.value='Read https://example.com/page';await page.submit();
  assert.equal(page.calls(),0);assert.equal(page.message.value,'Read https://example.com/page');assert.match(page.error.textContent,/Reading links is unavailable/);
 }
});
test('Read link requires a valid first HTTPS URL and preserves drafts before any request',async()=>{
 for(const draft of ['No link here','Read http://example.com','Read https://user:secret@example.com/first then https://example.com/second']){
  const page=setup();page.configuration.splice(0).forEach(resolve=>resolve(webConfig));await flush();page.chooseWeb('page');page.message.value=draft;
  await page.submit();assert.equal(page.calls(),0);assert.equal(page.message.value,draft);assert.equal(page.messages.children.length,0);assert.match(page.error.textContent,/Read link needs a valid HTTPS link/);
 }
});
test('web retrieval errors restore draft and keep file selection without pending message UI',async()=>{
 for(const mode of ['search','page']){
  const files=[{name:'notes.txt',mimeType:'text/plain',data:'aGk='}],page=setup({files,deferChat:true});
  page.configuration.splice(0).forEach(resolve=>resolve(webConfig));await flush();page.chooseWeb(mode);page.message.value='Read https://example.com/page';
  const send=page.submit();await flush();page.replies.shift()({ok:false,json:async()=>({error:'Web context could not be retrieved.'})});await send;
  assert.equal(page.payload().web,mode);assert.equal(page.message.value,'Read https://example.com/page');assert.equal(page.message.disabled,false);assert.equal(page.webMode.disabled,false);
  assert.deepEqual(JSON.parse(page.storage.get('dua-attachments')),files);assert.equal(page.messages.children.length,0);assert.match(page.error.textContent,/Web context could not be retrieved/);
 }
});
test('retries retain the original request web mode when the composer mode changes',async()=>{
 for(const originalMode of ['off','search','page']){
  const page=setup();page.configuration.splice(0).forEach(resolve=>resolve(webConfig));await flush();page.chooseWeb(originalMode);page.message.value='Read https://example.com/page';await page.submit();
  page.chooseWeb(originalMode==='search'?'page':'search');page.message.value='Unsent draft';
  await page.messages.children[1].querySelectorAll('.message-action')[1].events.click();await flush();
  assert.equal(page.calls(),2);assert.equal(page.payload().web,originalMode==='off'?undefined:originalMode);assert.equal(page.message.value,'Unsent draft');assert.equal(page.payload().messages.length,1);
 }
});
test('reloaded replies retry with Off and explain that the original web mode was not retained',async()=>{
 const page=setup({savedChat:{model:'Dua',draft:'Draft',web:'search',messages:[{role:'user',text:'Question',state:'complete'},{role:'model',text:'Original',state:'complete'}]}});
 page.configuration.splice(0).forEach(resolve=>resolve(webConfig));await flush();page.chooseWeb('search');
 const retry=page.messages.children[1].querySelectorAll('.message-action')[1];assert.match(retry.title,/Web: Off/);assert.match(retry.title,/after reload/);
 await retry.events.click();await flush();assert.equal(Object.hasOwn(page.payload(),'web'),false);assert.equal(page.message.value,'Draft');
});
test('Stop aborts web requests, unlocks the selector and ignores late source-bearing responses',async()=>{
 for(const mode of ['search','page']){
  const page=setup({deferChat:true});page.configuration.splice(0).forEach(resolve=>resolve(webConfig));await flush();page.chooseWeb(mode);page.message.value='Read https://example.com/page';
  const send=page.submit();await flush();assert.equal(page.webMode.disabled,true);page.stop();assert.equal(page.signal().aborted,true);assert.equal(page.webMode.disabled,false);
  page.replies.shift()({ok:true,json:async()=>({text:'Late\n\nSources:\n[1] Example\nhttps://example.com/page'})});await send;
  assert.equal(page.messages.children[1].dataset.messageText,'Response stopped');assert.equal(page.messages.children[1].querySelectorAll('.source-link').length,0);
  assert.equal(JSON.parse(page.storage.get('dua-chat-v1')).messages[1].state,'stopped');
 }
});
test('model HTTPS links use safe DOM anchors while HTML, non-HTTPS links and userinfo remain text',async()=>{
 const replyText='Literal <img src=x onerror=alert(1)> and javascript:alert(1), http://example.com/insecure.\n\nSources:\n[1] <script>alert(1)</script>\nhttps://example.com/page?x=1&y=2\n[2] Signed in\nhttps://user:secret@example.com/private\n[3] Markdown (https://example.com/other).';
 const page=setup({replyText,speechSupported:true});page.configuration.splice(0).forEach(resolve=>resolve(webConfig));await flush();await page.submit();
 const bubble=page.messages.children[1],links=bubble.querySelectorAll('.source-link');
 assert.deepEqual(links.map(link=>link.attributes.href),['https://example.com/page?x=1&y=2','https://example.com/other']);
 for(const link of links){assert.equal(link.tagName,'A');assert.equal(link.attributes.target,'_blank');assert.equal(link.attributes.rel,'noopener noreferrer');assert.equal(link.innerHTML,undefined);}
 assert.equal(bubble.dataset.messageText,replyText);assert.equal(bubble.textContent,replyText);assert.ok(bubble.children.every(child=>['SPAN','A','DIV'].includes(child.tagName)));
 const actions=bubble.querySelectorAll('.message-action');await actions[0].events.click();assert.equal(page.storage.get('copied'),replyText);actions[2].events.click();assert.match(page.synthesis.spoken[0].text,/Literal/);
 const snapshot=JSON.parse(page.storage.get('dua-chat-v1'));assert.equal(snapshot.messages[1].text,replyText);
 page.message.value='Next question';await page.submit();assert.equal(page.payload().messages[1].text,replyText);assert.ok(!page.payload().messages[1].text.includes('Copy response'));
});
test('nested schemes and malformed URL tokens stay text while wrapped and canonical source URLs preserve their ranges',async()=>{
 const unsafe=["javascript:alert('https://evil.com')",'data:text/plain,https://evil.com','myhttps://evil.com','https:///missing-host',"https://user:'https://evil.com'@example.com",'https://user:https://evil.com@example.com','http://user:https://evil.com@example.com','https://example.com\\@evil.com','https://@example.com'];
 const replyText=unsafe.join('\n')+'\nRead (https://example.com/wiki/Topic_(detail)). See [source](https://example.com/report).\n\nSources:\n[1] Exact punctuation\nhttps://example.com/report.\n[2] Query punctuation\nhttps://example.com/path_(v2)?item=done!';
 const page=setup({replyText});page.configuration.splice(0).forEach(resolve=>resolve(webConfig));await flush();await page.submit();
 const bubble=page.messages.children[1],links=bubble.querySelectorAll('.source-link');
 assert.deepEqual(links.map(link=>link.attributes.href),['https://example.com/wiki/Topic_(detail)','https://example.com/report','https://example.com/report.','https://example.com/path_(v2)?item=done!']);
 assert.deepEqual(links.map(link=>link.textContent),['https://example.com/wiki/Topic_(detail)','https://example.com/report','https://example.com/report.','https://example.com/path_(v2)?item=done!']);
 assert.equal(bubble.textContent,replyText);assert.equal(bubble.dataset.messageText,replyText);
});
test('restoring history renders safe source links and keeps the canonical footer available for copy',async()=>{
 const replyText='Saved answer\n\nSources:\n[1] Example\nhttps://example.com/page';
 const page=setup({savedChat:{model:'Dua',draft:'Draft',messages:[{role:'user',text:'Question',state:'complete'},{role:'model',text:replyText,state:'complete'}]}});
 const bubble=page.messages.children[1];assert.equal(bubble.querySelectorAll('.source-link')[0].attributes.href,'https://example.com/page');
 await bubble.querySelectorAll('.message-action')[0].events.click();assert.equal(page.storage.get('copied'),replyText);assert.equal(bubble.dataset.messageText,replyText);
});
