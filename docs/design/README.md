# docs/design/ — UI Design Audit

## Contents

- `ui-audit-stitch-vs-phase0.md` — discrepancy report comparing an AI-generated
  (Stitch) UI export against the Phase 0 documentation before implementation.

## Why it exists

The generated design looked polished but violated the documented domain in dozens of
places: serif typography inconsistencies, `SEV-1..4` labels instead of the defined
`LOW/MEDIUM/HIGH/CRITICAL`, a create-time visibility toggle that contradicts rule R14,
"Save Draft" despite the lifecycle starting at DETECTED, fabricated latency/uptime
metrics, and deferred features (attachments, follow-ups, Join Bridge, subscribe forms)
shown as active.

The audit is the paper trail for every visual correction: what was preserved, changed,
removed, or deferred — and why the documentation won each conflict.
