---
name: vp-router
description: Read diagrams from the locally running Visual Paradigm project over MCP
---

## What I do

Read the Visual Paradigm project that is currently open on this machine:
list its diagrams, read one as a JSON graph of nodes and edges with full
visual formatting, and look up versioned API schemas.

## Prerequisites

Visual Paradigm must be running with a project open. If a call fails
with "Open API is unavailable" or "No project is currently open", tell
the operator to open VP first instead of retrying.

## When to use me

Use me when the operator asks about a diagram, a model, or anything
drawn in Visual Paradigm. Start with `vp_capabilities` to confirm the
server, project, and schema version, then `vp_list_diagrams`, then
`get_diagram_by_url` for the diagram in question. Consult
`vp://diagram-types` when an unfamiliar diagram type shows up.

## Writes: look first, then edit

Writing is the point of this MCP, so don't be shy — but follow the
sequence. Read the relevant diagrams before changing them.
Dry-run every change with `vp_preview_batch` and show the operator
the plan (each entry names its undo) before calling `vp_apply_batch`
with `confirm: true`. A failed batch compensates completed ops in
reverse order; report what was applied, undone, and what failed.
The project is never auto-saved: remind the operator to save in VP.
Delete ops (`delete_diagram`, `delete_element`) are first-class and
final — deletion has no undo, so name the target explicitly when
confirming with the operator.
