# 0008: Writes as preview/apply split with plan refs

## Context

Writes mutate the user's open project, so they need the strongest
safety posture in the repo (AGENTS.md: confirm + dry-run, per-action
approval). A single "do it" tool cannot offer a dry run.

## Decision

- Two tools. `vp_preview_batch {ops[]}` validates and resolves only;
  `apply_batch` (later) executes. Preview output carries
  `"mutated": false` as a machine-checkable promise.
- Plan refs (`{"ref": "<op id>"}`) chain ops: connect can target an
  element created earlier in the same batch. Refs resolve against a
  plan symbol table; unknown refs are errors, never nulls.
- Validation lives in `vpmcp.write.PlanValidator`, which takes only
  `IProject` reads (diagram/element existence). No factory, no
  DiagramManager, no save path is reachable from it — the read-only
  shape is structural, not a flag.
- v1 allowlist (Core UML): diagrams ClassDiagram/UseCaseDiagram;
  elements Actor/UseCase/Class; rels Association/Include/Extend/
  Generalization/Dependency. Expansion is a data change, not redesign.

## Consequences

- 10 validator unit tests; mutation-checked (duplicate-id removal
  fails exactly `duplicateIdsRejected`).
- `apply_batch` must re-validate the resolved plan immediately before
  executing (TOCTOU between preview and apply), require an explicit
  confirm token, and audit-log every mutation.
