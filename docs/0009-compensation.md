# 0009: Saga compensation for write batches

## Context

VP's API has no transactions, verified by surface review
(`IDiagramUIModel.delete()`, `IModelElement.delete()` and
`removeDiagramElement()` exist; begin/commit/abort do not). A batch
that dies halfway must still leave the project sane.

## Decision

- Every creation registers a compensator at apply time; on first
  failure the applier runs them in reverse order (saga). Deletions
  are final and reported, never compensated.
- Compensation is best-effort: each step is individually reported in
  `compensated[] {id, undone, message?}` and a failed compensation
  never masks the original error. The audit log is the recovery
  record for anything left standing.
- Preview shows each op's `undo` string so the operator (and agent)
  sees reversibility before confirming.
- Verified live with HotSwap fault injection (var/probes/pd/): a
  mid-batch throw produced `applied:[d2]`,
  `compensated:[{d2, undone:true}]`, the temp diagram gone from the
  project afterwards. Injection flushed by restart; installed jar
  untouched.

## Consequences

- `delete_diagram` / `delete_element` double as user ops and as the
  vocabulary compensation is described in.
- Writes stay allowed and first-class; safety is preview + confirm +
  audit + compensate, and the project is still never auto-saved.
