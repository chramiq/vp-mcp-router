# 0015: Quirk fixes (names, columns, captions)

## Context

ADR-0014 left four VP quirks open. All closed or bounded here.

## Fixed

- Column types: `IDBColumn` takes `setTypeName(String)`, not
  `setType`. `setMemberType` now tries both; `varchar` applies
  cleanly. Column reads gained `type_name/length/nullable`
  (unit-tested fake, live-verified).
- State/decision captions: zero-area caption boxes, same bug class
  as actors. `insideCaptionBounds` centers a box in the shape;
  renders prove "Idle", "Driving", "Charged?".
- Table unsync: fresh tables get `SYNC_TYPE_NOT_SYNC` +
  `ORM_SYNC_STATE_NOT_SYNC` before naming (harmless — nothing to
  unsync from on a new table).

## Bounded (VP vetoes, now explicit)

- Table names still don't stick ("Vehicle" -> "EntityN") and the
  name "Order" is reserved ("Class4"). `setName` fails silently in
  both cases, so applied entries now carry `name_warning` with the
  requested vs kept name. The agent sees the discrepancy and adapts;
  nothing else the API offers (no alternate setter found).
