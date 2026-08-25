# docs/adr/ — Architecture Decision Records

Short-lived memory is how projects drift. Every significant decision made during
implementation — especially deviations from the Phase 0 documents — is recorded here.

## Index

| ADR | Topic |
|---|---|
| `0001-backend-decisions.md` | Stack versions · cross-module communication · policy-based authorization · tenancy enforcement · seeding · Phase 2/3 additions (realtime, notifications, public status, attachments, maintenance, rate limiting) |

## Format

Each record states the decision, the rationale, and (where relevant) the exit path —
the upgrade route when the trade-off stops being acceptable. Records are immutable;
superseding a decision adds a new record and a pointer.
