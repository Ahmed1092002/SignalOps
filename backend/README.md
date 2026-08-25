# SignalOps Backend

Spring Boot 3.5 (Java 21) modular monolith implementing the Phase 0 contracts in
`../docs/phase-0/`. PostgreSQL 16, Flyway, JWT access tokens (15 min), rotating opaque
refresh tokens with reuse detection, Testcontainers integration tests.

## Quick start (Docker Compose)

```bash
# from the repository root
cd docker
cp .env.example .env
# REQUIRED: set JWT_SECRET (base64 of >=32 random bytes):  openssl rand -base64 48
docker compose up --build
```

- API: http://localhost:8080/api/v1
- Swagger UI: http://localhost:8080/swagger-ui.html
- Demo accounts (seeded with `SEED_DEMO=true`, password `Demo1234!`):
  `owner@demo.signalops.io`, `admin@demo.signalops.io`, `commander@demo.signalops.io`,
  `responder@demo.signalops.io`, `viewer@demo.signalops.io`

## Local development (without Docker for the app)

```bash
# 1. PostgreSQL via compose (or any PostgreSQL 16)
docker compose -f ../docker/docker-compose.yml up -d postgres

# 2. Run the app
JAVA_HOME=<jdk-21> mvn spring-boot:run -Dspring-boot.run.profiles=local,demo
```

## Environment variables

| Variable | Required | Default | Notes |
|---|---|---|---|
| `DB_URL` | no | `jdbc:postgresql://localhost:5432/signalops` | |
| `DB_USER` / `DB_PASSWORD` | no | `signalops` / `signalops` | |
| `JWT_SECRET` | **yes** | – | Base64 of ≥32 random bytes (256-bit HS256) |
| `ACCESS_TTL` | no | `PT15M` | ISO-8601 duration |
| `REFRESH_TTL` | no | `P7D` | Refresh cookie lifetime |
| `COOKIE_SECURE` | no | `false` | Set `true` behind HTTPS |
| `CORS_ALLOWED_ORIGINS` | no | `http://localhost:3000` | Exact origins, comma-separated; no wildcards |
| `SEED_DEMO` | no | `false` | Demo seed (never enable on real data) |

## Modules

`shared` (kernel) · `auth` · `users` · `organizations` · `servicecatalog` ·
`incidents` · `timeline` · `audit` · `analytics` · `seed`.
Cross-module calls go through `api` packages or domain events; timeline/audit are written
in the mutation's transaction (R9/R15). See `../docs/adr/0001-backend-decisions.md`.

## Tests

```bash
mvn test          # unit + Testcontainers integration tests (Docker required)
```

Integration tests run against a real PostgreSQL 16 via Testcontainers — no H2 anywhere.
Rule IDs (R1–R23) from `docs/phase-0/02-domain-rules.md` each have named tests.

## Known limitations (Phase 1)

- No rate limiting, no CSP/HSTS yet (documented backlog).
- `AUTH_LOGIN_FAILED` audit events are not recorded (pre-tenant; deferred).
- Realtime (WebSocket/STOMP), notifications, and public status endpoints are Phase 2 (M9–M11).
