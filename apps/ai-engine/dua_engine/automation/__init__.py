"""Device automation client, companion, drivers and interactive terminals.

New imports use ``dua_engine.automation``. Root filenames and the earlier
``dua_engine.agent.agent_automation`` path alias the same implementation modules
so shared state, private helpers, monkeypatches and reloads remain compatible.
Internal imports retain those compatibility names in this slice.

Keep this initializer lazy: importing the engine's agent_automation client must
not import the companion/driver/terminal modules or start host services. Optional
host libraries are still loaded only when the existing implementations need them.
The root automation_companion.py script still dispatches main() for operators.
"""
