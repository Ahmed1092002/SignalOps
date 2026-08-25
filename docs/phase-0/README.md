# docs/phase-0/ — Design Baseline (Source of Truth)

Everything in this folder was produced **before implementation** and governs it.
When code and documentation disagree, the documentation wins — or the deviation is
recorded in `../adr/`.

## Contents

| File | What it defines |
|---|---|
| `01-product-scope.md` | Problem, personas, MVP scope, 24 user stories with acceptance criteria, portfolio objectives |
| `02-domain-rules.md` | Incident state machine, severity definitions, derived service status (R10), business-rule catalog **R1–R23**, concurrency & time policy |
| `03-data-model.md` | ERD, per-entity fields/constraints/indexes, module ownership map, archive/delete matrix |
| `04-backend-architecture.md` | Module boundaries & dependency rules, event pipeline, authorization layering, persistence & testing strategy |
| `05-api-contracts.md` | REST conventions, error envelope + code registry, full endpoint catalog, WebSocket contract |
| `06-frontend-architecture.md` | Rendering strategy, routes, state allocation, realtime→cache matrix, quality bars |
| `07-security.md` | Token strategy, refresh rotation & reuse detection, tenancy enforcement, permission matrix |
| `08-roadmap.md` | Milestones M1–M11, definition of done, status checklist |

## Key artifacts (fast links)

- **State machine & rules:** `02-domain-rules.md` §1, §4 — every rule ID has a named test
- **Permission matrix:** `07-security.md` §3
- **ERD:** `03-data-model.md` §1
- **Error codes:** `05-api-contracts.md` §2

## Status

Phase 0 sign-off decisions (org-context header, single role per membership, self-serve
orgs, Testcontainers from M1) are recorded in `08-roadmap.md` §4 and implemented.
Phase 2/3 completion status is tracked in the same file.
