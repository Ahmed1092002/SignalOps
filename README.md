# SignalOps

Real-time incident-management platform for software, operations, and DevOps teams.
Declare incidents, run a controlled lifecycle, coordinate responders, keep customers
informed, and review what happened — with every important action on an append-only
timeline and audit log.

**Status:** Phases 0–3 complete · backend 63/63 tests · frontend 32/32 tests · realtime E2E verified

---

## What it does

- **Multi-tenant by design** — multiple organizations, strict isolation enforced in queries,
  foreign keys, and authorization (cross-tenant access behaves as 404).
- **Controlled incident lifecycle** — `DETECTED → INVESTIGATING → IDENTIFIED → MONITORING → RESOLVED`
  with a central state machine; illegal transitions are rejected; reopening requires a reason.
- **Business rules that bite** — Critical incidents require a commander; resolution requires a
  summary; optimistic locking returns `409 STALE_WRITE` instead of losing updates.
- **Real-time collaboration** — WebSocket/STOMP broadcasts after commit; REST + PostgreSQL
  remain the source of truth.
- **Customer-safe public status page** — derived service statuses (worst active incident wins),
  published updates only, structurally separated from internal data.
- **Notifications, audit log, analytics** — in-app notifications, append-only audit, MTTR/MTTD
  and trend dashboards.

## Architecture

Modular monolith (package-per-module boundaries, ArchUnit-enforced), React frontend.

```
SignalOps/
├── backend/    Spring Boot 3.5 · Java 21 · PostgreSQL 16 · Flyway · JWT + rotating refresh
│               modules: shared · auth · users · organizations · servicecatalog · incidents
│                       timeline · notifications · audit · analytics · publicstatus · realtime
├── frontend/   Next.js 16 (App Router) · TypeScript strict · Tailwind v4 · shadcn/ui
│               TanStack Query · React Hook Form + Zod · Zustand (session/UI) · next-themes
├── docker/     Docker Compose (PostgreSQL + backend)
└── docs/       Phase 0 design docs, ADRs, UI audit
```

Domain model (ERD and full column reference): [`docs/phase-0/03-data-model.md`](docs/phase-0/03-data-model.md)

```
users ──< memberships >── organizations ──< services ──< incidents
                                              │             ├─< incident_comments   (internal only)
                                              │             ├─< incident_public_updates (customer-safe)
                                              │             ├─< timeline_events     (append-only)
                                              │             ├─< follow_up_tasks · attachments
                                              │             └─< maintenance_windows
                                              └─< audit_events · notifications · invitations
```

## Quick start

Prerequisites: **Docker**, **JDK 21**, **Node 20+**, Maven 3.9.

### 1. Database

```bash
cd docker
cp .env.example .env          # REQUIRED: set JWT_SECRET (openssl rand -base64 48)
docker compose up -d postgres
```

### 2. Backend (http://localhost:8080)

```bash
cd backend
set JWT_SECRET=<same value>          # PowerShell: $env:JWT_SECRET="..."
set SPRING_PROFILES_ACTIVE=local,demo
mvn spring-boot:run
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI: http://localhost:8080/v3/api/docs

### 3. Frontend (http://localhost:3000)

```bash
cd frontend
cp .env.example .env.local
npm install
npm run dev
```

Sign in at http://localhost:3000/login.

### Demo accounts (seeded with the `demo` profile)

All demo users share the documented throwaway password **`Demo1234!`**

| Email | Role |
|---|---|
| `owner@demo.signalops.io` | Owner |
| `admin@demo.signalops.io` | Admin |
| `commander@demo.signalops.io` | Incident Commander |
| `responder@demo.signalops.io` | Responder |
| `viewer@demo.signalops.io` | Viewer (read-only) |

Seeded organization **Northstar Cloud**: 6 services, 5 incidents across the lifecycle
(including an active CRITICAL incident and a resolved one with public updates).
Public status page: http://localhost:3000/status/northstar-cloud

### Full stack via Docker

```bash
cd docker
docker compose up --build     # postgres + backend (frontend runs separately)
```

## API

REST, `/api/v1`, JSON, UTC ISO-8601 timestamps. Organization-scoped requests carry
`X-Organization-Id`; auth via 15-minute bearer JWT; refresh token is an HttpOnly cookie
rotated on every use with family revocation on replay.

Full contract: [`docs/phase-0/05-api-contracts.md`](docs/phase-0/05-api-contracts.md) ·
live spec at `/v3/api/docs`.

## Testing

```bash
cd backend   && mvn test      # unit + Testcontainers PostgreSQL integration + ArchUnit
cd frontend  && npm test      # domain rules, HTTP client, form schemas
```

Backend tests run against **real PostgreSQL** via Testcontainers (Docker required).
Each business rule (R1–R23) has a named test — see
[`docs/phase-0/02-domain-rules.md`](docs/phase-0/02-domain-rules.md).

## Documentation

| Folder | Contents |
|---|---|
| [`docs/phase-0/`](docs/phase-0/) | Product scope, domain rules, ERD, architecture, API contracts, security, roadmap |
| [`docs/adr/`](docs/adr/) | Architecture decision records |
| [`docs/design/`](docs/design/) | UI design audit (Stitch export vs Phase 0) |
| [`backend/README.md`](backend/README.md) | Backend setup, env vars, modules |
| [`frontend/README.md`](frontend/README.md) | Frontend setup, security model, env vars |
| [`docker/README.md`](docker/README.md) | Compose reference |

## Known limitations

- Single backend instance (in-memory STOMP broker; Redis relay documented for scale-out)
- Rate limiting is per-instance (credential endpoints only)
- Invitations deliver in-app links, not email (email/webhooks deferred — needs external infra)
- Attachments capped at 5 MB, stored in PostgreSQL
- No RLS (app-layer tenancy is enforced and tested; RLS is hardening backlog)

## Roadmap

Phases 0–3 complete (design → MVP → realtime/public → analytics/tasks/attachments/maintenance).
Remaining backlog: email/webhook delivery, RLS evaluation, keyset pagination, CI/CD,
production deployment — tracked in [`docs/phase-0/08-roadmap.md`](docs/phase-0/08-roadmap.md).
