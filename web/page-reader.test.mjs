import test from 'node:test';
import assert from 'node:assert/strict';
import {EventEmitter} from 'node:events';
import {PassThrough} from 'node:stream';
import {PageReaderError,PAGE_READER_LIMITS,readPublicPage} from './page-reader.mjs';

const publicV4 = {address:'93.184.216.34',family:4};
const publicV6 = {address:'2606:4700:4700::1111',family:6};
function ports(pages=[{body:'Readable public page.'}],resolve=async()=>[publicV4]) {
 const calls = [];
 const responses = [];
 const requests = [];
 function request(options,onResponse) {
  const req = new EventEmitter();
  req.destroyed = false;
  req.destroy = () => { req.destroyed = true; };
  calls.push(options);
  requests.push(req);
  req.end = () => queueMicrotask(() => {
   if(req.destroyed) return;
   const page = pages[calls.indexOf(options)] ?? {};
   if(page.error) { req.emit('error',page.error); return; }
   const res = new PassThrough();
   res.statusCode = page.status ?? 200;
   res.headers = {'content-type':'text/plain; charset=utf-8',...page.headers};
   responses.push(res);
   onResponse(res);
   queueMicrotask(() => {
    if(res.destroyed || page.stall) return;
    for(const chunk of page.chunks ?? [page.body ?? 'Public text']) {
     if(res.destroyed) break;
     res.write(chunk);
    }
    if(!res.destroyed) res.end();
   });
  });
  return req;
 }
 return {resolve,request,calls,responses,requests};
}
const errorCode = code => error => {
 assert.ok(error instanceof PageReaderError);
 assert.equal(error.code,code);
 assert.equal(typeof error.status,'number');
 return true;
};

test('pins the validated DNS address while retaining HTTPS hostname and TLS SNI',async()=>{
 const dnsCalls = [];
 const mock = ports(undefined,async(host,options)=>{dnsCalls.push({host,options});return [publicV6,publicV4];});
 const page = await readPublicPage('https://Example.COM.:443/article?lang=en#section',mock);
 assert.deepEqual(page,{title:'example.com',url:'https://example.com/article?lang=en',text:'Readable public page.'});
 assert.deepEqual(dnsCalls,[{host:'example.com',options:{all:true,verbatim:true}}]);
 const options = mock.calls[0];
 assert.equal(options.hostname,'example.com');
 assert.equal(options.servername,'example.com');
 assert.equal(options.rejectUnauthorized,true);
 assert.equal(options.protocol,'https:');
 assert.equal(options.port,443);
 assert.equal(options.agent,false);
 assert.equal(options.family,4);
 assert.equal(options.autoSelectFamily,false);
 assert.equal(options.path,'/article?lang=en');
 assert.equal(options.method,'GET');
 assert.equal(options.headers['Accept-Encoding'],'identity');
 for(const key of Object.keys(options.headers)) assert.ok(!/cookie|authorization|token|api.key/i.test(key));
 let result;
 options.lookup('example.com',{},(error,address,family)=>{assert.equal(error,null);result={address,family};});
 assert.deepEqual(result,publicV4);
 options.lookup('example.com',{all:true},(error,addresses)=>{assert.equal(error,null);assert.deepEqual(addresses,[publicV4]);});
 options.lookup('other.example.com',{},error=>assert.equal(error.code,'UNSAFE_URL'));
 assert.equal(dnsCalls.length,1,'pinned lookup never re-resolves DNS');
});

test('URL normalization cannot turn alternate IP spellings or local names into connections',async()=>{
 const inputs = [
  'http://example.com/','https://example.com:8443/','https://user:secret@example.com/',
  'https://127.0.0.1/','https://2130706433/','https://0177.0.0.1/','https://0x7f000001/','https://%31%32%37.0.0.1/',
  'https://[::1]/','https://[::ffff:7f00:1]/','https://[2606:4700::1111]/',
  'https://localhost/','https://localhost./','https://machine.local/','https://machine.localdomain/',
  'https://metadata.google.internal/','https://private.home.arpa/','https://intranet/','https://site.onion/',
  'https://exa\tmple.com/','https://example.com/\narticle','https://example.com\\@evil.com/',
  'https://example.com/?access_token=secret','https://example.com/?%61pi_key=secret','https://example.com/?X-Amz-Signature=secret',
  'https://example.com/'+ '🙂'.repeat(1000)
 ];
 for(const input of inputs) {
  let dns = 0;
  const mock = ports(undefined,async()=>{dns++;return [publicV4];});
  await assert.rejects(readPublicPage(input,mock),errorCode('UNSAFE_URL'),input);
  assert.equal(dns,0,input);
  assert.equal(mock.calls.length,0,input);
 }
});

test('all DNS answers must be public including mixed, private, reserved and IPv4-mapped answers',async()=>{
 const addresses = [
  '0.1.2.3','10.0.0.1','100.64.0.1','100.127.255.254','127.0.0.2','169.254.169.254',
  '172.16.0.1','172.31.255.254','192.168.0.1','192.0.0.8','192.0.2.1','192.88.99.1',
  '198.18.0.1','198.19.255.254','198.51.100.1','203.0.113.1','224.0.0.1','240.0.0.1','255.255.255.255',
  '::','::1','::ffff:8.8.8.8','::ffff:0808:0808','64:ff9b::808:808','100::1',
  'fc00::1','fd00::1','fe80::1','ff02::1','2001::1','2001:2::1','2001:db8::1','2002:0808:0808::1',
  '2000::1','2003:4000::1','2410::1','2610:200::1','2620:200::1','2a20::1','3fff::1','4000::1'
 ];
 for(const address of addresses) {
  const family = address.includes(':') ? 6 : 4;
  const mock = ports(undefined,async()=>[publicV4,{address,family}]);
  await assert.rejects(readPublicPage('https://example.com/',mock),errorCode('UNSAFE_URL'),address);
  assert.equal(mock.calls.length,0,address);
 }
 for(const answer of [[],[{address:'not-an-ip',family:4}],[{address:'8.8.8.8',family:6}],null]) {
  const mock = ports(undefined,async()=>answer);
  await assert.rejects(readPublicPage('https://example.com/',mock),errorCode('UNSAFE_URL'));
  assert.equal(mock.calls.length,0);
 }
});

test('accepts public IPv4 and assigned IPv6 boundaries',async()=>{
 for(const address of ['8.8.8.8','1.1.1.1','100.63.255.254','100.128.0.1','172.15.0.1','172.32.0.1',
  '2001:200::1','2001:4860::8888','2003:3fff::1','240f:ffff::1','2600::1','260f:ffff::1',
  '2610:1ff::1','2620:fe::fe','2800::1','2a0f:ffff::1','2a10::1','2a1f:ffff::1','2c00::1']) {
  const family = address.includes(':') ? 6 : 4;
  const mock = ports(undefined,async()=>[{address,family}]);
  await readPublicPage('https://example.com/',mock);
  assert.equal(mock.calls[0].family,family,address);
  mock.calls[0].lookup('example.com',{},(error,pinned)=>{assert.equal(error,null);assert.equal(pinned,address);});
 }
});

test('each redirect is revalidated and a rebinding answer never opens a second request',async()=>{
 const dnsHosts = [];
 const mock = ports([{status:302,headers:{location:'https://other.example.com/story'}},{body:'Final public text'}],async(host)=>{
  dnsHosts.push(host);return [dnsHosts.length === 1 ? publicV4 : publicV6];
 });
 const page = await readPublicPage('https://example.com/start',mock);
 assert.deepEqual(dnsHosts,['example.com','other.example.com']);
 assert.equal(page.url,'https://other.example.com/story');
 assert.equal(page.text,'Final public text');
 assert.equal(mock.calls[1].servername,'other.example.com');
 assert.ok(mock.responses[0].destroyed);

 for(const location of ['http://example.com/','https://127.0.0.1/','https://user:secret@example.com/','https://example.com/?token=secret']) {
  const unsafeMock = ports([{status:302,headers:{location}}]);
  await assert.rejects(readPublicPage('https://example.com/',unsafeMock),errorCode('UNSAFE_URL'));
  assert.equal(unsafeMock.calls.length,1);
 }
 let lookups = 0;
 const rebound = ports([{status:302,headers:{location:'/again'}}],async()=>++lookups === 1 ? [publicV4] : [{address:'127.0.0.1',family:4}]);
 await assert.rejects(readPublicPage('https://example.com/',rebound),errorCode('UNSAFE_URL'));
 assert.equal(rebound.calls.length,1);
 assert.equal(lookups,2);
});

test('redirects stop after three hops and malformed redirects are rejected',async()=>{
 const mock = ports(Array.from({length:5},()=>({status:301,headers:{location:'/again'}})));
 await assert.rejects(readPublicPage('https://example.com/',mock),errorCode('TOO_MANY_REDIRECTS'));
 assert.equal(mock.calls.length,4);
 for(const headers of [{},{location:['/a','/b']}]) {
  const malformed = ports([{status:302,headers}]);
  await assert.rejects(readPublicPage('https://example.com/',malformed),errorCode('PAGE_UNAVAILABLE'));
 }
});

test('HTML extraction removes executable and navigation text and decodes entities',async()=>{
 const mock = ports([{headers:{'content-type':'text/html; charset="UTF-8"'},body:`<!doctype html>
  <html><head><title> News &amp; Updates &#x1f642; </title><style>hidden css</style><script>hidden script</script></head>
  <body><!-- hidden comment --><nav>hidden navigation</nav><h1>Public &lt;article&gt;</h1>
  <p>Hello&nbsp;world. &#169; &#8212; &#x1f642;</p><script>if (a < b) document.write('hidden attack')</script>
  <noscript>hidden fallback</noscript><template>hidden template</template><svg>hidden svg</svg><footer>hidden footer</footer>
  <p>Second <strong>paragraph</strong>.</p></body></html>`}]);
 assert.deepEqual(await readPublicPage('https://example.com/news',mock),{
  title:'News & Updates 🙂',url:'https://example.com/news',text:'Public <article> Hello world. © — 🙂 Second paragraph .'
 });
 assert.equal(mock.calls.length,1);
});

test('plain text and extracted fields remain bounded with normalized whitespace',async()=>{
 const mock = ports([{headers:{'content-type':'text/html'},body:`<title>${'T'.repeat(400)}</title><p>${'A'.repeat(20000)}</p>`}]);
 const page = await readPublicPage('https://example.com/',mock);
 assert.equal(page.title.length,PAGE_READER_LIMITS.title);
 assert.equal(page.text.length,PAGE_READER_LIMITS.text);
 const plain = await readPublicPage('https://example.com/',ports([{body:'  नमस्ते\r\n\t world\0  '}])) ;
 assert.equal(plain.text,'नमस्ते world');
 const cleanTitle = await readPublicPage('https://example.com/',ports([{headers:{'content-type':'text/html'},body:'<title>One\r\n\u0080Two</title><p>Visible</p>'}]));
 assert.equal(cleanTitle.title,'One Two');
 const unicode = await readPublicPage('https://example.com/',ports([{body:'a'.repeat(11999)+'🙂'}]));
 assert.equal(unicode.text.length,11999,'truncation never splits a UTF-16 surrogate pair');
});

test('malformed HTML at the byte limit is processed without repeated failed scans',async()=>{
 const bodies = ['<','<title>','<script','<input type='].map(fragment=>'<p>Visible</p>'+fragment.repeat(Math.floor((PAGE_READER_LIMITS.bytes-40)/fragment.length)));
 bodies.push('<p>Visible</p><'+' '.repeat(PAGE_READER_LIMITS.bytes-40)+'>');
 for(const body of bodies) {
  const mock = ports([{headers:{'content-type':'text/html'},body}]);
  const start = performance.now();
  const page = await readPublicPage('https://example.com/',mock);
  assert.ok(page.text.startsWith('Visible'));
  assert.ok(page.text.length <= PAGE_READER_LIMITS.text);
  assert.ok(performance.now()-start < 2000,'bounded HTML parsing must remain linear');
 }
});

test('byte cap applies to advertised length and streamed bytes even when Content-Length lies',async()=>{
 for(const page of [
  {headers:{'content-length':String(PAGE_READER_LIMITS.bytes+1)},stall:true},
  {headers:{'content-length':'1'},chunks:[Buffer.alloc(PAGE_READER_LIMITS.bytes,65),Buffer.from('overflow')]},
  {chunks:[Buffer.alloc(PAGE_READER_LIMITS.bytes+1,65)]}
 ]) {
  const mock = ports([page]);
  await assert.rejects(readPublicPage('https://example.com/',mock),errorCode('PAGE_TOO_LARGE'));
  assert.ok(mock.responses[0].destroyed);
  assert.ok(mock.requests[0].destroyed);
 }
 const exact = ports([{chunks:[Buffer.alloc(PAGE_READER_LIMITS.bytes,65)]}]);
 assert.equal((await readPublicPage('https://example.com/',exact)).text.length,PAGE_READER_LIMITS.text);
});

test('unsupported MIME, charset, compression and invalid UTF-8 are rejected and disposed',async()=>{
 for(const page of [
  {headers:{'content-type':'application/pdf'}},{headers:{'content-type':undefined}},
  {headers:{'content-type':'text/html; charset=iso-8859-1'}},
  {headers:{'content-encoding':'gzip'}},{headers:{'content-encoding':'br'}},
  {chunks:[Buffer.from([0xc3,0x28])]}
 ]) {
  const mock = ports([page]);
  await assert.rejects(readPublicPage('https://example.com/',mock),errorCode('UNSUPPORTED_PAGE'));
  assert.ok(mock.responses[0].destroyed);
 }
});

test('empty, unavailable, and sign-in HTML pages provide sanitized errors',async()=>{
 for(const status of [401,403,404,500,206]) {
  const mock = ports([{status,body:'private diagnostic'}]);
  await assert.rejects(readPublicPage('https://example.com/',mock),errorCode('PAGE_UNAVAILABLE'));
 }
 await assert.rejects(readPublicPage('https://example.com/',ports([{body:'   '}])) ,errorCode('EMPTY_PAGE'));
 await assert.rejects(readPublicPage('https://example.com/',ports([{headers:{'content-type':'text/html'},body:'<script>secret</script>'}])) ,errorCode('EMPTY_PAGE'));
 await assert.rejects(readPublicPage('https://example.com/',ports([{headers:{'content-type':'text/html'},body:'<h1>Sign in</h1><form><input type="password"></form>'}])) ,errorCode('PAGE_UNAVAILABLE'));
 for(const mock of [ports([{error:Error('sensitive socket diagnostic')}]),ports(undefined,async()=>{throw Error('sensitive resolver diagnostic');})]) {
  await assert.rejects(readPublicPage('https://example.com/',mock),error=>{
   assert.equal(error.code,'PAGE_NETWORK_ERROR');
   assert.ok(!error.message.includes('sensitive'));
   assert.equal(error.cause,undefined);
   return true;
  });
 }
});

test('pre-aborted calls and cancellation during DNS never open a connection',async()=>{
 const already = new AbortController();
 already.abort();
 let dnsCalls = 0;
 const pre = ports(undefined,async()=>{dnsCalls++;return [publicV4];});
 await assert.rejects(readPublicPage('https://example.com/',{...pre,signal:already.signal}),errorCode('PAGE_ABORTED'));
 assert.equal(dnsCalls,0);
 assert.equal(pre.calls.length,0);

 const controller = new AbortController();
 let finishDns;
 const during = ports(undefined,()=>new Promise(resolve=>{finishDns=resolve;}));
 const result = readPublicPage('https://example.com/',{...during,signal:controller.signal});
 controller.abort();
 await assert.rejects(result,errorCode('PAGE_ABORTED'));
 finishDns([publicV4]);
 await new Promise(resolve=>setImmediate(resolve));
 assert.equal(during.calls.length,0,'late DNS resolution must not connect');
});

test('total timeout covers DNS and stalled bodies; signal destroys active transport',async()=>{
 let finishDns;
 const dns = ports(undefined,()=>new Promise(resolve=>{finishDns=resolve;}));
 await assert.rejects(readPublicPage('https://example.com/',{...dns,timeoutMs:10}),errorCode('PAGE_TIMEOUT'));
 finishDns([publicV4]);
 await new Promise(resolve=>setImmediate(resolve));
 assert.equal(dns.calls.length,0);

 const stalled = ports([{stall:true}]);
 await assert.rejects(readPublicPage('https://example.com/',{...stalled,timeoutMs:10}),errorCode('PAGE_TIMEOUT'));
 assert.ok(stalled.responses[0].destroyed);
 assert.ok(stalled.requests[0].destroyed);

 const controller = new AbortController();
 const aborted = ports([{stall:true}]);
 const pending = readPublicPage('https://example.com/',{...aborted,signal:controller.signal});
 await new Promise(resolve=>setImmediate(resolve));
 controller.abort();
 await assert.rejects(pending,errorCode('PAGE_ABORTED'));
 assert.ok(aborted.responses[0].destroyed);
 assert.ok(aborted.requests[0].destroyed);
});
