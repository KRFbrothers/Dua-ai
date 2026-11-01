"""Dua ADK operations CLI. No paid/live model calls without an explicit run.

Use --help. Authentication comes from DUA_OWNER_ACCESS_TOKEN/DUA_APP_TOKEN, never
command arguments; auth redirects are blocked. Commands never merge or deploy.
"""
import argparse
import asyncio
import json
import os
from pathlib import Path
import re
import sys
from urllib.parse import urlsplit
from urllib.request import Request


def endpoint(value):
    parsed=urlsplit(value)
    if parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ('','/') or \
            not parsed.hostname or (parsed.scheme!='https' and not (parsed.scheme=='http' and parsed.hostname in ('localhost','127.0.0.1','::1'))):
        raise ValueError('Use an HTTPS service origin (HTTP only for loopback development)')
    return value.rstrip('/')


def request_api(origin,path,data=None,method=None):
    from owner_auth import auth_opener
    token=os.environ.get('DUA_OWNER_ACCESS_TOKEN','')
    if not token:
        raise ValueError('Set DUA_OWNER_ACCESS_TOKEN through secure environment input')
    headers={'Authorization':'Bearer '+token,'Content-Type':'application/json'}
    if os.environ.get('DUA_APP_TOKEN'):headers['X-Dua-App-Token']=os.environ['DUA_APP_TOKEN']
    return auth_opener.open(Request(endpoint(origin)+path,
        data=json.dumps(data).encode() if data is not None else None,headers=headers,method=method),timeout=900)


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    sub=parser.add_subparsers(dest='command',required=True)
    sub.add_parser('init-db',help='Create private ADK session/approval/document tables; no model calls')
    status=sub.add_parser('status',help='Read local configuration or owner-only remote capabilities')
    status.add_argument('--endpoint')
    run=sub.add_parser('run',help='Explicitly run a live agent request against the configured service')
    run.add_argument('--endpoint',required=True)
    run.add_argument('--prompt')
    run.add_argument('--resume',help='Resume an owned server-stored task ID')
    run.add_argument('--input',type=Path,help='JSON request with messages/attachments; credentials stay in env')
    ingest=sub.add_parser('index',help='Explicitly embed/index an extracted text document (provider charges may apply)')
    ingest.add_argument('--endpoint',required=True)
    ingest.add_argument('--file',type=Path,required=True)
    ingest.add_argument('--name')
    reindex=sub.add_parser('reindex-notes',help='Explicitly embed 1–20 existing owned notes; provider charges may apply')
    reindex.add_argument('--endpoint',required=True)
    reindex.add_argument('--offset',type=int,default=0)
    reindex.add_argument('--limit',type=int,default=20)
    delete=sub.add_parser('delete-document',help='Delete one owned indexed document')
    delete.add_argument('--endpoint',required=True)
    delete.add_argument('id')
    scaffold=sub.add_parser('scaffold',help='Create a small discoverable official ADK agent package')
    scaffold.add_argument('directory',type=Path)
    test=sub.add_parser('test',help='Run deterministic engine tests without provider calls')
    test.add_argument('--sdk',action='store_true',help='Require the actual ADK SDK before testing')
    evaluation=sub.add_parser('evaluate',help='Score separately collected model responses; no live judge calls')
    evaluation.add_argument('--cases',type=Path,default=Path(__file__).with_name('evals')/'adk-v1.json')
    evaluation.add_argument('--responses',type=Path,required=True)
    evaluation.add_argument('--output',type=Path,required=True)
    args=parser.parse_args(argv)
    try:
        if args.command=='init-db':
            import dua_engine.agent.agent_store as agent_store,dua_engine.agent.agent_documents as agent_documents
            if not agent_store.database_url():raise ValueError('Set server-side ADK_SESSION_DB_URL first')
            agent_store.initialize()
            agent_documents.initialize()
            async def initialize():
                store=agent_store.service()
                try:await store.prepare_tables()
                finally:await store.close()
            asyncio.run(initialize())
            print(json.dumps(agent_store.status()))
        elif args.command=='status':
            if args.endpoint:
                with request_api(args.endpoint,'/api/agent/capabilities') as response:
                    print(response.read(65536).decode())
            else:
                import dua_engine.agent.agent_store as agent_store,dua_engine.agent.agent_models as agent_models,dua_engine.agent.agent_toolkit as agent_toolkit,dua_engine.agent.agent_documents as agent_documents
                print(json.dumps({'storage':agent_store.status(),'routes':agent_models.routes(),
                    'customTools':sorted(agent_toolkit.configured().tools),'embeddingConfigured':agent_documents.enabled()}))
        elif args.command=='run':
            if sum(bool(v) for v in (args.prompt,args.resume,args.input))!=1:
                raise ValueError('Choose exactly one of --prompt, --resume or --input')
            data=json.loads(args.input.read_text()) if args.input else ({'resumeRun':args.resume} if args.resume else
                {'messages':[{'role':'user','text':args.prompt}]})
            data.update(mode='agent',stream=True)
            with request_api(args.endpoint,'/api/chat',data) as response:
                for line in response:
                    sys.stdout.write(line.decode())
                    sys.stdout.flush()
        elif args.command=='index':
            with request_api(args.endpoint,'/api/agent/documents',{'name':args.name or args.file.name,
                            'text':args.file.read_text()}) as response:print(response.read(65536).decode())
        elif args.command=='reindex-notes':
            with request_api(args.endpoint,'/api/agent/notes/reindex',{'offset':args.offset,'limit':args.limit}) as response:
                print(response.read(65536).decode())
        elif args.command=='delete-document':
            with request_api(args.endpoint,'/api/agent/documents',{'id':args.id},'DELETE') as response:
                print(response.read(65536).decode())
        elif args.command=='scaffold':
            directory=args.directory
            if directory.exists() or not re.fullmatch(r'[a-z][a-z0-9_]{0,63}',directory.name):
                raise ValueError('Choose a new lowercase Python package directory')
            directory.mkdir(parents=True)
            (directory/'__init__.py').write_text('from . import agent\n')
            (directory/'agent.py').write_text('import os\nfrom google.adk import Agent\n\n'
                'root_agent = Agent(name='+repr(directory.name)+', model=os.environ["VERTEX_MODEL"], '
                'instruction="You are an assistant. Use only the configured tools and permissions.")\n')
            print('Created official ADK package. Set explicit provider/model configuration, then use adk run '+directory.name)
        elif args.command=='test':
            if args.sdk:__import__('google.adk')
            import subprocess
            return subprocess.call([sys.executable,'-m','pytest','-q',str(Path(__file__).with_name('tests'))])
        elif args.command=='evaluate':
            from dua_engine.agent.agent_evaluation import evaluate,load_responses
            report=evaluate(json.loads(args.cases.read_text()),load_responses(args.responses))
            args.output.write_text(json.dumps(report,indent=2))
            print(json.dumps({k:v for k,v in report.items() if k!='results'}))
            return 0 if report['passed']==report['cases'] else 1
        return 0
    except Exception as error:
        # DB/provider/HTTP exception text can contain credentials or private data.
        print('ADK command failed ('+type(error).__name__+'). Check configuration/input; no secrets printed.',file=sys.stderr)
        return 2


if __name__=='__main__':raise SystemExit(main())
