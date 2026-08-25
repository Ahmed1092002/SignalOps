# SignalOps — Phase 0 · Product Scope & User Stories

Status: DRAFT — awaiting approval
Date: 2026-08-25

## 1. Problem Statement

When production breaks, incident response scatters across chat threads, sticky notes, and
status-page dashboards. SignalOps gives a team one place to run the incident: a controlled
lifecycle, clear command structure, an automatic timeline, customer-safe public updates,
and post-incident review data (duration, MTTR) — with every administrative and security
action recorded in an append-only audit log.

## 2. Personas

| Persona | Needs |
|---|---|
| **Ops Lead / Owner** | Govern the org: members, roles, services, audit visibility, analytics. |
| **Incident Commander** | Drive assigned incidents: status, severity, responders, public comms, resolution. |
| **Responder** | See and work their assigned incidents; post investigation findings. |
| **Stakeholder / Viewer** | Read-only visibility into incidents and timelines for reporting. |
| **Public Visitor** | Unauthenticated; see service health and published incident updates only. |

## 3. MVP Scope

### In scope (Phase 1–2)

- Email + password authentication, JWT access tokens, rotating refresh tokens.
- Multi-organization tenancy; one active-org context per client session.
- Memberships with five org roles; invitations via expiring single-use links.
- Service catalog per organization.
- Incident lifecycle (DETECTED → … → RESOLVED), severity, affected services,
  commander, responders.
- Automatic timeline per incident; internal comments; published public updates.
- Archive semantics for published incidents (no hard delete through the UI).
- Dashboard, incident list with server-side search/filter/sort/pagination, audit log view.
- Public status page (`/status/[orgSlug]`) with derived service statuses.
- WebSocket/STOMP live updates; in-app notifications.
- Flyway migrations, Swagger/OpenAPI, Docker Compose, unit + integration tests.

### Explicitly out of scope for MVP

Microservices, Kafka, Kubernetes, event sourcing, native mobile, AI root-cause analysis,
billing, email/Slack/webhook delivery (in-app only until Phase 3), attachments (Phase 3),
scheduled maintenance windows (Phase 3), follow-up tasks (Phase 3), SSO/SAML, rate
limiting (documented backlog).

## 4. Reference Scenario (walkthrough used to validate the model)

Payments start failing at ACME Corp:

1. On-call engineer (Responder) notices failed transactions, tells the IC-role lead.
2. Lead creates a **CRITICAL** incident on *Payments* — system **requires a commander**
   before creation succeeds (R1); lead assigns herself as commander and two responders.
   Status starts at `DETECTED`; she moves it to `INVESTIGATING`.
3. Responders post **internal** investigation updates ("error rate 40% on charge API").
4. IC publishes a **public update**: "We are investigating elevated payment failures."
   The Payments service now shows `MAJOR_OUTAGE` on the public page (derived from severity).
5. Root cause found (bad deploy) → `IDENTIFIED` → rollback deployed → `MONITORING`
   → error rate normal → `RESOLVED` with a mandatory resolution summary.
   Duration is frozen automatically; timeline holds the full story.
6. Two days later a follow-up task is created from the resolution notes (Phase 3).
7. Owner reviews MTTR and the audit log showing who edited what.

Every step above must be expressible with the rules in `02-domain-rules.md`.

## 5. MVP User Stories

Priority: P0 = Phase 1 core, P1 = Phase 1 polish, P2 = Phase 2.

### Epic: Authentication & Sessions

| ID | Story | Acceptance criteria |
|---|---|---|
| US-01 P0 | As a visitor I can register with email/password and create an organization; I become its Owner. | Org + Owner membership created atomically; duplicate email rejected case-insensitively; password never logged or returned. |
| US-02 P0 | As a user I can log in and receive an access token; my refresh token rides in an HttpOnly cookie. | Wrong credentials → generic `INVALID_CREDENTIALS`; access token ≤ 15 min TTL. |
| US-03 P0 | My session refreshes silently before expiry; logout revokes my refresh-token family. | Replayed rotated refresh token revokes the whole family (`REFRESH_REUSE_DETECTED`). |
| US-04 P1 | As a multi-org user I can switch the active organization. | Switching changes only the client context header; server re-validates membership per request. |

### Epic: Organization & Team

| ID | Story | Acceptance criteria |
|---|---|---|
| US-05 P0 | As Owner/Admin I can invite a member by email with a role. | Invite link contains a single-use hashed token valid 7 days; inviter sees pending invites. |
| US-06 P0 | As an invitee I can accept an invite via link (register or log in). | Membership becomes ACTIVE with invited role; token cannot be reused. |
| US-07 P0 | As Owner/Admin I can change a member's role or remove them. | Last Owner cannot be removed/downgraded (`CANNOT_REMOVE_LAST_OWNER`); role change writes an audit event. |
| US-08 P1 | As any member I can view the member directory (read-only). | Needed for assignment pickers; no security fields exposed. |

### Epic: Services

| ID | Story | Acceptance criteria |
|---|---|---|
| US-09 P0 | As Owner/Admin I can create/edit/archive a service. | Unique name+slug per org; archiving blocked while active incidents reference it (R18). |
| US-10 P1 | As a member I can browse services with current derived status and active-incident counts. | Derived status follows R10 worst-active rule. |

### Epic: Incidents

| ID | Story | Acceptance criteria |
|---|---|---|
| US-11 P0 | As Owner/Admin/IC I can create an incident (title, description, severity, ≥1 affected service, optional commander/responders). | CRITICAL without commander rejected (R1); invalid service refs rejected; creation appends `INCIDENT_CREATED` timeline event. |
| US-12 P0 | As authorized staff I can move status only along legal transitions. | Illegal move → 400/409 `INVALID_TRANSITION` naming from→to (R7). |
| US-13 P0 | As Owner/Admin/commanding IC I can change severity. | Setting CRITICAL enforces commander presence (R1); change is version-checked and timelines. |
| US-14 P0 | As Owner/Admin/commanding IC I can assign/remove commander and responders. | Assignees must be ACTIVE members of this org (R17); assignment timelines. |
| US-15 P0 | As a participating Responder I can post internal comments on my incidents. | Viewer or non-participant Responder → 403 (R3/R4). |
| US-16 P2 | As commanding IC I can publish a public update for the status page. | Stored immutably (R19); triggers `PUBLIC_UPDATE_PUBLISHED` timeline event + notification. |
| US-17 P0 | As authorized staff I can resolve with a summary. | Empty/blank summary → `MISSING_RESOLUTION_SUMMARY`; `resolvedAt` + duration set (R8). |
| US-18 P1 | As authorized staff I can reopen a resolved incident with a reason. | Only RESOLVED→INVESTIGATING allowed (R13); previous duration preserved in timeline. |
| US-19 P1 | As Owner/Admin I can archive an incident instead of deleting it. | No delete endpoint exists (R6); archived incidents leave dashboards/status but remain queryable. |
| US-20 P0 | As any member I can list/search/filter/sort/paginate incidents within my org. | Server-side filtering by status/severity/service/responder/date; cross-org results impossible (R11). |
| US-21 P0 | As any member I can read an incident's full detail: timeline, comments, public updates, participants. | Internal artifacts never appear in any public payload (R5/R14). |

### Epic: Oversight

| ID | Story | Acceptance criteria |
|---|---|---|
| US-22 P1 | As any member I see dashboard tiles: active count, critical count, this-month count, mean resolution, recent activity, service health. | Numbers computed from org-scoped queries only. |
| US-23 P1 | As Owner/Admin I can search/filter/paginate the audit log. | Read-only; no update/delete paths exist anywhere in the app (R15). |
| US-24 P2 | As a public visitor I can view `/status/[orgSlug]`: org name, per-service status, active public incidents with published updates, past incidents. | Zero internal fields in payloads; no auth required; unknown slug → 404. |

## 6. Portfolio Objectives (what this project must demonstrate)

1. Modular monolith with enforced module boundaries (later verified by ArchUnit tests).
2. A real domain: state machines, permission matrix, derived state, optimistic locking.
3. Security maturity: rotation + reuse detection, tenant isolation, structural separation
   of public vs internal data (not just "we filtered the JSON").
4. Real-time done correctly: events broadcast after commit; PostgreSQL stays source of truth.
5. Professional delivery: migrations, OpenAPI, Docker Compose, meaningful tests, honest README.

## 7. Non-Goals Reminder

See project brief §Non-Goals. Anything not listed as in-scope above needs explicit
justification before it enters the plan — the default answer is "later phase".
