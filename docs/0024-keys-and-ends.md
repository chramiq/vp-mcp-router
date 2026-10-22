# 0024: Keys and relationship ends

## Context

ER diagrams need primary keys, uniqueness and foreign-key cardinality;
class diagrams need association multiplicities and navigability. Both
were invisible to the router: `describeColumn` returned type/length/
nullable only, `DiagramExtractor` never looked at
`IEndRelationship.getFromEnd()/getToEnd()`, and `connect` created the
relationship model but set no end properties. An agent modelling an ERD
could not mark a key or read a crow's foot without looking at a
rendered image.

## API surface

- `IDBColumn`: `setPrimaryKey(boolean)`, `isPrimaryKey()`,
  `setUnique(boolean)`, `isUnique()`.
- `IAssociationEnd` (via `IEndRelationship.getFromEnd/getToEnd`):
  `setMultiplicity(String)`, `getMultiplicity()`,
  `setNavigable(int)` (`NAVIGABLE_*`), `getNavigable()`,
  `setAggregationKind(String)`/`getAggregationKind()`.
  Multiplicity values: `Unspecified`, `0`, `0..1`, `0..*`, `1`, `1..*`, `*`.
- `IDBForeignKey`: `setFromMultiplicity`/`setToMultiplicity`,
  `setIdentifying`, and the corresponding getters.
- `DiagramManager.createConnector(diagram, String connectorType, from,
  to, points)` — the typed overload.

## Decision

- `add_member`/`update_member` on `DBColumn` accept `primary_key` and
  `unique` (old-value snapshots, same path as type/length/nullable).
- `connect` accepts `from_multiplicity`, `to_multiplicity`,
  `from_navigable`, `to_navigable` (Association ends) and
  `identifying` (ForeignKey). Values are validated up front; applied
  values are echoed back from the model, not from the request.
- Edges now always carry `from_multiplicity`/`to_multiplicity`; UML
  associations add navigability and aggregation, ER foreign keys add
  `identifying`. Not gated behind full detail — the checks need them.
- A `ForeignKey` relationship cannot be placed from a model-first
  `createConnector`; VP returns no view. `connect` therefore uses the
  typed overload for ER foreign keys and adopts the model the view
  carries. Other relationship kinds keep the model-first path.

## Consequences

- Live-verified on VP 18.1: PK key icon and `U` marker render; a
  ForeignKey connector draws with the configured multiplicities and
  creates its own foreign-key column (default name
  `<ParentTable><ParentColumn>`) — callers must not pre-create that
  column, or rename it after the connector exists.
- The FK connector path is still pack-tier (`ForeignKey` is not in the
  verified relationship set), so preview marks it unverified.
- Failure paths now log the stack trace to `vpmcp.log`; a refused
  connector reports which relationship kind was refused.
