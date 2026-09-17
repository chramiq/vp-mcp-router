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
