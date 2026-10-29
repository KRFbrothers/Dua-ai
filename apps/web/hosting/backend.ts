import { router, json } from '@appdeploy/sdk';
import { createDuaBridge } from './dua-bridge.mjs';

const bridge = createDuaBridge();
const endpoint = (operation: string) => async ({ body }: { body: unknown }) => {
  const response = await bridge(operation, body ?? {});
  const reply = json(response.data, response.status);
  reply.headers['Cache-Control'] = 'no-store';
  return reply;
};
export const handler = router({
  'GET /api/dua/status': [endpoint('status')],
  'POST /api/dua/login': [endpoint('login')],
  'POST /api/dua/refresh': [endpoint('refresh')],
  'POST /api/dua/logout': [endpoint('logout')],
  'POST /api/dua/chat': [endpoint('chat')],
  'POST /api/dua/history': [endpoint('history')],
});
