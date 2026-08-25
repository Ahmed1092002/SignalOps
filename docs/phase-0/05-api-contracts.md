# SignalOps — Phase 0 · API Contracts

Status: DRAFT — awaiting approval

Base URL: `/api/v1`. JSON only. All timestamps ISO-8601 UTC (`2026-08-25T10:30:00Z`).
OpenAPI served at `/v3/api/docs`, UI at `/swagger-ui.html`.

## 1. Global Conventions

| Concern | Convention |
|---|---|
| Authentication | `Authorization: Bearer <access-jwt>` on all endpoints except `/auth/*`, `/public/*` |
| Organization context | `X-Organization-Id: <uuid>` header; server validates ACTIVE membership; invalid/foreign → `404 NOT_FOUND` (no existence leaks) |
| Resource naming | Plural nouns, kebab-free lowercase, sub-resources for actions that are stateful (`/transitions`, `/responders`) |
| Idempotency | Natural: PUT/PATCH idempotent by design; POSTs create distinct resources — client dedupe not needed for MVP |
| Versioning | Path major version only; additive changes don't bump |

### Pagination / Filtering / Sorting

Request: `?page=0&size=20&sort=createdAt,desc&q=text&status=INVESTIGATING&severity=CRITICAL&serviceId=&responderId=&createdFrom=&createdTo=`

Response envelope:

```json
{
  "items": [ ... ],
  "page": 0, "size": 20,
  "totalElements": 143, "totalPages": 8
}
```

Offset pagination for MVP; keyset documented as upgrade path.

## 2. Error Model

Every error uses one shape with a stable machine-readable `code`:

```json
{
  "code": "INVALID_INCIDENT_TRANSITION",
  "message": "Incident cannot move from RESOLVED to IDENTIFIED.",
  "timestamp": "2026-08-25T10:30:00Z",
  "path": "/api/v1/incidents/123/transitions",
  "details": []
}
```

Validation failures fill `details`: `[{"field":"title","code":"SIZE","message":"3–200 characters"}]`.
Conflict responses add context, e.g. `"details":[{"currentVersion":7}]`.

HTTP mapping: 400 validation/domain-guard · 401 unauthenticated/expired · 403 insufficient
permission · 404 missing or cross-tenant · 409 conflict (stale write, duplicate, illegal
state) · 500 opaque `INTERNAL_ERROR`.

Error code registry (grows as we build):

```
UNAUTHENTICATED, INVALID_CREDENTIALS, ACCESS_TOKEN_EXPIRED, REFRESH_REUSE_DETECTED,
FORBIDDEN, NOT_FOUND, VALIDATION_FAILED, DUPLICATE_EMAIL, DUPLICATE_SLUG,
INVITATION_INVALID_OR_EXPIRED, CANNOT_REMOVE_LAST_OWNER,
INVALID_INCIDENT_TRANSITION, MISSING_RESOLUTION_SUMMARY, REOPEN_REASON_REQUIRED,
CRITICAL_REQUIRES_COMMANDER, STALE_WRITE, ARCHIVED_INCIDENT_READONLY,
INCIDENT_REQUIRES_SERVICE, MEMBER_NOT_ACTIVE,
SERVICE_HAS_ACTIVE_INCIDENTS, INTERNAL_ERROR   (+RATE_LIMITED reserved)
```

## 3. Endpoint Catalog

Roles column = minimum capability (see permission matrix in security doc);
"C" marks incident-scoped conditions (commander/participation rules apply).

### Auth

| Method | Path | Roles | Notes |
|---|---|---|---|
| POST | `/auth/register` | – | body `{email,password,fullName,organizationName}` → creates org+Owner membership, returns session |
| POST | `/auth/login` | – | → `{accessToken, user}` + refresh cookie |
| POST | `/auth/refresh` | cookie | rotates refresh token; reuse → family revoked 401 |
| POST | `/auth/logout` | any | revokes family, clears cookie |
| GET | `/auth/me` | any | profile + memberships (org list for switcher) |
| PATCH | `/users/me` | any | name change; password change requires current password |

### Organizations & Members

| Method | Path | Roles |
|---|---|---|
| GET | `/organizations/mine` | any |
| GET | `/organizations/current` | member |
| PATCH | `/organizations/current` | OWNER/ADMIN (audit) |
| GET | `/organizations/current/members` | member (read-only directory) |
| PATCH | `/members/{memberId}` | OWNER/ADMIN (role/status; audit) |
| DELETE | `/members/{memberId}` | OWNER/ADMIN (audit; last-owner guard) |
| POST | `/invitations` | OWNER/ADMIN (returns invite URL) |
| GET | `/invitations?status=pending` | OWNER/ADMIN |
| DELETE | `/invitations/{id}` | OWNER/ADMIN (revoke) |
| POST | `/invitations/accept` | – | body `{token}` |

### Services

| Method | Path | Roles |
|---|---|---|
| GET | `/services?includeArchived=` | member |
| POST | `/services` | OWNER/ADMIN |
| GET | `/services/{id}` | member |
| PATCH | `/services/{id}` | OWNER/ADMIN (audit) |
| POST | `/services/{id}/archive` | OWNER/ADMIN (R18 guard; audit) |
| GET | `/services/{id}/incidents` | member |

### Incidents

| Method | Path | Roles | Notes |
|---|---|---|---|
| GET | `/incidents` (filters/sort/pagination §1) | member | archived excluded unless `archived=true` |
| POST | `/incidents` | O/A/IC | body includes `expectedVersion`-free create; R1/R16 enforced |
| GET | `/incidents/{id}` | member | full internal detail incl. timeline refs |
| PATCH | `/incidents/{id}` | O/A + IC(C) | title/description; body carries `version`; audits INCIDENT_EDITED |
| POST | `/incidents/{id}/transitions` | O/A + IC(C) | `{to, resolutionSummary?, reason?, version}` → R2/R7/R13 |
| PUT | `/incidents/{id}/severity` | O/A + IC(C) | `{severity, version}` → R1 guard when CRITICAL |
| PUT | `/incidents/{id}/commander` | O/A + IC(C, claim) | `{userId|null, version}` → R1/R17 |
| POST | `/incidents/{id}/responders` | O/A + IC(C) | `{userId, version}` |
| DELETE | `/incidents/{id}/responders/{userId}` | O/A + IC(C) | commander-removal blocked while CRITICAL (R1) |
| POST | `/incidents/{id}/comments` | O/A + IC(C) + RESPONDER(participant) | internal |
| GET | `/incidents/{id}/comments` | member | internal |
| POST | `/incidents/{id}/public-updates` | O/A + IC(C) | publish; immutable (R19); Phase 2 surfaces on status page |
| GET | `/incidents/{id}/public-updates` | member | |
| GET | `/incidents/{id}/timeline?page&size` | member | paginated, newest first |
| POST | `/incidents/{id}/archive` | O/A + IC(C) | R6 |

All incident mutations return the updated representation **including the new `version`** —
the client stores it for its next conditional write (this is how R12 stays usable).

### Dashboard / Audit

| Method | Path | Roles |
|---|---|---|
| GET | `/dashboard/summary` | member | active/critical/monthly counts, mean-resolution, recent activity, service health |
| GET | `/audit-events?action=&actorId=&entityType=&from=&to&page` | O/A | read-only |

### Notifications (Phase 2)

`GET /notifications` · `POST /notifications/{id}/read` · `POST /notifications/read-all`

### Public (no auth, no org header)

| Method | Path | Notes |
|---|---|---|
| GET | `/public/status/{orgSlug}` | org display name + public services w/ derived statuses (R10) + active public incidents |
| GET | `/public/status/{orgSlug}/incidents?page` | past incidents (published ones), each with published updates only |
| GET | `/public/incidents/{id}` | only if publicly visible & slug matches; payload allowlist: title, status, severity, timestamps, public updates. Internal fields physically absent |

Public payloads come from dedicated response DTOs mapped from public-only tables — there
is no code path where an internal comment reaches these serializers (structural rule R5).

## 4. Example Flows

Create critical incident without commander:

```http
POST /api/v1/incidents
{ "title":"Payments failing", "description":"Charge API 500s", "severity":"CRITICAL",
  "serviceIds":["…"] }
→ 400 { "code":"CRITICAL_REQUIRES_COMMANDER", "message":"A Critical incident requires an Incident Commander.", ... }
```

Resolve:

```http
POST /api/v1/incidents/{id}/transitions
{ "to":"RESOLVED", "resolutionSummary":"Rolled back deploy #482; error rate normal.", "version":7 }
→ 200 { …incident with status RESOLVED, resolvedAt, durationSeconds, version:8 }
```

Stale concurrent write:

```http
PUT /api/v1/incidents/{id}/severity
{ "severity":"HIGH", "version":6 }        // someone else already moved it to 7
→ 409 { "code":"STALE_WRITE", "message":"This incident was modified by someone else.",
        "details":[{"currentVersion":7}] }
```

## 5. WebSocket Contract (Phase 2, fixed now)

Transport: STOMP over `/ws` (SockJS fallback allowed). CONNECT carries `Authorization:
Bearer <jwt>`; server validates and attaches identity to the session.

Authorized subscriptions (server enforces on SUBSCRIBE frame):

| Destination | Audience | Payload class |
|---|---|---|
| `/topic/org.{orgId}.incidents` | members of org | full internal event payloads |
| `/topic/org.{orgId}.services` | members of org | service status changes |
| `/user/queue/notifications` | self | notification objects |
| `/topic/public.{orgSlug}` | anonymous | **public-safe events only** (separate mapper) |

Envelope: `{eventId, type, orgId, entityType, entityId, occurredAt, data}`.
Types mirror timeline event types minus anything sensitive. Publishing happens in the
after-commit bridge only (backend doc §4). WebSocket is a push optimization — REST +
PostgreSQL remain source of truth; clients reconcile via refetch after reconnect.
