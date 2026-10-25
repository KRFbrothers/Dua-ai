"""Request-scoped, content-free action traces and provider-reported usage.

No prompts, tool arguments, bearer tokens or thought text are written here.
Prices are operator-supplied USD/million-token estimates, never billed amounts.
"""
import json
import math
import os
import threading
import time
import uuid


def _integer(value):
    return value if isinstance(value, int) and not isinstance(value, bool) and value >= 0 else None


class Trace:
    def __init__(self, request_id=None, sink=None, prices=None, clock=time.monotonic):
        self.id = request_id or uuid.uuid4().hex
        self.sink = sink or (lambda row: print(json.dumps(row, separators=(',', ':')), flush=True))
        self.clock, self.started = clock, clock()
        self.lock = threading.RLock()
        self.calls, self.seen = [], set()
        self.pending_models = {}
        if prices is None:
            try:
                prices = json.loads(os.environ.get('ADK_MODEL_PRICES_JSON', '{}'))
            except (ValueError, TypeError):
                prices = {}
        self.prices = prices if isinstance(prices, dict) else {}
        self.emit('request.start')

    def emit(self, action, **fields):
        # Callers pass action labels/statuses/numbers only; never arbitrary payloads.
        safe = {k: v for k, v in fields.items() if k in (
            'agent', 'tool', 'model', 'status', 'duration_ms', 'attempt', 'calls', 'usage_complete')}
        row = {'source': 'dua-adk', 'request_id': self.id, 'action': action,
               'elapsed_ms': round((self.clock() - self.started) * 1000), **safe}
        with self.lock:
            self.sink(row)

    def begin_model(self, agent, model):
        with self.lock:
            self.pending_models[agent]=(uuid.uuid4().hex,self.clock(),str(model))
        self.emit('model.start',agent=agent,model=str(model))

    def end_model(self, callback_context=None, llm_response=None, **_):
        if getattr(llm_response,'partial',False):return None
        agent=getattr(callback_context,'agent_name','unknown')
        with self.lock:
            pending=self.pending_models.pop(agent,None)
        if pending:
            self.emit('model.end',agent=agent,model=pending[2],status='ok',duration_ms=round((self.clock()-pending[1])*1000))
        return None

    def fail_pending(self):
        with self.lock:
            pending=list(self.pending_models.items())
            self.pending_models.clear()
        for agent,(key,started,model) in pending:
            if not getattr(self,"sdk_metered",False):
                self.usage(key,model,None)
            self.emit('model.end',agent=agent,model=model,status='interrupted',duration_ms=round((self.clock()-started)*1000))

    def usage(self, key, model, metadata):
        with self.lock:
            if key in self.seen:
                return
            self.seen.add(key)
            def value(name):
                return _integer(metadata.get(name) if isinstance(metadata, dict) else getattr(metadata, name, None))
            row = {'model': model, 'input': value('prompt_token_count'), 'output': value('candidates_token_count'),
                   'cached_input': value('cached_content_token_count'), 'thinking': value('thoughts_token_count'),
                   'total': value('total_token_count')}
            row['reported'] = metadata is not None and row['input'] is not None and row['output'] is not None
            self.calls.append(row)

    def summary(self):
        with self.lock:
            calls = [dict(r) for r in self.calls]
        complete = bool(calls) and all(r['reported'] for r in calls)
        totals = {k: sum(r[k] for r in calls) if complete and all(r[k] is not None for r in calls) else None
                  for k in ('input', 'output', 'cached_input', 'thinking', 'total')}
        cost = 0.0
        priced = complete
        for row in calls:
            if row['cached_input'] is None or row['thinking'] is None:
                priced = False
            price = self.prices.get(row['model'])
            if not isinstance(price, dict) or any(not isinstance(price.get(k), (int, float)) or
                    isinstance(price.get(k), bool) or not math.isfinite(price[k]) or price[k] < 0
                    for k in ('input', 'output', 'cached_input')):
                priced = False
                continue
            cached = min(row['cached_input'] or 0, row['input'] or 0)
            # Gemini reports thinking separately from visible candidates; the
            # LiteLlm adapter can include reasoning inside completion tokens.
            # Reconcile against the reported total instead of charging twice.
            base=(row['input'] or 0)+(row['output'] or 0)
            if row['total']==base:
                charged_output=row['output'] or 0
            elif row['total']==base+(row['thinking'] or 0):
                charged_output=(row['output'] or 0)+(row['thinking'] or 0)
            else:
                priced=False
                continue
            cost += (((row['input'] or 0) - cached) * price['input'] + cached * price['cached_input'] +
                     charged_output * price['output']) / 1_000_000
        return {'requestId': self.id, 'calls': len(calls), 'usageComplete': complete, 'tokens': totals,
                'estimatedUsd': round(cost, 8) if priced else None, 'costIsEstimate': True,
                'models': sorted({r['model'] for r in calls}), 'scope': 'agent_request_including_router_tools_and_postprocessing'}


class ObservedModel:
    """Measure nested search/code/critic/learning calls through the shared adapter."""
    def __init__(self, model, trace, label='tool-model'):
        self.model, self.trace, self.label = model, trace, label

    def __getattr__(self, name):
        return getattr(self.model, name)

    def generate_content(self, *args, **kwargs):
        key = uuid.uuid4().hex
        model = getattr(self.model, 'model_name', self.label)
        start = time.monotonic()
        self.trace.emit('model.start', model=model)
        try:
            result = self.model.generate_content(*args, **kwargs)
        except BaseException:
            self.trace.usage(key, model, None)
            self.trace.emit('model.end', model=model, status='error', duration_ms=round((time.monotonic()-start)*1000))
            raise
        if kwargs.get('stream'):
            def stream():
                metadata, reported_model, status = None, model, 'ok'
                try:
                    for chunk in result:
                        if getattr(chunk, 'usage_metadata', None) is not None:
                            metadata = chunk.usage_metadata
                        reported_model = getattr(chunk, 'model_version', None) or reported_model
                        yield chunk
                except BaseException:
                    status = 'interrupted'
                    raise
                finally:
                    if hasattr(result,'close'):
                        result.close()
                    self.trace.usage(key, reported_model, metadata)
                    self.trace.emit('model.end', model=model, status=status, duration_ms=round((time.monotonic()-start)*1000))
            return stream()
        self.trace.usage(key, getattr(result, 'model_version', None) or model, getattr(result, 'usage_metadata', None))
        self.trace.emit('model.end', model=model, status='ok', duration_ms=round((time.monotonic()-start)*1000))
        return result
