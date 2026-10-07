export const MAX_REQUEST_BYTES = 3 * 1024 * 1024;
const MAX_FILE_BYTES = 2 * 1024 * 1024;
export function attachmentParts(files) {
 if (files === undefined) return [];
 const fail=(message,status=400)=>{throw Object.assign(new Error(message),{status})};
 if(!Array.isArray(files)||files.length>3)fail('Maximum 3 attachments select karein.');
 let total=0;
 return files.flatMap(file=>{
  if(!file||typeof file.name!=='string'||!file.name.trim()||file.name.length>200||typeof file.data!=='string'||typeof file.mimeType!=='string')fail('Invalid attachment. File dobara select karein.');
  if(file.data.length>Math.ceil(MAX_FILE_BYTES/3)*4)fail('Attachments total 2 MB se chhote rakhein.',413);
  if(file.data.length % 4 || !/^[A-Za-z0-9+/]*={0,2}$/.test(file.data))fail('Invalid attachment encoding.');
  const bytes=Buffer.from(file.data,'base64');
  if(!bytes.length||bytes.toString('base64')!==file.data)fail('Attachment empty ya invalid hai.');
  total+=bytes.length;if(total>MAX_FILE_BYTES)fail('Attachments total 2 MB se chhote rakhein.',413);
  const type=file.mimeType;
  if(!['image/jpeg','image/png','image/webp','application/pdf','text/plain'].includes(type))fail('JPEG, PNG, WebP, PDF ya TXT file select karein.',415);
  if(type==='text/plain'){
   if(bytes.length>65536)fail('TXT file 64 KB se chhoti rakhein.',413);
   let text;try{text=new TextDecoder('utf-8',{fatal:true}).decode(bytes)}catch{fail('TXT file UTF-8 encoding mein honi chahiye.');}
   return [{text:`Attached text file (${file.name}):\n${text}`}];
  }
  const valid=type==='image/png'?bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])):
   type==='image/jpeg'?bytes[0]===255&&bytes[1]===216&&bytes[2]===255:
   type==='image/webp'?bytes.subarray(0,4).toString()==='RIFF'&&bytes.subarray(8,12).toString()==='WEBP':
   bytes.subarray(0,5).toString()==='%PDF-';
  if(!valid)fail('File content selected type se match nahi karta. Dobara select karein.');
  return [{text:`Attached file: ${file.name}`},{inlineData:{mimeType:type,data:file.data}}];
 });
}
