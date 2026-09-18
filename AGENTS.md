# AGENTS.md

Read-only Visual Paradigm MCP router: an in-process VP plugin serving the
open project as JSON over Streamable HTTP, with versioned schema packs so
VP updates don't force plugin rebuilds.

## Repo layout

| Path | What lives there |
|---|---|
| `plugin/` | Java plugin source (planned): zero-dep MCP core, EDT invoker, extractor |
| `schemas/` | versioned JSON schema packs per VP minor, e.g. `schemas/v18.1/` (planned) |
| `opencode/` | `mcp.json` snippet + `SKILL.md` (planned) |
| `docs/` | architecture decision records (`INDEX.md` + one file per ADR) |
| `var/` | runtime state and probe captures (gitignored) |
| `TODO.md` | deferred ideas and tasks |

## Glossary

Terms below are the project's shared vocabulary: use them verbatim in code,
docstrings, prompts, and docs. If you spot a stale or conflicting use,
report it with file/line evidence and a proposed correction.

| Term | Definition |
|---|---|
| open project | The `.vpp` currently open in the running VP instance. The only project the plugin can see. |
| diagram view | A shape or connector on a diagram (`IDiagramElement`): bounds, z-order, visual properties. |
| model element | The logical element behind a view (`IModelElement`): name, type, documentation, stereotypes. |
| extractor | The VP-to-JSON transform producing `{diagram, nodes[], edges[], warnings[]}`. |
| schema pack | Versioned JSON schemas for one VP minor under `schemas/<version>/`. |
| probe | A throwaway script capturing real `openapi.jar`/VP output. Code is written against captures, never from docs alone. |
| capabilities | The `vp_capabilities` response: VP version, plugin version, supported families, schema version. |

## Rules

### Workflow

- Small increments: never land large untested code. If it doesn't work it just pollutes the repo.
- Stop after each phase and wait for review before starting the next.
- One scoped commit per phase, message documents what changed.
- Order: research → probe → implement → test → review → live run → docs.
- Probe-first for anything touching `openapi.jar` or live VP.

### VP safety

- v1 is read-only: mutating ops are absent from the binary, not just disabled.
- Never mutate the open project (create, update, delete, save) without explicit user approval per action.

### VP restarts (development and testing only)

- During development and testing you may kill and restart Visual
  Paradigm yourself without asking: `pkill -f install4j.RV`, then
  launch `/usr/bin/visual-paradigm` in the background and poll the
  MCP endpoint until it answers.
- Always pass the project path on launch
  (`visual-paradigm /home/v/Documents/VPProjects/untitled.vpp`): it
  skips the startup dialog, which otherwise blocks project load
  (verified: diagrams visible with zero clicks).
- Preconditions: the open project is saved (confirm once per session
  if unsure — an unsaved restart loses work); announce each restart
  in chat; never restart outside dev/test or while the user is
  actively modeling.

### Code

- New decisions get an ADR in `docs/`, appended to `docs/INDEX.md`.
- MCP tool descriptions are the single source of truth for tool use; the skill never duplicates them.
- Tool failures return explicit structured errors: never silent empties, never raw stack traces as the answer.

### Tests

- Tests assert behavior, never literals: structural invariants (counts
  derived from fixture builders, referential integrity, ordering,
  error contracts), never golden JSON blobs or hardcoded VP ids.
- No fake coverage: a test must fail if the code under it breaks. No
  tautologies (asserting the fixture back to itself), no mocks of the
  unit under test, no `assertTrue(true)` padding.
- Brittle-by-construction is banned: tests must survive irrelevant
  changes (new optional fields, reordered keys, extra warnings) and
  break only on contract changes.
- Two layers: unit tests on plain JDK (`mvn test`, proxy fakes, no VP)
  for logic; live runs over HTTP against VP for integration. Neither
  replaces the other.
