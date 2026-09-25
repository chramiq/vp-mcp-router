# 0010: Export formats, runtime-jar guard, region cut

## Context

Homework needs PDF; agents want zoomable SVG. Separately, the new
schema guard flagged a mismatch on a correct install, which exposed
that VP loads API classes from `lib/lib02.jar`, not `openapi.jar`.

## Decision

- `vp_export_image` gains `format: png|svg|pdf` (default png) and
  `out_dir` (default temp; files persist and paths are reported).
  PNG returns an image block; SVG returns raw markup as text (plus
  file); PDF returns path + size as text. Bad format/region fail in
  validation, before VP is touched (unit-tested).
- `region` was cut the same day it shipped: the `Rectangle` overload
  renders the full diagram, not a crop. Zoom stays possible
  client-side via SVG viewBox. No dead params.
- Schema packs generate from the runtime jar (`lib02.jar`), and the
  guard reports `runtime_jar` alongside the sha. Pack diff on the
  switch was meta-only (identical API surface).

## Consequences

- SVG exports vector XML, PDF writes `%PDF` output honoring
  `out_dir`, and the guard reports `match:true` naming `lib02.jar`.
- INSTALL.md build docs still point at `openapi.jar` for compilation;
  correct, since the compile surface is identical.
