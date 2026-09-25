# 0005: opencode over remote HTTP, minimal skill

## Context

Phase D connects a real agent. Two open questions: transport shape and
how much prompt-side material to ship.

## Decision

- Remote transport (`type: remote`, plain HTTP URL) to the in-VP
  server. No launcher process, no stdio bridge: VP owns the server
  lifecycle, loopback keeps it local.
- One minimal skill (`opencode/skills/vp-router/SKILL.md`): prerequisites
  (VP running, project open), the handshake order
  (capabilities → list → read), nothing else. Tool descriptions stay
  the single source of truth per repo rules.
- `docs/INSTALL.md` owns build/install/connect/uninstall; the skill
  never duplicates it.

## Consequences

- The read loop (capabilities → list → full diagram) works end to end
  over HTTP against the running VP.
