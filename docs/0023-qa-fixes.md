# 0023: QA-cycle findings (member fields, save guard, doc-only decisions)

## Context

A live QA pass over all 10 tools (empty `untitled` project) found one
real bug, one release blocker, and four minor inconsistencies. The bug
and blocker are fixed in code; the rest are documented, not rebuilt.

## Fixed in code

- `add_member` dropped Operation `return_type` (and all other kind
  fields) on create: apply shared the update field writers post-attach,
  the validator checks the full field set up front, and inapplicable
  fields get a `skipped_fields` note instead of vanishing. Live-verified:
  `return_type: int` reads back after create.
- `vp_save_project` blocked on a native Save-As dialog for never-saved
  projects (client timeout): it now checks `getProjectFile()` first and
  refuses with "save once in VP, then retry". Unit-pinned for
  null/missing/existing file.
- `add_member` parent error names the bad id and says "view id";
  description states parent is a view id or plan ref. Accepting model
  ids stays a TODO: multi-view ambiguity needs its own decision.

## Deliberately doc-only

- Export result fields stay per-format (png `{image_mime,
  image_data}`, svg `{..., document_text}`, pdf `{..., bytes}`):
  unifying renames would break existing callers for cosmetic gain.
  The tool description now spells the per-format fields out.
- Delete semantics stay as-is (`delete_element` removes view and model,
  `delete_diagram` orphans models, `delete_model` removes both) and are
  now stated in the description. A view-only `delete_view` is deferred
  until a caller needs it (YAGNI).
- Move without dimensions keeps current bounds (code falls back to the
  live bounds). One QA session saw a collapse to create defaults, but
  isolated probes (move alone, rename+move, style+move) all preserve —
  attributed to VP-side auto-fit on content change, not an MCP bug.
  No code change; revisit only with a reproducer.

## Consequences

- QA verdict flips to shippable once this ADR lands: no open
  behavior bugs, remaining quirks are explicit in tool descriptions.
