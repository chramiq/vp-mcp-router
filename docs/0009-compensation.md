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
- A mid-batch failure leaves the project as the plan promised:
  already-applied creations appear in `applied[]`, are undone via
  `compensated[] {id, undone}` entries, and compensated elements are
  gone from the project afterwards.

## Consequences

- `delete_diagram` / `delete_element` double as user ops and as the
  vocabulary compensation is described in.
- Writes stay allowed and first-class; safety is preview + confirm +
  audit + compensate, and the project is still never auto-saved.
