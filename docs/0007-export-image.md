# 0007: Image results as MCP image blocks

## Context

Agents reason better with the rendered diagram next to the JSON graph.
`ModelConvertionManager.exportDiagramAsImage(diagram, option)` returns a
`java.awt.Image` with no file involved; PNG encode via ImageIO.

## Decision

- New `vp_export_image` tool (PNG only, v1): locates the diagram by the
  same `vpp_url` semantics, renders in-memory, returns an envelope
  `{diagram, summary, image_mime, image_data}`.
- Protocol change (additive): when a tool output carries `image_data`,
  `tools/call` emits `content: [text(summary), image(base64)]` instead
  of a JSON text blob. All other tools untouched.

## Consequences

- PNG over HTTP is faithful to the canvas. SVG/PDF via option types
  left for later.
