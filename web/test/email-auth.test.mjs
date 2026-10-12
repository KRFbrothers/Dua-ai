import test from 'node:test';
import assert from 'node:assert/strict';
import { handleAccount } from '../src/core/account.mjs';
import { mockFetch } from './helpers.mjs';

const id='11111111-1111-4111-8111-111111111111';
const email='owner@example.com';
const user=(extra={})=>({id,email,email_confirmed_at:'2026-10-12T00:00:00Z',...extra});
const session=(extra={})=>({access_token:'fake-access',refresh_token:'fake-refresh',expires_in:3600,user:user(),...extra});
const settings=()=>Response.json({external:{email:true},mailer_autoconfirm:false});
const env=fetcher=>({url:'https://project.supabase.co',key:'public-anon-key',allowedEmail:email,fetcher});
const req=(action,body,method='POST',headers={})=>new Request('https://dua.test/api/account/'+action,{method,
 headers:{'content-type':'application/json','x-dua-native':'1',...headers},body:JSON.stringify(body)});
const credentials={email,password:'test-password-only'};

test('the password route requires PUT and does not accept the legacy POST method',async()=>{
 const fetcher=mockFetch();
 assert.equal((await handleAccount(req('password',{password:credentials.password},'POST'),env(fetcher))).status,405);
 assert.equal(fetcher.calls.length,0);
});

test('email login verifies provider settings, preserves password spaces and delivers only a confirmed owner session',async()=>{
 const fetcher=mockFetch(settings(),Response.json(session()));
 const response=await handleAccount(req('email-login',{email:' OWNER@EXAMPLE.COM ',password:' spaces are valid '}),env(fetcher));
 assert.equal(response.status,200);assert.equal((await response.json()).session.accessToken,'fake-access');
 assert.match(fetcher.calls[1].url,/grant_type=password$/);
 assert.deepEqual(fetcher.calls[1].body,{email,password:' spaces are valid '});
 assert.equal(response.headers.get('cache-control'),'no-store');
});
test('signup without a session reports confirmation instead of falsely signing in',async()=>{
 for(const value of [user({email_confirmed_at:null}),{user:user({email_confirmed_at:null})}]){
  const fetcher=mockFetch(settings(),Response.json(value));
  const response=await handleAccount(req('email-signup',credentials),env(fetcher));
  const body=await response.json();assert.equal(response.status,200);assert.equal(body.confirmationRequired,true);
  assert.equal(body.session,undefined);assert.equal(body.user,undefined);assert.match(body.message,/confirm/);
  assert.match(fetcher.calls[1].url,/\/auth\/v1\/signup$/);
 }
});
test('owner input, native-only access and password validation fail before contacting Supabase',async()=>{
 for(const [action,body,headers,status] of [
  ['email-signup',{...credentials,email:'other@example.com'},{},403],
  ['email-login',{email:'invalid',password:'x'},{},400],
  ['email-signup',{...credentials,password:'short'},{},400],
  ['email-login',{...credentials,password:'x'.repeat(129)},{},400],
  ['email-login',credentials,{'x-dua-native':'0'},400],
  ['email-login',credentials,{origin:'https://evil.test'},403]
 ]){
  const fetcher=mockFetch();assert.equal((await handleAccount(req(action,body,'POST',headers),env(fetcher))).status,status);
  assert.equal(fetcher.calls.length,0);
 }
 const fetcher=mockFetch();assert.equal((await handleAccount(req('email-signup',credentials),{...env(fetcher),allowedEmail:''})).status,503);
 assert.equal(fetcher.calls.length,0);
});
test('disabled email auth, disabled confirmation and unknown settings fail closed',async()=>{
 for(const value of [{external:{email:true},mailer_autoconfirm:true},{external:{email:false},mailer_autoconfirm:false},{}]){
  for(const action of ['email-login','email-signup']){
   const fetcher=mockFetch(Response.json(value));assert.equal((await handleAccount(req(action,credentials),env(fetcher))).status,503);
   assert.equal(fetcher.calls.length,1);
  }
 }
});
test('foreign or unconfirmed session responses are revoked and never delivered',async()=>{
 for(const value of [user({email:'other@example.com'}),user({email_confirmed_at:null})]){
  const fetcher=mockFetch(settings(),Response.json(session({user:value})),new Response(null,{status:204}));
  const response=await handleAccount(req('email-login',credentials),env(fetcher));
  assert.equal(response.status,403);assert.equal((await response.json()).session,undefined);
  assert.match(fetcher.calls[2].url,/logout\?scope=local/);
 }
});
test('upstream errors including rate limits do not echo credentials or detailed auth responses',async()=>{
 for(const status of [400,422,429]){
  const fetcher=mockFetch(settings(),Response.json({message:'private secret test-password-only owner@example.com'},{status}));
  const response=await handleAccount(req('email-signup',credentials),env(fetcher));
  assert.equal(response.status,status===429?429:400);
  const text=await response.text();assert.ok(!text.includes(credentials.password));assert.ok(!text.includes('private secret'));
 }
});
test('setting a password binds the verified owner ID and updates only the current Supabase user',async()=>{
 const fetcher=mockFetch(Response.json(user()),settings(),Response.json(user()));
 const response=await handleAccount(req('password',{password:credentials.password,expectedAccountId:id},'PUT',{authorization:'Bearer current-access'}),env(fetcher));
 assert.equal(response.status,200);assert.equal((await response.json()).ownerId,id);
 assert.deepEqual(fetcher.calls[2].body,{password:credentials.password});assert.equal(fetcher.calls[2].init.headers.Authorization,'Bearer current-access');
 assert.ok(fetcher.calls.every(call=>!call.url.includes('admin')&&!call.url.includes('cloud')));
});
test('password setup rejects sign-out, account mismatch and foreign response without confirming a change',async()=>{
 const signedOut=mockFetch();assert.equal((await handleAccount(req('password',{password:credentials.password,expectedAccountId:id},'PUT'),env(signedOut))).status,401);
 assert.equal(signedOut.calls.length,0);
 const mismatched=mockFetch(Response.json(user()));assert.equal((await handleAccount(req('password',{password:credentials.password,expectedAccountId:'other'},'PUT',{authorization:'Bearer current-access'}),env(mismatched))).status,409);
 assert.equal(mismatched.calls.length,1);
 const foreign=mockFetch(Response.json(user()),settings(),Response.json(user({id:'22222222-2222-4222-8222-222222222222'})));
 const response=await handleAccount(req('password',{password:credentials.password,expectedAccountId:id},'PUT',{authorization:'Bearer current-access'}),env(foreign));
 assert.equal(response.status,502);assert.equal((await response.json()).passwordUpdated,undefined);
});
