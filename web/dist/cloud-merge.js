(function(root){
 const canonical=chat=>JSON.stringify({updatedAt:chat.updatedAt,model:chat.model,draft:chat.draft,messages:chat.messages});
 root.DuaCloudMerge=function(local,remote,makeId){const merged=local.slice(),original=local.slice();let added=0,conflicts=0;for(const chat of remote){const same=original.find(c=>c.id===chat.id);if(same&&canonical(same)===canonical(chat)||original.some(c=>canonical(c)===canonical(chat)))continue;let next=chat;if(same){let id;do{id=makeId();}while(merged.some(c=>c.id===id));next={...chat,id};conflicts++;}merged.push(next);added++;}return {chats:merged,added,conflicts};};
})(globalThis);
