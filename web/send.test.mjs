import {test} from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';

const readAloudSource=await readFile(new URL('./dist/read-aloud.js',import.meta.url),'utf8');
const voiceSource=await readFile(new URL('./dist/voice-input.js',import.meta.url),'utf8');
const source=(await readFile(new URL('./dist/chat.html',import.meta.url),'utf8')).split('<script>')[1].split('</script>')[0];
function setup({deferChat=false,replyError=false,files=[],voiceDraft=null,speechSupported=false}={}) {
 const synthesis={spoken:[],speak(utterance){this.spoken.push(utterance)},cancel(){},getVoices(){return[]}};
 class Utterance{constructor(text){this.text=text;}}
 const windowEvents={};let redirected=null;
 const events={};const configuration=[];const replies=[];let chatCalls=0;let signal;let payload;
 const node=()=>{
  const n={hidden:false,value:'',dataset:{},children:[],attributes:{},events:{},style:{setProperty(){}},
   classList:{toggle(){},remove(){},contains(name){return (n.className||'').split(' ').includes(name)}},
   setAttribute(k,v){this.attributes[k]=v},getAttribute(k){return this.attributes[k]},removeAttribute(k){delete this.attributes[k]},toggleAttribute(){},focus(){},
   addEventListener(type,handler){this.events[type]=handler},querySelector(){return node()},querySelectorAll(selector){return this.children.flatMap(child=>[child,...child.querySelectorAll('*')]).filter(child=>selector==='*'||child.classList.contains(selector.slice(1)))},
   append(child){this.children.push(child);child.parent=this},remove(){if(this.parent)this.parent.children=this.parent.children.filter(n=>n!==this)}
  };
  n.contains=child=>n===child||n.children.some(c=>c.contains(child));
  n.closest=selector=>n.classList.contains(selector.slice(1))?n:n.parent?.closest(selector);
  Object.defineProperties(n,{lastElementChild:{get(){return n.children.at(-1)}},previousElementSibling:{get(){return n.parent?.children[n.parent.children.indexOf(n)-1]}},textContent:{get(){return n.text||''},set(value){n.text=value;n.children=[]}}});
  return n;
 };
 const message=node(),action=node(),picker=node(),composer=node(),error=node(),empty=node();message.value='Hi';
 composer.addEventListener=(type,handler)=>{events[type]=handler};
 const elements={'textarea':message,'#composer-action':action,'.model-picker':picker,'.composer':composer,'.chat-messages':node(),'.chat-error':error,'.empty':empty,'.model-chip-label':node(),'.model-sheet-footer':node()};
 const storage=new Map();if(voiceDraft)storage.set('dua-voice-draft',JSON.stringify({text:voiceDraft}));if(files.length)storage.set('dua-attachments',JSON.stringify(files));
 vm.runInNewContext(readAloudSource+voiceSource+source,{
  document:{querySelectorAll:s=>Object.values(elements).flatMap(n=>[n,...n.querySelectorAll('*')]).filter(n=>n.classList.contains(s.slice(1))),querySelector:s=>elements[s]||(elements[s]=node()),createElement:node,addEventListener(){},documentElement:node()},
  location:{protocol:'https:',search:'',pathname:'/chat.html',replace(url){redirected=url}},
  navigator:{clipboard:{async writeText(text){storage.set('copied',text)}}},window:{speechSynthesis:speechSupported?synthesis:null,SpeechSynthesisUtterance:speechSupported?Utterance:null,navigator:{language:"hi-IN"},addEventListener(event,handler){windowEvents[event]=handler},history:{replaceState(){}}},URLSearchParams,AbortController,setTimeout,clearTimeout,
  DuaHistory:{save(){},load(){return null},newChat(){}},
  sessionStorage:{getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)},
  fetch:async (url,options)=>{
   if(url==='/api/config')return new Promise(resolve=>configuration.push(configured=>resolve({ok:true,json:async()=>({geminiConfigured:configured})})));
   chatCalls++;signal=options.signal;payload=JSON.parse(options.body);
   if(deferChat)return new Promise(resolve=>replies.push(resolve));
   return {ok:!replyError,json:async()=>replyError?{error:'Gemini quota limit aa gayi.'}:{text:'Hello'}};
  }
 });
 return {windowEvents,redirected:()=>redirected,synthesis,message,error,empty,configuration,replies,storage,messages:elements['.chat-messages'],signal:()=>signal,
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
