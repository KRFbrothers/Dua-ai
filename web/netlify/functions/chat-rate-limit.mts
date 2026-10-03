// This endpoint never reads credentials or contacts a provider.
export default async function handler() {
  return Response.json({
    error: 'Bahut jaldi messages bheje gaye. 1 minute baad retry karein.',
    retryAfterSeconds: 60
  }, {
    status: 429,
    headers: { 'Cache-Control': 'no-store', 'Retry-After': '60' }
  });
}
export const config = { path: '/api/chat-rate-limit' };
