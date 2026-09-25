# 0019: update_member and member-aware lookups

## Context

Members could be added but never edited: no attribute type changes, no
operation signature changes. Reading members exposed a deeper gap:
VP's `getModelElementById` and `toAllLevelModelElementArray` do not
cover child models at all — attributes and operations are only
reachable through their parent's `getChildById`. Every id-based member
lookup (list, read, update) was impossible before this ADR.

## API surface

- `IAttribute`: `setType(String)`, `setVisibility`,
  `setMultiplicity(String)`, `setInitialValue(String)`.
- `IOperation`: `setReturnType(String)`, `setVisibility`,
  parameter CRUD (`createParameter`, `addParameter`, `removeParameter`).
- `IParameter`: `setName`, `setType(String)`, `setDirection`.
- `IDBColumn`: `setTypeName(String)`, `setLength(int)`,
  `setNullable(boolean)` (ADR-0015 quirk family).
- `IModelElement.getChildById(String)` resolves members through their
  parent; the project id index does not.

## Decision

- New op `update_member {id, member, name?, type?, visibility?,
  multiplicity?, initial_value?, return_type?, parameters?, length?,
  nullable?}`. Fields apply by member kind (Attribute: type/visibility/
  multiplicity/initial_value; Operation: return_type/visibility/
  parameters as full replacement, index-aligned in place where
  possible; DBColumn: type/length/nullable). Kind-inapplicable fields
  are skipped with a `skipped_fields` note, not errors.
- `ModelLookup.byId(project, id)`: top-level fast path, then a parent
  scan via `getChildById`. Used by `vp_get_model`,
  `vp_list_models` (which now also indexes members beside their
  parents, with `parent_id`), `PlanValidator.resolveMember` and
  `BatchApplier.requireModel`.
- Compensation snapshots every changed field before mutation
  (ADR-0018 pattern); parameters restore by index alignment.

## Consequences

- Members are first-class everywhere: listed, readable by id, and
  updatable. Attribute rename/retype/visibility/multiplicity/initial
  value round-trip; operation return type and full parameter
  replacement round-trip; the skipped-fields note fires on kind
  mismatches.
- 7 new validator contracts plus member-resolution tests in the
  model-tool tests.
