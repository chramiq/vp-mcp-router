# 0022: Tiered type gates (verified / pack / impossible)

## Context

ADR-0008's whitelist was designed as "v1 allowlist; expansion is a
data change". The schema pack already knows the full universe
(101 diagram types, 1454 factory creates), and ADR-0021 proved the
failure modes of unverified families are graceful and observable.
The wall could become a gradient.

## Decision

- `TypeTiers` carries two tiers: verified (the probe-tested
  ADR-0014 families) and pack (diagram type values and
  factory-creatable model types loaded from
  `<pluginDir>/schemas/<version>/`). A missing or unreadable pack
  degrades to verified-only with a log line, warn-never-fail
  (SchemaGuard philosophy).
- `PlanValidator` gates on the tiers:
  - verified type -> normal plan entry;
  - pack type -> accepted, plan entry flagged `"unverified": true`
    with the "VP may veto or misplace" note (ADR-0021 wording);
  - neither -> rejected as *impossible* ("VP has no diagram type
    ..."/"VP cannot create ..."), with a pointer to the
    vp://schemas resources — a real reason, not a policy.
- Applies to `create_diagram`, `create_element`, `connect`,
  `add_member`; `update_member` member resolution accepts the same
  tiers. `create_raw` stays the anything-hatch (ADR-0021).
- `RouterPlugin` loads the tiers once and hands the same instance to
  preview and apply, so re-validation sees identical gates.
- Applied-entry id contract unified (live-run papercut, bit twice):
  `vp_id` is the **model id** everywhere; `view_id` is added when a
  view exists (`create_element`, `connect`, `show_element`),
  matching what `create_raw` already did.

## Consequences

- The write surface expands from 7 diagram / 12 element / 3 member /
  7 relationship types to 101 / ~1400 / ~1400 / ~1400 with a visible
  verification gradient; agents see the tier in every plan entry.
- Live-verified: Brainstorm diagram created and deleted; Requirement
  element placed, extracted, deleted; Abstraction connected,
  extracted, deleted; verified ops unchanged; cleanup by `vp_id`
  straight from applied entries.
- 8 new validator/tier contracts (including pack loading and
  degradation).
