(function(root){
 function chunks(text){
  const points=Array.from(text.trim()),result=[];let start=0;
  while(start<points.length){
   let end=Math.min(start+300,points.length);
   if(end<points.length){for(let i=end-1;i>=Math.max(start+150,end-100);i--)if(/[\s.!?।]/u.test(points[i])){end=i+1;break;}}
   const part=points.slice(start,end).join('').trim();if(part)result.push(part);start=end;
  }
  return result;
 }
 root.DuaReadAloud=class {
  constructor(synthesis,Utterance,render,language){this.synthesis=synthesis;this.Utterance=Utterance;this.render=render;this.language=language;this.generation=0;this.timer=null;this.utterance=null;this.state={available:!!(synthesis&&Utterance),activeId:null,status:synthesis&&Utterance?'':'Read aloud is not supported in this browser.'};this.publish();}
  publish(){this.render({...this.state});}
  toggle(id,text){
   if(this.state.activeId===id){this.stop();return;}
   if(!this.state.available){this.state.status='Read aloud is not supported in this browser.';this.publish();return;}
   this.stop();const queue=chunks(text);if(!queue.length){this.state.status='No reply text to read.';this.publish();return;}
   const token=++this.generation;let index=0;
   this.state.activeId=id;this.state.status='Reading reply…';this.publish();
   const finish=status=>{if(token!==this.generation)return;this.stop(status);};
   const next=()=>{
    if(token!==this.generation)return;
    clearTimeout(this.timer);this.timer=null;
    if(index>=queue.length){this.utterance=null;this.state.activeId=null;this.state.status='';this.publish();return;}
    try{
     const utterance=new this.Utterance(queue[index++]);this.utterance=utterance;utterance.lang=this.language||'en-US';
     const voices=this.synthesis.getVoices?.()||[];const voice=voices.find(v=>v.lang.toLowerCase()===utterance.lang.toLowerCase());if(voice)utterance.voice=voice;
     utterance.onend=()=>{if(token===this.generation&&utterance===this.utterance)next();};
     utterance.onerror=()=>{if(utterance===this.utterance)finish('Read aloud failed. Tap the reply speaker to retry.');};
     this.timer=setTimeout(()=>{if(utterance===this.utterance)finish('Read aloud timed out. Tap the reply speaker to retry.');},45000);
     this.synthesis.speak(utterance);
    }catch{finish('Read aloud failed. Tap the reply speaker to retry.');}
   };
   next();
  }
  stop(status){this.generation++;clearTimeout(this.timer);this.timer=null;if(this.utterance){this.utterance.onend=this.utterance.onerror=null;}this.utterance=null;try{this.synthesis?.cancel();}catch{}this.state.activeId=null;this.state.status=status||(this.state.available?'':'Read aloud is not supported in this browser.');this.publish();}
 };
 root.DuaSpeechChunks=chunks;
})(globalThis);
