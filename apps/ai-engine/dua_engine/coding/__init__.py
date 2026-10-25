"""Coding implementation modules, grouped without changing their public APIs.

New callers can use ``from dua_engine.coding import coding_agent`` (or another
module). The former top-level filenames alias these same module objects so
existing imports, private helpers, shared state, monkeypatches and reloads keep
working. Internal imports still use those compatibility names in this first
slice. Keep this initializer lazy; importing the package alone starts no service.
"""
