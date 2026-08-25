# SignalOps — Phase 0 · Backend Architecture

Status: DRAFT — awaiting approval

## 1. Shape

**Modular monolith**, single Maven module, Java 21, Spring Boot 3.x, PostgreSQL 16.
Module boundaries are package boundaries now; Maven multi-module (or ArchUnit-enforced
build isolation) is an evolution path, not a starting requirement. We *will* add ArchUnit
tests in Phase 2 so boundaries stop being convention and become tests.

## 2. Modules & Dependency Direction

```
                 ┌────────────────────────────────────────────┐
                 │                  shared                    │
                 │  api(error model,pagination) security(kernel)│
                 │  domain(BaseEntity,DomainEvent,Clock) config │
                 └────────────────────────────────────────────┘
                        ▲            ▲              ▲
   auth ────► users ◄── organizations      incidents ───► servicecatalog(api)
     │                          │             │   ▲              ▲
     └─────────► realtime ◄─────┴─────────────┘   └── timeline ◄──┘
                     ▲    notifications ◄─────────┘        audit ◄── (all mutators)
```

Hard rules:

1. Everything may depend on `shared`. Nothing in `shared` depends on a feature module.
2. Within a module: `web → app → domain`. Controllers never touch repositories or entities.
3. Cross-module calls happen only through the target module's `api` package interfaces
   (e.g., `incidents.app` depends on `servicecatalog.api.ServiceCatalogApi`), or through
   domain events. Never import another module's `domain` or `web`.
4. `realtime`, `notifications`, `audit` are **event consumers** — they never get called
   synchronously from producers' happy paths (except thin api checks like "notify targets").
5. Dependency direction is one-way; cycles fail the build once ArchUnit lands.

## 3. Package Template

```
com.signalops
├── SignalOpsApplication.java
├── shared/
│   ├── api/          ErrorResponse, ErrorCodes, GlobalExceptionHandler, PageResponse
│   ├── security/     CurrentUser, TenantContext, Permission, @RequirePermission, evaluator
│   ├── domain/       BaseEntity, DomainEvent, DomainException hierarchy
│   └── config/       OpenAPI, Jackson(UTC), CORS properties, Clock bean
├── auth/         web/ app/ domain/   (login, register, refresh rotation, logout)
├── users/        web/ app/ domain/
├── organizations/web/ app/ domain/   (orgs, memberships, invites, TenantGuard)
├── servicecatalog/ web/ app/ api/ domain/
├── incidents/    web/ app/ domain/   (largest module; state machine, policies)
├── timeline/     app/ domain/        (projector: consumes IncidentEvent → timeline rows)
├── notifications/app/ domain/        (Phase 2)
├── audit/        app/ domain/        (AuditRecorder used by mutating services)
├── analytics/    web/ app/           (read-only SQL projections; Phase 3 full)
└── realtime/     ws/ eventbus/       (STOMP config, after-commit bridge; Phase 2)
```

No generic `util` packages. Helpers live beside the concept they serve.

## 4. The Event Pipeline (heart of the design)

One mechanism powers timeline, audit, notifications, and realtime — and guarantees
"no event without a committed transaction":

```
Command (HTTP)                                    SAME TX
   │
IncidentAppService.assignResponder(...)
   ├─ policy check (IncidentAuthorizationService)
   ├─ aggregate method Incident.addResponder(user)   ← business rule lives here
   ├─ repository.save(incident)
   └─ publisher.publishEvent(IncidentEvent.RESPONDER_ASSIGNED …)
                       │
        ┌──────────────┴───────────────────────────┐
        │ @EventListener (default, IN transaction) │ @TransactionalEventListener(AFTER_COMMIT)
        ▼                                          ▼
 TimelineProjector → INSERT timeline_events     RealtimeBridge → STOMP broadcast
 NotificationGenerator → INSERT notifications  (only if tx actually committed)
 AuditRecorder (for admin/security actions)
```

- Same-tx listeners keep timeline atomic with the mutation (R9) — rollback removes both.
- After-commit listener makes WebSocket delivery impossible for failed transactions and
  satisfies the test "WebSocket events published only after successful transaction".
- This shape later upgrades to a transactional outbox + worker without changing producers —
  which is why we don't need Kafka now.

## 5. Authorization Design

Three layers, each with a single job:

1. **Authentication filter**: JWT → `CurrentUser(userId)`. Token contains identity only
   (see security doc for why roles/orgs are NOT claims).
2. **Tenant resolution**: per request, `X-Organization-Id` header → membership lookup →
   `TenantContext(orgId, role, userId)`. Missing/foreign org → 404 (not 403: avoids
   existence leaks). Membership result cached ~30s per request-scope to keep it cheap.
3. **Permission checks**: annotations declare intent (`@RequirePermission(IncidentPermissions.COMMENT)`),
   but every incident-scoped decision funnels through ONE class —
   `IncidentAuthorizationService` — implementing the permission matrix + participation
   rules (commander/responders). No scattered role strings in services.

Repository methods on tenant entities take `organizationId` as a parameter by signature —
there is no `findById(UUID)` escape hatch for tenant-owned aggregates exposed to services.

## 6. Error Handling

- `@RestControllerAdvice` maps exceptions → the standard envelope (see API doc):
  domain errors carry stable codes (`INVALID_TRANSITION`, `MISSING_RESOLUTION_SUMMARY`,
  `CRITICAL_REQUIRES_COMMANDER`, `STALE_WRITE`, …).
- `OptimisticLockingFailureException` → 409 + current version.
- `MethodArgumentNotValidException` → 400 with field details.
- Anything unexpected → 500 `INTERNAL_ERROR`, logged server-side, stack traces never leave.
- `DomainException(code, message, httpStatus)` hierarchy thrown by aggregates/policies.

## 7. Persistence & Migrations

- Spring Data JPA + Flyway. Migration files reviewed like code; additive changes preferred;
  enum changes = drop/recreate CHECK constraint in a new migration.
- Demo seed data runs via a `demo` Spring profile `CommandLineRunner` (NOT a Flyway
  migration), so production databases never receive demo accounts by accident.
- Hibernate: `ddl-auto=validate` always — schema truth lives in Flyway.
- N+1 control: detail endpoints fetch collections with explicit joins/batch size;
  list endpoints never lazy-load associations (projections instead).

## 8. API & Docs

- springdoc-openapi UI at `/swagger-ui.html`, spec at `/v3/api/docs`; annotated controllers,
  DTOs validated with Bean Validation so the schema stays honest.
- REST conventions centralized in the API contract doc (`05-api-contracts.md`).

## 9. Testing Strategy

| Tier | Tooling | Covers |
|---|---|---|
| Unit | JUnit 5 + Mockito, no Spring | State machine, severity/status calculators, policies, duration math, token rotation logic |
| Application | Mockito, mocked repositories | App-service orchestration, event publishing, authorization branches |
| Integration | `@SpringBootTest` + Testcontainers PostgreSQL + MockMvc | HTTP contracts, error envelope, tenant isolation, optimistic-lock 409, R1–R23 behaviors, WS-after-commit |
| Boundary | ArchUnit (Phase 2) | Module dependency rules |

Decision (approved in Phase 0 sign-off): adopt **Testcontainers from milestone M1**, not
"later". Docker is already required for Compose; H2 diverges from PostgreSQL (JSONB,
partial indexes, composite FK tricks) and would make our integration tests lie to us.
Cost: first build needs Docker running. Benefit: migrations and PG-specific behavior are
verified continuously from day one.

Coverage expectations (measured, not decorative): domain packages ≥ 90%, overall ≥ 70%.
We do not chase coverage theater — every listed rule R# gets a named test.

## 10. Configuration & Profiles

- Env vars (12-factor): `DB_URL/DB_USER/DB_PASSWORD`, `JWT_SECRET` (≥256-bit),
  `ACCESS_TTL`, `REFRESH_TTL`, `CORS_ALLOWED_ORIGINS`, `SPRING_PROFILES_ACTIVE`.
- Profiles: `local` (compose defaults), `demo` (seed runner), `prod` (strict: validate
  schema, no swagger unless enabled, secure cookie flags on).
- Secrets never committed; `.env.example` documents every variable.

## 11. Deliberate Simplifications (and their exit paths)

| Choice now | Upgrade path |
|---|---|
| In-memory STOMP broker | Redis broker relay if we scale past one instance |
| Synchronous in-JVM events | Transactional outbox (+ worker) before any queue tech |
| Offset pagination | Keyset pagination for hot feeds if needed |
| BCrypt(12) | Argon2id re-hash-on-login migration |
| Header-based org context | Subdomain-per-org or path-prefix without touching domain code |
