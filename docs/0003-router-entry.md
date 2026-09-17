# 0003: Router entry beside the vendored entry

## Context

Phase B wires our toolset into VP. The vendored `VpMcpPlugin` already
does this for the upstream toolset.

## Decision

- New `vpmcp.RouterPlugin` + `vp.router/plugin.xml` instead of editing
  the vendored entry. Vendored files stay byte-identical to upstream
  @ `92baa57` so future cherry-picks stay trivial.
- New tools go in `vpmcp.tools` (`ListDiagramsTool` first).
- `maven-shade-plugin` packs `router.jar` (gson bundled, `openapi`
  excluded as VP-provided system scope).

## Consequences

- Verified live: `:8899` serves `vp_list_diagrams` +
  `get_diagram_by_url`; full Class Diagram1 graph over HTTP, 0 warnings.
