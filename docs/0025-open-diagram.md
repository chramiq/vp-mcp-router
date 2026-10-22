# 0025: open_diagram

## Context

The plugin could read, render and edit diagrams but never bring one to
the front. After a restart, VP reopens only the last active tab, and an
agent asked to "show me diagram X" had no way to do it — the operator
had to find the diagram in the Model Browser.

## Decision

- New tool `vp_open_diagram {diagram}` (diagram id or vpp url). Resolves
  the address with `DiagramLocator`, calls
  `DiagramManager.openDiagram(diagram)`, and returns `{opened, id, name,
  type}`.
- Read-only in the model: it changes the editor's active tab, not the
  project. No save is needed or implied.

## Consequences

- The agent can activate the diagram it just built or edited, so the
  operator sees the result without hunting the tree.
- MCP clients cache the tool list at handshake; a freshly installed
  plugin's new tool appears only after the client reconnects.
- 2 parameter-gate tests run without VP; the open call itself is
  live-verified.
