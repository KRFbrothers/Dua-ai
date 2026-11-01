"""Behavioral checks for the audited ADK gaps; no live provider calls."""
import asyncio
import json
import sys
import threading
from pathlib import Path
from types import SimpleNamespace
import pytest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import dua_engine.agent.agent_adk as agent_adk
from dua_engine.agent.agent_observability import Trace
from dua_engine.agent.agent_resilience import retry_read


def test_usage_deduplicates_reports_and_never_invents_unknown_cost():
    rows=[]
    trace=Trace(sink=rows.append, prices={'offline':{'input':1,'output':2,'cached_input':0.5}})
    usage={'prompt_token_count':100,'candidates_token_count':20,'cached_content_token_count':10,
           'thoughts_token_count':5,'total_token_count':125}
    trace.usage('call-a','offline',usage)
    trace.usage('call-a','offline',usage)
    report=trace.summary()
    assert report['calls']==1 and report['tokens']['total']==125
    assert report['estimatedUsd']==pytest.approx(0.000145)
    inclusive=Trace(sink=lambda row:None,prices={'offline':{'input':1,'output':2,'cached_input':0.5}})
    inclusive.usage('inclusive-reasoning','offline',{**usage,'total_token_count':120})
    assert inclusive.summary()['estimatedUsd']==pytest.approx(0.000135)
    inconsistent=Trace(sink=lambda row:None,prices=inclusive.prices)
    inconsistent.usage('inconsistent-total','offline',{**usage,'total_token_count':123})
    assert inconsistent.summary()['estimatedUsd'] is None
    trace.usage('call-b','offline',None)
    assert trace.summary()['estimatedUsd'] is None and trace.summary()['tokens']['input'] is None
    assert 'prompt' not in json.dumps(rows)


def test_retries_only_classified_reads_and_stops_on_cancel():
    calls=[]
    def invoke():
        calls.append(1)
        return {'error':'temporary','retryable':True} if len(calls)==1 else {'ok':True}
    assert retry_read('read_file',invoke,wait=lambda _:None)=={'ok':True}
    assert len(calls)==2
    calls.clear()
    assert retry_read('propose_changes',invoke,wait=lambda _:None)['error']=='temporary'
    assert len(calls)==1
    import dua_engine.agent.agent_tools as agent_tools
    with pytest.raises(agent_tools.Cancelled):
        retry_read('read_file',invoke,cancelled=lambda:True)


def test_real_adk_preserves_multimodal_context_and_streams_merger_deltas(monkeypatch):
    pytest.importorskip('google.adk')
    from google.adk.models import BaseLlm,LlmResponse
    from google.genai import types
    import dua_engine.agent.agent_models as agent_models
    from test_agent_adk import make_env
    observed, emitted, log=[],[],[]
    first='First ' + 'x'*300
    original=[types.Content(role='user',parts=[types.Part(text='Earlier task: compare the attached chart'),
                 types.Part(inline_data=types.Blob(data=b'chart-bytes',mime_type='image/png'))]),
              types.Content(role='model',parts=[types.Part(text='Earlier chart answer')]),
              types.Content(role='user',parts=[types.Part(text='Now research A and check notes B')])]
    class Offline(BaseLlm):
        async def generate_content_async(self,llm_request,stream=False):
            rendered='\n'.join(p.text or '' for c in llm_request.contents for p in c.parts or [])
            assert 'Earlier task:' in rendered and 'Earlier chart answer' in rendered
            assert any(p.inline_data and p.inline_data.data==b'chart-bytes' for c in llm_request.contents for p in c.parts or [])
            assert 'Owner system instruction' in str(llm_request.config.system_instruction)
            observed.append(self.model)
            if self.model=='controller':
                text=json.dumps({'split':True,'subtasks':[{'kind':'research','task':'research A'},{'kind':'notes','task':'notes B'}]})
            elif self.model=='helper':
                text='Helper result'
            else:
                assert stream is True
                yield LlmResponse(content=types.Content(role='model',parts=[types.Part(text=first)]),partial=True)
                assert emitted and first.startswith(''.join(emitted)), 'a safe answer delta must arrive before the next model event'
                yield LlmResponse(content=types.Content(role='model',parts=[types.Part(text='second')]),partial=True)
                text=first+'second'
            yield LlmResponse(content=types.Content(role='model',parts=[types.Part(text=text)]),
                usage_metadata=types.GenerateContentResponseUsageMetadata(prompt_token_count=10,candidates_token_count=2,total_token_count=12),
                model_version='offline')
    monkeypatch.setattr(agent_models,'resolve',lambda role,default:Offline(model=role))
    env=make_env([])
    env.update(adk_contents=original,adk_system='Owner system instruction',
               stream_text=lambda delta:emitted.append(delta) or True,trace=Trace(sink=log.append))
    answer,result=agent_adk.run(env,'Now research A and check notes B','flash','agent',
        lambda m,t,json_out=False:types.GenerateContentConfig(max_output_tokens=t))
    assert answer==first+'second' and ''.join(emitted)==answer
    assert result.streamed and result.usage['calls']==4
    assert result.usage['tokens']['total']==48
    assert sorted(observed)==['controller','helper','helper','merger']
    assert original[0].parts[0].text=='Earlier task: compare the attached chart'


@pytest.fixture
def sql_store(tmp_path,monkeypatch):
    pytest.importorskip('google.adk')
    import dua_engine.agent.agent_store as agent_store,dua_engine.agent.agent_documents as agent_documents
    monkeypatch.setenv('ADK_SESSION_DB_URL','sqlite+aiosqlite:///'+str(tmp_path/'adk.sqlite'))
    agent_store.initialize()
    agent_documents.initialize()
    return agent_store


def test_durable_approvals_survive_restart_dedupe_and_claim_once(sql_store):
    import dua_engine.agent.agent_safety as agent_safety
    action=sql_store.SqlPendingStore().create('owner-a','save_note',{'note':'Hello'},request_key='task:note:hash')
    assert sql_store.SqlPendingStore().create('owner-a','save_note',{'note':'Hello'},request_key='task:note:hash')['id']==action['id']
    with pytest.raises(agent_safety.ApprovalError):
        sql_store.SqlPendingStore().take(action['id'],'owner-b')
    barrier=threading.Barrier(2)
    outcomes=[]
    def claim():
        barrier.wait()
        try:
            sql_store.SqlPendingStore().take(action['id'],'owner-a')
            outcomes.append('claimed')
        except agent_safety.ApprovalError:
            outcomes.append('blocked')
    threads=[threading.Thread(target=claim) for _ in range(2)]
    for t in threads:t.start()
    for t in threads:t.join(timeout=5)
    assert sorted(outcomes)==['blocked','claimed']
    with pytest.raises(agent_safety.ApprovalError):
        sql_store.SqlPendingStore().create('owner-a','save_note',{'note':'Hello'},request_key='task:note:hash')


def test_vectors_retrieve_synonyms_isolate_owners_and_delete(sql_store):
    from dua_engine.agent.agent_documents import Documents
    class OfflineEmbedding:
        space='offline:semantic:2'
        def __call__(self,texts,query=False):
            return [[1.,0.] if any(w in t.lower() for w in ('car','automobile')) else [0.,1.] for t in texts]
    embedding_calls=[]
    class CountedEmbedding(OfflineEmbedding):
        def __call__(self,texts,query=False):
            embedding_calls.append(1)
            return super().__call__(texts,query=query)
    a=Documents('owner-a',CountedEmbedding())
    created=a.add('Travel','A car is a road vehicle.')
    Documents('owner-b',OfflineEmbedding()).add('Other owner','Private automobile details')
    blocked=Documents('owner-b',CountedEmbedding())
    before=len(embedding_calls)
    with pytest.raises(ValueError):blocked.add('Unauthorized','Do not embed this',created['id'])
    with pytest.raises(ValueError):a.add('Retag','Do not replace a document with a note',created['id'],kind='note')
    assert len(embedding_calls)==before
    fresh=Documents('owner-a',OfflineEmbedding())
    matches=fresh.search('automobile')
    assert len(matches)==1 and 'car' in matches[0]['text'] and matches[0]['score']==1
    assert matches[0]['citation'].startswith('[D:'+created['id'])
    assert not Documents('owner-b',OfflineEmbedding()).delete(created['id'])['deleted']
    assert fresh.delete(created['id'])['deleted'] and not fresh.search('automobile')


def test_custom_read_and_write_share_validation_and_approval_boundary():
    pytest.importorskip('google.adk')
    from dua_engine.agent.agent_toolkit import Tool,Registry
    import dua_engine.agent.agent_tools as agent_tools,dua_engine.agent.agent_safety as agent_safety
    executed=[]
    def send_message(text:str)->dict:
        """Send the approved synthetic message."""
        executed.append(text)
        return {'sent':True}
    spec=Tool(send_message,write=True,revision='checked-1')
    registry=Registry([spec])
    pending=agent_safety.PendingStore()
    run=agent_tools.AgentRun({'custom_tools':registry,'owner_id':'owner','approvals':pending,'enqueue':lambda *a:True})
    assert 'error' in run.run_tool('send_message',{'text':'hello'})
    assert executed==[]
    assert run.request_approval('send_message',{'text':'hello','__dua_tool_revision':spec.identity})['status']=='waiting_for_user_approval'
    action=pending.take(run.pending[0]['id'],'owner')
    assert executed==[]
    ok,_=agent_safety.execute(action,{'custom_tools':registry})
    assert ok and executed==['hello']
    with pytest.raises(Exception):
        spec.validate({'text':5,'extra':'ignored?'})


def test_official_sql_session_resume_does_not_repeat_completed_tool(sql_store):
    from google.adk.models import BaseLlm,LlmResponse
    from google.genai import types
    calls=[]
    fail={'once':True}
    class Offline(BaseLlm):
        async def generate_content_async(self,llm_request,stream=False):
            completed=any(p.function_response for c in llm_request.contents for p in c.parts or [])
            if not completed:
                yield LlmResponse(content=types.Content(role='model',parts=[types.Part(function_call=types.FunctionCall(name='inspect_file',args={}))]))
            elif fail['once']:
                fail['once']=False
                raise RuntimeError('synthetic disconnect after tool response')
            else:
                yield LlmResponse(content=types.Content(role='model',parts=[types.Part(text='Recovered answer')]))
    def inspect_file()->dict:
        """Read one synthetic file."""
        calls.append(1)
        return {'value':'checked'}
    adk=agent_adk.load_adk()
    options={'owner':'owner-a','id':'b98516ca-8f69-4a78-aed0-72af293bdf70','state':{'dua_schema':1},'resume':False}
    def agent():return adk.LlmAgent(name='resume_probe',model=Offline(model='offline'),tools=[inspect_file])
    with pytest.raises(RuntimeError,match='synthetic disconnect'):
        agent_adk.run_agent(adk,agent(),'Inspect the file',5,session_options=options)
    assert len(calls)==1
    assert asyncio.run(sql_store.get_task('owner-b',options['id'])) is None
    assert asyncio.run(sql_store.get_task('owner-a',options['id'])) is not None
    events=agent_adk.run_agent(adk,agent(),'ignored',5,session_options={**options,'resume':True})
    assert agent_adk.final_text(events,'resume_probe')=='Recovered answer' and len(calls)==1


def test_secret_filter_handles_keys_split_across_stream_deltas():
    from dua_engine.agent.agent_policy import Redactor
    key='synthetic-sensitive-value-123456789'
    r=Redactor(secrets=[key])
    a=r.feed('A'*300+key[:10])
    b=r.feed(key[10:]+'B'*300)
    c=r.feed(final=True)
    combined=a+b+c
    assert key not in combined and '[redacted secret]' in combined
    assert combined=='A'*300+'[redacted secret]'+'B'*300


def test_task_lease_rejects_concurrent_resume_and_releases(sql_store):
    import dua_engine.agent.agent_store as agent_store
    lease=agent_store.acquire('owner-a','a91d5d9b-99d1-409b-b38b-86a09b9c5276')
    with pytest.raises(agent_store.TaskBusy):
        agent_store.acquire('owner-a','a91d5d9b-99d1-409b-b38b-86a09b9c5276')
    agent_store.release('owner-a','a91d5d9b-99d1-409b-b38b-86a09b9c5276',lease)
    assert agent_store.acquire('owner-a','a91d5d9b-99d1-409b-b38b-86a09b9c5276')


def test_official_model_adapters_and_fallback_are_wired(monkeypatch):
    pytest.importorskip('google.adk')
    pytest.importorskip('litellm')
    import dua_engine.agent.agent_models as agent_models
    from google.adk.models import FallbackModel,Gemini
    from google.adk.models.lite_llm import LiteLlm
    monkeypatch.setenv('ADK_MODELS_JSON',json.dumps({'controller':['gemini-primary','openai/offline-test'],
        'helper':['anthropic/offline-test'],'merger':['ollama_chat/offline-test']}))
    monkeypatch.setenv('ADK_ALLOW_LOCAL_MODELS','1')
    route=agent_models.resolve('controller','unused')
    assert isinstance(route,FallbackModel) and isinstance(route.models[0],Gemini) and isinstance(route.models[1],LiteLlm)
    assert route.models[0].retry_options.attempts==2
    assert agent_models.resolve('helper','unused').model=='anthropic/offline-test'
    assert agent_models.resolve('merger','unused').model=='ollama_chat/offline-test'


def test_official_fallback_never_splices_after_partial_output():
    pytest.importorskip('google.adk')
    from google.adk.models import BaseLlm,LlmResponse,FallbackModel
    from google.adk.models.llm_request import LlmRequest
    from google.genai import types
    from google.genai.errors import ServerError
    called=[]
    class Primary(BaseLlm):
        partial:bool=False
        async def generate_content_async(self,llm_request,stream=False):
            called.append('primary')
            if self.partial:
                yield LlmResponse(content=types.Content(role='model',parts=[types.Part(text='Started')]),partial=True)
            raise ServerError(503,{'error':{'code':503,'message':'offline transient'}})
    class Backup(BaseLlm):
        async def generate_content_async(self,llm_request,stream=False):
            called.append('backup')
            yield LlmResponse(content=types.Content(role='model',parts=[types.Part(text='Backup')]))
    async def collect(partial):
        model=FallbackModel(models=[Primary(model='primary',partial=partial),Backup(model='backup')])
        return [e async for e in model.generate_content_async(LlmRequest(model='primary'),stream=True)]
    assert asyncio.run(collect(False))[0].content.parts[0].text=='Backup'
    assert called==['primary','backup']
    called.clear()
    with pytest.raises(ServerError):asyncio.run(collect(True))
    assert called==['primary']


def test_quality_report_penalizes_missing_cases_and_failed_rubrics():
    from dua_engine.agent.agent_evaluation import evaluate
    cases={'version':1,'cases':[{'id':'math','rubric':{'number':42,'required_tools':['calculator']}},
                               {'id':'approval','rubric':{'approval_required':True}}]}
    report=evaluate(cases,[{'id':'math','text':'42','tools':['calculator']}])
    assert report['cases']==2 and report['passed']==1 and report['passRate']==0.5
    assert not report['liveModelAccuracyClaim']
    bad=evaluate(cases,[{'id':'math','text':'43','tools':[]},
                        {'id':'approval','text':'sent','approval_required':False}])
    assert bad['passed']==0
    with pytest.raises(ValueError):evaluate(cases,[{'id':'math','text':'42'},{'id':'math','text':'42'}])


def test_cli_scaffolds_real_adk_package_without_overwrite(tmp_path,monkeypatch):
    pytest.importorskip('google.adk')
    import adk_cli,importlib.util
    folder=tmp_path/'new_agent'
    assert adk_cli.main(['scaffold',str(folder)])==0
    assert adk_cli.main(['scaffold',str(folder)])==2
    monkeypatch.setenv('VERTEX_MODEL','gemini-offline-placeholder')
    spec=importlib.util.spec_from_file_location('scaffold_probe',folder/'agent.py')
    module=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    assert module.root_agent.name=='new_agent' and module.root_agent.model=='gemini-offline-placeholder'


def test_resume_api_uses_only_owned_stored_input(monkeypatch):
    pytest.importorskip('google.adk')
    import app as engine,dua_engine.agent.agent_store as agent_store,dua_engine.agent.agent_notes as agent_notes
    monkeypatch.setenv('AGENT_ENGINE','adk')
    monkeypatch.delenv('ADK_EMBEDDING_MODEL',raising=False)
    monkeypatch.delenv('GITHUB_TOKEN',raising=False)
    monkeypatch.delenv('DUA_APP_TOKEN',raising=False)
    monkeypatch.delenv('ADK_SESSION_DB_URL',raising=False)
    monkeypatch.setattr(engine,'verify_owner',lambda _: 'verified-owner')
    monkeypatch.setattr(engine,'get_model',lambda *a:object())
    monkeypatch.setattr(engine,'get_agent_model',lambda *a:object())
    monkeypatch.setattr(agent_notes,'for_owner',lambda _:None)
    # Storage configuration is a service setting, never chosen by the caller.
    monkeypatch.setattr(agent_store,'database_url',lambda:'configured-server-storage')
    monkeypatch.setattr(agent_store,'approvals',lambda:None)
    stored={'dua_schema':1,'dua_request':{'messages':[{'role':'user','text':'Stored original task'}]}}
    lookups=[]
    async def get_task(owner,task):
        lookups.append((owner,task))
        return SimpleNamespace(state=stored)
    monkeypatch.setattr(agent_store,'get_task',get_task)
    received=[]
    def run(env,last,*a,**k):
        received.append((last,env['owner_id'],env['adk_resume']))
        return 'Resumed',SimpleNamespace(sources=[],steps=[],pending=[],cancelled=False)
    monkeypatch.setattr(agent_adk,'run',run)
    response=engine.app.test_client().post('/api/chat',json={'resumeRun':'a91d5d9b-99d1-409b-b38b-86a09b9c5276',
        'messages':[{'role':'user','text':'Attacker replacement'}],'owner_id':'attacker'},headers={'Authorization':'Bearer synthetic'})
    assert response.status_code==200 and b'Resumed' in response.data
    assert received==[('Stored original task','verified-owner',True)]
    assert lookups[0][0]=='verified-owner'
    stored['dua_answer']='Already complete'
    response=engine.app.test_client().post('/api/chat',json={'resumeRun':'a91d5d9b-99d1-409b-b38b-86a09b9c5276'},headers={'Authorization':'Bearer synthetic'})
    assert response.status_code==409 and len(received)==1


def test_real_sdk_custom_tool_registration_is_an_actual_runtime_caller(monkeypatch):
    pytest.importorskip('google.adk')
    from google.adk.models import BaseLlm,LlmResponse
    from google.genai import types
    from dua_engine.agent.agent_toolkit import configured
    from test_agent_adk import make_env
    import dua_engine.agent.agent_models as agent_models
    monkeypatch.setenv('ADK_TOOL_MODULES','adk_examples.text_tools')
    calls=[]
    class Offline(BaseLlm):
        async def generate_content_async(self,llm_request,stream=False):
            if self.model=='merger':text='There are 3 words.'
            elif any(p.function_response for c in llm_request.contents for p in c.parts or []):
                calls.append('tool-result-observed')
                text='word_count returned 3 words'
            else:
                yield LlmResponse(content=types.Content(role='model',parts=[types.Part(
                    function_call=types.FunctionCall(name='word_count',args={'text':'one two three'}))]))
                return
            yield LlmResponse(content=types.Content(role='model',parts=[types.Part(text=text)]))
    monkeypatch.setattr(agent_models,'resolve',lambda role,default:Offline(model=role))
    env=make_env([],notes=False)
    env['custom_tools']=configured()
    text,result=agent_adk.run(env,'Count these three words','flash','agent',
        lambda m,t,json_out=False:types.GenerateContentConfig(max_output_tokens=t))
    assert text=='There are 3 words.' and calls==['tool-result-observed']
    assert any('word_count' in s for s in result.steps)


def test_observed_fallback_attempts_report_failed_usage_as_unknown():
    pytest.importorskip('google.adk')
    import dua_engine.agent.agent_models as agent_models
    from google.adk.models import BaseLlm,LlmResponse,FallbackModel
    from google.adk.models.llm_request import LlmRequest
    from google.genai import types
    from google.genai.errors import ServerError
    trace=Trace(sink=lambda _:None)
    class Fail(BaseLlm):
        async def generate_content_async(self,llm_request,stream=False):
            if False:yield LlmResponse()
            raise ServerError(503,{'error':{'code':503,'message':'offline'}})
    class Pass(BaseLlm):
        async def generate_content_async(self,llm_request,stream=False):
            yield LlmResponse(content=types.Content(role='model',parts=[types.Part(text='ok')]),
                usage_metadata=types.GenerateContentResponseUsageMetadata(prompt_token_count=4,candidates_token_count=1,total_token_count=5))
    model=agent_models.observed(FallbackModel(models=[Fail(model='primary'),Pass(model='backup')]),trace)
    async def run():return [r async for r in model.generate_content_async(LlmRequest(model='primary'))]
    assert asyncio.run(run())[0].content.parts[0].text=='ok'
    assert trace.summary()['calls']==2 and not trace.summary()['usageComplete']
    assert trace.summary()['estimatedUsd'] is None


def test_secret_filter_does_not_buffer_normal_words_until_the_answer_finishes():
    from dua_engine.agent.agent_policy import Redactor
    r=Redactor(secrets=['private-test-value'])
    assert r.feed('Hello! ')=='Hello! '
    assert r.feed('How can I help?')=='How can I help?'
    assert r.feed(final=True)==''


def test_rollback_restores_recorded_traffic_and_rejects_other_targets(tmp_path,monkeypatch):
    import subprocess,os
    script=Path(__file__).resolve().parents[1]/'deploy-adk.sh'
    log=tmp_path/'commands.json'
    fake=tmp_path/'gcloud'
    fake.write_text('#!'+sys.executable+'\nimport json,sys\nfrom pathlib import Path\nPath('+repr(str(log))+').write_text(json.dumps(sys.argv[1:]))\n')
    fake.chmod(0o755)
    snapshot=tmp_path/'rollback.json'
    state={'service':'dua-ai-engine','project':'global-healer-m6shk','region':'us-central1',
        'traffic':[{'revisionName':'dua-ai-engine-old','percent':60},{'revisionName':'dua-ai-engine-older','percent':40}]}
    snapshot.write_text(json.dumps(state))
    monkeypatch.setenv('ADK_SNAPSHOT',str(snapshot))
    monkeypatch.setenv('PATH',str(tmp_path)+os.pathsep+os.environ['PATH'])
    for name in ('PROJECT_ID','REGION','SERVICE'):monkeypatch.delenv(name,raising=False)
    result= subprocess.run(['bash',str(script),'rollback'],capture_output=True,text=True)
    assert result.returncode==0
    args=json.loads(log.read_text())
    assert args[-2:]==['--to-revisions','dua-ai-engine-old=60,dua-ai-engine-older=40']
    log.unlink()
    state['project']='a-different-project'
    snapshot.write_text(json.dumps(state))
    result=subprocess.run(['bash',str(script),'rollback'],capture_output=True,text=True)
    assert result.returncode!=0 and not log.exists()


def test_evaluation_cli_writes_a_report_and_fails_missing_answers(tmp_path):
    import adk_cli
    cases=tmp_path/'cases.json'
    responses=tmp_path/'responses.jsonl'
    report=tmp_path/'report.json'
    cases.write_text(json.dumps({'version':1,'cases':[{'id':'a','rubric':{'number':42}},{'id':'b','rubric':{}}]}))
    responses.write_text(json.dumps({'id':'a','text':'42'})+'\n')
    assert adk_cli.main(['evaluate','--cases',str(cases),'--responses',str(responses),'--output',str(report)])==1
    result=json.loads(report.read_text())
    assert result['passed']==1 and result['cases']==2


def test_existing_notes_reindex_is_explicit_bounded_and_owner_store_scoped():
    import dua_engine.agent.agent_notes as agent_notes
    store=object.__new__(agent_notes.NotesStore)
    store._cache=[{'id':str(i),'note':'Owned note '+str(i)} for i in range(30)]
    calls=[]
    store.semantic=SimpleNamespace(add=lambda name,text,document_id,kind:calls.append((document_id,text,kind)))
    result=store.reindex(offset=10,limit=5)
    assert [c[0] for c in calls]==['10','11','12','13','14']
    assert result=={'indexed':5,'nextOffset':15,'more':True}
    with pytest.raises(ValueError):store.reindex(limit=21)


def test_approved_notes_use_the_same_semantic_index(monkeypatch):
    import app as engine
    import dua_engine.agent.agent_notes as agent_notes,dua_engine.agent.agent_documents as agent_documents,dua_engine.agent.agent_toolkit as agent_toolkit
    notes=SimpleNamespace(semantic=None)
    client=object()
    monkeypatch.setenv('GITHUB_TOKEN','')
    monkeypatch.setattr(agent_notes,'for_owner',lambda owner:notes)
    monkeypatch.setattr(agent_documents,'enabled',lambda:True)
    monkeypatch.setattr(engine,'get_client',lambda:client)
    monkeypatch.setattr(agent_documents,'VertexEmbeddings',lambda c:('embedding',c))
    monkeypatch.setattr(agent_documents,'Documents',lambda owner,embed:(owner,embed))
    monkeypatch.setattr(agent_toolkit,'configured',lambda:None)
    env=engine.approval_env('owner-a','test-owner-token')
    assert env['notes'] is notes and notes.semantic==('owner-a',('embedding',client))
