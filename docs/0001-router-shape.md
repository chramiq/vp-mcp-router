# 0001: Primitives + schema resources, zero-dep Java, read-only v1

## Context

`openapi.jar` in VP 18.1 exposes 1453 `IModelElementFactory.create*`
methods and 101 diagram types. The two reference MCPs target VP 17.x and
cover opposite ends (unimplemented use-case writes vs working generic
read). Hand-wrapping per-type tools would never finish and every VP minor
would break it.

## Decision

- Thin in-process Java plugin: ~6 stable read primitives
  (`vp_capabilities`, `vp_list_diagrams`, `vp_get_diagram`,
  `vp_get_element`, `vp_export_image`, `vp_describe_schema`).
- Type knowledge lives in versioned schema packs under `schemas/`,
  served as MCP resources and loaded from the plugin dir at runtime.
- Zero-dependency HTTP core (plain `ServerSocket`, loopback-only),
  all VP access marshalled onto the Swing EDT.
- v1 is read-only. Write ops (`preview_batch` / `apply_batch` with
  confirm + dry-run) are designed, not built.

## Consequences

- New VP version = re-run the reflection probe, diff schemas, commit.
  No plugin recompile for new diagram/model types.
- Unknown types degrade to standard dump + warning, never crash.
- One opaque `vp_call{op, params}` was rejected: worse LLM usability,
  runtime-only validation, weaker audit trail.
