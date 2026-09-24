# 0020: style_element (view colours, fill, line, font)

## Context

Reads reported the full visual formatting of every element
(`visual_properties`: fill, line, font, caption) while writes could
not touch any of it. Phase 5 of the compatibility roadmap.

## Probe findings

- `IDiagramElement.setBackground(Color)` / `setForeground(Color)`.
- `getLineModel()` -> `IDiagramElementLineModel.setColor(Color, boolean)`
  and `setWeight(float, boolean)`; the trailing boolean is undocumented
  and `false` works (probe-verified).
- `getElementFont()` -> `IElementFont` with `setColor`, `setSize`,
  `setName`, `setBold`, `setItalic`.
- `background` and the visible shape fill are different properties:
  the fill is `IShapeUIModel.getFillColor().setColor1(Color, boolean)`
  (reads report it as `fill.color1`). Styling only `background` leaves
  the shape looking unchanged — hence a separate `fill_color` field.

## Decision

- New op `style_element {id, element, background?, fill_color?,
  foreground?, line_color?, line_weight?, font_color?, font_size?,
  font_bold?, font_italic?, font_name?}`. Colours are hex like the
  reads report them ("#FF0000", with or without leading "#").
- `element` resolves like `move_element`: a view id or a plan ref.
  `fill_color` applies to shapes only (silently absent for
  connectors, which have no fill).
- Compensation snapshots every changed value before mutation
  (ADR-0018 pattern); inherited (null) colours restore to null,
  best-effort.
- The applied entry carries the effective values read back after
  each setter.

## Consequences

- Reads and writes now cover the same visual surface: colours, line,
  font family/size/weight/style.
- Live-verified: a probe element styled with every field round-tripped
  through `get_diagram_by_url` `visual_properties` (fill #00AA55,
  background #FF0000, line #0000FF weight 3, Monospace 16 bold italic
  green) and survived a VP restart; probe deleted afterwards.
- 5 new validator contracts in `PlanValidatorTest`.
