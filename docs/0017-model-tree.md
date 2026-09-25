# 0017: Model tree access (list, read, model-id neighborhoods)

## Context

Extraction started from diagram views (`toDiagramElementArray`), so model
elements with no view were invisible to every tool. In a real project
(806 models, one empty diagram) the entire model tree was unreachable,
including 35 use cases.

## Findings

`IProject.toAllLevelModelElementArray()` and `getModelElementById(String)`
cover the tree; `IModelElement.getParent()` gives the parent link; view
membership is derivable by scanning `toDiagramArray()` per diagram. No
new VP machinery required.

## Decision

- `vp_list_models {model_type?}`: light index of every model element
  (id, name, model_type, parent_id), all-level, optional type filter.
- `vp_get_model {model, detail?}`: full read of one model — identity,
  documentation, stereotypes, tagged values, members, sub-diagrams, parent,
  plus `shown_on[]` (diagram id/name/type + view id) and `detail:"full"`
  raw model properties. Reuses `ModelPropertiesReader`.
- `vp_get_neighborhood` `element` now accepts a model id: resolved to the
  model's view on the addressed diagram (with a warning naming the
  substitution); a model that exists but has no view there is an explicit
  structured error pointing at `vp_get_model`.

## Consequences

- Orphan models are first-class: listable, readable, and clearly reported
  as not shown, instead of silently invisible.
- Tool cores are static (`index`, `read`, `resolveCenter`) beside the
  live-project entry points so unit tests run on proxy fakes
  (8 new tests across three classes).
- The index, type filter, orphan reads, empty `shown_on`, and the
  not-shown neighborhood error are all exercised against a real
  project; the model-id-to-view success path is unit-tested.
