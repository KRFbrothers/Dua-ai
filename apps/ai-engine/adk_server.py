"""Validated container entry point; shell expansion is not needed for PORT."""
import os
import sys


def main():
    import dua_engine.agent.agent_models as agent_models,dua_engine.agent.agent_store as agent_store,dua_engine.agent.agent_toolkit as agent_toolkit
    agent_models.check_adapters()
    agent_toolkit.configured()
    port=int(os.environ.get('PORT','8080'))
    if not 1<=port<=65535:raise ValueError('Invalid PORT')
    if (os.environ.get('K_SERVICE') or os.environ.get('ADK_PRODUCTION')=='1') and agent_store.database_url():
        from sqlalchemy.engine import make_url
        if make_url(agent_store.database_url()).get_backend_name()!='postgresql':
            raise ValueError('Production persistence requires external PostgreSQL, not temporary SQLite storage')
        if not agent_store.status()['ready']:
            raise ValueError('ADK storage is not initialized; run the migration command before rollout')
    os.execvp('gunicorn',['gunicorn','--bind',f':{port}','--workers','1','--threads','8','--timeout','0','wsgi:app'])


if __name__=='__main__':
    try:main()
    except Exception as error:
        print('ADK startup failed ('+type(error).__name__+'); check server configuration.',file=sys.stderr)
        raise SystemExit(1)
