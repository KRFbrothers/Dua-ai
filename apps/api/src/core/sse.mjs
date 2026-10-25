/**
 * Dua chat stream wire format (opt-in with `"stream": true` on POST /api/chat).
 *
 * Server-Sent Events, one JSON object per event; the SSE `event:` name always equals `type`:
 *   event: thinking  data: {"type":"thinking","delta":"..."}       reasoning summary text (Reasoning mode only)
 *   event: text      data: {"type":"text","delta":"..."}           answer text
 *   event: sources   data: {"type":"sources","sources":[{id,title,url,domain}]}  web sources: before the text for Brave
 *                    search / Read link, after it for provider-native search (citations arrive with the answer)
 *   event: context   data: {"type":"context","summary":"...","covered":12}  older turns were summarised: the first
 *                    `covered` sent messages are now in `summary`; keep it with the conversation (Phase 2)
 *   event: notice    data: {"type":"notice","message":"..."}        non-fatal note (e.g. provider has no visible thinking)
 *   event: files     data: {"type":"files","files":[{path,transcript}]}  transcripts made for stored audio (keep them with the message)
 *   event: code      data: {"type":"code","index":0,"language":"python","code":"..."}  code the provider ran in its sandbox (Run code, Phase 5)
 *                    data: {"type":"code","index":0,"output":"...","outcome":"ok"}       its output for the same index; outcome ok | error | timeout
 *                    (at most 10 runs per reply; code and output are each capped at 20,000 characters)
 *   event: build     data: {"type":"build","repository":"owner/name","branch":"main","files":[{path,bytes,truncated?}],"rounds":1,"searches":0}
 *                    Build mode (Phase 5B): the repository and the files read so far; sent at the start, after
 *                    each tool round and once more before the answer text (the latest event replaces earlier ones)
 *   event: proposal  data: {"type":"proposal","id":"...","repository":"owner/name","baseBranch":"main","branch":"dua/…",
 *                    "title":"…","summary":"…","createdAt":"…","files":[{"path","action":"create|update|delete",
 *                    "content?","baseSha?","diff","additions","deletions","truncated?"}]}
 *                    Build mode (Phase 5C): a change proposal the model made. Nothing was written; the app shows a review
 *                    card and only the owner's confirmed Create PR tap (POST /api/build/pr) can create a pull request.
 *                    Sent once, right before the answer text.
 *   event: progress  data: {"type":"progress","step":"search","label":"Searching the web…","state":"active|done"}
 *                    Cloud Run Agent only: a user-facing step for the app's live progress UI (the raw details stay
 *                    in thinking). A newer step finishes the earlier ones; clients ignore unknown event types.
 *   event: done      data: {"type":"done","truncated":false,"usage":{...}}  last event of a successful reply
 *   event: saved     owner/chat/message-bound receipt with revision, chat metadata, replyHash and original metadata
 *                    before done, only when persistence was requested (see reply-persistence.mjs)
 *   event: error     data: {"type":"error","message":"...","status":504}    last event of a failed reply
 * Lines starting with ":" are heartbeats sent every 15 s so idle-timeouts on the client measure
 * the gap between bytes rather than total reply time.
 */
export const SSE_HEADERS = Object.freeze({
  'Content-Type': 'text/event-stream; charset=utf-8',
  'Cache-Control': 'no-store',
  'X-Accel-Buffering': 'no',
  'X-Content-Type-Options': 'nosniff'
});
const TYPES = new Set(['thinking', 'text', 'sources', 'notice', 'files', 'context', 'code', 'build', 'proposal', 'saved', 'done', 'error']);

export function formatEvent(event) {
  if (!event || !TYPES.has(event.type)) throw new TypeError('Unknown stream event type.');
  return `event: ${event.type}\ndata: ${JSON.stringify(event)}\n\n`;
}

/**
 * Returns a streaming Response. `run(send, signal)` produces events; it must not throw for
 * expected failures (send an error event instead). Unexpected throws become a generic error event.
 */
export function eventStreamResponse(run, { signal, heartbeatMs = 15000, headers = {}, onUnexpected } = {}) {
  const encoder = new TextEncoder();
  const stop = new AbortController();
  const combined = signal ? AbortSignal.any([signal, stop.signal]) : stop.signal;
  let closed = false;
  let timer;
  const stream = new ReadableStream({
    start(controller) {
      const write = text => {
        if (closed) return;
        try { controller.enqueue(encoder.encode(text)); } catch { closed = true; }
      };
      const send = event => write(formatEvent(event));
      // An initial comment flushes headers through proxies before the provider answers.
      write(': stream\n\n');
      timer = setInterval(() => write(': ping\n\n'), heartbeatMs);
      (async () => {
        try { await run(send, combined); }
        catch (error) {
          onUnexpected?.(error);
          send({ type: 'error', message: 'Connection failed while replying. Partial reply kept; retry.', status: 502 });
        } finally {
          clearInterval(timer);
          if (!closed) { closed = true; try { controller.close(); } catch {} }
        }
      })();
    },
    cancel() {
      closed = true;
      clearInterval(timer);
      stop.abort();
    }
  });
  return new Response(stream, { status: 200, headers: { ...SSE_HEADERS, ...headers } });
}

/** Parses an upstream SSE body into {event, data} records. Bounded so a bad upstream cannot exhaust memory. */
export async function* readSse(body, { maxBufferBytes = 2 * 1024 * 1024 } = {}) {
  if (!body) return;
  const decoder = new TextDecoder();
  let buffer = '';
  const parse = block => {
    let event = 'message';
    const data = [];
    for (const line of block.split('\n')) {
      if (!line || line.startsWith(':')) continue;
      const colon = line.indexOf(':');
      const field = colon < 0 ? line : line.slice(0, colon);
      let value = colon < 0 ? '' : line.slice(colon + 1);
      if (value.startsWith(' ')) value = value.slice(1);
      if (field === 'event') event = value;
      else if (field === 'data') data.push(value);
    }
    return data.length ? { event, data: data.join('\n') } : null;
  };
  let pendingCR = false;
  for await (const chunk of body) {
    let text = decoder.decode(chunk, { stream: true });
    if (pendingCR) { text = '\r' + text; pendingCR = false; }
    // Keep a trailing CR until the next chunk so a CRLF split across chunks is one line break.
    if (text.endsWith('\r')) { pendingCR = true; text = text.slice(0, -1); }
    buffer += text.replace(/\r\n?/g, '\n');
    if (buffer.length > maxBufferBytes) throw Object.assign(new Error('Upstream event too large.'), { status: 502 });
    let index;
    while ((index = buffer.indexOf('\n\n')) >= 0) {
      const record = parse(buffer.slice(0, index));
      buffer = buffer.slice(index + 2);
      if (record) yield record;
    }
  }
  buffer += (decoder.decode() + (pendingCR ? '\r' : '')).replace(/\r\n?/g, '\n');
  const record = parse(buffer);
  if (record) yield record;
}
