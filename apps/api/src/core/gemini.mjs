// Gemini-specific error mapping. Requests are built in adapters.mjs and sent from providers.mjs.
import { readJsonLimited } from './limited-json.mjs';
function retrySeconds(response, details) {
  const delays = [];
  const header = response.headers.get('retry-after');
  if (header && /^\d+(?:\.\d+)?$/.test(header)) delays.push(Number(header));
  else if (header) {
    const time = Date.parse(header);
    if (Number.isFinite(time)) delays.push((time - Date.now()) / 1000);
  }
  if (Array.isArray(details?.error?.details)) {
    for (const info of details.error.details) {
      if (info?.['@type'] !== 'type.googleapis.com/google.rpc.RetryInfo') continue;
      const match = typeof info.retryDelay === 'string' && /^(\d+(?:\.\d+)?)s$/.exec(info.retryDelay);
      if (match) delays.push(Number(match[1]));
    }
  }
  const valid = delays.filter(n => Number.isFinite(n) && n > 0 && n <= 604800);
  return valid.length ? Math.ceil(Math.max(...valid)) : undefined;
}
function waitText(seconds) {
  if (seconds < 60) return seconds + ' second baad retry karein.';
  if (seconds < 3600) return Math.ceil(seconds / 60) + ' minute baad retry karein.';
  const minutes = Math.ceil(seconds / 60);
  return Math.floor(minutes / 60) + ' ghante ' + (minutes % 60) + ' minute baad retry karein.';
}

export const GEMINI_SYSTEM_PROMPT = 'You are Dua, a helpful AI assistant powered by Gemini. Reply in the language the user uses. Be clear and concise.';

/** Allowlisted error for a non-OK Gemini response; raw provider text never leaves the server. */
export async function geminiUpstreamError(response, signal) {
  const json = (data, status, headers = {}) => Response.json(data, { status, headers: { 'Cache-Control': 'no-store', ...headers } });
  const status = response.status;
  const hints = {
    400: 'Gemini API key invalid hai ya request reject hui. Google AI Studio mein key check karein.',
    401: 'Gemini API key valid nahi hai.',
    403: 'Is key ko Gemini API access nahi hai. Google AI Studio project aur key restrictions check karein.',
    404: 'Selected Gemini model available nahi hai. GEMINI_MODEL setting check karein.',
    429: 'Gemini quota limit aa gayi. Google AI Studio mein quota aur billing check karein.',
    500: 'Gemini server par internal error aaya (HTTP 500). Thodi der baad retry karein.',
    502: 'Gemini gateway error aaya (HTTP 502). Thodi der baad retry karein.',
    503: 'Gemini service abhi unavailable hai (HTTP 503). Thodi der baad retry karein.',
    504: 'Gemini upstream request timeout hui (HTTP 504). Thodi der baad retry karein.'
  };
  let details;
  try { details = await readJsonLimited(response, 1024 * 1024); } catch {}
  signal?.throwIfAborted();
  // Only the HTTP status goes to runtime logs; never log provider bodies, keys, prompts or headers.
  console.error(`[dua-gemini] Upstream request failed (HTTP ${status}).`);
  const hint = hints[status] || `Gemini request fail hui (HTTP ${status}). Dobara try karein.`;
  const delay = status === 429 ? retrySeconds(response, details) : undefined;
  return json({
    error: delay ? 'Gemini quota limit aa gayi. ' + waitText(delay) : hint,
    upstreamStatus: status,
    ...(delay ? { retryAfterSeconds: delay } : {})
  }, status === 429 ? 429 : 502, delay ? { 'Retry-After': String(delay) } : {});
}
