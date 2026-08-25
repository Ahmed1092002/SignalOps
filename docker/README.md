# docker/

Docker Compose configuration for SignalOps local development and single-host deployment.

## Contents

- `docker-compose.yml` — PostgreSQL 16 + backend service
- `.env.example` — every supported variable with safe defaults

## Usage

```bash
cp .env.example .env
# REQUIRED: set JWT_SECRET (base64 of >= 32 random bytes): openssl rand -base64 48
docker compose up -d postgres          # database only
docker compose up --build              # full stack (postgres + backend)
```

## Services

| Service | Port | Notes |
|---|---|---|
| `postgres` | `DB_PORT` (default 5432) | Data persists in the `postgres-data` volume; health-checked |
| `backend` | `BACKEND_PORT` (default 8080) | Built from `../backend/Dockerfile`; waits for postgres health |

> **Port conflict tip:** if a local PostgreSQL already owns 5432, set `DB_PORT=5433`
> in `.env` and point the backend's `DB_URL` at `jdbc:postgresql://localhost:5433/signalops`.

## Environment

All variables and defaults are documented in `.env.example`. Highlights:

- `JWT_SECRET` — **required for the backend service**; base64 of ≥ 32 random bytes
- `SEED_DEMO=true` + `SPRING_PROFILES_ACTIVE=local,demo` — seeds demo accounts/data
  (never enable against a real database)
- `CORS_ALLOWED_ORIGINS` — exact frontend origins, comma-separated (no wildcards)
- `COOKIE_SECURE=true` — set when serving over HTTPS

The frontend is intentionally **not** part of the compose file (it deploys to Vercel in the
target topology); run it with `npm run dev` from `../frontend`.
