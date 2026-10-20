"""Durable, owner-scoped approvals alongside official ADK SQL sessions.

ADK_SESSION_DB_URL is server-only. SQLite is for local persisted tests; production
uses a dedicated PostgreSQL database/role, never Cloud Run's temporary disk.
Atomic claims are consumed before side effects; uncertain writes are not retried.
"""
import hashlib
import json
import os
import secrets
import time


def database_url():
    return os.environ.get('ADK_SESSION_DB_URL', '').strip()


def service():
    from google.adk.sessions import DatabaseSessionService
    from sqlalchemy.pool import NullPool
    options = {'poolclass':NullPool,'hide_parameters':True}
    from sqlalchemy.engine import make_url
    url = make_url(database_url())
    if url.get_backend_name() == 'sqlite':
        url=url.set(drivername='sqlite+aiosqlite')
    if url.get_backend_name() == 'postgresql':
        url=url.set(drivername='postgresql+asyncpg')
        import ssl
        local = url.host in ('localhost','127.0.0.1','::1') and os.environ.get('ADK_DB_LOCAL') == '1'
        options['connect_args'] = {'server_settings':{'search_path':'dua_adk,extensions'},
                                   'ssl':False if local else ssl.create_default_context(), 'timeout':10}
    return DatabaseSessionService(db_url=url.render_as_string(hide_password=False), **options)


def engine():
    from sqlalchemy import create_engine
    from sqlalchemy.engine import make_url
    from sqlalchemy.pool import NullPool
    url = make_url(database_url())
    if url.get_backend_name() == 'sqlite':
        url = url.set(drivername='sqlite')
    elif url.get_backend_name() == 'postgresql':
        url = url.set(drivername='postgresql+psycopg')
    else:
        raise ValueError('ADK sessions support only SQLite locally or PostgreSQL in production')
    options = {}
    if url.get_backend_name() == 'postgresql':
        local = url.host in ('localhost','127.0.0.1','::1') and os.environ.get('ADK_DB_LOCAL') == '1'
        options['connect_args'] = {'options':'-csearch_path=dua_adk,extensions', 'sslmode':'disable' if local else 'verify-full', 'connect_timeout':5}
    return create_engine(url, poolclass=NullPool, hide_parameters=True, **options)


def tables():
    from sqlalchemy import MetaData, Table, Column, String, Float, JSON
    metadata = MetaData()
    table = Table('dua_agent_approvals', metadata,
                  Column('id', String(64), primary_key=True), Column('owner', String(128), nullable=False),
                  Column('request_key', String(64), unique=True, nullable=False),
                  Column('run_id', String(36), nullable=True, index=True),
                  Column('state', String(16), nullable=False), Column('created', Float, nullable=False),
                  Column('expires', Float, nullable=False), Column('payload', JSON, nullable=False))
    Table('dua_agent_leases', metadata, Column('id',String(36),primary_key=True),
          Column('owner',String(128),primary_key=True),Column('holder',String(36),nullable=False),
          Column('expires',Float,nullable=False))
    return metadata, table


def initialize():
    """Explicit CLI migration for the approval table; official SDK owns its schema."""
    metadata, _ = tables()
    db = engine()
    try:
        if db.url.get_backend_name() == 'postgresql':
            from sqlalchemy import text
            with db.begin() as con:
                if con.execute(text("SELECT to_regnamespace('dua_adk')")).scalar_one() is None:
                    raise ValueError('An administrator must create the private dua_adk schema before init-db')
                con.execute(text('REVOKE ALL ON SCHEMA dua_adk FROM PUBLIC'))
        metadata.create_all(db)
    finally:
        db.dispose()


class SqlPendingStore:
    def create(self, owner, name, args, request_text='', request_key=None):
        import agent_safety
        from sqlalchemy import select, insert, func
        from sqlalchemy.exc import IntegrityError
        if not owner:
            raise agent_safety.ApprovalError('Owner authentication required.', 401)
        now = time.time()
        title, details = agent_safety.describe(name, args)
        action = {'id': secrets.token_urlsafe(18), 'owner': owner, 'tool': name, 'args': args,
                  'title': title, 'details': details, 'request_text': str(request_text)[:2000],
                  'created': now, 'expires': now + agent_safety.APPROVAL_SECONDS}
        key = hashlib.sha256((str(owner) + ':' + (request_key or action['id'])).encode()).hexdigest()
        _, table = tables()
        db = engine()
        try:
            with db.begin() as con:
                lock_owner(con,owner,'approvals')
                existing = con.execute(select(table).where(table.c.request_key == key)).mappings().first()
                if existing:
                    if existing['owner'] != owner or existing['state'] != 'pending' or existing['expires'] <= now:
                        raise agent_safety.ApprovalError('This action was already used or expired. It will not be repeated.', 409)
                    return existing['payload']
                count = con.execute(select(func.count()).select_from(table).where(
                    table.c.owner == owner, table.c.state == 'pending', table.c.expires > now)).scalar_one()
                if count >= agent_safety.MAX_STORED:
                    raise agent_safety.ApprovalError('Too many pending actions. Resolve existing approvals first.', 429)
                con.execute(insert(table).values(id=action['id'], owner=owner, request_key=key, state='pending',
                                                created=now, expires=action['expires'], payload=action,
                    run_id=request_key.split(':',1)[0] if request_key else None))
            return action
        except IntegrityError:
            # A concurrent resume won the same idempotency key; read it once,
            # never enqueue a second write for the same stored operation.
            with db.connect() as con:
                row = con.execute(select(table).where(table.c.request_key == key, table.c.owner == owner)).mappings().first()
            if row and row['state'] == 'pending' and row['expires'] > now:
                return row['payload']
            raise agent_safety.ApprovalError('This action was already claimed.', 409) from None
        finally:
            db.dispose()

    def take(self, action_id, owner):
        import agent_safety
        from sqlalchemy import update
        if not owner or not isinstance(action_id, str):
            raise agent_safety.ApprovalError('Approval not found.', 404)
        _, table = tables()
        db = engine()
        try:
            with db.begin() as con:
                row = con.execute(update(table).where(table.c.id == action_id, table.c.owner == owner,
                    table.c.state == 'pending').values(state='claimed').returning(table.c.expires, table.c.payload)).first()
            if row is None:
                raise agent_safety.ApprovalError('Approval not found or already used.', 404)
            if row.expires <= time.time():
                raise agent_safety.ApprovalError('Approval expired. Ask the agent again.', 410)
            return row.payload
        finally:
            db.dispose()


def approvals():
    if database_url():
        return SqlPendingStore()
    import agent_safety
    return agent_safety.STORE


async def get_task(owner, task_id):
    """A SQL session can only be loaded using the authenticated owner ID."""
    import uuid
    uuid.UUID(task_id)
    store = service()
    try:
        return await store.get_session(app_name='dua-agent', user_id=owner, session_id=task_id)
    finally:
        await store.close()


def status():
    """Read-only deployment diagnostic; never creates tables or exposes a DB URL."""
    if not database_url():
        return {'configured':False,'ready':False,'reason':'not_configured','durableAcrossInstances':False}
    from sqlalchemy import inspect
    db=engine()
    try:
        with db.connect() as con:
            names=set(inspect(con).get_table_names())
        needed={'dua_agent_approvals','dua_agent_documents','dua_agent_leases','dua_agent_chunks','sessions','events'}
        return {'configured':True,'ready':needed <= names,
                'reason':'ok' if needed <= names else 'migration_required',
                'durableAcrossInstances':db.url.get_backend_name()=='postgresql'}
    except Exception:
        return {'configured':True,'ready':False,'reason':'unavailable','durableAcrossInstances':False}
    finally:
        db.dispose()


class TaskBusy(Exception):
    pass


def acquire(owner,task_id,seconds=960):
    from sqlalchemy import insert,update
    from sqlalchemy.exc import IntegrityError
    import uuid
    metadata,_=tables()
    table=metadata.tables['dua_agent_leases']
    holder=str(uuid.uuid4())
    db=engine()
    try:
        with db.begin() as con:
            result=con.execute(update(table).where(table.c.id==task_id,table.c.owner==owner,
                table.c.expires<=time.time()).values(holder=holder,expires=time.time()+seconds))
            if result.rowcount:return holder
            try:
                con.execute(insert(table).values(id=task_id,owner=owner,holder=holder,expires=time.time()+seconds))
            except IntegrityError:
                raise TaskBusy('This task is already running. Resume after it stops.') from None
        return holder
    finally:db.dispose()


def release(owner,task_id,holder):
    from sqlalchemy import delete
    metadata,_=tables()
    table=metadata.tables['dua_agent_leases']
    db=engine()
    try:
        with db.begin() as con:
            con.execute(delete(table).where(table.c.id==task_id,table.c.owner==owner,table.c.holder==holder))
    finally:db.dispose()


def lock_owner(connection,owner,scope):
    """Serialize quota checks/updates for one owner, not every request globally."""
    if connection.dialect.name=='postgresql':
        from sqlalchemy import text
        key=int.from_bytes(hashlib.sha256((scope+':'+str(owner)).encode()).digest()[:8],'big',signed=True)
        connection.execute(text('SELECT pg_advisory_xact_lock(:key)'),{'key':key})
    elif connection.dialect.name=='sqlite':
        connection.exec_driver_sql('BEGIN IMMEDIATE')
