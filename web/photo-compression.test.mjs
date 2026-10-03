import {test} from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
const compression=await readFile(new URL('./dist/photo-compression.js',import.meta.url),'utf8');
const attachments=await readFile(new URL('./dist/attachments.js',import.meta.url),'utf8');
function setup({outputSize=1000,decodeFail=false,contextFail=false}={}){
 let closed=0,decodes=0,white=0,drawn=0,released=0;
 const context={Blob,DataView,Uint8Array,Error,createImageBitmap:async()=>{decodes++;if(decodeFail)throw Error('Bad image');return {width:2400,height:1600,close(){closed++}}},document:{createElement(){return {width:0,_height:0,set height(value){this._height=value;if(!value)released++},getContext(){if(contextFail)return null;return {set fillStyle(value){assert.equal(value,'#ffffff')},fillRect(){white++},drawImage(){drawn++}}},toBlob(callback,type,quality){assert.equal(type,'image/jpeg');assert.ok(quality>=.55);callback(new Blob([new Uint8Array(outputSize)],{type}))}}}},btoa:input=>Buffer.from(input,'binary').toString('base64')};
 vm.runInNewContext(compression,context);vm.runInNewContext(attachments,context);
 return {photos:context.DuaPhotos,files:context.DuaAttachments,stats:()=>({closed,decodes,white,drawn,released})};
}
function png(size=2*1024*1024+1,width=2400,height=1600){const bytes=new Uint8Array(size);bytes.set([137,80,78,71,13,10,26,10]);const view=new DataView(bytes.buffer);view.setUint32(16,width);view.setUint32(20,height);return bytes;}
const file=(name,type,bytes)=>({name,type,size:bytes.length,arrayBuffer:async()=>bytes.buffer});
test('photo policy preserves aspect ratio without upscaling and rejects excessive dimensions',()=>{
 const {photos}=setup();assert.deepEqual({...photos.size(6000,4000)},{width:2048,height:1365});assert.deepEqual({...photos.size(200,100)},{width:200,height:100});
 for(const pair of [[0,10],[16001,1],[10000,5000]])assert.throws(()=>photos.size(...pair));
});
test('budget reserves untouched document bytes and splits remaining capacity across images',()=>{
 const {photos}=setup();assert.deepEqual(Array.from(photos.budgets([{mimeType:'application/pdf',size:100},{mimeType:'image/png',size:2000000},{mimeType:'image/jpeg',size:2000000}])),[100,1048526,1048526]);
 assert.deepEqual(Array.from(photos.budgets([{mimeType:'image/png',size:1900000},{mimeType:'image/jpeg',size:100}])),[1900000,100]);
 assert.throws(()=>photos.budgets([{mimeType:'application/pdf',size:2097152},{mimeType:'image/png',size:1}]));
});
test('small images and documents retain exact original bytes without decoding',async()=>{
 const {files,stats}=setup();const original=new Uint8Array([1,2,3]);const result=await files.read([file('small.png','image/png',original),file('notes.txt','text/plain',new TextEncoder().encode('नमस्ते'))]);
 assert.equal(result[0].name,'small.png');assert.deepEqual(Buffer.from(result[0].data,'base64'),Buffer.from(original));assert.equal(result[1].mimeType,'text/plain');assert.equal(stats().decodes,0);
});
test('oversized image is resized, flattened on white and released, with changed MIME/name',async()=>{
 const {files,stats}=setup();const result=await files.read([file('透明.png','image/png',png())]);
 assert.equal(result[0].name,'透明-resized.jpg');assert.equal(result[0].mimeType,'image/jpeg');assert.equal(Buffer.from(result[0].data,'base64').length,1000);assert.deepEqual(stats(),{closed:1,decodes:1,white:1,drawn:1,released:1});
});
test('combined images over the limit are resized even when each original is below 2 MiB',async()=>{
 const {files,stats}=setup();const result=await files.read([file('one.png','image/png',png(1500000)),file('two.png','image/png',png(1500000))]);
 assert.equal(stats().decodes,2);assert.ok(result.every(item=>item.name.endsWith('-resized.jpg')));assert.ok(result.reduce((sum,item)=>sum+Buffer.from(item.data,'base64').length,0)<=2097152);
});
test('oversized source, document, text, unsupported and empty files are rejected before decoding',async()=>{
 const {files,stats}=setup();for(const input of [[{name:'huge.png',type:'image/png',size:10485761}],[{name:'huge.pdf',type:'application/pdf',size:2097153}],[{name:'huge.txt',type:'text/plain',size:65537}],[{name:'empty.png',type:'image/png',size:0}],[{name:'x.gif',type:'image/gif',size:10}],Array(4).fill({})])await assert.rejects(()=>files.read(input));assert.equal(stats().decodes,0);
});
test('invalid dimensions fail before decode and decode/output failures never produce partial attachments',async()=>{
 const {files,stats}=setup();await assert.rejects(()=>files.read([file('bomb.png','image/png',png(2097153,10000,5000))]));assert.equal(stats().decodes,0);
 await assert.rejects(()=>setup({decodeFail:true}).files.read([file('bad.png','image/png',png())]));
 const failed=setup({outputSize:2097153});await assert.rejects(()=>failed.files.read([file('large.png','image/png',png())]),/could not fit/);assert.equal(failed.stats().closed,1);assert.equal(failed.stats().released,6);
});
test('JPEG and WebP dimension headers are bounded and truncated/spoofed data rejected',()=>{
 const {photos}=setup();const jpeg=new Uint8Array([255,216,255,192,0,8,8,0,100,0,200,0]);assert.deepEqual({...photos.dimensions(jpeg,'image/jpeg')},{width:200,height:100});
 const webp=new Uint8Array(30);webp.set(Buffer.from('RIFF'),0);webp.set(Buffer.from('WEBPVP8X'),8);webp[24]=199;webp[27]=99;assert.deepEqual({...photos.dimensions(webp,'image/webp')},{width:200,height:100});
 for(const mime of ['image/jpeg','image/png','image/webp'])assert.throws(()=>photos.dimensions(new Uint8Array([1,2,3]),mime));
});

test('unavailable canvas releases decoded pixels and surfaces a recoverable error',async()=>{
 const page=setup({contextFail:true});await assert.rejects(()=>page.files.read([file('large.png','image/png',png())]),/unavailable/);assert.equal(page.stats().closed,1);assert.equal(page.stats().released,1);
});
