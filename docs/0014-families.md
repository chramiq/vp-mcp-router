# 0014: Homework families (curated-generic writes)

## Context

ADR-0013 probed generic creation via HotSwap. This promotes the
probe findings into product: five new diagram families, member
writes, connector waypoints.

## Decision

- `create_diagram` accepts Activity, State, ER, Interaction,
  Deployment diagrams (the manager call was always type-agnostic).
- `create_element` uses a creation strategy, not a type switch:
  reflective `factory.createX()` first, view-first fallback with a
  shape map (`LifeLine` -> `InteractionLifeLine`) when the manager
  returns null. View-first auto-models get named; bare orphans are
  dropped best-effort.
- New `add_member {parent, member_type, name, type?}` for
  Attribute/Operation/DBColumn via interface-derived adders
  (`addAttribute`, `addDBColumn`…); mirrored removers compensate.
  Members are rejected as top-level elements with a pointer to
  `add_member` — columns/attributes are not diagram-placeable
  (both creation paths return null).
- `connect` takes optional `points[]`; Message/Transition2 added.
  Message models are not IRelationships, so model-end wiring is
  conditional — views carry the endpoints.

## Probe-verified behavior (all rendered)

Activity/decision/final/initial, nodes/components, states +
transitions, tables + columns, lifelines + messages, attributes
with `type: int` round-tripping through reads. Saga compensated
6 ops on a mid-batch column-type failure.

## Known VP vetoes (not our bugs)

- The name "Order" is silently remapped ("Class4"); DB table names
  don't stick ("Vehicle" -> "Entity2"). `setName` has no effect and
  reports nothing — the applied entry carries the true name, so the
  agent sees the discrepancy and adapts.
- DB columns have no string `setType`; explicit column types fail
  loudly at apply. Columns still get server defaults.
- Captions on initial/decision/final/state shapes don't show names
  (same family as the actor bug — same fix pattern, open item).
