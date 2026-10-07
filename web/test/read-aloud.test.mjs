import {test} from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
const source=await readFile(new URL('../dist/read-aloud.js',import.meta.url),'utf8');
function setup(supported=true){
 const spoken=[],states=[];let cancelled=0,timeout;
 const synthesis={speak(utterance){spoken.push(utterance)},cancel(){cancelled++},getVoices(){return [{lang:'hi-IN'}]}};
 class Utterance{constructor(text){this.text=text;}}
 const context={setTimeout(fn){timeout=fn;return 1},clearTimeout(){timeout=null}};vm.runInNewContext(source,context);
 const reader=new context.DuaReadAloud(supported?synthesis:null,supported?Utterance:null,state=>states.push(state),'hi-IN');
 return {reader,spoken,states,cancelled:()=>cancelled,timeout:()=>timeout?.(),chunks:context.DuaSpeechChunks};
}
test('read aloud requires a click and advances long replies one chunk at a time',()=>{
 const p=setup();assert.equal(p.spoken.length,0);p.reader.toggle('reply','नमस्ते Dua. '.repeat(100));
 assert.equal(p.spoken.length,1);while(p.reader.state.activeId)p.spoken.at(-1).onend();
 assert.ok(p.spoken.length>1);assert.equal(p.reader.state.activeId,null);assert.equal(p.spoken[0].lang,'hi-IN');
});
test('Stop and replacement prevent stale callbacks from resuming playback',()=>{
 const p=setup();p.reader.toggle('first','x'.repeat(700));const old=p.spoken[0].onend;
 p.reader.toggle('second','Latest reply');old();assert.equal(p.reader.state.activeId,'second');assert.equal(p.spoken.length,2);
 const latest=p.spoken[1].onend;p.reader.toggle('second','Latest reply');latest();assert.equal(p.reader.state.activeId,null);assert.equal(p.spoken.length,2);
});
test('unsupported engines, empty replies and playback errors have safe retry states',()=>{
 const unsupported=setup(false);unsupported.reader.toggle('reply','Hello');assert.equal(unsupported.spoken.length,0);assert.match(unsupported.reader.state.status,/not supported/);
 const p=setup();p.reader.toggle('reply',' ');assert.equal(p.spoken.length,0);
 p.reader.toggle('reply','Hello');p.spoken[0].onerror();assert.equal(p.reader.state.activeId,null);assert.match(p.reader.state.status,/retry/);
 p.reader.toggle('reply','Hello again');p.timeout();assert.equal(p.reader.state.activeId,null);assert.match(p.reader.state.status,/timed out/);
});
test('Hindi and emoji stay intact across speech chunks',()=>{
 const p=setup(),text='नमस्ते 😀 Dua! '.repeat(200),chunks=p.chunks(text);
 assert.equal(chunks.join('').replace(/\s/g,''),text.replace(/\s/g,''));
 assert.ok(chunks.every(chunk=>Array.from(chunk).length<=300&&!/^[\uDC00-\uDFFF]|[\uD800-\uDBFF]$/.test(chunk)));
});

test('an error from an already finished chunk cannot stop the next chunk',()=>{
 const p=setup();p.reader.toggle('reply','x'.repeat(700));const first=p.spoken[0],lateError=first.onerror;
 first.onend();lateError();assert.equal(p.reader.state.activeId,'reply');assert.equal(p.spoken.length,2);
});
