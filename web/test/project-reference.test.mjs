import test from 'node:test';
import assert from 'node:assert/strict';
import { defaultProjects, validateProjects, resolveProjectContext } from '../src/core/projects.mjs';
const detail = () => ({ createdAt: 100, updatedAt: 200, icon: 'book', color: 'blue', pinned: true, deleted: false });
const project = () => ({ id: 'owner-project', name: 'Owner notes', instructions: 'Cite the project sources.', archived: false, details: detail() });
const source = () => ({ path: '11111111-1111-4111-8111-111111111111/22222222-2222-4222-8222-222222222222.txt', name: 'Notes.txt', mimeType: 'text/plain', size: 4 });
const req = new Request('https://dua.example/api/chat');
test('fresh defaults never populate demo projects or chats', () => { assert.deepEqual(defaultProjects().map(p => p.id), ['personal']); });
test('metadata/source references round trip without changing legacy fields', () => {
 const p = {...project(), sources: [source()]}; assert.deepEqual(validateProjects([...defaultProjects(), p]).at(-1), p);
 assert.deepEqual(validateProjects(defaultProjects()), defaultProjects());
});
test('malformed presentation and source metadata fail closed', () => {
 for (const alter of [p=>p.details.updatedAt=99,p=>p.details.pinned='true',p=>p.details.icon='unknown',p=>p.details.deleted=true,
  p=>p.sources=[source(),source()],p=>p.sources=[{...source(),size:0}],p=>p.sources=[{...source(),path:'other/file.txt'}],p=>p.sources=[{...source(),mimeType:'evil/data'}]]) {
  const p=project();alter(p);assert.throws(()=>validateProjects([...defaultProjects(),p]));
 }
});
test('project sources are added only from the authenticated authoritative snapshot', async () => {
 const p={...project(),sources:[source()]};const data={projectId:p.id,context:{id:'new-chat'},memory:'untrusted note',messages:[{role:'user',text:'Summarize my source.'}]};
 await resolveProjectContext(req,data,{env:{DUA_ALLOWED_EMAIL:'owner@example.com'},cloud:{snapshot:{projects:[...defaultProjects(),p],chats:[],memory:[]}}});
 assert.deepEqual(data.messages[0].files,[source()]);assert.match(data.memory,/Cite the project sources/);assert.doesNotMatch(data.memory,/untrusted/);
});
test('project source paths cannot cross project boundaries and deleted projects cannot chat', async () => {
 const p=project();const other={...project(),id:'other',sources:[source()]};const data={projectId:p.id,messages:[{role:'user',text:'Hi',files:[source()]}]};
 const env={DUA_ALLOWED_EMAIL:'owner@example.com'};const snapshot={projects:[...defaultProjects(),p,other],chats:[],memory:[]};
 await assert.rejects(resolveProjectContext(req,data,{env,cloud:{snapshot}}),/another project/);
 p.archived=true;p.details.deleted=true;
 await assert.rejects(resolveProjectContext(req,{...data,messages:[{role:'user',text:'Hi'}]},{env,cloud:{snapshot}}),/archived/);
});
test('deleted definitions retain their references without consuming the active project quota', () => {
 const removed = Array.from({length:20},(_,i)=>({...project(),id:`removed-${i}`,archived:true,details:{...detail(),deleted:true}}));
 assert.equal(validateProjects([...defaultProjects(),...removed,project()]).length,22);
 assert.throws(()=>validateProjects([...defaultProjects(),...Array.from({length:20},(_,i)=>({...project(),id:`active-${i}`}))]));
});
