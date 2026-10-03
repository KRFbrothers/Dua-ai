(function(root){
 function merge(existing,transcript){
  const words=String(transcript||'').trim();if(!words)throw Error('No speech detected. Tap mic to retry.');
  const result=existing.trim()?existing+'\n'+words:words;
  if(result.length>8000)throw Error('Message limit is 8000 characters. Shorten your draft or recording.');
  return result;
 }
 root.DuaVoiceDraft={merge};
 root.DuaVoiceInput=class {
  constructor(Recognition,render,language){this.Recognition=Recognition;this.render=render;this.language=language;this.generation=0;this.recognition=null;this.timer=null;this.state={busy:false,transcript:'',partial:'',status:'Tap mic to speak. Review before sending.'};this.publish();}
  publish(){this.render({...this.state});}
  start(){
   if(this.state.busy)return;
   if(!this.Recognition){this.state.status='Voice input is not supported in this browser. Type in Chat instead.';this.publish();return;}
   this.release();const token=++this.generation;
   this.state={busy:true,transcript:'',partial:'',status:'Starting microphone…'};this.publish();
   const active=()=>token===this.generation;
   const finish=(status)=>{if(!active())return;this.generation++;this.release();this.state.busy=false;this.state.partial='';this.state.status=status;this.publish();};
   try{
    const recognition=new this.Recognition();this.recognition=recognition;
    recognition.lang=this.language||'hi-IN';recognition.continuous=false;recognition.interimResults=true;
    recognition.onstart=()=>{if(active()){this.state.status='Listening… Tap mic to stop.';this.publish();}};
    recognition.onresult=event=>{
     if(!active())return;
     const results=Array.from(event.results);const final=results.filter(r=>r.isFinal).map(r=>r[0].transcript).join(' ').trim();
     const partial=results.filter(r=>!r.isFinal).map(r=>r[0].transcript).join(' ').trim();
     if(final.length>8000||partial.length>8000){this.state.transcript='';finish('Message limit is 8000 characters. Record a shorter message.');return;}
     this.state.transcript=final;this.state.partial=partial;this.publish();
    };
    recognition.onerror=event=>finish(event.error==='not-allowed'||event.error==='service-not-allowed'?'Microphone permission denied. Allow it in browser/site settings and retry.':event.error==='no-speech'?'No speech detected. Tap mic to retry.':'Voice input unavailable. Check connection and tap mic to retry.');
    recognition.onend=()=>finish(this.state.transcript?'Review transcript, then use it in Chat.':'No speech detected. Tap mic to retry.');
    this.timer=setTimeout(()=>finish('Voice input timed out. Tap mic to retry.'),30000);
    recognition.start();
   }catch{finish('Voice input unavailable. Tap mic to retry or type in Chat.');}
  }
  release(){clearTimeout(this.timer);this.timer=null;const recognition=this.recognition;this.recognition=null;if(recognition){recognition.onstart=recognition.onresult=recognition.onerror=recognition.onend=null;try{recognition.abort();}catch{}}}
  stop(){this.generation++;this.release();this.state.busy=false;this.state.partial='';this.state.status=this.state.transcript?'Review transcript, then use it in Chat.':'Stopped. Tap mic to retry.';this.publish();}
 };
})(globalThis);
