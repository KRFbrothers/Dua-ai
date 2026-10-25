"""Safe integer settings from environment variables (no imports from other engine modules)."""
import os
import sys


def env_int(name, default):
    """int(os.environ[name]); blank or missing gives default, an invalid value warns once and gives default."""
    raw = os.environ.get(name)
    if raw is None or not str(raw).strip():
        return default
    try:
        return int(raw)
    except ValueError:
        print(f"warning: {name} is not a whole number; using {default}", file=sys.stderr)
        return default
