# Kill-switch contract

This is the authoritative definition of kill-switch names, defaults, ownership
and response messages. The Python engine and Node API implement this contract
independently. Their tests consume the definition below; production does not load
this file or call another service to resolve switches. Runtime deployments remain
self-contained and keep their current behavior.

The engine owns enforcement of engine work, including direct clients and old
APKs. The API owns its routes and publishes `/api/config` capabilities. Each
service reads its own Cloud Run environment. Setting a shared flag on one service
does not update or configure the other. There is no cross-service flag polling.

```json
{
  "version": 1,
  "truthy": ["1", "true", "yes", "on"],
  "maintenanceMessageMaxLength": 300,
  "flags": {
    "DUA_DISABLE_AGENT": {
      "kind": "switch", "default": false,
      "implementedBy": ["api", "engine"], "apiField": "agent"
    },
    "DUA_DISABLE_IMAGINE": {
      "kind": "switch", "default": false,
      "implementedBy": ["api", "engine"], "apiField": "imagine"
    },
    "DUA_DISABLE_VOICE": {
      "kind": "switch", "default": false,
      "implementedBy": ["api"], "apiField": "voice"
    },
    "DUA_MAINTENANCE": {
      "kind": "switch", "default": false,
      "implementedBy": ["api", "engine"], "apiField": "maintenance.enabled"
    },
    "DUA_MAINTENANCE_MESSAGE": {
      "kind": "message", "default": "",
      "implementedBy": ["api", "engine"]
    }
  },
  "messages": {
    "maintenance": "Dua is taking a short break for maintenance. Please check back soon.",
    "agent": "Agent mode is paused right now. Normal chat still works; try Agent again later.",
    "imagine": "Imagine is paused right now. Please try again later.",
    "voice": "AI voices and live voice are paused right now. Device voice still works."
  }
}
```

Shared: Agent, Imagine, maintenance and the optional maintenance message.
API-only: Voice. Engine-only: none. Both implementation headers document the
complete set and explicitly identify Voice as API-only; this does not add a Voice
switch to the engine.

Environment values are strings. Switches accept the listed truthy values after
native whitespace trimming and case normalization; missing, empty and other
values leave a switch off. The message is not a boolean switch and has no effect
while maintenance is off. It replaces runs of ASCII control characters
U+0000–U+001F/U+007F with a space, trims whitespace and truncates to the existing
limit, falling back to the maintenance message when empty.

Preserve the existing language details: Python's limit counts Unicode code
points; JavaScript's counts UTF-16 code units. Trimming also follows each language's
native rules. The API `maintenanceMessage` helper always returns a message; the
engine `maintenance_message` returns `None` when maintenance is off. Call sites
retain their existing checks and response shapes.

Maintenance takes precedence where the current route guards apply. It pauses
selected AI work, not every route: account, configuration and stored-data access
remain available as described in the [operations guide](../../docs/KILL_SWITCHES.md).
Agent leaves normal chat available; Imagine pauses image work (and API video);
Voice pauses API AI/live voice while device voice remains available. Route
coverage and exemptions are implementation-specific and are not expanded by
this contract. Names and shared messages must stay aligned; keep the existing
endpoint behavior unless a separate behavior change is requested.

When changing this contract, update the affected implementation and its tests
in the same PR. Do not enable a switch by default or change deployed values as
part of a documentation update. Operator commands live only in the operations
guide; this contract requires no deployment or environment changes.
