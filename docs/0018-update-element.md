# 0018: update_element with old-value snapshots

## Context

Writes could create, move and delete, but never change a property: reads
were lossless while writes could not touch name, documentation or
stereotypes. Deletions were the only non-compensable op class; property
mutation would have joined them unless the saga learned old values.

## Probe findings

`IModelElement` offers `setName`, `setDocumentation`,
`addStereotype(String)`, `removeStereotype(String)`; stereotypes are
readable as names via `toStereotypeModelArray`. The silent `setName`
vetoes documented in ADR-0015 apply to updates too.

## Decision

- New op `update_element {id, model, name?, documentation?, stereotypes?}`
  in `vp_preview_batch`/`vp_apply_batch`; at least one field required.
- `model` resolves like `delete_model`: an existing model id, or a plan
  ref to a created element/member/relationship.
- `stereotypes` replaces all: current names not kept are removed, missing
  kept ones added. Empty `documentation` clears it.
- Compensation snapshots name, documentation and stereotype names before
  the first mutation and restores them on batch failure — updates are
  compensable, unlike deletions.
- VP name vetoes surface as `name_warning` on the applied entry
  (ADR-0015 pattern); the entry always carries the effective values.

## Consequences

- The saga now has two restorable op classes (creations, updates) and
  two final ones (deletions).
- 7 new validator contracts in `PlanValidatorTest` (field checks, plan
  refs, undo entries).
- Live-verified on the scratch project: update round-tripped through
  `vp_get_model`, baseline restored by a second update.
