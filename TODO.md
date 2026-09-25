# TODO

- `style_element` fill/background doesn't render on Class shapes (live
  2026-09-25): `fill.setColor1`/`setBackground` stick in the view model
  (reads report them) but `exportDiagramAsImage` still paints theme
  blue; line/font styling renders fine. Probe-first: capture what the
  `boolean` on `setColor1(color, ?)` means, whether `color2`/gradient
  or a revalidate call is needed. Don't guess — earlier `false` was
  verified only for the line model.
- Member compartments don't repaint until the view is touched (live
  2026-09-25): attributes/operations added in the same batch as the
  shape render only after a `move_element` nudge. Find the proper
  invalidation (revalidate/repaint on the view or diagram) and apply it
  in `BatchApplier` after member/style ops; verify via export bytes.
- `connect.points` needs full paths: intermediate-only waypoints are
  straightened by VP; elbows stick when the points include the
  endpoints (live-verified). Document in the `connect` description and
  skill routing guidance.
