import {test} from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
const source=await readFile(new URL('./dist/data-controls.js',import.meta.url),'utf8');
function setup({fail=false}={}){
 const elements={},windowEvents={};let cleared=0,imported=0,opened=false,download=null;let revoke;
 const node=()=>({textContent:'',events:{},disabled:false,addEventListener(event,handler){this.events[event]=handler},showModal(){this.open=true;opened=true},close(){this.open=false;opened=false;this.events.close?.()},focus(){},click(){download={href:this.href,name:this.download}},remove(){}});
 const api={maxImportBytes:4194304,previewImport(raw){if(raw==='bad')throw Error('Invalid export');return raw==='empty'?0:2},importJson(){if(fail)throw Error('Storage blocked');imported+=2;return 2},list(){return cleared?[]:[{id:'1'}]},clearAll(){if(fail)throw Error('Storage blocked');cleared++},exportJson(){return JSON.stringify({format:'dua-chat-export',version:1,chats:[{draft:'नमस्ते'}]})}};
 vm.runInNewContext(source,{
  document:{querySelector(selector){return elements[selector]||=(node())},createElement:node,body:{append(){}}},
  window:{addEventListener(event,fn){windowEvents[event]=fn}},TextDecoder,TypeError,DuaHistory:api,location:{search:''},URLSearchParams,Date,Blob,
  URL:{createObjectURL(){return 'blob:download'},revokeObjectURL(){}},setTimeout(fn){revoke=fn}
 });
 return {elements,api,windowEvents,imported:()=>imported,change:async file=>{elements["#import-file"].files=[file];await elements["#import-file"].events.change()},cleared:()=>cleared,opened:()=>opened,download:()=>download,click:selector=>elements[selector].events.click()};
}
test('opening and cancelling clear confirmation never deletes history',()=>{
 const page=setup();page.click('#clear-chats');assert.equal(page.opened(),true);assert.equal(page.cleared(),0);
 page.click('#cancel-clear');assert.equal(page.opened(),false);assert.equal(page.cleared(),0);
});
test('confirm performs real local deletion and storage failures keep the dialog open',()=>{
 const page=setup();page.click('#clear-chats');page.click('#confirm-clear');assert.equal(page.cleared(),1);assert.equal(page.opened(),false);assert.match(page.elements['.status'].textContent,/cleared/);
 const blocked=setup({fail:true});blocked.click('#clear-chats');blocked.click('#confirm-clear');assert.equal(blocked.cleared(),0);assert.equal(blocked.opened(),true);assert.match(blocked.elements['#delete-error'].textContent,/Storage blocked/);
});
test('export downloads a named JSON document without deleting history',()=>{
 const page=setup();page.click('#export-chats');assert.match(page.download().name,/^dua-chats-\d{4}-\d{2}-\d{2}\.json$/);assert.equal(page.download().href,'blob:download');assert.equal(page.cleared(),0);
});

const file=raw=>({size:new TextEncoder().encode(raw).length,arrayBuffer:async()=>new TextEncoder().encode(raw).buffer});
test('import waits for explicit confirmation and cancel retains all chats',async()=>{
 const page=setup();await page.change(file('good'));assert.equal(page.imported(),0);assert.equal(page.elements['#import-confirm'].open,true);assert.match(page.elements['#import-summary'].textContent,/2 chats/);
 page.click('#cancel-import');page.click('#confirm-import');assert.equal(page.imported(),0);assert.equal(page.elements['#import-file'].value,'');
 await page.change(file('good'));page.click('#confirm-import');assert.equal(page.imported(),2);assert.equal(page.cleared(),0);assert.equal(page.elements['#import-confirm'].open,false);
});
test('invalid, oversized and malformed UTF-8 files cannot reach confirmation',async()=>{
 const page=setup();for(const candidate of [file('bad'),{size:4194305}, {size:1,arrayBuffer:async()=>new Uint8Array([255]).buffer},file('empty')]){
  await page.change(candidate);assert.notEqual(page.elements['#import-confirm'].open,true);assert.equal(page.imported(),0);assert.equal(page.elements['#import-chats'].disabled,false);
 }
});
test('failed import retains the checked file for retry and never clears history',async()=>{
 const page=setup({fail:true});await page.change(file('good'));page.click('#confirm-import');assert.equal(page.elements['#import-confirm'].open,true);assert.match(page.elements['#import-error'].textContent,/Storage blocked/);assert.equal(page.imported(),0);assert.equal(page.cleared(),0);
});

test('leaving during file read discards late results and releases disabled controls',async()=>{
 const page=setup();let finish;const read=page.change({size:4,arrayBuffer:()=>new Promise(resolve=>finish=resolve)});
 assert.equal(page.elements['#import-chats'].disabled,true);page.windowEvents.pagehide();finish(new TextEncoder().encode('good').buffer);await read;
 assert.equal(page.elements['#import-chats'].disabled,false);assert.equal(page.elements['#import-confirm'].open,false);assert.equal(page.imported(),0);
});
