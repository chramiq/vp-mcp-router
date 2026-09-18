# 0013: Duplicate diagram + dynamic-mode probe results

## Context

The agent workflow wants scratch copies (duplicate, trial, observe,
delete) and generic creation across all diagram families instead of
hand-curated tools per type. Two questions: can we duplicate, and does
generic create actually work?

## Decision

- New `duplicate_diagram {id, diagram, name}` batch op (validator +
  applier, previewable, compensatable via `copy.delete()`).
- Shallow copy by design: fresh views, **shared models**. Additive
  trials on the copy are safe; edits to shared model properties leak
  to the source; deleting the copy never deletes models. The undo
  string says so verbatim. Live-verified: 8-node/3-edge copy exact,
  trial went 9/4 while source stayed 8/3, full cleanup.
- Copy fidelity: bounds, connector endpoints (multi-pass for
  connector-to-connector chains), waypoints, member pins, captions.

## Probe results (HotSwap, NOT committed — see var/probes/dyn/)

Generic creation is real but NOT uniform. Three different behaviors:

1. `createDiagram(type)` is fully generic — InteractionDiagram,
   StateDiagram, ERDiagram all created through the existing path.
2. Model-first `createDiagramElement(diagram, factory.createX())`
   works for State2, DBTable (and Class/Actor/UseCase) but returns
   **null** for LifeLine. No exception — silent null.
3. View-first `diagram.createDiagramElement(shape)` works for
   lifelines, but the shape string is diagram-specific
   (`InteractionLifeLine`, not `LifeLine`).

Further per-type quirks: Message connectors need explicit waypoints
(null points lands at y=-50, offscreen); with points the arrow
renders correctly. Message models are not IRelationships — endpoint
wiring must be conditional. State captions don't show names (same
class of bug as actor captions, ADR open item). Reads and image
export handled every new type with zero changes.

## Consequences

- The product shape is therefore curated-generic, not reflective:
  a per-family creation-strategy table (model-first vs view-first +
  shape name + waypoint policy), validated against the schema pack,
  still behind preview/confirm/saga. A blind `"create"+type`
  reflection would NPE on lifelines and misplace messages.
- Next: promote the probe to `create_element` strategy fallback
  (model-first, then view-first with pack-derived shape), plus
  `points` on connect. One phase per family, live-rendered.
- Save is asynchronous: `saveProject()` returns before the write
  lands (~5-20s observed). The restart rule now polls mtime instead
  of statting once.
