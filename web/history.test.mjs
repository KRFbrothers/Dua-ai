import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';
const source=await readFile(new URL('./dist/chat-history.js',import.meta.url),'utf8');
function storage(){const data=new Map();return {getItem:key=>data.get(key)||null,setItem:(key,value)=>data.set(key,value),removeItem:key=>data.delete(key)};}
function open(localStorage=storage(),sessionStorage=storage()){
 const context={TextEncoder,localStorage,sessionStorage,crypto:{randomUUID:()=>Math.random().toString(36).slice(2)}};
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

test('export preserves all local messages/drafts and does not mutate stored history',()=>{
 const page=open();page.api.save(first);page.api.newChat();page.api.save({draft:'Draft only 😀',model:'Dua',messages:[]});
 const original=page.localStorage.getItem('dua-history-v2');const data=JSON.parse(page.api.exportJson());
 assert.equal(data.format,'dua-chat-export');assert.equal(data.version,1);assert.equal(data.chats.length,2);
 assert.ok(data.chats.some(chat=>chat.draft==='अधूरा'&&chat.messages[0].text==='नमस्ते'));
 assert.ok(!data.chats.some(chat=>'attachments' in chat));assert.equal(page.localStorage.getItem('dua-history-v2'),original);
});
test('clear all removes chats and temporary selections and prevents legacy migration',()=>{
 const page=open();page.api.save(first);
 for(const key of ['dua-chat-v1','dua-attachments','dua-voice-draft'])page.sessionStorage.setItem(key,JSON.stringify(first));
 page.api.clearAll();assert.equal(open(page.localStorage,page.sessionStorage).api.list().length,0);
 for(const key of ['dua-chat-v1','dua-attachments','dua-voice-draft'])assert.equal(page.sessionStorage.getItem(key),null);
 page.api.save({model:'Dua',draft:'New',messages:[]});assert.equal(page.api.list().length,1);
});
test('another tab cannot resurrect history after clear all, until explicitly reloaded',()=>{
 const firstTab=open();const id=firstTab.api.save(first);const secondTab=open(firstTab.localStorage,storage());secondTab.api.load(id);
 firstTab.api.clearAll();assert.throws(()=>secondTab.api.save(first),/cleared in another tab/);assert.equal(firstTab.api.list().length,0);
 const fresh=open(firstTab.localStorage,storage());fresh.api.load();fresh.api.save({model:'Dua',draft:'New',messages:[]});assert.equal(fresh.api.list().length,1);
});
test('failed deletion leaves existing history and pending selections untouched',()=>{
 const page=open();page.api.save(first);page.sessionStorage.setItem('dua-attachments','retain');
 const original=page.localStorage.getItem('dua-history-v2');page.localStorage.setItem=()=>{throw Error('Blocked')};
 assert.throws(()=>page.api.clearAll(),/Blocked/);assert.equal(page.localStorage.getItem('dua-history-v2'),original);assert.equal(page.sessionStorage.getItem('dua-attachments'),'retain');
});
test('confirmed clear all can reset unreadable stored data',()=>{
 const page=open();page.localStorage.setItem('dua-history-v2','broken');page.api.clearAll();assert.equal(page.api.list().length,0);
});

function exported(chat=first){return JSON.stringify({format:'dua-chat-export',version:1,exportedAt:123,chats:[{...chat,id:'source',updatedAt:0}]});}
test('import previews without writes, restores export as copies and keeps current draft and active chat',()=>{
 const page=open();const active=page.api.save(first);const original=page.localStorage.getItem('dua-history-v2');
 assert.equal(page.api.previewImport(exported()),1);assert.equal(page.localStorage.getItem('dua-history-v2'),original);
 assert.equal(page.api.importJson(exported()),1);const records=page.api.list();assert.equal(records.length,2);
 const copy=records.find(chat=>chat.id!==active);assert.notEqual(copy.id,'source');assert.equal(copy.draft,'अधूरा');assert.equal(copy.messages[0].text,'नमस्ते');
 assert.equal(JSON.parse(page.localStorage.getItem('dua-history-v2')).activeId,active);
 page.api.save({...first,draft:'Existing still editable'});assert.equal(page.api.list().length,2);
 page.api.importJson(exported());assert.equal(page.api.list().length,3);assert.equal(new Set(page.api.list().map(c=>c.id)).size,3);
});
test('web can restore native canonical exports, preserve stopped states and reload imported history',()=>{
 const page=open();const raw=exported({model:'Dua',draft:'Draft 😀',messages:[{role:'user',text:'<img onerror=alert(1)>',state:'complete'},{role:'model',text:'Response stopped',state:'stopped'}]});
 page.api.importJson('\uFEFF'+raw);const copy=open(page.localStorage).api.list()[0];
 assert.equal(copy.updatedAt,0);assert.equal(copy.messages[1].state,'stopped');assert.equal(copy.messages[0].text,'<img onerror=alert(1)>');assert.equal(copy.draft,'Draft 😀');
});
test('import rejects wrong formats, versions, roles, model, types, limits and duplicate ids without any writes',()=>{
 const page=open();page.api.save(first);const before=page.localStorage.getItem('dua-history-v2');
 const valid=JSON.parse(exported());
 const cases=['broken','null',JSON.stringify({...valid,version:2}),JSON.stringify({...valid,version:'1'}),JSON.stringify({...valid,format:'other'}),JSON.stringify({...valid,exportedAt:1.5}),JSON.stringify({...valid,chats:Array(101).fill(valid.chats[0])})];
 for(const patch of [{draft:4},{draft:'x'.repeat(8001)},{model:'Unknown'},{updatedAt:-1},{id:''},{messages:[{role:'system',text:'Hi',state:'complete'}]},{messages:[{role:'model',text:'Hi',state:'pending'}]},{messages:[{role:'user',text:null,state:'complete'}]},{messages:Array(501).fill({role:'user',text:'Hi',state:'complete'})},{messages:[{role:'model',text:'x'.repeat(1048577),state:'complete'}]}])cases.push(JSON.stringify({...valid,chats:[{...valid.chats[0],...patch}]}));
 cases.push(JSON.stringify({...valid,chats:[valid.chats[0],valid.chats[0]]}));cases.push('x'.repeat(page.api.maxImportBytes+1));cases.push('['.repeat(65)+'0'+']'.repeat(65));
 for(const raw of cases){assert.throws(()=>page.api.importJson(raw));assert.equal(page.localStorage.getItem('dua-history-v2'),before);}
});
test('import failure is atomic, empty exports do nothing, and confirmed import preserves concurrent tab changes',()=>{
 const page=open();page.api.save(first);const original=page.localStorage.getItem('dua-history-v2');const setter=page.localStorage.setItem;
 page.localStorage.setItem=()=>{throw Error('Full')};assert.throws(()=>page.api.importJson(exported()),/Full/);assert.equal(page.localStorage.getItem('dua-history-v2'),original);
 page.localStorage.setItem=setter;assert.equal(page.api.importJson(JSON.stringify({format:'dua-chat-export',version:1,exportedAt:0,chats:[]})),0);assert.equal(page.localStorage.getItem('dua-history-v2'),original);
 page.api.previewImport(exported());const other=open(page.localStorage);other.api.newChat();other.api.save({model:'Dua',draft:'Other tab',messages:[]});
 page.api.importJson(exported());assert.equal(page.api.list().length,3);assert.ok(page.api.list().some(c=>c.draft==='Other tab'));
});

test('all configured provider names survive save and export/import',()=>{const page=open();for(const model of ['Dua','Gemini','ChatGPT','Claude','Grok','Kimi']){page.api.newChat();page.api.save({...first,model});}const raw=page.api.exportJson(),restored=open();restored.api.importJson(raw);assert.deepEqual(Array.from(new Set(restored.api.list().map(chat=>chat.model))).sort(),['Claude','Dua','Gemini','Grok','Kimi','ChatGPT'].sort());});
