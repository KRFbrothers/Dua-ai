"""Opt-in native PostgreSQL/pgvector integration on a disposable local database.

ADK_TEST_POSTGRES_URL must be a local test administrator connection. Each test
creates/drops ONLY its own random database and role; it never reuses app tables.
"""
import asyncio
import os
from pathlib import Path
import secrets
import sys
import uuid
import pytest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))


def test_native_postgres_sessions_approvals_vectors_and_private_schema(monkeypatch):
    source=os.environ.get('ADK_TEST_POSTGRES_URL')
    if not source:pytest.skip('Disposable local PostgreSQL not configured')
    pytest.importorskip('google.adk')
    import psycopg
    from psycopg import sql
    from sqlalchemy.engine import make_url
    import agent_store,agent_documents
    from test_adk_capabilities import test_official_sql_session_resume_does_not_repeat_completed_tool
    admin=make_url(source)
    assert admin.host in ('localhost','127.0.0.1','::1'), 'Refuse non-local database integration tests'
    name='dua_audit_'+uuid.uuid4().hex[:12]
    role='dua_test_'+uuid.uuid4().hex[:12]
    password=secrets.token_hex(24)
    admin_url=admin.set(drivername='postgresql').render_as_string(hide_password=False)
    with psycopg.connect(admin_url,autocommit=True) as con:
        con.execute(sql.SQL('CREATE ROLE {} LOGIN PASSWORD {}').format(sql.Identifier(role),sql.Literal(password)))
        con.execute(sql.SQL('CREATE DATABASE {}').format(sql.Identifier(name)))
    try:
        target=admin.set(drivername='postgresql',database=name).render_as_string(hide_password=False)
        with psycopg.connect(target,autocommit=True) as con:
            con.execute('CREATE SCHEMA extensions')
            con.execute('CREATE EXTENSION vector WITH SCHEMA extensions')
            con.execute('REVOKE ALL ON SCHEMA extensions FROM PUBLIC')
            con.execute(sql.SQL('GRANT USAGE ON SCHEMA extensions TO {}').format(sql.Identifier(role)))
            con.execute(sql.SQL('CREATE SCHEMA dua_adk AUTHORIZATION {}').format(sql.Identifier(role)))
            con.execute('REVOKE ALL ON SCHEMA dua_adk FROM PUBLIC')
            con.execute('CREATE TABLE public.unrelated_private_data (value text)')
        runtime=admin.set(drivername='postgresql',database=name,username=role,password=password)
        monkeypatch.setenv('ADK_SESSION_DB_URL',runtime.render_as_string(hide_password=False))
        monkeypatch.setenv('ADK_DB_LOCAL','1')
        agent_store.initialize()
        agent_documents.initialize()
        async def initialize():
            store=agent_store.service()
            try:await store.prepare_tables()
            finally:await store.close()
        asyncio.run(initialize())
        assert agent_store.status()['ready'] and agent_store.status()['durableAcrossInstances']
        class Embed:
            space='offline:pgvector:2'
            def __call__(self,texts,query=False):
                return [[1.,0.] if any(w in t for w in ('car','automobile')) else [0.,1.] for t in texts]
        owned=agent_documents.Documents('owner-a',Embed())
        doc=owned.add('Road','A car on the road')
        other=agent_documents.Documents('owner-b',Embed())
        other.add('Other','A private automobile')
        matches=owned.search('automobile')
        assert len(matches)==1 and matches[0]['id']==doc['id'] and matches[0]['score']==1
        assert not other.delete(doc['id'])['deleted']
        assert owned.delete(doc['id'])['deleted'] and not owned.search('automobile')
        action=agent_store.SqlPendingStore().create('owner-a','save_note',{'note':'Synthetic pg test'},request_key='once')
        assert agent_store.SqlPendingStore().take(action['id'],'owner-a')['id']==action['id']
        with psycopg.connect(target) as con:
            assert not con.execute('SELECT has_table_privilege(%s,%s,%s)',(role,'public.unrelated_private_data','SELECT')).fetchone()[0]
            assert con.execute("SELECT format_type(atttypid,atttypmod) FROM pg_attribute WHERE attrelid='dua_adk.dua_agent_chunks'::regclass AND attname='embedding'").fetchone()[0]=='extensions.vector'
        test_official_sql_session_resume_does_not_repeat_completed_tool(agent_store)
    finally:
        with psycopg.connect(admin_url,autocommit=True) as con:
            con.execute(sql.SQL('DROP DATABASE {} WITH (FORCE)').format(sql.Identifier(name)))
            con.execute(sql.SQL('DROP ROLE {}').format(sql.Identifier(role)))


def _model_supabase_pooler(monkeypatch):
    """Backend sessions start with search_path=public and client startup options are dropped."""
    import re
    import asyncpg
    import psycopg
    original=psycopg.Connection.connect.__func__
    def pooled(cls,conninfo='',**kwargs):
        conninfo=re.sub(r'options=\S+','',conninfo or '')
        kwargs.pop('options',None)
        return original(cls,conninfo,options='-c search_path=public',**kwargs)
    monkeypatch.setattr(psycopg.Connection,'connect',classmethod(pooled))
    monkeypatch.setattr(psycopg,'connect',psycopg.Connection.connect)
    real=asyncpg.connect
    async def pooled_async(*args,**kwargs):
        kwargs['server_settings']={'search_path':'public'}
        return await real(*args,**kwargs)
    monkeypatch.setattr(asyncpg,'connect',pooled_async)


def test_private_schema_is_used_when_pooler_overrides_search_path(monkeypatch):
    source=os.environ.get('ADK_TEST_POSTGRES_URL')
    if not source:pytest.skip('Disposable local PostgreSQL not configured')
    pytest.importorskip('google.adk')
    import psycopg
    from psycopg import sql
    from sqlalchemy.engine import make_url
    import agent_store,agent_documents
    admin=make_url(source)
    assert admin.host in ('localhost','127.0.0.1','::1'), 'Refuse non-local database integration tests'
    name='dua_audit_'+uuid.uuid4().hex[:12]
    role='dua_test_'+uuid.uuid4().hex[:12]
    password=secrets.token_hex(24)
    admin_url=admin.set(drivername='postgresql').render_as_string(hide_password=False)
    with psycopg.connect(admin_url,autocommit=True) as con:
        con.execute(sql.SQL('CREATE ROLE {} LOGIN PASSWORD {}').format(sql.Identifier(role),sql.Literal(password)))
        con.execute(sql.SQL('CREATE DATABASE {}').format(sql.Identifier(name)))
    try:
        target=admin.set(drivername='postgresql',database=name).render_as_string(hide_password=False)
        with psycopg.connect(target,autocommit=True) as con:
            ident=sql.Identifier(role)
            con.execute('CREATE SCHEMA extensions')
            con.execute('CREATE EXTENSION vector WITH SCHEMA extensions')
            con.execute(sql.SQL('GRANT USAGE ON SCHEMA extensions TO {}').format(ident))
            # Supabase-compatible setup: admin owns the schema, runtime gets grants.
            con.execute('CREATE SCHEMA dua_adk')
            con.execute('REVOKE ALL ON SCHEMA dua_adk FROM PUBLIC')
            con.execute(sql.SQL('GRANT USAGE, CREATE ON SCHEMA dua_adk TO {}').format(ident))
            con.execute('REVOKE CREATE ON SCHEMA public FROM PUBLIC')
            con.execute(sql.SQL('ALTER ROLE {} SET search_path = dua_adk, extensions').format(ident))
        runtime=admin.set(drivername='postgresql',database=name,username=role,password=password)
        monkeypatch.setenv('ADK_SESSION_DB_URL',runtime.render_as_string(hide_password=False))
        monkeypatch.setenv('ADK_DB_LOCAL','1')
        _model_supabase_pooler(monkeypatch)
        with psycopg.connect(runtime.render_as_string(hide_password=False)) as con:
            assert con.execute('SELECT current_schema()').fetchone()[0]=='public', 'pooler model must override role defaults'
        agent_store.initialize()
        agent_documents.initialize()
        async def initialize():
            store=agent_store.service()
            try:
                await store.prepare_tables()
                created=await store.create_session(app_name='dua-agent',user_id='owner-a',state={'probe':1})
                loaded=await store.get_session(app_name='dua-agent',user_id='owner-a',session_id=created.id)
                assert loaded.state['probe']==1
            finally:await store.close()
        asyncio.run(initialize())
        assert agent_store.status()['ready']
        class Embed:
            space='offline:pgvector:2'
            def __call__(self,texts,query=False):return [[1.,0.] for _ in texts]
        docs=agent_documents.Documents('owner-a',Embed())
        doc=docs.add('Probe','Pooler search path probe')
        assert docs.search('probe')[0]['id']==doc['id']
        with psycopg.connect(target) as con:
            assert not con.execute("SELECT count(*) FROM pg_tables WHERE schemaname='public' AND tableowner=%s",(role,)).fetchone()[0]
            assert con.execute("SELECT count(*) FROM pg_tables WHERE schemaname='dua_adk' AND tablename IN ('sessions','events','dua_agent_chunks')").fetchone()[0]==3
    finally:
        with psycopg.connect(admin_url,autocommit=True) as con:
            con.execute(sql.SQL('DROP DATABASE {} WITH (FORCE)').format(sql.Identifier(name)))
            con.execute(sql.SQL('DROP ROLE {}').format(sql.Identifier(role)))
