import { AsyncLocalStorage } from 'node:async_hooks';
import { randomUUID } from 'node:crypto';

const context = new AsyncLocalStorage();
const routes = new Set(['/api/chat', '/api/connectors/github', '/api/build/pr']);
const stages = new Set(['repository_list', 'repository_load', 'repository_tree', 'file_read', 'repository_search', 'proposal_creation', 'pr_submission']);
const statusOf = value => Number.isInteger(value?.status) && value.status >= 100 && value.status <= 599 ? value.status : undefined;

// Deliberately no arbitrary metadata, error text, URLs, headers, bodies or model/tool arguments.
function emit(event, { stage, outcome, started, status } = {}) {
  const state = context.getStore();
  if (!state) return;
  const record = {
    source: 'dua-github', requestId: state.id, route: state.route, event,
    ...(stage ? { stage } : {}), ...(outcome ? { outcome } : {}),
    ...(started === undefined ? {} : { durationMs: Math.max(0, Math.round(performance.now() - started)) }),
    ...(status === undefined ? {} : { status }),
  };
  // Observability must never prevent an authorized read, proposal or PR from completing.
  try { state.write(JSON.stringify(record)); } catch { /* Hosting log sink unavailable. */ }
}

/** One fresh server-generated ID per supported request, isolated across concurrent async work. */
export function withRequestLog(request, operation, { logWriter = line => console.info(line) } = {}) {
  const route = new URL(request.url).pathname;
  if (!routes.has(route)) return operation();
  return context.run({ id: randomUUID(), route, write: logWriter }, async () => {
    const started = performance.now();
    emit('request.started');
    try {
      const response = await operation();
      // This is response creation, not stream completion, durable reply saving or PR merging.
      emit('request.response', { started, status: statusOf(response) });
      return response;
    } catch (error) {
      emit('request.failed', { started, status: statusOf(error) });
      throw error;
    }
  });
}

/** Decorates existing operations without changing their arguments, return type, errors or permissions. */
export function loggedStage(stage, operation, classify = () => 'success') {
  if (!stages.has(stage)) throw new TypeError('Unknown logging stage');
  return function (...args) {
    if (!context.getStore()) return operation.apply(this, args);
    const started = performance.now();
    emit('stage.started', { stage });
    const finished = result => {
      emit('stage.finished', { stage, started, outcome: classify(result) === 'refused' ? 'refused' : 'success' });
      return result;
    };
    const failed = error => {
      emit('stage.finished', { stage, started, outcome: 'error', status: statusOf(error) });
      throw error;
    };
    try {
      const result = operation.apply(this, args);
      return result instanceof Promise ? result.then(finished, failed) : finished(result);
    } catch (error) { return failed(error); }
  };
}
