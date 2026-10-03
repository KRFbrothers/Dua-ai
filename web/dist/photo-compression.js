(function(root){
 const maxSourceBytes=10*1024*1024,maxBytes=2*1024*1024;
 function size(width,height,edge=2048){
  if(!Number.isInteger(width)||!Number.isInteger(height)||width<1||height<1||Math.max(width,height)>16000||width*height>40000000)throw Error('Photo dimensions are too large or invalid. Choose a smaller image.');
  const scale=Math.min(1,edge/Math.max(width,height));return {width:Math.max(1,Math.floor(width*scale)),height:Math.max(1,Math.floor(height*scale))};
 }
 function budgets(files){
  if(files.reduce((total,file)=>total+file.size,0)<=maxBytes)return files.map(file=>file.size);
  const count=files.filter(file=>file.mimeType.startsWith('image/')).length;
  const documents=files.filter(file=>!file.mimeType.startsWith('image/')).reduce((total,file)=>total+file.size,0);
  if(documents>maxBytes||count&&documents>=maxBytes)throw Error('Attachments total 2 MB se chhote rakhein. Use fewer files.');
  const budget=count?Math.floor((maxBytes-documents)/count):0;
  return files.map(file=>file.mimeType.startsWith('image/')?budget:file.size);
 }
 function dimensions(bytes,mime){
  const view=new DataView(bytes.buffer,bytes.byteOffset,bytes.byteLength);
  const invalid=()=>{throw Error('Photo could not be decoded. Choose another image.');};
  if(mime==='image/png'){
   if(bytes.length<24||bytes.slice(0,8).some((value,i)=>value!==[137,80,78,71,13,10,26,10][i]))invalid();
   return {width:view.getUint32(16),height:view.getUint32(20)};
  }
  if(mime==='image/jpeg'){
   if(bytes.length<4||bytes[0]!==255||bytes[1]!==216)invalid();
   let offset=2;
   while(offset<bytes.length){
    if(bytes[offset++]!==255)invalid();while(bytes[offset]===255)offset++;
    const marker=bytes[offset++];if(marker===217||marker===218)break;
    if(marker===1||marker>=208&&marker<=215)continue;
    if(offset+2>bytes.length)invalid();const length=view.getUint16(offset);
    if(length<2||offset+length>bytes.length)invalid();
    if([192,193,194,195,197,198,199,201,202,203,205,206,207].includes(marker)){
     if(length<8)invalid();return {height:view.getUint16(offset+3),width:view.getUint16(offset+5)};
    }
    offset+=length;
   }invalid();
  }
  if(mime==='image/webp'){
   const text=(start,end)=>String.fromCharCode(...bytes.slice(start,end));
   if(bytes.length<30||text(0,4)!=='RIFF'||text(8,12)!=='WEBP')invalid();
   const kind=text(12,16);
   if(kind==='VP8X')return {width:1+bytes[24]+(bytes[25]<<8)+(bytes[26]<<16),height:1+bytes[27]+(bytes[28]<<8)+(bytes[29]<<16)};
   if(kind==='VP8 '&&bytes[23]===157&&bytes[24]===1&&bytes[25]===42)return {width:view.getUint16(26,true)&16383,height:view.getUint16(28,true)&16383};
   if(kind==='VP8L'&&bytes[20]===47){const bits=view.getUint32(21,true);return {width:1+(bits&16383),height:1+((bits>>>14)&16383)};}
  }
  invalid();
 }
 async function resize(bytes,mime,name,budget){
  if(bytes.length>maxSourceBytes)throw Error('Photos must be 10 MiB or smaller before resizing.');
  const bounds=dimensions(bytes,mime);size(bounds.width,bounds.height);
  if(!root.createImageBitmap)throw Error('Photo resizing is unavailable in this browser. Choose a smaller image.');
  let bitmap;
  try{
   bitmap=await root.createImageBitmap(new Blob([bytes],{type:mime}),{imageOrientation:'from-image'});
   size(bitmap.width,bitmap.height);
   for(const edge of [2048,1536,1024,768,512,256]){
    const target=size(bitmap.width,bitmap.height,edge),canvas=root.document.createElement('canvas');canvas.width=target.width;canvas.height=target.height;
    try{const context=canvas.getContext('2d');if(!context)throw Error('Photo resizing is unavailable in this browser.');
    context.fillStyle='#ffffff';context.fillRect(0,0,target.width,target.height);context.drawImage(bitmap,0,0,target.width,target.height);
    for(const quality of [0.85,0.70,0.55]){
     const blob=await new Promise(resolve=>canvas.toBlob(resolve,'image/jpeg',quality));
     if(blob&&blob.size<=budget)return {bytes:new Uint8Array(await blob.arrayBuffer()),mimeType:'image/jpeg',name:name.replace(/\.[^.]*$/,'').slice(0,188)+'-resized.jpg'};
    }}finally{canvas.width=canvas.height=0;}
   }
   throw Error('Photo could not fit the 2 MB upload limit. Use fewer files or a smaller photo.');
  }finally{bitmap?.close();}
 }
 root.DuaPhotos={maxSourceBytes,maxBytes,size,budgets,dimensions,resize};
})(globalThis);
