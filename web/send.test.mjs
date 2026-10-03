import {test} from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';

const source=(await readFile(new URL('./dist/chat.html',import.meta.url),'utf8')).split('<script>')[1].split('</script>')[0];
function setup({deferChat=false,replyError=false}={}) {
 const events={};const configuration=[];const replies=[];let chatCalls=0;let signal;
 const node=()=>{
  const n={hidden:false,value:'',dataset:{},children:[],attributes:{},events:{},style:{setProperty(){}},
   classList:{toggle(){},remove(){},contains(name){return (n.className||'').split(' ').includes(name)}},
   setAttribute(k,v){this.attributes[k]=v},getAttribute(k){return this.attributes[k]},removeAttribute(k){delete this.attributes[k]},toggleAttribute(){},focus(){},
   addEventListener(type,handler){this.events[type]=handler},querySelector(){return node()},querySelectorAll(){return []},
   append(child){this.children.push(child);child.parent=this},remove(){if(this.parent)this.parent.children=this.parent.children.filter(n=>n!==this)}
  };return n;
 };
 const message=node(),action=node(),picker=node(),composer=node(),error=node(),empty=node();message.value='Hi';
 composer.addEventListener=(type,handler)=>{events[type]=handler};
 const elements={'textarea':message,'#composer-action':action,'.model-picker':picker,'.composer':composer,'.chat-messages':node(),'.chat-error':error,'.empty':empty,'.model-chip-label':node(),'.model-sheet-footer':node()};
 const storage=new Map();
 vm.runInNewContext(source,{
  document:{querySelector:s=>elements[s]||(elements[s]=node()),createElement:node,addEventListener(){},documentElement:node()},
  location:{protocol:'https:',search:'',pathname:'/chat.html'},
  window:{addEventListener(){},history:{replaceState(){}}},URLSearchParams,AbortController,
  sessionStorage:{getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)},
  fetch:async (url,options)=>{
   if(url==='/api/config')return new Promise(resolve=>configuration.push(configured=>resolve({ok:true,json:async()=>({geminiConfigured:configured})})));
   chatCalls++;signal=options.signal;
   if(deferChat)return new Promise(resolve=>replies.push(resolve));
   return {ok:!replyError,json:async()=>replyError?{error:'Gemini quota limit aa gayi.'}:{text:'Hello'}};
  }
 });
 return {message,error,empty,configuration,replies,storage,messages:elements['.chat-messages'],signal:()=>signal,
  calls:()=>chatCalls,submit:()=>events.submit({preventDefault(){}}),stop:()=>action.events.click({preventDefault(){}})};
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
