/**
 * API implementation of packages/contracts/KILL_SWITCHES.md (authoritative).
 * DUA_DISABLE_AGENT and DUA_DISABLE_IMAGINE are shared with the engine.
 * DUA_DISABLE_VOICE is API-only; device voice keeps working.
 * DUA_MAINTENANCE pauses selected AI routes; DUA_MAINTENANCE_MESSAGE is optional.
 * All switches default off. Each service reads its own Cloud Run environment;
 * matching names do not synchronize configuration. Published in /api/config as `switches`.
 */
const on = value => ['1', 'true', 'yes', 'on'].includes(String(value ?? '').trim().toLowerCase());
export const DEFAULT_MAINTENANCE_MESSAGE = 'Dua is taking a short break for maintenance. Please check back soon.';
export const PAUSED = {
  agent: 'Agent mode is paused right now. Normal chat still works; try Agent again later.',
  imagine: 'Imagine is paused right now. Please try again later.',
  voice: 'AI voices and live voice are paused right now. Device voice still works.',
};

export function maintenanceMessage(env = {}) {
  const text = String(env.DUA_MAINTENANCE_MESSAGE ?? '').replace(/[\u0000-\u001f\u007f]+/g, ' ').trim().slice(0, 300);
  return text || DEFAULT_MAINTENANCE_MESSAGE;
}

export function killSwitches(env = {}) {
  const maintenance = on(env.DUA_MAINTENANCE);
  return {
    agent: on(env.DUA_DISABLE_AGENT),
    imagine: on(env.DUA_DISABLE_IMAGINE),
    voice: on(env.DUA_DISABLE_VOICE),
    maintenance: { enabled: maintenance, ...(maintenance ? { message: maintenanceMessage(env) } : {}) },
  };
}

/** Routes that stop during maintenance. Account, cloud sync, files and config keep working so data stays reachable. */
const MAINTENANCE_PATHS = /^\/api\/(chat|web\/chat|search|connectors\/github|build\/pr|imagine\/.*|voice\/.*)$/;

/** A 503 for a request blocked by a switch, or null. */
export function switchResponse(path, env = {}) {
  const switches = killSwitches(env);
  const refuse = (error, extra = {}) => Response.json({ error, ...extra }, { status: 503, headers: { 'Cache-Control': 'no-store' } });
  if (switches.maintenance.enabled && MAINTENANCE_PATHS.test(path)) return refuse(switches.maintenance.message, { maintenance: true });
  if (switches.imagine && path.startsWith('/api/imagine/')) return refuse(PAUSED.imagine, { paused: 'imagine' });
  if (switches.voice && path.startsWith('/api/voice/')) return refuse(PAUSED.voice, { paused: 'voice' });
  return null;
}
