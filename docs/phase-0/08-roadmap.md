# SignalOps — Phase 0 · Repository Layout & Implementation Roadmap

Status: DRAFT — awaiting approval

## 1. Repository Structure

```
SignalOps/
├── backend/                     Spring Boot (Maven), package-per-module (see backend doc)
├── frontend/                    Next.js app
├── docs/
│   ├── phase-0/                 these design documents
│   └── adr/                     architecture decision records (started in M1)
├── docker/docker-compose.yml    postgres (+redis when justified)
├── .env.example
├── README.md                    product overview, quickstart, screenshots (later)
└── .github/workflows/ci.yml     Phase 3
```

## 2. Milestones (each ends runnable + tested)

### Phase 1 — MVP

| # | Milestone | Scope | Key tests |
|---|---|---|---|
| M1 | Skeleton | Maven project, compose (PG), Flyway V1 core schema, error envelope, OpenAPI, health, CI-less local run | context loads; Testcontainers boots PG; 404/error shape |
| M2 | Auth | register/login/refresh-rotation/logout/me, BCrypt, JWT filter | credential errors uniform; reuse revokes family; expired access → 401 |
| M3 | Org & RBAC foundation | orgs, memberships, invites, TenantContext, permission evaluator, member management | invite lifecycle; last-owner guard; cross-org 404; matrix cells O/A |
| M4 | Services | CRUD + archive + guards | R18; duplicate slug 409; audit rows written |
| M5 | Incidents core | create/detail/list(filters,pagination)/PATCH/transitions/severity/archive, state machine, @Version | R1,R2,R7,R8,R12,R13,R16,R23; conflict flow end-to-end |
| M6 | People & comms | commander/responders, internal comments, public updates, timeline projector | R4,R9,R17,R19; timeline atomic w/ rollback test |
| M7 | Oversight | dashboard summary, audit log endpoint, polish, validation hardening | dashboard math; audit filters/pagination; viewer read-only sweep |
| M8 | Demo-ready | seed runner (`demo` profile), README, screenshots, deployment prep (Render/Vercel/managed PG), known-limitations doc | fresh-clone quickstart verified |

### Phase 2 — Real-time & Public

| # | Milestone | Scope | Key tests |
|---|---|---|---|
| M9 | WebSocket backbone | STOMP config, auth interceptor, after-commit bridge, FE ws-client + cache matrix | events only after commit; unauthorized SUBSCRIBE rejected; rollback ⇒ no event |
| M10 | Notifications | domain-event generators, list/read endpoints, bell UI, assignment & critical-severity triggers | notification written in-tx; only participants notified |
| M11 | Public experience | derived service statuses, status endpoints, `/status/[orgSlug]` SSR page, publish flow UI | R10 unit+integration; public payloads contain zero internal fields |

### Phase 3 — Advanced (only after stability)

Analytics & MTTR charts · follow-up tasks · attachments · email/webhook delivery via
outbox-shaped workers · scheduled maintenance windows · post-incident reports ·
ArchUnit boundary suite · CI/CD · observability (structured logs, metrics, tracing) ·
rate limiting (bucket4j+Redis) · RLS evaluation · keyset pagination where hot.

## 3. Feature Definition of Done (applies to every milestone item)

1. Acceptance criteria written down before coding.
2. Domain rules touched have their named tests (R# ↔ test name mapping kept in PR description).
3. Migration + entity + indexes reviewed against query patterns.
4. Error paths return documented codes; nothing leaks internals.
5. Frontend states complete: loading/empty/error/populated (+conflict dialog where versioned).
6. Swagger reflects reality; README updated if setup changed.
7. App runs green locally from clean checkout.

## 4. Current Status Checklist

**Phase 2 (completed 2026-08-25)**
- [x] M9 WebSocket/STOMP backbone — bearer auth on CONNECT, per-subscription authorization, after-commit bridge
- [x] M10 Notifications — event-driven generators, list/read endpoints, bell UI
- [x] M11 Public experience — derived statuses, public-safe DTOs, /status/[orgSlug] SSR page

**Phase 3 (completed 2026-08-25)**
- [x] Analytics & MTTR/MTTD (backend overview endpoint + Recharts page)
- [x] Follow-up tasks (backend + incident detail UI)
- [x] Attachments (DB-stored, 5 MiB cap, upload/list/download)
- [x] Scheduled maintenance windows (status override + UI + public display)
- [x] Post-incident data (resolution summary, duration, timeline history)
- [x] ArchUnit boundary suite
- [x] Rate limiting on credential endpoints (per-instance; Redis documented for scale-out)
- [x] Security headers (frame-deny, referrer-policy)
- [ ] Email/webhook delivery — DEFERRED (requires external infrastructure/credentials)
- [ ] RLS evaluation, keyset pagination — DEFERRED (documented backlog)
- [ ] CI/CD pipelines, production deployment — DEFERRED (no repository/credentials in environment)

**Phase 0**
- [x] Product scope refined (`01-product-scope.md`)
- [x] MVP user stories with acceptance criteria
- [x] Permission matrix (`07-security.md`)
- [x] State-transition table & business rules catalog (`02-domain-rules.md`)
- [x] ERD + entity specifications (`03-data-model.md`)
- [x] Backend module boundaries (`04-backend-architecture.md`)
- [x] API contracts (`05-api-contracts.md`)
- [x] Frontend structure (`06-frontend-architecture.md`)
- [x] Roadmap (`08-roadmap.md`, this file)
- [x] Sign-off on open decisions — resolved 2026-08-25: org context via `X-Organization-Id`
      header · single role per membership · self-serve orgs + invite links ·
      Testcontainers PostgreSQL from M1
- [ ] Repo scaffolding (M1)

**Open decisions carried into implementation review**
- `INVESTIGATING → MONITORING` omitted from brief's transition list — implemented spec-exact first.
- LOW severity maps to OPERATIONAL on the public page (noise-reduction policy).

**Known limitations ledger (starts here, grows honestly)**

- In-memory STOMP broker ⇒ single backend instance until Redis relay.
- Offset pagination everywhere initially.
- No rate limiting, CSP/HSTS yet (Phase 3).
- Invitations deliver links in-app (copy button), not email, until Phase 3.
