# 0002: Vendor avernus sources as copy, keep package names

## Context

Phase A needs the proven MCP core + extractor. Upstream
(`avernussoftware/visualparadigmmcp` @ `92baa57`, v0.1.0) publishes no
artifact; the license is MIT, which permits copy with notice
(`plugin/NOTICE.txt`).

## Decision

- Copy the 28 files under `src/main/java/vpmcp/` into `plugin/`,
  package names unchanged, so diffs against upstream stay reviewable.
- No submodule: upstream is single-commit dormant (drift risk ~zero)
  and VP deployment is one hand-copied jar — a copy keeps the build
  hermetic with no init step on the VP machine.
- Minimal `plugin/pom.xml`: `--release 11`, system `openapi.jar`
  (overridable via `-Dvp.openApi=`), gson 2.10.1. No shade yet.

## Consequences

- `mvn compile` is green (28 classes); P1 roundtrip re-verified from
  repo sources (`initialize` + `echo`).
- Upstream fixes, if any ever appear, are cherry-picked by hand.
