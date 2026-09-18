---
name: vp-router
description: Read and model UML/ERD diagrams in the locally running Visual Paradigm project over MCP
---

## What I do

Read the open Visual Paradigm project as JSON graphs and modify it
through validated write batches: create diagrams and elements,
connect them, add members, duplicate diagrams for trials, delete,
save. Tool descriptions are the contract; this file is the workflow.

## Prerequisites

VP must be running with a project open. On "Open API is unavailable"
or "No project is currently open", tell the operator to open VP
instead of retrying. Start every session with `vp_capabilities`.

## Reads

`vp_list_diagrams`, `get_diagram_by_url` (full graph + visuals),
`vp_get_neighborhood` (N-hop slice), `vp_export_image` (png/svg/pdf,
crop or `crop_to_element`). Reads cover all diagram types;
`vp://diagram-types` lists them.

## Writes (fixed schemas — check, don't invent)

Dry-run with `vp_preview_batch`, show the plan with undos, then
`vp_apply_batch` with `confirm: true`. Partial failure compensates
in reverse; report applied/compensated/errors. Supported families:

- diagrams: Class, UseCase, Activity, State, ER, Interaction
  (sequence), Deployment. Nothing else validates.
- elements: Actor, UseCase, Class, Activity, InitialNode,
  DecisionNode, ActivityFinalNode, State2, DBTable, Component,
  Node, LifeLine.
- relationships: Association, Include, Extend, Generalization,
  Dependency, Message, Transition2. `points[]` waypoints for
  messages; `from_member`/`to_member` pins where known.
- members via `add_member`, never top-level: Attribute, Operation
  (class), DBColumn (table). Optional string `type`.
- `duplicate_diagram` is a shallow copy (fresh views, shared
  models): safe for additive trials, model edits leak to source.

Outside these lists the validator rejects — don't retry variants,
say what's unsupported. `name_warning` on an applied entry means VP
overrode the name (reserved words, table naming); use the kept name.

## Trial loop

Duplicate the target, apply candidates on the copy, read/export to
verify, then keep (replicate to source) or `delete_diagram` the
copy. Delete trial elements before the diagram so no orphan models
linger. Deletion is final — name targets explicitly at confirm.

## Saving

Never auto-saved. After clean batches offer `vp_save_project`
(`confirm: true`) and report the file path.
