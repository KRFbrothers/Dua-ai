import { handleGemini } from '../../gemini.mjs';
declare const Netlify: { env: { get(name: string): string | undefined } };
export default async function handler(req: Request) {
  return handleGemini(req, {
    key: Netlify.env.get('GEMINI_API_KEY'),
    model: Netlify.env.get('GEMINI_MODEL')
  });
}
// Configuration checks must not spend the chat request allowance.
export const config = { path: '/api/config' };
