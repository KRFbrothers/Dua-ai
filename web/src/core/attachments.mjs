export const MAX_REQUEST_BYTES = 3 * 1024 * 1024;
const MAX_FILE_BYTES = 2 * 1024 * 1024;
/** Per text/code file limit (UTF-8 bytes). Older APKs still enforce 64 KB on the device. */
export const MAX_TEXT_FILE_BYTES = 256 * 1024;
const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
/**
 * Text-like MIME types accepted as inline UTF-8 text. Android sends every text/code file as
 * text/plain (the most compatible type for older servers and provider allowlists); other clients
 * may send the specific type. All of them are normalized to text/plain for provider checks.
 */
export const TEXT_TYPES = Object.freeze([
  'text/plain', 'text/csv', 'text/tab-separated-values', 'text/markdown', 'text/x-markdown',
  'application/json', 'application/xml', 'text/xml', 'application/yaml', 'application/x-yaml', 'text/yaml', 'text/x-yaml',
  'text/html', 'text/css', 'text/javascript', 'application/javascript', 'text/x-python', 'text/x-java', 'text/x-kotlin',
  'text/x-c', 'text/x-c++', 'text/x-go', 'text/x-rust', 'text/x-swift', 'application/sql', 'text/x-sql', 'application/x-sh', 'text/x-shellscript',
  'text/x-log', 'application/toml', 'text/x-toml'
]);
/** File extensions Android offers as text attachments (documented in PROVIDERS.md). */
export const TEXT_EXTENSIONS = Object.freeze(['txt', 'text', 'csv', 'tsv', 'md', 'markdown', 'json', 'jsonl', 'xml', 'yaml', 'yml', 'toml', 'ini', 'cfg', 'conf', 'env', 'log',
  'kt', 'kts', 'java', 'js', 'mjs', 'cjs', 'jsx', 'ts', 'tsx', 'py', 'go', 'rs', 'c', 'h', 'cc', 'cpp', 'hpp', 'cs', 'swift', 'rb', 'php', 'dart', 'scala', 'sql',
  'html', 'htm', 'css', 'scss', 'sh', 'bash', 'zsh', 'ps1', 'bat', 'gradle', 'properties', 'gitignore', 'dockerfile', 'tex', 'r', 'lua', 'pl', 'vue', 'svelte']);
export const isTextType = type => TEXT_TYPES.includes(type);
/** Provider allowlists are expressed with text/plain for every text-like file. */
export const providerType = type => isTextType(type) ? 'text/plain' : type;
const SUPPORTED_HINT = 'Choose JPEG, PNG, WebP, PDF, or a UTF-8 text/code file (TXT, CSV, MD, JSON, XML, YAML, LOG, source code).';

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
  if(![...IMAGE_TYPES,'application/pdf'].includes(type)&&!isTextType(type))fail(SUPPORTED_HINT,415);
  if(isTextType(type)){
   if(bytes.length>MAX_TEXT_FILE_BYTES)fail(`Text file "${file.name}" is larger than 256 KB. Shorten it or split it.`,413);
   let text;try{text=new TextDecoder('utf-8',{fatal:true}).decode(bytes)}catch{fail(`Text file "${file.name}" is not valid UTF-8. Save it as UTF-8 and retry.`);}
   if(text.includes('\u0000'))fail(`"${file.name}" looks like a binary file, not text.`,415);
   return [{text:`Attached text file (${file.name}):\n${text.replace(/^\uFEFF/,'')}`}];
  }
  const valid=type==='image/png'?bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])):
   type==='image/jpeg'?bytes[0]===255&&bytes[1]===216&&bytes[2]===255:
   type==='image/webp'?bytes.subarray(0,4).toString()==='RIFF'&&bytes.subarray(8,12).toString()==='WEBP':
   bytes.subarray(0,5).toString()==='%PDF-';
  if(!valid)fail('File content selected type se match nahi karta. Dobara select karein.');
  return [{text:`Attached file: ${file.name}`},{inlineData:{mimeType:type,data:file.data}}];
 });
}
