// Shared fakes for backend tests: no network and no real keys.
export function sseResponse(chunks, { status = 200, delayMs = 0 } = {}) {
  const encoder = new TextEncoder();
  const body = new ReadableStream({
    async start(controller) {
      for (const chunk of chunks) {
        if (delayMs) await new Promise(resolve => setTimeout(resolve, delayMs));
        controller.enqueue(encoder.encode(chunk));
      }
      controller.close();
    }
  });
  return new Response(body, { status, headers: { 'content-type': 'text/event-stream' } });
}
export const sse = (data, event) => (event ? `event: ${event}\n` : '') + `data: ${typeof data === 'string' ? data : JSON.stringify(data)}\n\n`;

/** Records calls and replies with the queued responses in order. */
export function mockFetch(...responses) {
  const calls = [];
  const fetcher = async (url, init = {}) => {
    calls.push({ url: String(url), init, body: init.body ? JSON.parse(init.body) : undefined });
    const next = responses.shift();
    if (!next) throw new Error('Unexpected fetch ' + url);
    return typeof next === 'function' ? next(url, init) : next;
  };
  fetcher.calls = calls;
  return fetcher;
}

export function chatRequest(body, { headers = {}, signal } = {}) {
  return new Request('https://dua.test/api/chat', { method: 'POST', headers: { 'content-type': 'application/json', ...headers }, body: JSON.stringify(body), signal });
}

/** Parses a Dua SSE response body into the JSON events it carries (heartbeat comments skipped). */
export async function readEvents(response) {
  const text = await response.text();
  return text.split('\n\n').filter(block => block.split('\n').some(line => line.startsWith('data:'))).map(block => {
    const lines = block.split('\n');
    const name = lines.find(line => line.startsWith('event: '))?.slice(7);
    const event = JSON.parse(lines.filter(line => line.startsWith('data: ')).map(line => line.slice(6)).join('\n'));
    if (name !== event.type) throw new Error(`SSE event name ${name} != type ${event.type}`);
    return event;
  });
}

export const env = {
  GEMINI_API_KEY: 'test-gemini-key', GEMINI_MODEL: 'gemini-3.8-flash',
  GROK_API_KEY: 'test-grok-key', GROK_MODEL: 'grok-4.7',
  OPENAI_API_KEY: 'test-openai-key', OPENAI_MODEL: 'gpt-6.1-sol',
  ANTHROPIC_API_KEY: 'test-anthropic-key', ANTHROPIC_MODEL: 'claude-test',
  KIMI_API_KEY: 'test-kimi-key', KIMI_MODEL: 'kimi-test'
};
export const b64 = text => Buffer.from(text).toString('base64');
