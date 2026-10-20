#!/usr/bin/env bash
# Run only in Google Cloud Shell. Source is pinned to the reviewed merged commit.
set -euo pipefail
set +x
umask 077
adk_action="${1:-plan}"
case "$adk_action" in plan|stage) ;; *) echo 'Usage: bash dua-adk-cloudshell.sh plan|stage [--reset-db-credential]' >&2; exit 2;; esac
adk_extra=()
for adk_arg in "${@:2}"; do
  case "$adk_arg" in --reset-db-credential) adk_extra+=("$adk_arg");; *) echo "Unknown option: $adk_arg" >&2; exit 2;; esac
done
for adk_bin in gcloud git python3 openssl; do command -v "$adk_bin" >/dev/null || { echo "Missing $adk_bin; open Google Cloud Shell." >&2; exit 2; }; done
# Earlier runs left large work dirs (venv) in $HOME. Keep only rollback
# snapshots of runs that staged a candidate; remove everything else.
adk_cleanup_dir() {
  [ -d "$1" ] || return 0
  if [ -f "$1/rollback.json" ]; then rm -rf "$1/venv" "$1/database-ca.pem" "$1/cloudshell_setup.py"; else rm -rf "$1"; fi
}
for adk_old in "$HOME"/dua-adk-stage.*; do adk_cleanup_dir "$adk_old"; done
adk_work="$(mktemp -d "$HOME/dua-adk-stage.XXXXXX")"
trap 'adk_cleanup_dir "$adk_work"' EXIT
adk_source="$adk_work/source"
git init -q "$adk_source"
git -C "$adk_source" remote add origin https://github.com/KRFbrothers/Dua-ai.git
git -C "$adk_source" fetch --depth=1 origin 9a7b4d79cdca297cddc7fc2aaebf1aab26d987ad
git -C "$adk_source" sparse-checkout init --cone
git -C "$adk_source" sparse-checkout set google-cloud-backend
git -C "$adk_source" checkout -q -b main FETCH_HEAD
cat > "$adk_work/cloudshell_setup.py" <<'DUA_ADK_PYTHON'
"""Run in the owner's Google Cloud Shell; no credentials accepted in arguments.

Uses the merged official-ADK engine. Stage preserves serving traffic and existing
configuration. It does not promote a revision or call a hosted model.
"""
import argparse
import asyncio
import getpass
import json
import os
from pathlib import Path
import re
import secrets
import ssl
import subprocess
import sys
from urllib.parse import quote, unquote, urlsplit
from urllib.request import urlopen

PROJECT='global-healer-m6shk'
REGION='us-central1'
SERVICE='dua-ai-engine'
SUPABASE_REF='gmjzshgzdaizfximuhsf'
ROLE='dua_adk_runtime'
DB_SECRET='dua-adk-session-db-url'
CA_SECRET='dua-adk-database-ca'
MAX_SECRET_BYTES=60000  # Secret Manager payload limit is 64 KiB.
HOST_RE=r'aws-[0-9]+-[a-z0-9-]+\.pooler\.supabase\.com'


def command(args, payload=None, visible=False):
    result=subprocess.run(args,input=payload,text=True,capture_output=not visible,check=False)
    if result.returncode:
        # SDK/CLI diagnostics may contain private connection data. Don't replay it.
        raise RuntimeError('Command failed: '+args[0]+' '+args[1]+' (exit '+str(result.returncode)+'). Check Cloud Console permissions/logs; secret values were not printed.')
    return result.stdout.strip() if not visible else ''


def gcloud(*args, payload=None):
    return command(['gcloud',*args,'--project',PROJECT,'--quiet'],payload)


def metadata(*args):
    return json.loads(gcloud(*args,'--format=json'))


STEP='starting'


def step(name):
    global STEP
    STEP=name


def prompt(label):
    # A tty is not seekable: open('/dev/tty','r+') raises UnsupportedOperation.
    # Use separate read and write handles.
    with open('/dev/tty','w') as out, open('/dev/tty') as tty:
        out.write(label);out.flush()
        return tty.readline().strip()


def normalize_traffic(service):
    return sorted((row.get('revisionName'),row.get('percent',0)) for row in service['status'].get('traffic',[]) if row.get('percent',0)>0)


def service_info():
    return metadata('run','services','describe',SERVICE,'--region',REGION)


def secret_exists(name):
    items=metadata('secrets','list','--filter','name:'+name)
    return any(item['name'].rsplit('/',1)[-1]==name for item in items)


def secret_versions(name):
    if not secret_exists(name):return None
    versions=metadata('secrets','versions','list',name,'--filter','state:ENABLED','--sort-by=~createTime','--limit=1')
    if not versions:return None
    return versions[0]['name'].rsplit('/',1)[-1]


def save_secret(name,value):
    """Idempotent: add a version to an existing secret, else create it. Value goes via stdin only."""
    if len(value.encode())>MAX_SECRET_BYTES:raise ValueError('Value for '+name+' is too large for Secret Manager (64 KiB limit).')
    if secret_exists(name):gcloud('secrets','versions','add',name,'--data-file=-',payload=value)
    else:gcloud('secrets','create',name,'--replication-policy=automatic','--data-file=-',payload=value)
    version=secret_versions(name)
    if not version:raise RuntimeError('Secret '+name+' was saved but no enabled version is visible.')
    return version


def allow_runtime(name,account):
    member='serviceAccount:'+account
    policy=metadata('secrets','get-iam-policy',name)
    if any(b.get('role')=='roles/secretmanager.secretAccessor' and member in b.get('members',[]) for b in policy.get('bindings',[])):return
    gcloud('secrets','add-iam-policy-binding',name,'--member='+member,
           '--role=roles/secretmanager.secretAccessor')


def runtime_ready(url,ca_file):
    """True when the stored runtime credential logs in and the private schema/pgvector are usable."""
    import psycopg
    try:
        with psycopg.connect(url,sslmode='verify-full',sslrootcert=str(ca_file),connect_timeout=15) as con:
            row=con.execute("SELECT to_regnamespace('dua_adk') IS NOT NULL AND has_schema_privilege(current_user,'dua_adk','USAGE,CREATE') AND EXISTS (SELECT 1 FROM pg_extension e JOIN pg_namespace n ON e.extnamespace=n.oid WHERE e.extname='vector' AND n.nspname='extensions') AND has_schema_privilege(current_user,'extensions','USAGE')").fetchone()
            return bool(row and row[0])
    except Exception:
        return False


def read_ca(path):
    extra=Path(path).expanduser().read_text()
    if 'PRIVATE KEY' in extra or '-----BEGIN CERTIFICATE-----' not in extra:
        raise ValueError('A public CA certificate is required, never a private key.')
    return extra.strip()+'\n'


def verify_storage():
    """Synthetic persisted-session/vector smoke, no hosted provider calls."""
    import uuid
    import agent_store,agent_documents
    owner='setup-probe-'+uuid.uuid4().hex
    task=str(uuid.uuid4())
    async def session_probe():
        first=agent_store.service()
        try:
            await first.create_session(app_name='dua-agent',user_id=owner,session_id=task,state={'setup_probe':task})
        finally:await first.close()
        second=agent_store.service()
        try:
            loaded=await second.get_session(app_name='dua-agent',user_id=owner,session_id=task)
            if loaded is None or loaded.state.get('setup_probe')!=task:
                raise ValueError('Persisted session smoke failed; do not deploy.')
        finally:
            try:await second.delete_session(app_name='dua-agent',user_id=owner,session_id=task)
            finally:await second.close()
    try:
        asyncio.run(session_probe())
    finally:
        # SDK session deletion does not remove the per-user state row. Delete
        # only this random synthetic user, leaving shared application state.
        from sqlalchemy import text
        db=agent_store.engine()
        try:
            with db.begin() as con:
                con.execute(text('DELETE FROM user_states WHERE app_name=:app AND user_id=:owner'),
                            {'app':'dua-agent','owner':owner})
        finally:db.dispose()
    class LocalEmbedding:
        space='setup-smoke:synthetic:2'
        def __call__(self,texts,query=False):return [[1.,0.] for _ in texts]
    documents=agent_documents.Documents(owner,LocalEmbedding())
    record=documents.add('Setup probe','Synthetic ADK database setup check')
    try:
        found=documents.search('Synthetic check')
        if not found or found[0]['id']!=record['id']:
            raise ValueError('Native vector smoke failed; do not deploy.')
    finally:documents.delete(record['id'])
    print('Database session persistence and native vector smoke passed; synthetic records cleaned.')


SEARCH_PATH='dua_adk, extensions'
RETRYABLE_SQLSTATES=('28P01','28000','42501','42P01','3F000','3D000')


def sanitize(text,hidden=()):
    text=str(text)
    for value in hidden:
        if value:text=text.replace(value,'***').replace(quote(value,safe=''),'***')
    text=re.sub(r'postgres(?:ql)?(?:\+\w+)?://\S+','postgresql://***',text)
    text=re.sub(r'(?i)(password\s*=\s*)\S+',r'\1***',text)
    return ' '.join(text.split())[:400]


def db_error(error,hidden=()):
    """Return (sqlstate, safe one-line server message). Never includes credentials."""
    orig=getattr(error,'orig',None) or error
    state=getattr(orig,'sqlstate',None) or getattr(orig,'pgcode',None)
    diag=getattr(orig,'diag',None)
    primary=getattr(diag,'message_primary',None) if diag else None
    message=primary or (str(orig).splitlines() or [''])[0]
    message=re.sub(r"<class '[^']*'>:\s*",'',message)
    return state,sanitize(message,hidden)


def database_failure(error,hidden=()):
    state,message=db_error(error,hidden)
    return 'Database error'+(' '+state if state else '')+' ('+type(getattr(error,'orig',None) or error).__name__+'): '+message


def ask_yes(label):
    try:return prompt(label).strip().lower() in ('y','yes')
    except OSError:return False


def runtime_connect(url,ca_file):
    import psycopg
    return psycopg.connect(url,sslmode='verify-full',sslrootcert=str(ca_file),connect_timeout=15,autocommit=True)


def ensure_search_path(url,ca_file,hidden=()):
    """Best-effort role default. Supabase's pooler overrides it, so the pinned
    app sets search_path on every connection; verify_app_schema() checks that."""
    try:
        with runtime_connect(url,ca_file) as con:
            con.execute('ALTER ROLE CURRENT_USER SET search_path = '+SEARCH_PATH)
    except Exception as error:
        print('Note: role search_path default not set ('+database_failure(error,hidden)+'); the app sets it per connection.',file=sys.stderr)


def verify_app_schema(agent_store,hidden=()):
    """Check the mechanism Cloud Run uses: the app engine must land in dua_adk."""
    from sqlalchemy import text
    db=agent_store.engine()
    try:
        with db.connect() as con:
            schema=con.execute(text('SELECT current_schema()')).scalar_one()
    except Exception as error:
        raise ValueError(database_failure(error,hidden)) from None
    finally:db.dispose()
    if schema!='dua_adk':
        raise ValueError('App database sessions use schema '+str(schema)+' instead of dua_adk. Stop; no tables were created.')


def admin_repair(psycopg,sql,host,runtime_password,ca_file,save_url=None):
    """Create/repair the dedicated role, private schema and grants as the Supabase admin."""
    step('database admin login')
    admin_password=getpass.getpass('Supabase DATABASE password (hidden; not your Google password): ')
    try:
        admin=psycopg.connect(host=host,port=5432,dbname='postgres',user='postgres.'+SUPABASE_REF,
            password=admin_password,sslmode='verify-full',sslrootcert=str(ca_file),connect_timeout=15)
    except Exception as error:
        raise ValueError('Database login/TLS failed ('+type(error).__name__+'). Verify the session host, database password and project CA; TLS was not weakened.') from None
    del admin_password
    step('preparing database role/schema')
    version=None
    try:
        with admin:
            ext=admin.execute("SELECT n.nspname FROM pg_extension e JOIN pg_namespace n ON e.extnamespace=n.oid WHERE e.extname='vector'").fetchone()
            if ext and ext[0]!='extensions':
                raise ValueError('pgvector already exists outside extensions. Ask the database administrator to review it; no extension was moved or reinstalled.')
            admin.execute("SET LOCAL lock_timeout='5s'")
            # Send only the SCRAM verifier in DDL, not the cleartext password.
            verifier=admin.pgconn.encrypt_password(runtime_password.encode(),ROLE.encode(),algorithm=b'scram-sha-256').decode()
            role=sql.Identifier(ROLE)
            if admin.execute('SELECT 1 FROM pg_roles WHERE rolname=%s',(ROLE,)).fetchone():
                # Dedicated ADK-only role: align its password with the stored secret.
                # On PostgreSQL 16+ a non-superuser may not even name SUPERUSER/
                # REPLICATION/BYPASSRLS in ALTER ROLE, so set only what is needed
                # and verify the privileged attributes are off instead.
                admin.execute(sql.SQL('ALTER ROLE {} WITH LOGIN NOINHERIT PASSWORD {}').format(role,sql.Literal(verifier)))
                unsafe=admin.execute('SELECT rolsuper OR rolcreaterole OR rolcreatedb OR rolreplication OR rolbypassrls FROM pg_roles WHERE rolname=%s',(ROLE,)).fetchone()[0]
                if unsafe:raise ValueError('Existing '+ROLE+' role has elevated attributes. Stop for a focused permission review; nothing was committed.')
            else:
                admin.execute(sql.SQL('CREATE ROLE {} LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD {}').format(role,sql.Literal(verifier)))
            admin.execute(sql.SQL('ALTER ROLE {} SET search_path = '+SEARCH_PATH).format(role))
            admin.execute('CREATE SCHEMA IF NOT EXISTS extensions')
            admin.execute('CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA extensions')
            # Supabase's postgres login cannot GRANT a role to itself or create a
            # schema AUTHORIZATION another role. Own the schema as the current
            # admin user and grant the runtime role exactly what it needs.
            admin.execute('CREATE SCHEMA IF NOT EXISTS dua_adk')
            admin.execute('REVOKE ALL ON SCHEMA dua_adk FROM PUBLIC,anon,authenticated')
            admin.execute(sql.SQL('GRANT USAGE, CREATE ON SCHEMA dua_adk TO {}').format(role))
            admin.execute(sql.SQL('GRANT ALL ON ALL TABLES IN SCHEMA dua_adk TO {}').format(role))
            admin.execute(sql.SQL('GRANT ALL ON ALL SEQUENCES IN SCHEMA dua_adk TO {}').format(role))
            admin.execute(sql.SQL('ALTER DEFAULT PRIVILEGES IN SCHEMA dua_adk GRANT ALL ON TABLES TO {}').format(role))
            admin.execute(sql.SQL('ALTER DEFAULT PRIVILEGES IN SCHEMA dua_adk GRANT ALL ON SEQUENCES TO {}').format(role))
            admin.execute(sql.SQL('GRANT USAGE ON SCHEMA extensions TO {}').format(role))
            # Store first, commit second: a Secret Manager error rolls back SQL.
            if save_url:version=save_secret(DB_SECRET,save_url)
    except Exception as error:
        if type(error) in (ValueError,RuntimeError):raise
        raise ValueError(database_failure(error,(runtime_password,))) from None
    print('Private ADK schema, dedicated role and pgvector prepared in the existing project.')
    return version


def initialize_tables(source):
    import agent_store,agent_documents
    agent_store.initialize();agent_documents.initialize()
    async def prepare():
        store=agent_store.service()
        try:await store.prepare_tables()
        finally:await store.close()
    asyncio.run(prepare())


def database_setup(source,account,reset_credential=False):
    import psycopg
    from psycopg import sql
    # Use verified Supabase session pooling for IPv4. Never guess the host from
    # the region: copy it from this project's Connect > Session pooler panel.
    step('reading existing Secret Manager entries')
    version=secret_versions(DB_SECRET)
    ca_version=secret_versions(CA_SECRET)
    system_ca=Path(ssl.get_default_verify_paths().cafile or '/etc/ssl/certs/ca-certificates.crt')
    ca_file=system_ca
    extra=None
    step('loading database CA certificate')
    if ca_version:
        # The secret holds only the Supabase CA (small); combine with system trust locally.
        extra=gcloud('secrets','versions','access',ca_version,'--secret',CA_SECRET)
    else:
        extra_ca=prompt('Supabase CA certificate file path, if required (Enter for system trust): ')
        if extra_ca:extra=read_ca(extra_ca)
    if extra:
        combined=source.parent/'database-ca.pem'
        combined.write_text(system_ca.read_text()+'\n'+extra);combined.chmod(0o600)
        ssl.create_default_context(cafile=str(combined))
        ca_file=combined
    step('checking stored database credential')
    reuse_password=None
    host=None
    if version:
        url=gcloud('secrets','versions','access',version,'--secret',DB_SECRET)
        parsed=urlsplit(url)
        if parsed.scheme!='postgresql' or parsed.hostname is None or parsed.username is None or not parsed.password or \
           parsed.username not in (ROLE,ROLE+'.'+SUPABASE_REF) or parsed.port not in (None,5432) or \
           parsed.path!='/postgres' or parsed.query or not (parsed.hostname=='db.'+SUPABASE_REF+'.supabase.co' or \
           re.fullmatch(HOST_RE,parsed.hostname)):
            if not reset_credential:
                raise ValueError('Existing database secret does not identify the expected dedicated ADK role. Rerun with: stage --reset-db-credential')
        else:host=parsed.hostname
        if reset_credential:
            print('Resetting the ADK database credential: a new password is generated and stored as a new secret version.')
            needs_admin=True
        elif runtime_ready(url,ca_file):
            print('Reusing the existing ADK database credential and schema; no password rotation.')
            needs_admin=False
        else:
            print('Stored ADK credential found, but the database role/schema is not ready yet. Repairing it with the same stored password (no rotation).')
            needs_admin=True
            reuse_password=unquote(parsed.password)
    else:
        needs_admin=True
    if needs_admin and not reuse_password and not host:
        host=prompt('Supabase Connect > Session pooler HOST (no URL/password): ')
        if not re.fullmatch(HOST_RE,host):
            raise ValueError('Copy the session pooler host from the project Connect panel; do not use the transaction port.')
    step('saving CA certificate secret')
    if extra and not ca_version:ca_version=save_secret(CA_SECRET,extra)
    if needs_admin:
        runtime_password=reuse_password or secrets.token_urlsafe(36)
        save_url=None
        if not reuse_password:
            url=save_url='postgresql://'+ROLE+'.'+SUPABASE_REF+':'+quote(runtime_password,safe='')+'@'+host+':5432/postgres'
        saved=admin_repair(psycopg,sql,host,runtime_password,ca_file,save_url)
        if saved:version=saved
        del runtime_password
    hidden=(unquote(urlsplit(url).password or ''),)
    step('setting database search_path')
    ensure_search_path(url,ca_file,hidden)
    step('granting runtime secret access')
    allow_runtime(DB_SECRET,account)
    if ca_version:allow_runtime(CA_SECRET,account)
    os.environ['PGSSLROOTCERT']=str(ca_file)
    os.environ['SSL_CERT_FILE']=str(ca_file)
    os.environ['ADK_SESSION_DB_URL']=url
    os.environ.pop('ADK_DB_LOCAL',None)
    sys.path.insert(0,str(source/'google-cloud-backend'))
    import agent_store
    step('checking app database schema')
    verify_app_schema(agent_store,hidden)
    def prepare_storage(label):
        step('initializing ADK tables'+label)
        initialize_tables(source)
        status=agent_store.status()
        if not status.get('ready') or not status.get('durableAcrossInstances'):
            raise ValueError('Storage readiness failed ('+sanitize(status.get('reason','unknown'),hidden)+'); no candidate was deployed.')
        engine=agent_store.engine()
        try:
            from sqlalchemy import text
            with engine.connect() as con:
                # The runtime must not own or have direct grants to unrelated public
                # app tables. PUBLIC grants cannot be fixed by NOINHERIT alone.
                unrelated=con.execute(text("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname IN ('public','auth','storage') AND c.relkind IN ('r','p','v','m','f') AND has_table_privilege(current_user,c.oid,'SELECT,INSERT,UPDATE,DELETE')")).scalar_one()
                if unrelated:raise ValueError('Dedicated role can access unrelated app tables. Stop for a focused permission review; no public grants were changed.')
        finally:engine.dispose()
        step('storage smoke test'+label)
        verify_storage()
    reset_hint='. To re-enter the database credential, rerun: bash ~/dua-adk-cloudshell.sh stage --reset-db-credential'
    try:prepare_storage('')
    except Exception as error:
        if type(error) in (ValueError,RuntimeError):raise
        state,_=db_error(error,hidden)
        failed=STEP
        print('SETUP PROBLEM during '+failed+': '+database_failure(error,hidden),file=sys.stderr)
        if state not in RETRYABLE_SQLSTATES or not host or \
           not ask_yes('Repair the database role/grants with the Supabase admin password and retry once? [y/N]: '):
            raise ValueError(database_failure(error,hidden)+reset_hint) from None
        admin_repair(psycopg,sql,host,hidden[0],ca_file)
        step('setting database search_path')
        ensure_search_path(url,ca_file,hidden)
        try:prepare_storage(' (retry)')
        except Exception as again:
            if type(again) in (ValueError,RuntimeError):raise
            raise ValueError(database_failure(again,hidden)+reset_hint) from None
    os.environ.pop('ADK_SESSION_DB_URL',None)
    return str(version),str(ca_version) if ca_version else None


def stage(source,service,reset_credential=False):
    spec=service['spec']['template']['spec']
    container=spec['containers'][0]
    image=container['image'].split('@',1)[0]
    # Reuse the existing Artifact Registry repository; do not provision a paid
    # registry/project/branch or enable services as a side effect.
    match=re.fullmatch(r'([a-z0-9-]+-docker\.pkg\.dev/'+re.escape(PROJECT)+r'/[^/]+)/[^/:]+(?::[^/]+)?',image)
    if not match:raise ValueError('Existing image is not in the expected Artifact Registry project. Review an image target before deploying.')
    step('checking pinned source')
    sha=command(['git','-C',str(source),'rev-parse','HEAD'])
    if command(['git','-C',str(source),'status','--porcelain']):raise ValueError('Source checkout is not clean.')
    account=spec.get('serviceAccountName')
    if not account:
        number=gcloud('projects','describe',PROJECT,'--format=value(projectNumber)')
        account=number+'-compute@developer.gserviceaccount.com'
    db_version,ca_version=database_setup(source,account,reset_credential)
    target=match[1]+'/dua-ai-engine-adk:'+sha
    step('building image')
    print('Building the merged source; Cloud Build/registry usage follows your existing project billing.')
    command(['gcloud','builds','submit',str(source/'google-cloud-backend'),'--project',PROJECT,
        '--config',str(source/'google-cloud-backend/cloudbuild-adk.yaml'),
        '--substitutions','_IMAGE='+target+',_SOURCE_SHA='+sha,'--quiet'],visible=True)
    digest=gcloud('artifacts','docker','images','describe',target,'--format=value(image_summary.digest)')
    if not re.fullmatch(r'sha256:[a-f0-9]{64}',digest):raise ValueError('Could not verify the pushed image digest.')
    immutable=target.rsplit(':',1)[0]+'@'+digest
    before=normalize_traffic(service)
    if not before or sum(n for _,n in before)!=100:raise ValueError('Cannot snapshot the serving traffic safely.')
    snapshot=source.parent/'rollback.json'
    snapshot.write_text(json.dumps({'service':SERVICE,'project':PROJECT,'region':REGION,'traffic':service['status']['traffic']},indent=2))
    env_updates=['AGENT_ENGINE=adk','ADK_PRODUCTION=1','DUA_BUILD_SHA='+sha]
    bindings=['ADK_SESSION_DB_URL='+DB_SECRET+':'+db_version]
    if ca_version:
        # The CA secret holds only the Supabase CA (system bundles exceed the
        # 64 KiB secret limit). Mount it under its OpenSSL hash name and add its
        # directory to system trust. SSL_CERT_FILE is not set: httpx would then
        # trust only this CA and break HTTPS to model/API providers.
        ca_text=gcloud('secrets','versions','access',ca_version,'--secret',CA_SECRET)
        ca_hash=command(['openssl','x509','-noout','-subject_hash'],payload=ca_text)
        if not re.fullmatch(r'[0-9a-f]{8}',ca_hash):raise ValueError('Could not read the database CA certificate subject hash.')
        mounted='/var/run/dua-adk/'+ca_hash+'.0'
        bindings.append(mounted+'='+CA_SECRET+':'+ca_version)
        env_updates+=['PGSSLROOTCERT='+mounted,'SSL_CERT_DIR=/etc/ssl/certs:/var/run/dua-adk']
    else:env_updates+=['PGSSLROOTCERT=/etc/ssl/certs/ca-certificates.crt']
    if service_info().get('metadata',{}).get('generation')!=service.get('metadata',{}).get('generation'):
        raise RuntimeError('Service configuration changed during setup. Inspect it and rerun; no candidate was deployed.')
    step('deploying 0% candidate')
    gcloud('run','deploy',SERVICE,'--region',REGION,'--image',immutable,'--timeout','900',
        '--no-traffic','--tag','adk-candidate','--update-env-vars',','.join(env_updates),
        '--update-secrets',','.join(bindings))
    after=service_info()
    if normalize_traffic(after)!=before:raise RuntimeError('Serving traffic differs from the snapshot. Stop and inspect rollback.json; no automatic promotion was requested.')
    candidate=next((row for row in after['status'].get('traffic',[]) if row.get('tag')=='adk-candidate'),None)
    if not candidate or not candidate.get('url') or not candidate.get('revisionName'):
        raise RuntimeError('Candidate URL/revision could not be verified; serving traffic was preserved.')
    revision=metadata('run','revisions','describe',candidate['revisionName'],'--region',REGION)
    ready=any(c.get('type')=='Ready' and c.get('status')=='True' for c in revision['status'].get('conditions',[]))
    if not ready:raise RuntimeError('Candidate is not Ready. Do not promote.')
    candidate_url=candidate['url']
    parsed=urlsplit(candidate_url)
    if parsed.scheme!='https' or not parsed.hostname or not parsed.hostname.endswith('.run.app'):
        raise ValueError('Unexpected candidate URL; no HTTP request was sent.')
    http_health='not_verified'
    try:
        with urlopen(candidate_url+'/',timeout=20) as response:
            health=json.load(response)
            if health.get('engine')!='google-cloud' or not health.get('agent'):
                raise ValueError('Candidate health/capabilities are unexpected. Do not promote.')
            http_health=200
    except Exception as error:
        # IAM-protected service health may require an identity token. Ready
        # alone is not full owner-flow verification; retain this distinction.
        if isinstance(error,ValueError):raise
        http_health='requires_manual_verification'
    result={'project':PROJECT,'service':SERVICE,'source':sha,'image':immutable,'candidate':candidate['url'],
        'revision':candidate['revisionName'],'candidateReady':True,'httpHealth':http_health,'storageInitialized':True,'storageSmokePassed':True,'servingTrafficUnchanged':True,
        'liveModelVerified':False,'embeddingsEnabledByThisScript':False,'rollback':str(snapshot)}
    (source.parent/'stage-result.json').write_text(json.dumps(result,indent=2))
    print(json.dumps(result,indent=2))
    print('Rollback command: ADK_SNAPSHOT='+str(snapshot)+' bash '+str(source/'google-cloud-backend/deploy-adk.sh')+' rollback')
    print('No traffic promotion or paid model/embedding call was performed. Share only this result, never credentials.')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action',choices=('plan','stage'))
    parser.add_argument('--source',type=Path,required=True)
    parser.add_argument('--reset-db-credential',action='store_true',help='Generate a new ADK database password (needs the Supabase admin password).')
    args=parser.parse_args()
    try:
        step('reading Cloud Run service')
        service=service_info()
        if args.action=='plan':
            names=sorted(e['name'] for e in service['spec']['template']['spec']['containers'][0].get('env',[]))
            print(json.dumps({'project':PROJECT,'service':SERVICE,'region':REGION,'supabaseProject':SUPABASE_REF,
                'servingTraffic':normalize_traffic(service),'configuredVariableNames':names,
                'changesForStage':['Create/reuse dedicated private ADK DB role/schema and pgvector','Initialize official SDK tables',
                'Store/reuse version-pinned DB credential and optional CA in Secret Manager (adds a version if the secret exists)','Grant only the existing runtime service account access to those secrets',
                'Build merged source in the existing registry','Create a tagged candidate with serving traffic unchanged'],
                'noAutomaticPromotion':True,'noAutomaticHostedModelCalls':True},indent=2))
        else:stage(args.source,service,args.reset_db_credential)
        return 0
    except Exception as error:
        # Our diagnostics contain metadata; third-party errors can include DSNs.
        if type(error) in (ValueError,RuntimeError):print('SETUP STOPPED during '+STEP+': '+str(error),file=sys.stderr)
        else:
            # Third-party messages can include DSNs; print only type, step and a fixed hint.
            hint={'OSError':'a local file or terminal could not be read','UnsupportedOperation':'terminal input could not be read',
                  'FileNotFoundError':'a file path was not found','PermissionError':'a local file was not readable'}.get(type(error).__name__,'check database/Cloud Console access')
            print('SETUP STOPPED during '+STEP+' ('+type(error).__name__+': '+hint+'). No exception payload printed.',file=sys.stderr)
        return 1


if __name__=='__main__':raise SystemExit(main())
DUA_ADK_PYTHON
if [ "$adk_action" = stage ]; then
  adk_python="$(command -v python3.12 || command -v python3)"
  "$adk_python" -c 'import sys; assert sys.version_info[:2]==(3,12), "Use Python 3.12 for the tested lock file"'
  "$adk_python" -m venv "$adk_work/venv"
  "$adk_work/venv/bin/python" -m pip install --disable-pip-version-check --no-cache-dir -q -r "$adk_source/google-cloud-backend/requirements-adapters-lock.txt"
  "$adk_work/venv/bin/python" -m pip check
  "$adk_work/venv/bin/python" "$adk_work/cloudshell_setup.py" stage --source "$adk_source" "${adk_extra[@]}"
else
  python3 "$adk_work/cloudshell_setup.py" plan --source "$adk_source"
fi
