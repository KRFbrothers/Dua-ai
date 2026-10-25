"""Compatibility alias for ``dua_engine.coding.coding_artifacts``."""
from importlib import import_module as _import_module
import sys as _sys

_sys.modules[__name__] = _import_module("dua_engine.coding.coding_artifacts")
