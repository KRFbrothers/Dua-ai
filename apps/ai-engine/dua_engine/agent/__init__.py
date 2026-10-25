"""Agent orchestration and satellites: ADK, tool loop, context, policy, evaluation,
resilience, verification, tools, models, observability, storage, documents,
safety and private notes.

Use ``from dua_engine.agent import agent_context`` (or another agent module) for
new imports. Legacy root modules alias the same implementation objects, preserving
private helpers, shared state, monkeypatches and reloads. Internal imports retain
those compatibility names in this slice. Keep this initializer lazy.

The orchestration hubs ``agent_adk`` and ``agent_tools`` live here too, with legacy
aliases at the engine root. SDK imports remain lazy. The coding package is unchanged.
Device automation is owned by ``dua_engine.automation``; the earlier
``dua_engine.agent.agent_automation`` path remains a compatibility alias.
"""
