import {test} from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';

const source=(await readFile(new URL('./dist/chat.html',import.meta.url),'utf8')).split('<script>')[1].split('</script>')[0];
function setup() {
 const events={};const configuration=[];let chatCalls=0;
 const node=()=>({hidden:false,value:'Hi',dataset:{},classList:{toggle(){}},setAttribute(){},removeAttribute(){},toggleAttribute(){},focus(){},addEventListener(){},querySelector(){return node()},querySelectorAll(){return []},append(){},remove(){}});
 const message=node(),action=node(),picker=node(),composer=node(),error=node(),empty=node();
 composer.addEventListener=(type,handler)=>{events[type]=handler};
 const elements={'textarea':message,'#composer-action':action,'.model-picker':picker,'.composer':composer,'.chat-messages':node(),'.chat-error':error,'.empty':empty,'.model-chip-label':node(),'.model-sheet-footer':node()};
 vm.runInNewContext(source,{
  document:{querySelector:s=>elements[s]||node(),createElement:node,addEventListener(){}},
  location:{protocol:'https:'},
  fetch:async url=>{
   if(url==='/api/config')return new Promise(resolve=>configuration.push(configured=>resolve({ok:true,json:async()=>({geminiConfigured:configured})})));
   chatCalls++;
   return {ok:true,json:async()=>({text:'Hello'})};
  }
 });
 return {message,error,empty,configuration,calls:()=>chatCalls,submit:()=>events.submit({preventDefault(){}})};
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
