import { test } from 'node:test';
import assert from 'node:assert/strict';
import { guardChatAccess } from './access-policy.mjs';
const id='11111111-1111-4111-8111-111111111111';
const user={id,email:'owner@example.com'};
const request=headers=>new Request('https://dua.example/api/chat',{method:'POST',headers,body:'{}'});
const env={url:'https://project.supabase.co',key:'public-key'};
test('access policy defaults to anonymous local-compatible online mode',async()=>{const result=await guardChatAccess(request(),{fetcher:()=>assert.fail('No auth call')});assert.equal(result.allowed,true);assert.equal(result.user,null);});
test('personal owner restriction verifies identity and rejects other accounts',async()=>{
 let calls=0;const fetcher=async()=>{calls++;return Response.json(user);};
 const settings={...env,allowedEmail:' OWNER@example.com '};
 const allowed=await guardChatAccess(request({authorization:'Bearer token'}),{env:settings,fetcher});assert.equal(allowed.allowed,true);assert.equal(allowed.user.id,id);
 const guest=await guardChatAccess(request(),{env:settings,fetcher});assert.equal(guest.response.status,401);assert.equal(calls,1);
 const other=await guardChatAccess(request({authorization:'Bearer token'}),{env:settings,fetcher:async()=>Response.json({...user,email:'other@example.com'})});assert.equal(other.response.status,403);
});
test('required login refuses missing setup and account outages instead of silently opening AI',async()=>{
 assert.equal((await guardChatAccess(request(),{env:{requireLogin:true},fetcher:()=>assert.fail('No config')})).response.status,503);
 assert.equal((await guardChatAccess(request({authorization:'Bearer token'}),{env:{...env,requireLogin:true},fetcher:async()=>{throw Error('offline')}})).response.status,502);
});
test('expired access refreshes and forwards secure session cookies through the guard',async()=>{
 const result=await guardChatAccess(request({cookie:'dua_access=expired; dua_refresh=refresh'}),{env:{...env,requireLogin:true},fetcher:async url=>url.endsWith('/auth/v1/user')?Response.json({error:'expired'},{status:401}):Response.json({access_token:'new-token',refresh_token:'new-refresh',expires_in:3600,user})});assert.equal(result.allowed,true);assert.equal(result.headers.getSetCookie().length,2);
});
test('durable request budget uses verified owner, server admin key and fails closed at daily limit',async()=>{
 const calls=[];let used=1;const settings={...env,dailyRequests:2,serviceRoleKey:'sb_secret_admin-test'};
 const fetcher=async(url,options)=>{calls.push({url,options});return url.endsWith('/auth/v1/user')?Response.json(user):Response.json({allowed:used<3,used:Math.min(used++,2),resetAt:Math.floor(Date.now()/1000)+600});};
 const first=await guardChatAccess(request({authorization:'Bearer token'}),{env:settings,fetcher});assert.equal(first.allowed,true);assert.deepEqual(JSON.parse(calls[1].options.body),{account_id:id,daily_limit:2});assert.equal(calls[1].options.headers.Authorization,'Bearer sb_secret_admin-test');assert.equal(first.headers.get('X-Dua-Requests-Remaining'),'1');
 assert.equal((await guardChatAccess(request({authorization:'Bearer token'}),{env:settings,fetcher})).allowed,true);
 const denied=await guardChatAccess(request({authorization:'Bearer token'}),{env:settings,fetcher});assert.equal(denied.response.status,429);assert.ok(Number(denied.response.headers.get('Retry-After'))>0);assert.ok(!(await denied.response.text()).includes('sb_secret'));
});
test('daily request budget rejects bad limits, missing admin and missing migration',async()=>{
 const fetcher=async url=>url.endsWith('/auth/v1/user')?Response.json(user):Response.json({error:'private details'},{status:404});
 assert.equal((await guardChatAccess(request(),{env:{...env,dailyRequests:'-1'},fetcher:()=>assert.fail('Bad setup')})).response.status,503);
 assert.equal((await guardChatAccess(request({authorization:'Bearer token'}),{env:{...env,dailyRequests:5},fetcher})).response.status,503);
 const result=await guardChatAccess(request({authorization:'Bearer token'}),{env:{...env,dailyRequests:5,serviceRoleKey:'sb_secret_admin-test'},fetcher});assert.equal(result.response.status,503);assert.ok(!(await result.response.text()).includes('private details'));
});

test('login policy accepts explicit 1 and fails closed for malformed configuration',async()=>{
 for (const settings of [{...env,DUA_REQUIRE_LOGIN:'1'},{...env,requireLogin:'1'},{...env,requireLogin:true}]) { const blocked=await guardChatAccess(request(),{env:settings,fetcher:()=>assert.fail('No guest auth call')});assert.equal(blocked.response.status,401); }
 for (const settings of [{...env,requireLogin:'tru'},{...env,dailyRequests:true},{...env,dailyRequests:'1e3'},{...env,dailyRequests:'1.5'}]) { const blocked=await guardChatAccess(request(),{env:settings,fetcher:()=>assert.fail('No invalid config call')});assert.equal(blocked.response.status,503); }
});
