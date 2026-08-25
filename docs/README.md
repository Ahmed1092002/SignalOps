# docs/

Project documentation. Phase 0 documents are the **source of truth** — implementation
conforms to them, and deviations are recorded as ADRs.

## Layout

| Folder | Contents |
|---|---|
| `phase-0/` | Design baseline produced before any code: product scope, user stories, domain rules (R1–R23), state machine, ERD, backend architecture, API contracts, frontend architecture, security & RBAC, roadmap |
| `adr/` | Architecture decision records — stack choices, module boundaries, tenancy, seeding, Phase 2/3 additions |
| `design/` | UI design audit: Stitch-generated export vs Phase 0 documentation (conflicts resolved in favor of the docs) |

## Reading order

1. `phase-0/01-product-scope.md` — what SignalOps is and its user stories
2. `phase-0/02-domain-rules.md` — the business rules every test asserts (R1–R23)
3. `phase-0/05-api-contracts.md` — the REST contract
4. `phase-0/07-security.md` — token strategy, permission matrix, tenancy
5. `adr/0001-backend-decisions.md` — what was decided while building

## Changing something?

Domain rules, API contracts, and security decisions here are binding. To change one:
update the Phase 0 document, record the decision in `adr/`, and update the named tests.
