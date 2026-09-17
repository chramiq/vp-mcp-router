# 0006: Installer script, always ask directories

## Context

Manual install is three copies plus a JSON merge — error-prone and
unfriendly. An opencode npm plugin cannot help: no hook registers MCP
servers, and proxying tools through JS would duplicate the surface.

## Decision

- `opencode/install.sh`: VP files, MCP entry merge, skill copy.
- Always asks for directories (VP plugins dir, opencode.json target,
  skill destination); detection only suggests defaults, never decides.
- Idempotent, rerunnable, `--dry-run` previews; env overrides
  (`VP_PLUGINS_DIR`, `OPENCODE_JSON`, `SKILL_DIR`, `ROUTER_JAR`,
  `SCHEMA_VERSION`) for testing. Verified against a sandbox tree.

## Consequences

- JSON merge does not preserve comments in an existing opencode.json;
  stated in script output.
- VP restart is still manual (VP loads plugins at boot).
