"""Compatibility alias for ``dua_engine.automation.automation_companion``."""
from importlib import import_module as _import_module
import sys as _sys

if __name__ == "__main__":
    _import_module("dua_engine.automation.automation_companion").main()
else:
    _sys.modules[__name__] = _import_module("dua_engine.automation.automation_companion")
