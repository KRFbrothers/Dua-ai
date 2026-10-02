declare const Netlify: { env: { get(name: string): string | undefined } };

export default async function handler(req: Request) {
  const json = (data: unknown, status = 200) => Response.json(data, { status, headers: { 'Cache-Control': 'no-store' } });
  const key = Netlify.env.get('GEMINI_API_KEY');
  const model = Netlify.env.get('GEMINI_MODEL') || 'gemini-2.5-flash';
  const url = new URL(req.url);
  if (url.pathname === '/api/config' && req.method === 'GET') return json({ geminiConfigured: Boolean(key), model });
  if (url.pathname !== '/api/chat' || req.method !== 'POST') return json({ error: 'Method not allowed.' }, 405);
  if (req.headers.get('origin') && req.headers.get('origin') !== url.origin) return json({ error: 'Request origin is not allowed.' }, 403);
  if (!key) return json({ error: 'Gemini API key hosting settings mein set karein.' }, 503);
  try {
    const raw = await req.text();
    if (new TextEncoder().encode(raw).length > 262144) return json({ error: 'Message history is too large.' }, 413);
    let data;
    try { data = JSON.parse(raw); } catch { return json({ error: 'Invalid request.' }, 400); }
    if (!['Dua', 'Gemini'].includes(data.model)) return json({ error: 'Dua ya Gemini select karein.' }, 400);
    if (!Array.isArray(data.messages) || !data.messages.length || data.messages.length > 40 || data.messages.some((m: { role: string; text: string }) => !['user', 'model'].includes(m.role) || typeof m.text !== 'string' || !m.text.trim() || m.text.length > 8000) || data.messages.at(-1).role !== 'user') return json({ error: 'Invalid messages.' }, 400);
    const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', 'x-goog-api-key': key },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: 'You are Dua, a helpful AI assistant powered by Gemini. Reply in the language the user uses. Be clear and concise.' }] },
        contents: data.messages.map((m: { role: string; text: string }) => ({ role: m.role, parts: [{ text: m.text }] }))
      }), signal: AbortSignal.timeout(45000)
    });
    if (!response.ok) return json({ error: response.status === 429 ? 'Gemini quota limit aa gayi. Baad mein try karein.' : 'Gemini key ya model configuration check karein.' }, response.status === 429 ? 429 : 502);
    const result = await response.json();
    const text = (result.candidates?.[0]?.content?.parts || []).filter((p: { thought?: boolean; text?: string }) => !p.thought && typeof p.text === 'string').map((p: { text: string }) => p.text).join('\n');
    return text ? json({ text }) : json({ error: 'Gemini ne text response nahi diya. Dobara try karein.' }, 422);
  } catch {
    return json({ error: 'Connection fail hua. Dobara try karein.' }, 502);
  }
}

export const config = { path: ['/api/config', '/api/chat'] };
