# ADR Records — SignalOps Backend

## ADR-0001 · Stack versions (2026-08-25)

- Java 21 (Temurin 21.0.12), Spring Boot **3.5.x** (stable line; Boot 4 evaluated and
  deferred — team familiarity and ecosystem stability beat novelty for a portfolio MVP).
- Maven single module, package-by-module boundaries (per `docs/phase-0/04-backend-architecture.md`).
  ArchUnit boundary tests planned Phase 2.
- PostgreSQL 16 (Testcontainers `postgres:16-alpine`), Flyway 10/11 (Boot-managed),
  Hibernate 6.6 via Boot, `ddl-auto=validate` always.
- springdoc-openapi 2.8.x, JJWT 0.12.x (HS256), MapStruct 1.6.x + Lombok 1.18.x with
  `lombok-mapstruct-binding`, Testcontainers 1.20.x (Boot-managed BOM).
- No H2 anywhere: integration tests run against real PostgreSQL via Testcontainers
  (approved Phase 0 decision).

## ADR-0002 · Cross-module communication

- Events: producers publish records defined in their own `api` package
  (e.g. `incidents.api.IncidentEvent`). Consumers (`timeline`, `audit`) import those
  `api` types only. Timeline reads are exposed to `incidents` through the
  `TimelineReader` port implemented by the `timeline` module — dependency stays
  one-directional (incidents.api ← timeline; incidents → timeline port).
- No shared-module event types: `shared` never imports feature modules.

## ADR-0003 · Authorization via policy services, not annotations

The Phase 0 matrix is encoded in two policy classes: `OrgAccessPolicy` (organizations,
services, audit) and `IncidentAccessPolicy` (incident-scoped rules incl. commander/
responder participation). App services call policies explicitly at use-case entry.
Rationale: participation rules need the loaded aggregate anyway; explicit calls are
trivially unit-testable and avoid reflection magic. The `@RequirePermission` annotation
idea from the design doc is superseded by this approach (recorded deviation, same guarantees).

## ADR-0004 · Tenancy

Shared schema, `organization_id` discriminator. Tenant repositories take `organizationId`
in their signatures; no global finders on tenant aggregates. Composite FKs
`(organization_id, id)` on `incidents`/`services` back the join-table integrity.
`TenantContext` is request-scoped, populated by `TenantContextInterceptor` from
`X-Organization-Id` + ACTIVE membership; foreign/unknown org → 404, missing header → 400.

## ADR-0005 · Seeding

Demo data is seeded by a `demo`-profile `CommandLineRunner`, never by Flyway migrations,
so managed/production databases cannot receive demo accounts by accident.

## ADR-0006 · Deferred by roadmap

Notifications, realtime (WebSocket/STOMP), and public status endpoints are Phase 2
(M9–M11) and are intentionally absent from Phase 1 code except the event seams
(same-tx + after-commit listener pattern) they will plug into.

## ADR-0007 · Phase 2/3 additions (2026-08-25)

- Realtime: STOMP endpoint `/ws` (raw + SockJS). The HTTP upgrade is public; authentication
  happens on the STOMP CONNECT frame (bearer JWT) and every SUBSCRIBE is re-authorized
  against ACTIVE membership. Broadcasts are AFTER_COMMIT only — rolled-back transactions
  never reach a socket. Payloads are invalidation signals; REST stays authoritative.
- Notifications: generated in-transaction from incident events (responder assigned,
  severity → CRITICAL, public update published); recipients exclude the actor.
- Public status: dedicated allowlisted DTOs assembled in `publicstatus.app` — internal
  comments, participants, audit data, and internal incident keys are structurally absent.
- Attachments: stored as PostgreSQL BYTEA, hard-capped at 5 MiB (DB CHECK + service guard).
- Maintenance windows: active windows override the derived service status to MAINTENANCE
  (worst-status-wins is preserved — a real outage during a window still shows MAJOR_OUTAGE).
- Rate limiting: fixed-window per-instance limiter on credential endpoints only; the
  distributed (Redis) limiter remains backlog for multi-instance deployments.
- AUTH_LOGIN_FAILED audit events remain deferred (pre-tenant context).
