# 0016: Production footing (skill contract, releases, versions)

## Context

The toolkit worked but wasn't shippable: the skill described a
read-only server, installs required Maven, and version bumps were
hand-edited (mismatched twice).

## Decision

- The skill is the agent contract. Rewritten to state the fixed op
  schemas and supported families explicitly — the validator is no
  longer the first place the agent learns its guesses were wrong.
  Discovery tools (`vp_search_types` et al.) remain unbuilt;
  creation is curated-generic, and the skill says so.
- Releases are local versioned zips (`dist/vp-router-<v>.zip`:
  jar, plugin.xml, schemas, skill), cut only by
  `opencode/release.sh <v>`, which refuses dirty trees, tests,
  builds, and packages. `install.sh --release <zip>` installs
  without Maven.
- Versions change only through release.sh (AGENTS.md rule).

## Non-goals (stated, not hidden)

Loopback-only with no auth is the threat model — safe for local
use, not for shared machines. MCP protocol stays hand-pinned;
integration runs stay manual curl. Both are maintenance debt,
neither blocks local production use.
