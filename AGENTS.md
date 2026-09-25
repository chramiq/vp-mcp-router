# AGENTS.md

Visual Paradigm MCP router: an in-process VP plugin that serves the
open project as JSON over Streamable HTTP and writes back through
previewed, compensable batches. Writes are tiered — probe-verified
families behave exactly as documented, schema-pack types are accepted
but flagged unverified. Schema packs are versioned per VP minor so VP
updates ship as data, not rebuilds.

## Repo layout

| Path | What lives there |
|---|---|
| `plugin/` | Java plugin source: zero-dep MCP core, EDT invoker, extractor, write path |
| `schemas/` | versioned JSON schema packs per VP minor (`v18.1/`); `autovendor.py` regenerates from `openapi.jar` |
| `opencode/` | `install.sh`, `release.sh`, `mcp.json` snippet, `skills/vp-router/` |
| `docs/` | architecture decision records (`INDEX.md` + one file per ADR), `INSTALL.md` |
| `dist/` | release zips built by `opencode/release.sh` (gitignored; published as GitHub Release assets) |
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
| member | A child model element (attribute, operation, column). Not in VP's project id index — reachable only through its parent (`ModelLookup`). |
| extractor | The VP-to-JSON transform producing `{diagram, nodes[], edges[], warnings[]}`. |
| schema pack | Versioned JSON schemas for one VP minor under `schemas/<version>/`. |
| tier | Write-type gate: verified (probe-tested, exact behavior), pack (schema-pack member, accepted but flagged `unverified`), or impossible (VP cannot create it, hard error). |
| applied entry | Per-op apply result: `vp_id` is the model id, `view_id` is added when a view exists. Both ids are needed downstream — move/style/delete_element take views, update/delete_model take models. |
| probe | A throwaway script capturing real `openapi.jar`/VP output. Code is written against captures, never from docs alone. |
| capabilities | The `vp_capabilities` response: VP version, plugin version, schema version, schema guard report. |

## Rules

### Workflow

- Small increments: never land large untested code. If it doesn't work it just pollutes the repo.
- Stop after each phase and wait for review before starting the next.
- One scoped commit per phase, message documents what changed.
- Versions change only through `opencode/release.sh <v>` (refuses
  dirty trees, tests, builds, packages `dist/`). Never hand-edit
  the VERSION string.
- Order: research → probe → implement → test → review → live run → docs.
- Probe-first for anything touching `openapi.jar` or live VP.

### VP safety

- Never mutate the open project (create, update, delete, save) without explicit user approval per action.
- All writes go through `vp_apply_batch` with `confirm: true`, re-validated
  immediately before the first mutation; creations and updates compensate
  in reverse order on failure, deletions are final. Apply never saves —
  `vp_save_project` (also confirm-gated) is the only path to disk.
- Unverified-tier writes may hit VP's silent vetoes (names that don't
  stick, refused placement). The applied entry carries the effective
  values and `name_warning`; always read back what actually landed.

### VP restarts (development and testing only)

- During development and testing you may kill and restart Visual
  Paradigm yourself without asking: `pkill -f '[i]nstall4j.RV'` (the
  bracket matters: a plain pattern matches the wrapping shell's own
  command line and kills your own command), then launch
  `/usr/bin/visual-paradigm` in the background and poll the MCP
  endpoint until it answers.
- Always pass the project path on launch
  (`visual-paradigm /home/v/Documents/VPProjects/untitled.vpp`): it
  skips the startup dialog, which otherwise blocks project load
  (verified: diagrams visible with zero clicks).
- The endpoint answers before the project finishes loading — early
  answers can show a transient empty project. Poll until the diagram
  count is stable before trusting reads.
- Preconditions: the open project is saved (confirm once per session
  if unsure — an unsaved restart loses work); announce each restart
  in chat; never restart outside dev/test or while the user is
  actively modeling.
- Restarts can drop VP's academic-license activation (observed
  2026-09-25: license needed re-activation after a restart). Prefer
  to avoid restarts entirely; when one is truly needed, warn the user
  first and have them confirm the license is still active afterwards.
- Never copy a jar over the running VP (`cp` straight onto
  `plugins/vp.router/lib/router.jar` tore the classloader with a
  ZipException, 2026-09-25). Stage atomically instead: copy to a temp
  file in the same directory, then `mv` over the target.
- Before killing, save via `vp_save_project` with confirm and poll
  the `.vpp` mtime until it moves past the pre-save value (writes land
  async, 5-20s observed — a single stat proves nothing); if the mtime
  never moves, do not kill — ask the user to save first.

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
