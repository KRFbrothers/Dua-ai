import {test} from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
const source=await readFile(new URL('./dist/voice-input.js',import.meta.url),'utf8');
function setup(supported=true){
 const instances=[];const states=[];let timeout;
 class Recognition {
  constructor(){instances.push(this);this.starts=0;this.aborts=0;}
  start(){this.starts++;}
  abort(){this.aborts++;}
 }
 const context={setTimeout(fn){timeout=fn;return 1},clearTimeout(){timeout=null}};
 vm.runInNewContext(source,context);
 const voice=new context.DuaVoiceInput(supported?Recognition:null,state=>states.push(state),'hi-IN');
 return {voice,states,instances,merge:context.DuaVoiceDraft.merge,timeout:()=>timeout?.()};
}
const result=(words,final=true)=>Object.assign([{transcript:words}],{isFinal:final});
test('voice results distinguish interim from final and finish without sending',()=>{
 const p=setup();p.voice.start();const recognition=p.instances[0];recognition.onstart();
 recognition.onresult({results:[result('नमस्ते',false)]});assert.equal(p.voice.state.transcript,'');assert.equal(p.voice.state.partial,'नमस्ते');
 recognition.onresult({results:[result('नमस्ते Dua')]});recognition.onend();
 assert.equal(p.voice.state.transcript,'नमस्ते Dua');assert.equal(p.voice.state.busy,false);assert.match(p.voice.state.status,/Review/);
 assert.equal(recognition.lang,'hi-IN');assert.equal(recognition.continuous,false);
});
test('repeated mic clicks cannot start overlapping speech sessions',()=>{
 const p=setup();p.voice.start();p.voice.start();assert.equal(p.instances.length,1);assert.equal(p.instances[0].starts,1);
});
test('Stop discards interim speech and ignores late callbacks',()=>{
 const p=setup();p.voice.start();const r=p.instances[0],late=r.onresult;
 r.onresult({results:[result('unfinished',false)]});p.voice.stop();
 late({results:[result('late words')]});assert.equal(p.voice.state.transcript,'');assert.equal(p.voice.state.partial,'');assert.equal(p.voice.state.busy,false);assert.equal(r.aborts,1);
});
test('unsupported, denied and timeout states allow safe retry',()=>{
 const unsupported=setup(false);unsupported.voice.start();assert.match(unsupported.voice.state.status,/not supported/);
 const denied=setup();denied.voice.start();denied.instances[0].onerror({error:'not-allowed'});assert.equal(denied.voice.state.busy,false);assert.match(denied.voice.state.status,/permission denied/);
 denied.voice.start();assert.equal(denied.instances.length,2);denied.timeout();assert.equal(denied.voice.state.busy,false);assert.match(denied.voice.state.status,/timed out/);
});
test('empty/oversize speech is rejected and existing drafts are preserved',()=>{
 const p=setup();assert.equal(p.merge('Draft','  Hello  '),'Draft\nHello');
 assert.throws(()=>p.merge('Draft',' '),/No speech/);assert.throws(()=>p.merge('Draft','x'.repeat(8000)),/8000/);
 p.voice.start();p.instances[0].onresult({results:[result('x'.repeat(8001))]});assert.equal(p.voice.state.transcript,'');assert.equal(p.voice.state.busy,false);assert.match(p.voice.state.status,/8000/);
});
