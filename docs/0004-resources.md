# 0004: Resources via provider boundary, files on disk

## Context

ADR-0001 promised schemas as MCP resources. The vendored protocol
handler knew only tools; its server builds the handler internally, so
there is no injection point.

## Decision

- New VP-free `core/ResourceProvider` (`list`/`read`); additive-only
  overloads on `McpServer`/`McpProtocolHandler`. Old constructors keep
  working (`DevServer` untouched).
- `vp/FileResourceProvider` serves `<pluginDir>/schemas/<version>/`
  plus a capabilities snapshot baked at startup. Only `.json` inside
  the pack dir is reachable; traversal is rejected.
- `vp_capabilities` tool reports the live part (open project); the
  resource carries the static part. No `vp_describe_schema` tool:
  `resources/read` already answers it, fewer tools is better.
- `schemas/autovendor.py` regenerates the pack from `openapi.jar`
  (javap, no class loading). VP bump = rerun, diff, commit.

## Consequences

- `resources/list` / `resources/read` cover capabilities and the
  diagram-type universe; `vp_capabilities` reports the open project.
- First divergence from upstream in two vendored files; additive only,
  cherry-picks stay mechanical.
