(function(){
 const status=document.querySelector('.status'),count=document.querySelector('#chat-count'),exportButton=document.querySelector('#export-chats'),clearButton=document.querySelector('#clear-chats'),dialog=document.querySelector('#clear-confirm');
 const importButton=document.querySelector('#import-chats'),fileInput=document.querySelector('#import-file'),importDialog=document.querySelector('#import-confirm');
 let pendingImport=null,readVersion=0,reading=false;
 function setReading(value){reading=value;importButton.disabled=value;clearButton.disabled=value;}
 function cancelImport(){readVersion++;pendingImport=null;fileInput.value='';importDialog.close();}
 importButton.addEventListener('click',()=>{if(reading)return;fileInput.value='';fileInput.click();});
 fileInput.addEventListener('change',async()=>{
  const file=fileInput.files?.[0];if(!file)return;
  const version=++readVersion;pendingImport=null;setReading(true);status.textContent='Checking export…';
  try{
   if(file.size>DuaHistory.maxImportBytes)throw new Error('Choose a Dua JSON export up to 4 MiB.');
   const bytes=await file.arrayBuffer();
   const raw=new TextDecoder('utf-8',{fatal:true}).decode(bytes);
   const count=DuaHistory.previewImport(raw);
   if(version!==readVersion)return;
   if(!count){status.textContent='This export has no chats. Nothing was changed.';return;}
   pendingImport=raw;document.querySelector('#import-summary').textContent='Add '+count+' chats as separate copies? Existing chats and drafts will be kept. Importing the same file again creates additional copies.';
   document.querySelector('#import-error').textContent='';status.textContent='Export checked. Confirm to add chats.';importDialog.showModal();
  }catch(error){if(version===readVersion)status.textContent=error instanceof TypeError?'This file is not valid UTF-8 JSON.':error.message||'Export could not be read.';}
  finally{if(version===readVersion){setReading(false);fileInput.value='';}}
 });
 document.querySelector('#cancel-import').addEventListener('click',cancelImport);
 importDialog.addEventListener('cancel',cancelImport);
 importDialog.addEventListener('close',()=>{pendingImport=null;});
 document.querySelector('#confirm-import').addEventListener('click',()=>{
  if(pendingImport===null)return;
  try{const count=DuaHistory.importJson(pendingImport);cancelImport();refresh();status.textContent=count+' chats imported. Existing chats and drafts were kept.';importButton.focus();}
  catch(error){document.querySelector('#import-error').textContent=error.message||'Import could not be saved. No chats were changed.';}
 });
 window.addEventListener('pagehide',()=>{cancelImport();setReading(false);});
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
 window.addEventListener('storage',refresh);
 if(new URLSearchParams(location.search).get('from')==='voice')document.querySelector('.header a').href='settings.html?from=voice';
})();
