(function(){
 const status=document.querySelector('.status'),count=document.querySelector('#chat-count'),exportButton=document.querySelector('#export-chats'),clearButton=document.querySelector('#clear-chats'),dialog=document.querySelector('#clear-confirm');
 function refresh(){try{const chats=DuaHistory.list();count.textContent=chats.length+' saved chats · this browser';exportButton.disabled=!chats.length;}catch{exportButton.disabled=true;status.textContent='Stored chats could not be read. Export is unavailable; Clear all can remove local stored history.';}}
 refresh();
 exportButton.addEventListener('click',()=>{
  let url;
  try{
   const data=DuaHistory.exportJson();url=URL.createObjectURL(new Blob([data],{type:'application/json;charset=utf-8'}));
   const link=document.createElement('a');link.href=url;link.download='dua-chats-'+new Date().toISOString().slice(0,10)+'.json';document.body.append(link);link.click();link.remove();
   status.textContent='Export download requested. Your chats are unchanged.';
  }catch(error){status.textContent=error.message||'Export could not be created.';}
  finally{if(url)setTimeout(()=>URL.revokeObjectURL(url),1000);}
 });
 clearButton.addEventListener('click',()=>{document.querySelector('#delete-error').textContent='';dialog.showModal();});
 document.querySelector('#cancel-clear').addEventListener('click',()=>dialog.close());
 document.querySelector('#confirm-clear').addEventListener('click',()=>{
  try{DuaHistory.clearAll();refresh();dialog.close();status.textContent='All local chats, drafts and pending files cleared.';clearButton.focus();}
  catch(error){document.querySelector('#delete-error').textContent=error.message||'History could not be cleared. Try again.';}
 });
 if(new URLSearchParams(location.search).get('from')==='voice')document.querySelector('.header a').href='settings.html?from=voice';
})();
