import { handleGemini } from '../../gemini.mjs';
declare const Netlify: { env: { get(name: string): string | undefined } };
export default async function handler(req: Request) {
  return handleGemini(req, {
    key: Netlify.env.get('GEMINI_API_KEY'),
    model: Netlify.env.get('GEMINI_MODEL')
  });
}
// Netlify enforces this at the edge across function instances, before Gemini runs.
export const config = {
  path: '/api/chat',
  rateLimit: {
    windowLimit: 5,
    windowSize: 60,
    aggregateBy: ['ip', 'domain'],
    action: 'rewrite',
    to: '/api/chat-rate-limit'
  }
};

