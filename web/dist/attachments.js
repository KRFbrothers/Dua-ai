(function(root){
 const types=['image/jpeg','image/png','image/webp','application/pdf','text/plain'];
 root.DuaAttachments={
  async read(files){
   if(files.length>3)throw Error('Maximum 3 files select karein.');
   const sources=[];
   for(const file of files){
    const mimeType=file.type||(/\.txt$/i.test(file.name)?'text/plain':'');
    if(!types.includes(mimeType))throw Error('JPEG, PNG, WebP, PDF ya TXT file select karein.');
    if(!file.size)throw Error('Empty file select nahi kar sakte.');
    if(mimeType==='text/plain'&&file.size>65536)throw Error('TXT file 64 KB se chhoti rakhein.');
    const limit=mimeType.startsWith('image/')?root.DuaPhotos.maxSourceBytes:root.DuaPhotos.maxBytes;
    if(file.size>limit)throw Error(mimeType.startsWith('image/')?'Photos must be 10 MiB or smaller before resizing.':'Attachments total 2 MB se chhote rakhein.');
    sources.push({file,name:file.name.slice(0,200),mimeType,size:file.size});
   }
   const budgets=root.DuaPhotos.budgets(sources),output=[];
   // Process sequentially to avoid decoding several large images at the same time.
   for(let index=0;index<sources.length;index++){
    const source=sources[index];let bytes=new Uint8Array(await source.file.arrayBuffer()),mimeType=source.mimeType,name=source.name;
    if(bytes.length!==source.size)throw Error('File changed while reading. Select it again.');
    if(bytes.length>budgets[index])({bytes,mimeType,name}=await root.DuaPhotos.resize(bytes,mimeType,name,budgets[index]));
    let binary='';for(let i=0;i<bytes.length;i+=8192)binary+=String.fromCharCode(...bytes.subarray(i,i+8192));
    output.push({name,mimeType,data:btoa(binary)});
   }
   return output;
  }
 };
})(globalThis);
