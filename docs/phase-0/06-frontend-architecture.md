# SignalOps — Phase 0 · Frontend Architecture

Status: DRAFT — awaiting approval

Next.js (App Router) + TypeScript + Tailwind CSS + shadcn/ui + TanStack Query +
React Hook Form + Zod + Zustand (UI state only) + Recharts + STOMP client.

## 1. Rendering Strategy (important decision)

The authenticated app renders **client-side** (TanStack Query drives data); server
components handle layout/shell/static text. The public status page is **fully SSR/ISR**
(no auth needed → cacheable at the edge).

Why: our access token lives in browser memory (security decision in `07-security.md`),
so Next.js *server* fetches cannot authenticate. A BFF/proxy layer would fix that but adds
a second auth surface for little gain in a dashboard app. This split gives us security
(memory tokens) + fast public pages (ISR) with no token gymnastics. Trade-off accepted:
first contentful paint inside the app relies on skeletons — standard for ops tools.

## 2. Route Tree

```
app/
├── (auth)/                      minimal layout, redirects if session valid
│   ├── login/page.tsx
│   ├── register/page.tsx
│   └── accept-invite/page.tsx           ?token=
├── (app)/                       AppShell layout: sidebar+topbar, guarded by middleware
│   ├── dashboard/page.tsx
│   ├── incidents/page.tsx               list: filters, sort, pagination (URL search params)
│   ├── incidents/[id]/page.tsx          detail: tabs Overview | Timeline | Updates | Tasks(P3)
│   ├── services/page.tsx
│   ├── team/page.tsx                    members, invites, roles
│   ├── audit/page.tsx
│   ├── analytics/page.tsx               Phase 3
│   └── settings/(profile|organization)/page.tsx
└── (public)/status/[orgSlug]/page.tsx    marketing-clean layout, ISR revalidate=30
middleware.ts                               route protection + org-context bootstrap redirect
```

Deep links keep filter/sort/pagination state in the URL (shareable incident queues).

## 3. State Allocation

| State | Owner | Notes |
|---|---|---|
| Server data (incidents, members, services…) | TanStack Query | single cache, keys below |
| Session (userId, memberships, activeOrgId) | Zustand `sessionStore` (persist activeOrgId) | access token held in memory variable, never persisted |
| UI (theme, sidebar collapsed, command palette) | Zustand `uiStore` | |
| Forms | React Hook Form + zodResolver | schemas shared per feature folder |
| Realtime connection | custom hook over STOMP | feeds Query cache, owns reconnect/backoff |

Query key factory (single source, typed):

```ts
qk.incidents.list(filters)      qk.incident.detail(id)     qk.incident.timeline(id,{page})
qk.services.list()              qk.members.list()          qk.audit.list(filters)
qk.dashboard.summary()          qk.notifications.list()    qk.public.status(slug)
```

## 4. Realtime → Cache Strategy (decision matrix)

Rule of thumb: **patch detail views (fast, local truth), invalidate collections and
aggregates (cheap correctness)**. Always refetch everything relevant after reconnect.

| WS event | Action | Why |
|---|---|---|
| comment added | prepend into `incident.detail(id)` cache | complete payload arrives; avoids refetch flicker |
| status/severity changed | patch detail + invalidate `incidents.list`, `dashboard.summary` | lists/aggregates depend on them |
| responder/commander changed | patch participants section | contained change |
| incident created/archived | invalidate `incidents.list` + dashboard | collection membership changed |
| public update published | invalidate `public.status(slug)` + detail updates tab | two surfaces |
| service status changed | patch `services.list` entry + dashboard health tile | |
| notification | increment unread badge via notifications query patch | |

Conflict UX (409 `STALE_WRITE`): modal offers "Load latest" (refetch, discard local edits)
or "Keep my text" (copies edit into clipboard/form for manual reapply). No silent retries.

Session expiry: api-client intercepts 401 → single-flight refresh (queued while in flight)
→ replay original request; if refresh fails → clear session, redirect to login preserving
return path.

## 5. API Client

Thin `fetch` wrapper: injects `Authorization` from memory store + `X-Organization-Id`
from activeOrg; parses error envelope into typed `ApiError {code,message,details}`;
Zod-parses success bodies for contract drift detection in dev. No axios dependency needed.

## 6. Feature Folders

```
features/
├── auth/        api.ts hooks.ts schemas.ts components/
├── incidents/   list/ detail/ shared(timeline,badges,status-select,conflict-dialog)
├── services/    ├── members/ ├── audit/ ├── dashboard/
├── notifications/ (P2)  └── status-page/ (P2)
components/ui/          shadcn primitives (button, dialog, table, toast, skeleton…)
components/app/         PageHeader, EmptyState, ErrorState, DataTable, SeverityBadge…
lib/                    api-client, query-keys, ws-client, date(UTC helpers), utils
stores/                 session-store, ui-store
```

## 7. Design System & Quality Bars

- shadcn/ui + Tailwind tokens; dark/light via `next-themes` (class strategy), system default.
- Every data surface implements four states explicitly: loading (skeletons), empty
  (actionable hint), error (retry), populated.
- Responsive: sidebar collapses ≤ lg; incident detail stacks timeline/comments on mobile;
  tables become cards ≤ md.
- Accessibility floor: labeled inputs, focus-visible rings, dialog focus traps, AA contrast
  both themes, keyboard-navigable tables.
- Charts (Recharts): MTTR trend, severity distribution, per-service counts — Phase 3,
  wrapped in `<ChartCard>` with empty states.

## 8. Environment

`NEXT_PUBLIC_API_URL`, `NEXT_PUBLIC_WS_URL`. No secrets in the frontend bundle — ever;
anything secret is backend-only by construction.
