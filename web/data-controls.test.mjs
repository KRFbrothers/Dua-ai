import {test} from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
const source=await readFile(new URL('./dist/data-controls.js',import.meta.url),'utf8');
function setup({fail=false}={}){
 const elements={};let cleared=0,opened=false,download=null;let revoke;
 const node=()=>({textContent:'',events:{},disabled:false,addEventListener(event,handler){this.events[event]=handler},showModal(){opened=true},close(){opened=false},focus(){},click(){download={href:this.href,name:this.download}},remove(){}});
 const api={list(){return cleared?[]:[{id:'1'}]},clearAll(){if(fail)throw Error('Storage blocked');cleared++},exportJson(){return JSON.stringify({format:'dua-chat-export',version:1,chats:[{draft:'नमस्ते'}]})}};
 vm.runInNewContext(source,{
  document:{querySelector(selector){return elements[selector]||=(node())},createElement:node,body:{append(){}}},
  DuaHistory:api,location:{search:''},URLSearchParams,Date,Blob,
  URL:{createObjectURL(){return 'blob:download'},revokeObjectURL(){}},setTimeout(fn){revoke=fn}
 });
 return {elements,cleared:()=>cleared,opened:()=>opened,download:()=>download,click:selector=>elements[selector].events.click()};
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
