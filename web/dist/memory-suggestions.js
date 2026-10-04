(function(root){
 'use strict';
 const normalize=text=>text.trim().replace(/\s+/g,' ');
 function propose(text,existing=[]){
  if(typeof text!=='string'||text.length>8000)return [];
  const candidates=[];
  for(const line of text.split(/\n|(?<=[.!?])\s+/)){
   const match=line.trim().match(/^(?:please\s+)?remember(?:\s+that|\s+this)?\s*[:,-]?\s+(.+)$|^(?:yaad\s+rakho|yaad\s+rakhna|याद\s+रखो|याद\s+रखना)\s*[:,-]?\s+(.+)$/i);
   if(!match)continue;
   const note=normalize(match[1]||match[2]);
   if(!note||note.length>200||/\b(password|passcode|api[ _-]?key|access[ _-]?token|refresh[ _-]?token|secret|otp|pin\s*(?:code|number))\b/i.test(note))continue;
   if(!existing.some(value=>normalize(value).toLocaleLowerCase()===note.toLocaleLowerCase())&&!candidates.some(value=>value.toLocaleLowerCase()===note.toLocaleLowerCase()))candidates.push(note);
   if(candidates.length===4)break;
  }
  return candidates;
 }
 function confirm(candidate,preferences){
  const note=normalize(candidate||'');if(!note||note.length>200)throw Error('Memory notes must contain 1–200 characters.');
  const current=preferences.read();if(current.memory.some(value=>normalize(value).toLocaleLowerCase()===note.toLocaleLowerCase()))return current;
  if(current.memory.length>=10)throw Error('Memory is full. Remove a note before confirming another.');
  // Extraction approval does not silently enable retrieval in future AI requests.
  return preferences.update({memory:[...current.memory,note]});
 }
 function create({preferences,onChange=()=>{}}){
  let enabled=false,candidates=[];
  function publish(){onChange({enabled,candidates:candidates.slice()});}
  return {get enabled(){return enabled;},get candidates(){return candidates.slice();},enable(){enabled=true;publish();},disable(){enabled=false;candidates=[];publish();},propose(text){candidates=enabled?propose(text,preferences.read().memory):[];publish();return candidates.slice();},confirm(candidate){if(!enabled||!candidates.includes(candidate))throw Error('Review a proposed memory note before saving.');const next=confirm(candidate,preferences);candidates=candidates.filter(value=>value!==candidate);publish();return next;},dismiss(candidate){candidates=candidates.filter(value=>value!==candidate);publish();}};
 }
 root.DuaMemorySuggestions={propose,confirm,create};
})(globalThis);
