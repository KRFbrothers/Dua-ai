export async function handleGemini(req, {key, model, fetcher = fetch}) {
  const json = (data, status = 200) => Response.json(data, { status, headers: { 'Cache-Control': 'no-store' } });
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
    if (!Array.isArray(data.messages) || !data.messages.length || data.messages.length > 40 || data.messages.some((m) => !['user', 'model'].includes(m.role) || typeof m.text !== 'string' || !m.text.trim() || m.text.length > 8000) || data.messages.at(-1).role !== 'user') return json({ error: 'Invalid messages.' }, 400);
    const response = await fetcher(`https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', 'x-goog-api-key': key },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: 'You are Dua, a helpful AI assistant powered by Gemini. Reply in the language the user uses. Be clear and concise.' }] },
        contents: data.messages.map((m) => ({ role: m.role, parts: [{ text: m.text }] }))
      }), signal: AbortSignal.timeout(45000)
    });
    if (!response.ok) {
      const status = response.status;
      const hints = {
        400: 'Gemini API key invalid hai ya request reject hui. Google AI Studio mein key check karein.',
        401: 'Gemini API key valid nahi hai.',
        403: 'Is key ko Gemini API access nahi hai. Google AI Studio project aur key restrictions check karein.',
        404: 'Selected Gemini model available nahi hai. GEMINI_MODEL setting check karein.',
        429: 'Gemini quota limit aa gayi. Google AI Studio mein quota aur billing check karein.'
      };
      let reason = '';
      try {
        const details = await response.json();
        if (typeof details.error?.message === 'string') reason = details.error.message.split(key).join('[hidden]').replace(/AIza[\\w-]+/g, '[hidden]').slice(0, 1000);
      } catch {}
      const hint = hints[status] || 'Gemini service se response nahi aaya. Dobara try karein.';
      return json({ error: hint + (reason ? ' ' + reason : ''), upstreamStatus: status }, status === 429 ? 429 : 502);
    }
    const result = await response.json();
    const text = (result.candidates?.[0]?.content?.parts || []).filter((p) => !p.thought && typeof p.text === 'string').map((p) => p.text).join('\n');
    return text ? json({ text }) : json({ error: 'Gemini ne text response nahi diya. Dobara try karein.' }, 422);
  } catch {
    return json({ error: 'Connection fail hua. Dobara try karein.' }, 502);
  }
}

