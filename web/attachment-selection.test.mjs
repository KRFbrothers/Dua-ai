import {test} from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
const source=(await readFile(new URL('./dist/add-to-chat.html',import.meta.url),'utf8')).split('<script>')[1].split('</script>')[0];
function setup() {
 const status={textContent:''};const storage=new Map([['dua-attachments','previous selection']]);const pending=[];
 const inputs=Array.from({length:3},()=>({value:'chosen.jpg',files:[],addEventListener(type,fn){this[type]=fn}}));
 vm.runInNewContext(source,{
  document:{querySelectorAll(selector){return selector==='.attachment-input'?inputs:[]},querySelector(){return status}},
  sessionStorage:{setItem(key,value){storage.set(key,value)}},
  DuaAttachments:{read(files){return new Promise((resolve,reject)=>pending.push({files,resolve,reject}))}}
 });
 return {status,storage,pending,inputs,choose(index,files=[{name:'Camera.jpg'}]){inputs[index].files=files;return inputs[index].change()}};
}
test('camera selection saves bytes and resets input so same photo can be selected again',async()=>{
 const page=setup();const reading=page.choose(0);
 const files=[{name:'Camera.jpg',mimeType:'image/jpeg',data:'/9j/'}];page.pending[0].resolve(files);await reading;
 assert.deepEqual(JSON.parse(page.storage.get('dua-attachments')),files);
 assert.equal(page.inputs[0].value,'');assert.match(page.status.textContent,/Ready/);
});
test('cancelled selection retains existing files without an empty upload',async()=>{
 const page=setup();await page.choose(0,[]);
 assert.equal(page.pending.length,0);assert.equal(page.storage.get('dua-attachments'),'previous selection');
});
test('failed camera read retains previous selection and allows retry',async()=>{
 const page=setup();const reading=page.choose(0);page.pending[0].reject(Error('Attachments total 2 MB se chhote rakhein.'));await reading;
 assert.equal(page.storage.get('dua-attachments'),'previous selection');assert.equal(page.inputs[0].value,'');assert.match(page.status.textContent,/2 MB/);
});
test('a slow earlier picker cannot overwrite the latest selection',async()=>{
 const page=setup();const camera=page.choose(0);const photos=page.choose(1,[{name:'Latest.png'}]);
 page.pending[1].resolve([{name:'Latest.png',mimeType:'image/png',data:'latest'}]);await photos;
 page.pending[0].resolve([{name:'Camera.jpg',mimeType:'image/jpeg',data:'old'}]);await camera;
 assert.equal(JSON.parse(page.storage.get('dua-attachments'))[0].name,'Latest.png');assert.match(page.status.textContent,/Latest.png/);
});
