import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';
const source=await readFile(new URL('./dist/chat-history.js',import.meta.url),'utf8');
function storage(){const data=new Map();return {getItem:key=>data.get(key)||null,setItem:(key,value)=>data.set(key,value),removeItem:key=>data.delete(key)};}
function open(localStorage=storage(),sessionStorage=storage()){
 const context={localStorage,sessionStorage,crypto:{randomUUID:()=>Math.random().toString(36).slice(2)}};
 vm.runInNewContext(source,context);return {api:context.DuaHistory,localStorage,sessionStorage};
}
const first={model:'Gemini',draft:'अधूरा',messages:[{role:'user',text:'नमस्ते',state:'complete'},{role:'model',text:'Hello',state:'complete'}]};
test('multiple chats survive a fresh page and restore their own draft/model/messages',()=>{
 const page=open();const id=page.api.save(first);page.api.newChat();page.api.save({model:'Dua',draft:'Second',messages:[]});
 const reopened=open(page.localStorage,page.sessionStorage);assert.equal(reopened.api.list().length,2);
 const chat=reopened.api.load(id);assert.equal(chat.model,'Gemini');assert.equal(chat.draft,'अधूरा');assert.equal(chat.messages[0].text,'नमस्ते');
});
test('delete removes only the selected chat and survives reload',()=>{
 const page=open();const id=page.api.save(first);page.api.newChat();const second=page.api.save({model:'Dua',draft:'Second',messages:[]});
 page.api.remove(id);const reopened=open(page.localStorage,page.sessionStorage);assert.equal(reopened.api.list().length,1);assert.equal(reopened.api.load(second).draft,'Second');
 reopened.api.remove(second);assert.equal(open(page.localStorage,page.sessionStorage).api.list().length,0);
});
test('legacy chat migrates once and starting new chat preserves it',()=>{
 const page=open();page.sessionStorage.setItem('dua-chat-v1',JSON.stringify(first));page.api.newChat();assert.equal(page.api.list().length,1);
 page.api.save({draft:'new',model:'Dua',messages:[]});assert.equal(open(page.localStorage,page.sessionStorage).api.list().length,2);
});
test('interrupted placeholder is restored as stopped, not a completed provider reply',()=>{
 const page=open();const id=page.api.save({...first,messages:[{role:'model',text:'Dua is thinking…',state:'pending'}]});
 const chat=open(page.localStorage,page.sessionStorage).api.load(id);assert.equal(chat.messages[0].state,'stopped');assert.equal(chat.messages[0].text,'Response stopped');
});
test('corrupt data and full storage surface errors without erasing existing history',()=>{
 const page=open();page.localStorage.setItem('dua-history-v2','broken');assert.throws(()=>page.api.list());assert.equal(page.localStorage.getItem('dua-history-v2'),'broken');
 const good=open();good.api.save(first);const original=good.localStorage.getItem('dua-history-v2');good.localStorage.setItem=()=>{throw Error('Full')};
 assert.throws(()=>good.api.save({...first,draft:'unsaved'}));assert.equal(good.localStorage.getItem('dua-history-v2'),original);
});
