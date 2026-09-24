# 0021: create_raw, the factory escape hatch

## Context

The write vocabulary was curated (ADR-0014 families) while the factory
offers 1454 create methods. Phase 6 of the compatibility roadmap: let
the model call anything, honestly.

## Probe findings

- 1453 of 1454 `IModelElementFactory` create methods are no-arg (only
  the generic `create(String)` takes one) — a reflective no-arg
  invocation covers the universe.
- Placement is diagram-kind-sensitive: an ArchiMate business process
  places fine on a UseCase diagram, a comment refuses placement there.
- A few model kinds (comments among them) are **transient**: created
  in-session, absent from every project array (`getModelElementById`,
  `toAllLevelModelElementArray`, parent scans), and never persisted to
  the `.vpp` — they vanish on reload.
- A hallucinated method name fails with an explicit error and mutates
  nothing (verified live with a made-up `createBPMNProcess`).

## Decision

- New op `create_raw {id, factory_method, diagram?, name?, x?, y?,
  width?, height?}`. `factory_method` must start with `create` — the
  hatch is for creation, not arbitrary method invocation.
- `diagram` optional: without it the model is an orphan; with it,
  placement is attempted and a refusal is reported as a note, never
  hidden. `vp_id` is the model id; `view_id` is added when placed.
- The plan entry carries an explicit "unverified family, VP may veto
  or misplace" warning; notes mention that some kinds are transient
  and never saved or listed.
- Unknown methods fail loudly at apply with a pointer to the
  `vp://schemas` factory-creates resource.
- `PlanValidator.resolveModel` now uses `ModelLookup.byId`
  (ADR-0019), so `delete_model` also resolves members and other
  parent-scanned models.

## Consequences

- The theoretical write surface expands from ~30 curated types to
  1453 factory methods, with a quality/verification tradeoff the
  agent can see in the plan.
- Live-verified: unknown-method error, placed ArchiMate element on a
  UseCase diagram (extractable), orphan Requirement (readable),
  transient comment (vanished on reload), cleanup deletions.
- 5 new validator contracts in `PlanValidatorTest`.
