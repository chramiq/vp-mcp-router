# TODO

## Done

- Probes P0–P3, phases A–D.

## Next: reads++

- `vp_export_image` first (diagram PNG/SVG for the agent's visual
  channel; documented `exportDiagramAsImage`, read-only).
- Connector coverage in tests (current fixture diagram has no edges).
- Schema-vs-runtime version guard (compare `meta.json` sha at startup,
  warn on mismatch).

## Later: writes (confirm + dry-run posture)

- `preview_batch` (dry-run diff artifact) / `apply_batch` (explicit
  confirm token), allowlisted ops only; no auto-save, no delete,
  no teamwork in v1 of writes.
