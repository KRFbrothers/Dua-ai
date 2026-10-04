import {test} from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFileSync} from 'node:fs';

function modules(){const ctx={TextEncoder,setTimeout,clearTimeout};vm.runInNewContext(readFileSync('dist/auto-sync.js','utf8'),ctx);vm.runInNewContext(readFileSync('dist/memory-suggestions.js','utf8'),ctx);return ctx;}
const copy=value=>JSON.parse(JSON.stringify(value));
const chat=(id='one',text='Hi')=>({id,updatedAt:1,model:'Dua',draft:'',messages:[{role:'user',text,state:'complete'}]});
const snapshot=(chats=[],memory=[])=>({format:'dua-cloud-snapshot',version:1,chats,memory});
function fixture({local=snapshot(),cloud=snapshot(),revision=0}={}){
 const ctx=modules(),calls=[],timers=new Map(),saved=[];let here=copy(local),remote=copy(cloud),version=revision,timerId=0;
 const account={state:{configured:true,user:{id:'alpha'}},async load(){calls.push('load');return this.state;},async cloud(){calls.push('cloud');return {ownerId:this.state.user?.id,revision:version,snapshot:copy(remote)};},async upload(expected,next,owner){calls.push(['upload',expected,owner]);assert.equal(owner,this.state.user.id);assert.equal(expected,version);remote=copy(next);return {revision:++version};}};
 const localIO={read:()=>copy(here),async merge(value){calls.push('merge');here=copy(value);}};
 const api=ctx.DuaAutoSync.create({account,local:localIO,persist:state=>saved.push(copy(state)),setTimer:(fn,ms)=>{const id=++timerId;timers.set(id,{fn,ms});return id;},clearTimer:id=>timers.delete(id)});
 return {ctx,api,account,calls,timers,saved,get local(){return here;},set local(value){here=copy(value);},get cloud(){return remote;},set cloud(value){remote=copy(value);},get revision(){return version;},set revision(value){version=value;}};
}
const uploads=p=>p.calls.filter(call=>Array.isArray(call)&&call[0]==='upload');

test('automatic sync is off until explicit consent and requires an authenticated account',async()=>{
 const p=fixture({local:snapshot([chat()])});await p.api.flush();p.api.changed();assert.equal(p.calls.length,0);assert.equal(p.timers.size,0);
 p.account.state.user=null;await assert.rejects(p.api.enable(),/Sign in/);assert.equal(uploads(p).length,0);
});
test('turning sync off while initial authentication is pending prevents a late enable and upload',async()=>{
 const p=fixture({local:snapshot([chat()])});let resolve;p.account.load=()=>new Promise(done=>resolve=done);
 const enabling=p.api.enable();p.api.disable();resolve(p.account.state);await enabling;
 assert.equal(p.api.enabled,false);assert.equal(p.api.status.phase,'disabled');assert.equal(uploads(p).length,0);assert.equal(p.calls.filter(call=>call==='cloud').length,0);assert.equal(p.saved.at(-1).enabled,false);
});
test('explicitly enabled sync uploads with bound owner and does not upload unchanged data twice',async()=>{
 const p=fixture({local:snapshot([chat()])});await p.api.enable();assert.equal(uploads(p).length,1);assert.equal(uploads(p)[0][2],'alpha');await p.api.flush();assert.equal(uploads(p).length,1);assert.equal(p.api.status.phase,'synced');
});
test('local retry replaces its unchanged cloud baseline without resurrecting the older response',async()=>{
 const p=fixture({local:snapshot([chat()])});await p.api.enable();p.local=snapshot([{...chat(),updatedAt:2,draft:'Unsent',messages:[{role:'user',text:'Hi',state:'complete'},{role:'model',text:'New reply',state:'complete'}]}]);await p.api.flush();assert.equal(p.local.chats.length,1);assert.equal(p.cloud.chats[0].messages.at(-1).text,'New reply');assert.equal(uploads(p).length,2);
});
test('CAS conflict merges remote additions once and stops all automatic uploads until review',async()=>{
 const p=fixture({local:snapshot([chat()])});await p.api.enable();p.local=snapshot([chat(),chat('local','New local')]);
 p.account.upload=async()=>{p.calls.push(['upload-conflict']);p.cloud=snapshot([chat(),chat('remote','Other device')]);p.revision++;throw Error('Conflict');};
 await p.api.flush();assert.equal(p.api.status.phase,'review');assert.equal(p.local.chats.length,3);const calls=p.calls.length;await p.api.flush();p.api.changed();assert.equal(p.calls.length,calls);assert.equal(p.timers.size,0);assert.equal(p.saved.at(-1).review,true);
});
test('cloud conflicts get stable copies and repeated merges remain idempotent',()=>{
 const {DuaAutoSync:s}=modules(),local=snapshot([chat()]),remote=snapshot([chat('one','Different prompt')]);const first=s.merge(local,remote),second=s.merge(first,remote);assert.equal(first.chats.length,2);assert.match(first.chats[1].id,/^auto-/);assert.equal(JSON.stringify(first),JSON.stringify(second));
});
test('content conflicts discovered before CAS keep both local versions and pause before uploading',async()=>{
 const p=fixture({local:snapshot([chat()])});await p.api.enable();p.local=snapshot([{...chat('one','Local edit'),updatedAt:2}]);p.cloud=snapshot([{...chat('one','Remote edit'),updatedAt:3}]);p.revision++;
 await p.api.flush();assert.equal(p.api.status.phase,'review');assert.equal(p.local.chats.length,2);assert.equal(uploads(p).length,1);assert.equal(p.cloud.chats[0].messages[0].text,'Remote edit');
 await p.api.resumeAfterReview();assert.equal(p.api.status.phase,'synced');assert.equal(p.cloud.chats.length,2);
 const fresh=fixture({local:snapshot([chat('one','Local')]),cloud:snapshot([chat('one','Cloud')]),revision:1});await fresh.api.enable();assert.equal(fresh.api.status.phase,'review');assert.equal(uploads(fresh).length,0);
});
test('local deletion pauses before merge or upload and cannot silently resurrect a removed chat',async()=>{
 const p=fixture({local:snapshot([chat()],['Prefer Hindi'])});await p.api.enable();p.local=snapshot([],['Prefer Hindi']);await p.api.flush();assert.equal(p.api.status.phase,'review');assert.equal(p.local.chats.length,0);assert.equal(uploads(p).length,1);
});
test('remote deletion pauses without reuploading the deleted cloud data',async()=>{
 const p=fixture({local:snapshot([chat()],['Prefer Hindi'])});await p.api.enable();p.cloud=snapshot([chat()],[]);p.revision++;await p.api.flush();assert.equal(p.api.status.phase,'review');assert.equal(uploads(p).length,1);assert.deepEqual(p.cloud.memory,[]);
});
test('durable deletion baseline survives page reload and rejects account changes',async()=>{
 const p=fixture({local:snapshot([chat()])});await p.api.enable();const saved=p.saved.at(-1);const reopened=fixture({local:snapshot(),cloud:p.cloud,revision:p.revision});await reopened.api.enable(saved);assert.equal(reopened.api.status.phase,'review');assert.equal(uploads(reopened).length,0);
 const switched=fixture({local:snapshot([chat()])});switched.account.state.user={id:'beta'};await switched.api.enable(saved);assert.equal(switched.api.status.phase,'review');assert.equal(uploads(switched).length,0);
});
test('corrupt persisted consent cannot enable uploads for a newly signed-in account',async()=>{
 const p=fixture({local:snapshot([chat()])});await assert.rejects(p.api.enable({enabled:true,baseline:null,revision:null,review:false}),/Saved sync baseline/);assert.equal(uploads(p).length,0);assert.equal(p.api.enabled,false);
});
test('account changes during a request prevent both local merge and upload',async()=>{
 const p=fixture({cloud:snapshot([chat('remote')])});let resolve;p.account.cloud=()=>new Promise(done=>resolve=done);const enabling=p.api.enable();for(let i=0;i<6;i++)await Promise.resolve();p.account.state.user={id:'beta'};resolve({ownerId:'alpha',revision:1,snapshot:p.cloud});await enabling;assert.equal(p.local.chats.length,0);assert.equal(uploads(p).length,0);assert.equal(p.api.status.phase,'review');
});
test('change notifications debounce and request failures have a bounded exponential retry budget',async()=>{
 const p=fixture();await p.api.enable();p.api.changed();p.api.changed();assert.equal(p.timers.size,1);assert.equal([...p.timers.values()][0].ms,1200);
 p.account.cloud=async()=>{throw Error('Offline');};for(let i=0;i<5;i++)await p.api.flush();assert.equal(p.api.status.phase,'error');assert.equal(p.timers.size,0);p.api.changed();await p.api.flush();assert.equal(p.timers.size,0);assert.equal(uploads(p).length,0);
});
test('oversized or malformed snapshots pause before writes and do not include private metadata',async()=>{
 const p=fixture({local:snapshot([chat()])});p.local={...p.local,chats:[{...chat(),messages:[{role:'user',text:'x'.repeat(1048577),state:'complete'}]}]};await p.api.enable();assert.equal(p.api.status.phase,'review');assert.equal(uploads(p).length,0);
 const safe=p.ctx.DuaAutoSync.validate({...snapshot([chat()]),accessToken:'private',attachments:['private']});assert.ok(!JSON.stringify(safe).includes('private'));
});
test('memory proposals require opt-in, explicit remember statements, and per-note confirmation',()=>{
 const {DuaMemorySuggestions:m}=modules();let prefs={memoryEnabled:false,memory:[]};const api=m.create({preferences:{read:()=>copy(prefs),update:value=>prefs={...prefs,...value}}});
 assert.equal(api.propose('Remember Prefer Hindi').length,0);api.enable();assert.equal(api.propose('I prefer Hindi').length,0);assert.deepEqual(Array.from(api.propose('Remember Prefer Hindi\nYaad rakho I like tea\nRemember my password is private')),['Prefer Hindi','I like tea']);assert.equal(prefs.memory.length,0);api.confirm('Prefer Hindi');assert.deepEqual(Array.from(prefs.memory),['Prefer Hindi']);assert.equal(prefs.memoryEnabled,false);assert.throws(()=>api.confirm('Unreviewed text'),/Review/);api.disable();assert.equal(api.candidates.length,0);
});
test('memory bounds and duplicate checks preserve existing saved notes',()=>{
 const {DuaMemorySuggestions:m}=modules();assert.equal(m.propose('Remember '+'x'.repeat(201)).length,0);assert.equal(m.propose('Remember prefer Hindi',['Prefer Hindi']).length,0);const prefs={read:()=>({memory:Array.from({length:10},(_,i)=>'note '+i)}),update:()=>assert.fail('Must not write')};assert.throws(()=>m.confirm('Extra note',prefs),/Memory is full/);
});
test('installed sync avoids cross-tab storage loops and survives back-forward cache pauses',async()=>{
 const p=fixture(),events={},values=new Map();let writes=0;
 p.ctx.document={hidden:false,addEventListener:(type,fn)=>events['document:'+type]=fn};p.ctx.navigator={onLine:true};p.ctx.addEventListener=(type,fn)=>events[type]=fn;
 p.ctx.localStorage={getItem:key=>values.get(key)??null,setItem:(key,value)=>{writes++;values.set(key,value);}};
 const api=p.ctx.DuaAutoSync.install({account:p.account,history:{exportJson:()=>JSON.stringify({chats:[]}),mergeCloud(){}},preferences:{read:()=>({memory:[]}),update(){}}});
 await api.restore();assert.equal(api.enabled,false);await api.enable();events.pagehide({persisted:true});assert.equal(api.enabled,true);assert.equal(api.status.phase,'paused');events.pageshow({persisted:true});const before=writes;events.storage({key:'dua-automation-sync-v1'});assert.equal(api.enabled,false);assert.equal(writes,before);api.destroy();
});
