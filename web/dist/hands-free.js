(function(root){
 // Only one stage can consume a callback. Stop invalidates every pending turn.
 root.DuaHandsFreeSession=class {
  constructor(actions){this.actions=actions;this.phase='idle';this.generation=0;}
  set(phase){this.phase=phase;this.actions.render(phase);}
  start(){if(this.phase!=='idle')return;this.generation++;this.set('listening');this.actions.listen();}
  transcript(text){if(this.phase!=='listening')return;const words=String(text||'').trim();if(!words||words.length>8000){this.stop('No speech detected or message too long. Start again.');return;}const token=this.generation;this.set('thinking');this.actions.send(words,token);}
  reply(text,token){if(token!==this.generation||this.phase!=='thinking')return;this.set('speaking');this.actions.speak(text);}
  spoken(){if(this.phase!=='speaking')return;this.set('listening');this.actions.listen();}
  stop(reason='Stopped.'){this.generation++;this.set('idle');this.actions.cancel();this.actions.notice(reason);}
 };
})(globalThis);
