"""Owner-scoped document RAG and semantic note memory on the session SQL store.

PostgreSQL uses native pgvector cosine queries; SQLite has an exact offline
fallback. Vectors are persisted with model/dimension identity, not in process.
Exact cosine search is intentionally bounded for this personal app; large-corpus
approximate indexing is not implied. Hosted embeddings require explicit config.
"""
import json
import math
import os
import uuid

MAX_DOCUMENT_CHARS = 100_000
MAX_DOCUMENTS = 50
CHUNK_SIZE, OVERLAP = 1000, 150


def enabled():
    import dua_engine.agent.agent_store as agent_store
    return bool(agent_store.database_url() and os.environ.get('ADK_EMBEDDING_MODEL', '').strip())


def tables():
    from sqlalchemy import MetaData, Table, Column, String, JSON, Integer, ForeignKey
    from pgvector.sqlalchemy import Vector
    metadata = MetaData()
    table = Table('dua_agent_documents', metadata,
        Column('id', String(36), primary_key=True), Column('owner', String(128), index=True, nullable=False),
        Column('name', String(200), nullable=False), Column('kind', String(16), nullable=False),
        Column('space', String(250), nullable=False), Column('chunks', Integer, nullable=False))
    Table('dua_agent_chunks', metadata,
        Column('document_id',String(36),ForeignKey('dua_agent_documents.id',ondelete='CASCADE'),primary_key=True),
        Column('position',Integer,primary_key=True),Column('owner',String(128),index=True,nullable=False),
        Column('space',String(250),nullable=False),Column('text',String(CHUNK_SIZE),nullable=False),
        Column('embedding',Vector().with_variant(JSON,'sqlite'),nullable=False))
    return metadata, table


def initialize():
    import dua_engine.agent.agent_store as agent_store
    metadata, _ = tables()
    db = agent_store.engine()
    try:
        if db.url.get_backend_name()=='postgresql':
            from sqlalchemy import text
            with db.connect() as con:
                available=con.execute(text("SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid=t.typnamespace WHERE t.typname='vector' AND n.nspname='extensions'")).first()
            if not available:
                raise ValueError('An administrator must enable pgvector in schema extensions before init-db')
        metadata.create_all(db)
    finally:
        db.dispose()


def chunks(text):
    if not isinstance(text, str) or not text.strip() or len(text) > MAX_DOCUMENT_CHARS:
        raise ValueError('Provide 1–100000 characters of extracted document text')
    return [text[i:i+CHUNK_SIZE] for i in range(0, len(text), CHUNK_SIZE-OVERLAP)]


def vectors(values):
    result = []
    dimension = None
    for row in values:
        if not isinstance(row, (list, tuple)) or not row or len(row) > 4096 or any(
                isinstance(v, bool) or not isinstance(v, (int, float)) or not math.isfinite(v) for v in row):
            raise ValueError('Embedding provider returned invalid vectors')
        if dimension is None:
            dimension = len(row)
        if len(row) != dimension:
            raise ValueError('Embedding dimensions changed within one response')
        norm = math.sqrt(sum(v*v for v in row))
        if not norm:
            raise ValueError('Embedding provider returned a zero vector')
        result.append([v/norm for v in row])
    return result


class VertexEmbeddings:
    def __init__(self, client, trace=None):
        self.client, self.trace = client, trace
        self.model = os.environ.get('ADK_EMBEDDING_MODEL', '').strip()
        self.dimensions = int(os.environ.get('ADK_EMBEDDING_DIMENSIONS', '768'))
        if not self.model or not 1 <= self.dimensions <= 4096:
            raise ValueError('Configure an embedding model and 1–4096 dimensions')
        self.space = f'vertex:{self.model}:{self.dimensions}'

    def __call__(self, texts, query=False):
        from google.genai import types
        raw = []
        for offset in range(0,len(texts),32):
            result = self.client.models.embed_content(model=self.model, contents=texts[offset:offset+32],
                config=types.EmbedContentConfig(output_dimensionality=self.dimensions,
                    http_options=types.HttpOptions(timeout=30000),
                    task_type='RETRIEVAL_QUERY' if query else 'RETRIEVAL_DOCUMENT'))
            if self.trace:
                # Some embedding endpoints report characters rather than tokens.
                # Unknown usage/cost must not become a fabricated zero.
                self.trace.usage(uuid.uuid4().hex, self.model, getattr(result, 'usage_metadata', None))
                self.trace.emit('embedding.end', model=self.model, status='ok')
            raw.extend(e.values for e in result.embeddings or [])
        found = vectors(raw)
        if len(found) != len(texts) or any(len(v) != self.dimensions for v in found):
            raise ValueError('Embedding response does not match configured dimensions/input count')
        return found


class Documents:
    def __init__(self, owner, embed):
        if not owner:
            raise ValueError('Owner authentication required')
        self.owner, self.embed = owner, embed

    def add(self, name, text, document_id=None, kind='document'):
        import dua_engine.agent.agent_store as agent_store
        from sqlalchemy import select, func, insert, update, delete
        document_id = str(uuid.UUID(document_id)) if document_id else str(uuid.uuid4())
        if not isinstance(name, str) or not name.strip() or len(name) > 200 or kind not in ('document', 'note'):
            raise ValueError('Provide a short document name and supported kind')
        parts = chunks(text)
        metadata, table = tables()
        indexed=metadata.tables['dua_agent_chunks']
        def preflight(con):
            existing=con.execute(select(table.c.owner,table.c.kind).where(table.c.id==document_id)).first()
            if existing and (existing.owner!=self.owner or existing.kind!=kind):
                raise ValueError('Document not found')
            count=con.execute(select(func.count()).select_from(table).where(
                table.c.owner==self.owner,table.c.kind==kind)).scalar_one()
            if not existing and count >= (200 if kind=='note' else MAX_DOCUMENTS):
                raise ValueError('Document limit reached; remove an old document first')
            return existing
        db = agent_store.engine()
        try:
            # Reject unauthorized IDs/full quotas before a paid embedding call.
            # Recheck under the owner lock afterwards; concurrent inserts cannot
            # bypass quotas while embedding runs outside the DB transaction.
            with db.connect() as con:
                preflight(con)
            embeddings = vectors(self.embed(parts))
            if len(embeddings) != len(parts):
                raise ValueError('Embedding result count does not match chunks')
            payload = [{'text': t, 'embedding': e} for t,e in zip(parts, embeddings)]
            with db.begin() as con:
                agent_store.lock_owner(con,self.owner,'documents')
                existing=preflight(con)
                fields = dict(name=name.strip(), kind=kind, space=self.embed.space, chunks=len(payload))
                if existing:
                    con.execute(update(table).where(table.c.id==document_id, table.c.owner==self.owner).values(**fields))
                else:
                    con.execute(insert(table).values(id=document_id, owner=self.owner, **fields))
                con.execute(delete(indexed).where(indexed.c.document_id==document_id,indexed.c.owner==self.owner))
                con.execute(insert(indexed),[{'document_id':document_id,'position':i,'owner':self.owner,
                    'space':self.embed.space,'text':part['text'],'embedding':part['embedding']} for i,part in enumerate(payload)])
            return {'id':document_id, 'name':name.strip(), 'chunks':len(parts), 'indexed':True}
        finally:
            db.dispose()

    def search(self, query, kind='document', limit=5):
        import dua_engine.agent.agent_store as agent_store
        from sqlalchemy import select
        if not isinstance(query, str) or not query.strip() or len(query)>4000:
            raise ValueError('Provide a short retrieval query')
        query_vector = vectors(self.embed([query], query=True))[0]
        metadata, table = tables()
        indexed=metadata.tables['dua_agent_chunks']
        db = agent_store.engine()
        limit=max(1,min(limit,10))
        try:
            query=select(indexed.c.document_id,indexed.c.position,indexed.c.text,table.c.name).join(
                table,indexed.c.document_id==table.c.id).where(indexed.c.owner==self.owner,
                table.c.owner==self.owner,table.c.kind==kind,indexed.c.space==self.embed.space)
            with db.connect() as con:
                if db.url.get_backend_name()=='postgresql':
                    distance=indexed.c.embedding.cosine_distance(query_vector)
                    rows=con.execute(query.add_columns((1-distance).label('score')).order_by(distance).limit(limit)).mappings().all()
                else:
                    rows=con.execute(query.add_columns(indexed.c.embedding).limit(6000)).mappings().all()
        finally:
            db.dispose()
        matches=[]
        for row in rows:
            if 'score' in row:
                score=float(row['score'])
            else:
                if len(row['embedding'])!=len(query_vector):continue
                score=sum(x*y for x,y in zip(query_vector,row['embedding']))
            matches.append({'id':row['document_id'],'name':row['name'],'chunk':row['position'],
                'text':row['text'],'citation':f"[D:{row['document_id']}:{row['position']}]",'score':round(score,6)})
        return sorted(matches,key=lambda x:x['score'],reverse=True)[:limit]

    def delete(self, document_id):
        import dua_engine.agent.agent_store as agent_store
        from sqlalchemy import delete
        document_id = str(uuid.UUID(document_id))
        metadata, table = tables()
        indexed=metadata.tables['dua_agent_chunks']
        db = agent_store.engine()
        try:
            with db.begin() as con:
                con.execute(delete(indexed).where(indexed.c.document_id==document_id,indexed.c.owner==self.owner))
                result=con.execute(delete(table).where(table.c.id==document_id, table.c.owner==self.owner))
            return {'deleted':result.rowcount == 1}
        finally:
            db.dispose()

    def list(self):
        import dua_engine.agent.agent_store as agent_store
        from sqlalchemy import select
        _,table=tables()
        db=agent_store.engine()
        try:
            with db.connect() as con:
                return [dict(r) for r in con.execute(select(table.c.id,table.c.name,table.c.kind).where(
                    table.c.owner==self.owner).limit(MAX_DOCUMENTS+200)).mappings()]
        finally:
            db.dispose()
