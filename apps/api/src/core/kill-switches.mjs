/**
 * Remote kill switches read from Cloud Run environment variables. All default to off.
 *   DUA_DISABLE_AGENT=1   Agent mode paused (the Google Cloud engine refuses it too)
 *   DUA_DISABLE_IMAGINE=1 Imagine (image and video) paused
 *   DUA_DISABLE_VOICE=1   AI reply voices and live voice paused (device voice keeps working)
 *   DUA_MAINTENANCE=1     Whole app paused; DUA_MAINTENANCE_MESSAGE (optional, up to 300 characters) is shown
 * Never secret; published in /api/config as `switches`.
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
