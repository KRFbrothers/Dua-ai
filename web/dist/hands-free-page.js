(function(){
 const phase=document.querySelector('#phase'),notice=document.querySelector('#notice'),start=document.querySelector('#start'),stop=document.querySelector('#stop');
 let session,voice,reader,controller,configController,started=false,history=[],chatId=null,heardBusy=false,spoke=false,preparing=false;
 function message(text){notice.textContent=text;}
 function save(draft=''){chatId=DuaHistory.save({draft,model:'Dua',messages:history});}
 session=new DuaHandsFreeSession({
  render(state){phase.textContent={idle:'Ready',listening:'Listening…',thinking:'Dua is thinking…',speaking:'Reading reply…'}[state];start.disabled=state!=='idle'||preparing;stop.disabled=state==='idle'&&!preparing;document.querySelector('.voice-orbit').dataset.active=String(state!=='idle');},
  notice:message,
  listen(){heardBusy=false;voice.start();},
  speak(text){spoke=false;reader.toggle('voice-reply',text);},
  cancel(){preparing=false;configController?.abort();controller?.abort();voice?.stop();reader?.stop();},
  async send(text,token){
   const user={role:'user',text,state:'complete'};history.push(user);document.querySelector('#heard').textContent='You: '+text;document.querySelector('#reply').textContent='';
   try{
    save();controller=new AbortController();const signal=controller.signal;
    const response=await fetch('/api/chat',{method:'POST',headers:{'Content-Type':'application/json'},signal,body:JSON.stringify({model:'Dua',messages:history.slice(-39).map(({role,text})=>({role,text})),attachments:[],memory:DuaPreferences.memoryText()})});
    const result=await response.json();signal.throwIfAborted();if(token!==session.generation)return;
    if(!response.ok||typeof result.text!=='string'||!result.text.trim())throw Error(result.error||'Reply unavailable. Retry in Chat.');
    history.push({role:'model',text:result.text,state:'complete'});save();document.querySelector('#reply').textContent='Dua: '+result.text;session.reply(result.text,token);
   }catch(error){if(token!==session.generation)return;history=history.filter(m=>m!==user);try{save(text);}catch{}session.stop(error.name==='AbortError'?'Stopped.':'Reply failed. Open Chat to review and retry.');}
  }
 });
 voice=new DuaVoiceInput(globalThis.SpeechRecognition||globalThis.webkitSpeechRecognition,state=>{
  if(session.phase!=='listening')return;if(state.busy){heardBusy=true;document.querySelector('#heard').textContent=state.partial||state.transcript;}
  else if(heardBusy){if(state.transcript&&state.status.startsWith('Review transcript'))session.transcript(state.transcript);else session.stop(state.status);}
  else if(state.status.includes('not supported')||state.status.includes('unavailable'))session.stop(state.status);
 },navigator.language);
 reader=new DuaReadAloud(globalThis.speechSynthesis,globalThis.SpeechSynthesisUtterance,state=>{
  if(session.phase!=='speaking')return;if(state.activeId)spoke=true;else if(spoke){if(state.status)session.stop(state.status);else session.spoken();}
 },navigator.language);
 start.addEventListener('click',async()=>{
  if(preparing||session.phase!=='idle')return;
  if(document.hidden)return;if(!reader.state.available||!voice.Recognition){message('Speech input and read aloud must be available. Use Chat instead.');return;}
  if(sessionStorage.getItem('dua-attachments')){message('Return to Chat and send or remove selected attachments first.');return;}
  preparing=true;start.disabled=true;stop.disabled=false;message('Checking Gemini availability…');configController=new AbortController();const signal=configController.signal;
  try{const response=await fetch('/api/config',{signal});const data=await response.json();signal.throwIfAborted();if(!response.ok||!data.geminiConfigured)throw Error('Gemini is not configured. Use Chat after server setup.');if(document.hidden)throw Error('Keep this screen open.');if(!started){DuaHistory.newChat();started=true;}preparing=false;message('Final speech sends automatically.');session.start();}
  catch(error){if(!signal.aborted)session.stop(error.message);}
 });
 stop.addEventListener('click',()=>session.stop());
 const leave=()=>session.stop('Stopped when leaving the foreground.');document.addEventListener('visibilitychange',()=>{if(document.hidden)leave();});window.addEventListener('pagehide',leave);
 window.addEventListener('storage',event=>{if(event.key==='dua-history-v2')leave();});
})();
