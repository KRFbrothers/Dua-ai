// Provider-hosted code execution (Phase 5, "Run code"). The provider runs the code in its own sandbox;
// nothing executes on this server or on the phone. Request building lives in adapters.mjs; this module
// decides which provider may run code and normalises the code + output the providers return.
//
// Official tool names (checked against provider docs, Oct 2026):
//   Gemini generateContent: tools: [{ code_execution: {} }] -> parts executableCode {language, code} and
//                           codeExecutionResult {outcome, output}. Python only, 30 s per run, no extra charge
//                           (billed as normal input/output tokens), works on the free AI Studio tier.
//   OpenAI Responses:       tools: [{ type: 'code_interpreter', container: { type: 'auto' } }] -> output items
//                           code_interpreter_call {code, outputs[{type:'logs', logs}]} (container fee per session).
//   xAI Responses:          tools: [{ type: 'code_interpreter' }] (xAI SDK name: code_execution), same item shape;
//                           billed per tool call by xAI.
const off = value => String(value ?? '').trim() === '0';
const on = value => String(value ?? '').trim() === '1';
export const MAX_CODE_RUNS = 10;
export const MAX_CODE_CHARS = 20000;
export const MAX_OUTPUT_CHARS = 20000;

/**
 * Providers that may run code. DUA_CODE_EXECUTION=0 turns Run code off everywhere. Gemini (Dua, Gemini) is on
 * by default because code execution costs nothing extra there; ChatGPT and Grok are paid per container / call,
 * so they need an explicit OPENAI_CODE_EXECUTION=1 / GROK_CODE_EXECUTION=1.
 */
export function codeExecutionProviders(env = {}, definitions = []) {
  if (off(env.DUA_CODE_EXECUTION)) return [];
  return definitions.filter(provider => provider.type === 'gemini' ? !off(env.GEMINI_CODE_EXECUTION)
    : provider.type === 'responses' && on(env[provider.prefix + '_CODE_EXECUTION'])).map(provider => provider.name);
}

/** Public Run code capability for /api/config (no secrets). `configured`: provider names with a key and model. */
export function codeExecutionConfiguration(env = {}, definitions = [], configured = new Set()) {
  const providers = codeExecutionProviders(env, definitions).filter(name => configured.has(name));
  const reason = off(env.DUA_CODE_EXECUTION) ? 'Run code is turned off on this server (DUA_CODE_EXECUTION=0).'
    : !providers.length ? 'Run code needs GEMINI_API_KEY and GEMINI_MODEL on the server.' : null;
  return { enabled: !reason, ...(reason ? { reason } : {}), providers, language: 'python', maxRuns: MAX_CODE_RUNS, maxChars: MAX_OUTPUT_CHARS };
}

const clip = (value, limit) => typeof value === 'string' ? (value.length > limit ? value.slice(0, limit) + '\n[…truncated]' : value) : '';
const language = value => typeof value === 'string' && /^[A-Za-z0-9+#._-]{1,20}$/.test(value) ? value.toLowerCase() : 'python';
/** Gemini outcomes: OUTCOME_OK, OUTCOME_FAILED, OUTCOME_DEADLINE_EXCEEDED. */
export const outcomeOf = value => value === 'OUTCOME_OK' || value === 'ok' || value === 'completed' ? 'ok'
  : value === 'OUTCOME_DEADLINE_EXCEEDED' || value === 'timeout' ? 'timeout' : value ? 'error' : undefined;

/**
 * Adds code that ran (or is about to run) to `state.code` and returns the Dua event, or null past the cap.
 * Events: {type:'code', index, language, code} then {type:'code', index, output, outcome} for the same index.
 */
export function addCodeCall(state, { language: lang, code }) {
  state.code ||= [];
  if (state.code.length >= MAX_CODE_RUNS || typeof code !== 'string' || !code.trim()) return null;
  const run = { language: language(lang), code: clip(code, MAX_CODE_CHARS) };
  state.code.push(run);
  return { type: 'code', index: state.code.length - 1, language: run.language, code: run.code };
}
/** Attaches a result to the newest run without output (Gemini sends the result in the next part). */
export function addCodeResult(state, { output, outcome }) {
  const index = (state.code || []).findLastIndex(run => run.output === undefined && run.outcome === undefined);
  if (index < 0) return null;
  const run = state.code[index];
  run.output = clip(typeof output === 'string' ? output : '', MAX_OUTPUT_CHARS);
  const result = outcomeOf(outcome);
  if (result) run.outcome = result;
  return { type: 'code', index, output: run.output, ...(result ? { outcome: result } : {}) };
}

/** Responses API code_interpreter_call item -> [call event, result event]. */
export function addInterpreterItem(state, item) {
  if (item?.type !== 'code_interpreter_call') return [];
  const call = addCodeCall(state, { language: 'python', code: item.code });
  if (!call) return [];
  const logs = (Array.isArray(item.outputs) ? item.outputs : []).filter(out => out?.type === 'logs' && typeof out.logs === 'string').map(out => out.logs).join('\n');
  const images = (Array.isArray(item.outputs) ? item.outputs : []).filter(out => out?.type === 'image').length;
  const result = addCodeResult(state, { output: logs + (images ? `${logs ? '\n' : ''}[${images} image${images === 1 ? '' : 's'} not shown]` : ''),
    outcome: item.status === 'failed' ? 'failed' : item.status === 'incomplete' ? 'timeout' : 'completed' });
  return [call, ...(result ? [result] : [])];
}

/** Public copy of the runs for the JSON reply: [{language, code, output?, outcome?}]. */
export const codeRuns = state => (state.code || []).map(run => ({ ...run }));
