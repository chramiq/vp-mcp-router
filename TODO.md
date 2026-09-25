# TODO

- Fill color FIXED 2026-09-25: `setColor1(color, true)` + `applySetting()`
  (the boolean gates renderer update; `false` stages silently).
  Live-verified red Class rendering end to end. The `background`
  channel's visible effect is still unknown (green set, no clear
  change) — `fill_color` is the documented channel for shape fill.
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
