import {lookup as dnsLookup} from 'node:dns/promises';
import {request as httpsRequest} from 'node:https';
import {isIP} from 'node:net';

export const PAGE_READER_LIMITS = Object.freeze({bytes:512*1024, timeoutMs:15000, redirects:3, title:200, text:12000});

export class PageReaderError extends Error {
 constructor(code, status, message) {
  super(message);
  this.name = 'PageReaderError';
  this.code = code;
  this.status = status;
 }
}

const unsafe = () => new PageReaderError('UNSAFE_URL',400,'Use a public HTTPS page without sign-in credentials.');
const unavailable = () => new PageReaderError('PAGE_UNAVAILABLE',422,'This page is unavailable or requires sign-in.');
const unsupported = () => new PageReaderError('UNSUPPORTED_PAGE',415,'Only UTF-8 HTML and plain-text pages can be read.');
const tooLarge = () => new PageReaderError('PAGE_TOO_LARGE',413,'This page is too large to read.');
const networkError = () => new PageReaderError('PAGE_NETWORK_ERROR',502,'The public page could not be read.');
const sensitiveQuery = /^(?:access[-_]?token|refresh[-_]?token|id[-_]?token|api[-_]?key|authorization|authentication|auth|token|jwt|bearer|session(?:[-_]?id)?|sid|credential|password|passwd|secret|client[-_]?secret|signature|sig|key|x-amz-(?:credential|security-token|signature)|x-goog-(?:credential|signature))$/i;

function publicUrl(value, base) {
 if(typeof value !== 'string' || value.length > 4096 || /[\u0000-\u0020\u007f\\]/u.test(value)) throw unsafe();
 let url;
 try { url = new URL(value,base); } catch { throw unsafe(); }
 const host = url.hostname.toLowerCase().replace(/\.$/,'');
 if(url.protocol !== 'https:' || (url.port && url.port !== '443') || url.username || url.password || !host || isIP(host.replace(/^\[|\]$/g,''))) throw unsafe();
 // Require a fully qualified DNS hostname, never a local/service discovery name.
 if(host.length > 253 || !host.includes('.') || !host.split('.').every(label => /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/.test(label)) || /(?:^|\.)(?:localhost|local|localdomain|internal|intranet|lan|home|corp|onion|invalid|test|example|arpa)$/.test(host)) throw unsafe();
 for(const key of url.searchParams.keys()) if(sensitiveQuery.test(key)) throw unsafe();
 url.hostname = host;
 url.hash = '';
 if(url.href.length > 4096) throw unsafe();
 return url;
}

function publicAddress(address) {
 const family = isIP(address);
 if(family === 4) {
  const octets = address.split('.').map(Number);
  const [a,b,c] = octets;
  return !(a === 0 || a === 10 || a === 127 || a >= 224 ||
   (a === 100 && b >= 64 && b <= 127) || (a === 169 && b === 254) ||
   (a === 172 && b >= 16 && b <= 31) || (a === 192 && (b === 168 || (b === 0 && (c === 0 || c === 2)) || (b === 88 && c === 99))) ||
   (a === 198 && (b === 18 || b === 19 || (b === 51 && c === 100))) || (a === 203 && b === 0 && c === 113));
 }
 if(family !== 6 || address.includes('.') || address.includes('%')) return false;
 const halves = address.toLowerCase().split('::');
 const left = halves[0] ? halves[0].split(':') : [];
 const right = halves[1] ? halves[1].split(':') : [];
 const words = (halves.length === 1 ? left : [...left,...Array(8-left.length-right.length).fill('0'),...right]).map(word => parseInt(word,16));
 const [first,second] = words;
 // A conservative allowlist of assigned global-unicast allocations also excludes
 // mapped/NAT64, local, multicast, transition, special-use and unassigned space.
 if(first === 0x2001) {
  if(second === 0x0db8) return false;
  return (second >= 0x0200 && second < 0x1000) || (second >= 0x1200 && second < 0x4e00) ||
   (second >= 0x5000 && second < 0x6000) || (second >= 0x8000 && second < 0xc000);
 }
 if(first === 0x2003) return second < 0x4000;
 if(first === 0x2610 || first === 0x2620) return second < 0x0200;
 return [0x2400,0x2600,0x2800,0x2a00,0x2a10,0x2c00].includes(first & 0xfff0);
}

const entities = Object.freeze({amp:'&',lt:'<',gt:'>',quot:'"',apos:"'",nbsp:' ',ensp:' ',emsp:' ',thinsp:' ',ndash:'–',mdash:'—',hellip:'…',lsquo:'‘',rsquo:'’',ldquo:'“',rdquo:'”',copy:'©',reg:'®',trade:'™',bull:'•',middot:'·',euro:'€',pound:'£',yen:'¥',cent:'¢'});
function decodeEntities(text) {
 return text.replace(/&(#x[0-9a-f]{1,8}|#[0-9]{1,10}|[a-z][a-z0-9]{1,15});/gi,(whole,entity) => {
  if(entity[0] !== '#') return entities[entity.toLowerCase()] ?? whole;
  const point = entity[1].toLowerCase() === 'x' ? parseInt(entity.slice(2),16) : Number(entity.slice(1));
  return point > 0 && point <= 0x10ffff && !(point >= 0xd800 && point <= 0xdfff) ? String.fromCodePoint(point) : ' ';
 });
}
function cleanText(text) {
 return decodeEntities(text).replace(/[\u0000-\u001f\u007f-\u009f]/g,' ').replace(/\s+/gu,' ').trim();
}
function boundedText(text,limit) {
 const clipped = text.slice(0,limit);
 return /[\ud800-\udbff]$/.test(clipped) ? clipped.slice(0,-1) : clipped;
}
function removeHtmlBlocks(body,names) {
 const fragments = [];
 let cursor = 0;
 let hidden;
 // Advance past each complete tag once. Malformed runs of '<' cannot cause
 // failed closing-tag searches to repeatedly scan the full response body.
 while(cursor < body.length) {
  if(hidden) {
   const closing = new RegExp('<\\/'+hidden+'\\s*>','ig');
   closing.lastIndex = cursor;
   const match = closing.exec(body);
   if(!match) break;
   cursor = match.index+match[0].length;
   hidden = undefined;
   fragments.push(' ');
   continue;
  }
  const start = body.indexOf('<',cursor);
  if(start < 0) { fragments.push(body.slice(cursor)); break; }
  fragments.push(body.slice(cursor,start));
  if(body.startsWith('<!--',start)) {
   const end = body.indexOf('-->',start+4);
   fragments.push(' ');
   if(end < 0) break;
   cursor = end+3;
   continue;
  }
  const end = body.indexOf('>',start+1);
  if(end < 0) break;
  const tag = /^\s*(\/)?([a-z][a-z0-9:-]*)\b/i.exec(body.slice(start+1,end));
  const name = tag?.[2].toLowerCase();
  if(tag && !tag[1] && names.has(name)) {
   hidden = name;
   fragments.push(' ');
  } else fragments.push(body.slice(start,end+1));
  cursor = end+1;
 }
 return fragments.join('');
}
function readablePage(body, type, url) {
 let title = url.hostname;
 let text = body;
 if(type === 'text/html') {
  // Bounded string processing only: no DOM, JavaScript, subrequests or crawling.
  const safeHtml = removeHtmlBlocks(body,new Set(['script','style','noscript','template','svg','iframe','object']));
  const titleOpen = /<title\b[^<>]*>/i.exec(safeHtml);
  if(titleOpen) {
   const start = titleOpen.index+titleOpen[0].length;
   const titleClose = /<\/title\s*>/i.exec(safeHtml.slice(start));
   if(titleClose) title = cleanText(safeHtml.slice(start,start+titleClose.index).replace(/<[^>]*(?:>|$)/g,' ')) || title;
  }
  const html = removeHtmlBlocks(safeHtml,new Set(['head','nav','footer']));
  text = html.replace(/<[^>]*(?:>|$)/g,' ');
  if(/<input\b[^<>]*\btype\s*=\s*(?:["']password["']|password\b)/i.test(html) && /\b(?:sign\s*in|log\s*in|login)\b/i.test(cleanText(text))) throw unavailable();
 }
 text = cleanText(text);
 if(!text) throw new PageReaderError('EMPTY_PAGE',422,'This page has no readable text.');
 return {title:boundedText(cleanText(title),PAGE_READER_LIMITS.title),url:url.href,text:boundedText(text,PAGE_READER_LIMITS.text)};
}

/** Read one public page. resolve/request are low-level test ports; no fetch fallback. */
export async function readPublicPage(value, {signal,resolve=dnsLookup,request=httpsRequest,timeoutMs=PAGE_READER_LIMITS.timeoutMs} = {}) {
 let stopError;
 let activeRequest;
 let activeResponse;
 let rejectStopped;
 const stopped = new Promise((_,reject) => { rejectStopped = reject; });
 function stop(error) {
  if(stopError) return;
  stopError = error;
  rejectStopped(error);
  activeResponse?.destroy();
  activeRequest?.destroy();
 }
 const onAbort = () => stop(new PageReaderError('PAGE_ABORTED',499,'Reading the page was cancelled.'));
 const duration = Number.isFinite(timeoutMs) && timeoutMs > 0 ? Math.min(timeoutMs,PAGE_READER_LIMITS.timeoutMs) : PAGE_READER_LIMITS.timeoutMs;
 const deadline = performance.now()+duration;
 const onTimeout = () => stop(new PageReaderError('PAGE_TIMEOUT',504,'Reading the page timed out.'));
 const timer = setTimeout(onTimeout,duration);
 const checkStopped = () => {
  if(!stopError && performance.now() >= deadline) onTimeout();
  if(stopError) throw stopError;
 };
 signal?.addEventListener('abort',onAbort,{once:true});
 if(signal?.aborted) onAbort();

 async function readHop(url) {
  checkStopped();
  let addresses;
  try { addresses = await resolve(url.hostname,{all:true,verbatim:true}); } catch { checkStopped(); throw networkError(); }
  checkStopped(); // A late DNS result must never open a connection after cancellation.
  if(!Array.isArray(addresses) || !addresses.length || addresses.some(item => !item || !publicAddress(item.address) || item.family !== isIP(item.address))) throw unsafe();
  const pinned = addresses.find(address => address.family === 4) ?? addresses[0];
  return new Promise((accept,reject) => {
   let settled = false;
   function finish(error,value) {
    if(settled) return;
    settled = true;
    if(error) {
     reject(error instanceof PageReaderError ? error : networkError());
     activeResponse?.destroy();
     activeRequest?.destroy();
    } else accept(value);
   }
   function onResponse(response) {
    activeResponse = response;
    response.on('error',error => finish(error));
    if(stopError || settled) { response.destroy(); return; }
    const status = response.statusCode;
    if([301,302,303,307,308].includes(status)) {
     const location = response.headers.location;
     if(typeof location !== 'string' || !location) { finish(unavailable()); return; }
     finish(null,{redirect:location});
     response.destroy();
     return;
    }
    if(status !== 200) { finish(unavailable()); return; }
    const contentType = response.headers['content-type'];
    if(typeof contentType !== 'string') { finish(unsupported()); return; }
    const type = contentType.split(';')[0].trim().toLowerCase();
    const charset = /;\s*charset\s*=\s*(?:"([^"]*)"|'([^']*)'|([^;\s]*))/i.exec(contentType);
    const encoding = response.headers['content-encoding'];
    if(!['text/html','text/plain'].includes(type) || (charset && !/^utf-?8$/i.test(charset[1] ?? charset[2] ?? charset[3])) || (encoding && String(encoding).trim().toLowerCase() !== 'identity')) { finish(unsupported()); return; }
    const length = response.headers['content-length'];
    if(length !== undefined && (!/^\d+$/.test(String(length)) || Number(length) > PAGE_READER_LIMITS.bytes)) { finish(tooLarge()); return; }
    const chunks = [];
    let bytes = 0;
    response.on('data',chunk => {
     if(settled || stopError) return;
     const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
     bytes += buffer.length;
     if(bytes > PAGE_READER_LIMITS.bytes) { finish(tooLarge()); return; }
     chunks.push(buffer);
    });
    response.on('aborted',() => finish(networkError()));
    response.on('end',() => {
     if(settled || stopError) return;
     try {
      const body = new TextDecoder('utf-8',{fatal:true}).decode(Buffer.concat(chunks,bytes));
      finish(null,{page:readablePage(body,type,url)});
     } catch(error) { finish(error instanceof PageReaderError ? error : unsupported()); }
    });
    response.on('close',() => { if(!settled && !stopError) finish(networkError()); });
   }
   try {
    activeResponse = undefined;
    activeRequest = request({
     protocol:'https:',hostname:url.hostname,port:443,path:url.pathname+url.search,
     method:'GET',servername:url.hostname,rejectUnauthorized:true,agent:false,family:pinned.family,autoSelectFamily:false,
     headers:{Accept:'text/html, text/plain;q=0.9','Accept-Encoding':'identity','User-Agent':'DuaPublicPageReader/1.0'},
     lookup(host,options,callback) {
      if(typeof options === 'function') { callback = options; options = {}; }
      if(stopError) { callback(stopError); return; }
      if(host !== url.hostname) { callback(unsafe()); return; }
      if(options?.all) callback(null,[{address:pinned.address,family:pinned.family}]);
      else callback(null,pinned.address,pinned.family);
     }
    },onResponse);
    activeRequest.on('error',error => finish(error));
    checkStopped();
    activeRequest.end();
   } catch(error) { finish(error); }
  });
 }

 async function run() {
  checkStopped();
  let url = publicUrl(value);
  for(let redirects=0;;redirects++) {
   const result = await readHop(url);
   checkStopped();
   if(result.page) return result.page;
   if(redirects >= PAGE_READER_LIMITS.redirects) throw new PageReaderError('TOO_MANY_REDIRECTS',422,'This page redirects too many times.');
   url = publicUrl(result.redirect,url);
  }
 }
 try { return await Promise.race([run(),stopped]); }
 catch(error) { throw error instanceof PageReaderError ? error : networkError(); }
 finally { clearTimeout(timer); signal?.removeEventListener('abort',onAbort); }
}
