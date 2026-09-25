# TODO

- Fill color does NOT render (live 2026-09-25, still open): staged via
  `setColor1` + `applySetting()` (shipped in `setFillColor`), reads
  report it, pixels stay theme blue — on Class AND Actor shapes,
  same-batch and later-batch styles alike. Line/font styling renders,
  so the export path honors overrides in general. Next probe (needs
  rebuild + restart, user approval required — restarts drop the
  academic license): `setColor1(color, true)` — the boolean may gate
  renderer update and was only ever verified as `false` for the line
  model. If that fails, suspect theme-locked fills or a different
  renderer channel entirely.
- Member compartments don't repaint until the view is touched (live
  2026-09-25): attributes/operations added in the creating batch
  sometimes render only after a `move_element` nudge; reads are always
  correct. Same-value `setBounds` does NOT invalidate (proven live),
  +1px does. No auto-jiggle shipped: find the real invalidation call
  first.
- `connect.points` needs full paths: intermediate-only waypoints are
  straightened by VP; elbows stick when the points include the
  endpoints (live-verified). Document in the `connect` description and
  skill routing guidance.
