import { handleGemini } from '../../gemini.mjs';
declare const Netlify: { env: { get(name: string): string | undefined } };
export default async function handler(req: Request) {
  return handleGemini(req, {
    key: Netlify.env.get('GEMINI_API_KEY'),
    model: Netlify.env.get('GEMINI_MODEL') || 'gemini-2.5-flash'
  });
}
export const config = { path: ['/api/config', '/api/chat'] };
