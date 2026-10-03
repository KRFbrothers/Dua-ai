(function(root){
 const types=['image/jpeg','image/png','image/webp','application/pdf','text/plain'];
 root.DuaAttachments={
  async read(files){
   if(files.length>3)throw Error('Maximum 3 files select karein.');
   if(files.reduce((total,file)=>total+file.size,0)>2*1024*1024)throw Error('Attachments total 2 MB se chhote rakhein.');
   return Promise.all(files.map(async file=>{
    const mimeType=file.type||(/\.txt$/i.test(file.name)?'text/plain':'');
    if(!types.includes(mimeType))throw Error('JPEG, PNG, WebP, PDF ya TXT file select karein.');
    if(!file.size)throw Error('Empty file select nahi kar sakte.');
    if(mimeType==='text/plain'&&file.size>65536)throw Error('TXT file 64 KB se chhoti rakhein.');
    const buffer=await file.arrayBuffer();const bytes=new Uint8Array(buffer);let binary='';
    for(let i=0;i<bytes.length;i+=8192)binary+=String.fromCharCode(...bytes.subarray(i,i+8192));
    return {name:file.name.slice(0,200),mimeType,data:btoa(binary)};
   }));
  }
 };
})(globalThis);
