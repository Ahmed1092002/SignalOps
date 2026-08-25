# SignalOps Frontend

Next.js 16 (App Router) · TypeScript strict · Tailwind CSS v4 · shadcn/ui · TanStack Query ·
React Hook Form + Zod · Zustand (session/UI state only) · next-themes (dark default).

## Run

```bash
# backend must be running (see ../docker/docker-compose.yml, port 8080)
cp .env.example .env.local      # defaults point at http://localhost:8080/api/v1
npm install
npm run dev                     # http://localhost:3000
```

Demo accounts (backend `demo` profile, password `Demo1234!`):
`owner@demo.signalops.io`, `admin@demo.signalops.io`, `commander@demo.signalops.io`,
`responder@demo.signalops.io`, `viewer@demo.signalops.io`.

## Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080/api/v1` | Backend API base |
| `NEXT_PUBLIC_WS_URL` | unset | WebSocket URL — Phase 2 only; leave unset until the backend ships `/ws` |

No secrets belong in the frontend; everything `NEXT_PUBLIC_*` is public by design.

## Security model

- Access token: **in-memory only** (Zustand, not persisted). Lost on refresh → silent
  cookie-based re-auth makes this invisible.
- Refresh token: **HttpOnly cookie owned by the backend** — JavaScript never reads it.
  Refresh/logout requests send `credentials: "include"` + `X-Requested-With: XMLHttpRequest`.
- Only the selected **organization ID** is persisted (convenience), validated against
  `/auth/me` memberships on every bootstrap.
- 401 handling: single-flight refresh → exactly one retry of the original request →
  on failure, session cleared and the user lands on `/login`.
- Every tenant-scoped request carries `X-Organization-Id`; query caches are keyed by
  organization ID, so switching organizations cannot leak data.

## Scripts

```bash
npm run dev         # dev server
npm run build       # production build
npm run start       # serve production build
npm run lint        # eslint
npm run typecheck   # tsc --noEmit
npm test            # vitest (domain rules, http client, form schemas)
```

## Phase status

- **Phase 1 (complete):** auth + session restore, organization switcher, role-aware UI,
  services CRUD/archive, incident lifecycle (legal transitions only, resolve summary,
  reopen reason), severity changes, participants, internal comments, public updates,
  timeline, archiving, dashboard summary, audit log, stale-write conflict dialog.
- **Phase 2 (deferred — backend endpoints not yet implemented):** WebSocket/STOMP live
  updates, in-app notifications, public status page. The UI shows honest placeholders
  (e.g. "REST" connection indicator) instead of simulating these.
- **Phase 3:** analytics charts (placeholder card on the dashboard), follow-up tasks,
  attachments.
