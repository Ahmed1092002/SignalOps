# SignalOps — Phase 0 · Domain Rules, State Machine, Severity, Derived Status

Status: DRAFT — awaiting approval

This is the contract the backend implements and the tests assert. Rule IDs (R#) are
referenced from code comments, error codes, and test names.

---

## 1. Incident Lifecycle

States: `DETECTED`, `INVESTIGATING`, `IDENTIFIED`, `MONITORING`, `RESOLVED`.

State meanings:

| State | Meaning | Status-page effect |
|---|---|---|
| DETECTED | Reported/observed, not yet being worked. | Active (degrades service status) |
| INVESTIGATING | Actively investigating; includes reopened incidents. | Active |
| IDENTIFIED | Root cause known, fix in progress. | Active |
| MONITORING | Fix applied, watching for regression. | Active |
| RESOLVED | Closed with a resolution summary. Terminal until reopened. | Inactive (service recovers unless other incidents) |

### Allowed transitions (exact, centrally defined)

| From | To | Guard | Side effects |
|---|---|---|---|
| DETECTED | INVESTIGATING | actor may transition (permission matrix) | set `acknowledgedAt` if null |
| INVESTIGATING | IDENTIFIED | permission | — |
| IDENTIFIED | MONITORING | permission | — |
| MONITORING | RESOLVED | permission + **non-blank resolution summary** (R2) | set `resolvedAt`, freeze `durationSeconds`, store resolver |
| MONITORING | INVESTIGATING | permission + reason | — |
| RESOLVED | INVESTIGATING | permission + reason (**the only reopen path**, R13) | clear `resolvedAt`/`durationSeconds` (history lives in timeline) |
| *(everything else)* | *(—)* | rejected with `INVALID_TRANSITION` | — |

Notes:

- Transitions are executed only via `Incident.applyStatus(newStatus, actor, ctx)` inside
  the aggregate — no direct field writes from services.
- Archived incidents reject all transitions (`ARCHIVED_INCIDENT_READONLY`).
- Every successful transition appends exactly one timeline event in the same transaction (R9).
- **Flagged ambiguity:** the brief's transition list omits `INVESTIGATING → MONITORING`.
  We implement the list exactly as specified for v1 (faithful to spec, easy to extend),
  and revisit if it feels artificial during testing.

Severity is **not** part of the state machine — it changes freely (with permission +
version check), except for the CRITICAL guard below.

---

## 2. Severity Levels

| Level | Definition | Expected handling | Status-page mapping (active incident) |
|---|---|---|---|
| LOW | Minor issue, negligible user impact, workaround exists. | Business hours. | none (OPERATIONAL) |
| MEDIUM | Noticeable degradation of non-critical function, or limited user subset affected. | Same-day response. | DEGRADED_PERFORMANCE |
| HIGH | Major functionality degraded/unavailable for many users; revenue or SLA risk. | Immediate response. | PARTIAL_OUTAGE |
| CRITICAL | Critical service fully down, data-loss or security event. All hands. | Immediate, commander mandatory (R1). | MAJOR_OUTAGE |

Why LOW maps to no visible degradation: status pages exist for customers; LOW noise would
train visitors to ignore them. This mapping is a documented v1 policy, centralized in one
class so we can later make it per-organization configurable.

---

## 3. Derived Service Status (R10)

Service status is **never stored** — always derived. Algorithm:

```
visibleStatus(service) =
  max_by_rank( rank( map(severity(s)) ) for s in activeIncidentsAffecting(service) )
where activeIncident.status != RESOLVED and service.archivedAt is null
```

Rank order (worst wins): `MAJOR_OUTAGE > PARTIAL_OUTAGE > DEGRADED_PERFORMANCE >
MAINTENANCE > OPERATIONAL`. Ties are the same value by construction.

Example: Payments has one MEDIUM + one CRITICAL active incident ⇒ `MAJOR_OUTAGE`.
When both resolve ⇒ back to `OPERATIONAL`. Maintenance windows (Phase 3) will override
to MAINTENANCE only while scheduled.

Implementation: pure function `ServiceStatusCalculator` (unit-tested) fed by one indexed
query (integration-tested).

---

## 4. Business Rules Catalog

Enforcement layers: **DB** = constraint/index, **POLICY** = domain/policy class,
**SVC** = application-service validation. Each rule lists its owning tests.

| ID | Rule | Enforced by |
|---|---|---|
| R1 | A CRITICAL incident must have a commander — on create, on severity→CRITICAL, and commander removal is blocked while CRITICAL. | POLICY+SVC; unit+integration |
| R2 | Cannot transition to RESOLVED without non-blank resolution summary. | POLICY; unit |
| R3 | VIEWER can read but never mutate operational data. | POLICY; integration |
| R4 | RESPONDER may comment/work only on incidents where they are an assigned responder (or commander). | POLICY; integration |
| R5 | Internal comments never appear on the public surface. | Structural (separate tables + separate public endpoints, see below); integration |
| R6 | Published incidents have no delete path; archive only. | Absence of DELETE endpoint + repository method; code review + ArchUnit later |
| R7 | Status changes follow the central transition table. | POLICY; unit |
| R8 | Duration auto-computed: `resolvedAt − createdAt`, frozen on resolve, cleared on reopen (history kept in timeline). | DOMAIN; unit |
| R9 | Every important mutation produces a timeline event **atomically** (same transaction). | Event projector in-tx; integration asserts row exists after each mutation |
| R10 | Public service status = worst active incident (§3). | Pure function; unit+integration |
| R11 | Strict tenant isolation: every query scoped by resolved `organizationId`; cross-org access returns 404. | Repository signatures require orgId; TenantGuard; integration |
| R12 | Concurrent mutation on stale `version` → HTTP 409 `STALE_WRITE` with current version. | JPA `@Version` + exception mapping; integration |
| R13 | Reopen only via RESOLVED → INVESTIGATING, requires reason. | POLICY; unit |
| R14 | Public updates and internal comments are different models (separate tables), not a visibility flag on one table. | Schema design; makes R5 leak-proof by construction |
| R15 | Audit events append-only from the application: repositories expose insert/read only. | Repo surface; later DB grants revoke UPDATE/DELETE |
| R16 | An incident must affect ≥ 1 existing service of the same org. | SVC + composite FKs (see data model) |
| R17 | Commander/responders must be ACTIVE members of the same org. | POLICY; integration |
| R18 | Cannot archive a service that has active incidents. | SVC → 409 `SERVICE_HAS_ACTIVE_INCIDENTS` |
| R19 | Published public updates are immutable; corrections are new updates. | No update endpoint; append-only repo |
| R20 | User emails unique case-insensitively. | Functional unique index |
| R21 | Invitation tokens: single-use, stored hashed, expire after 7 days. | SVC; integration |
| R22 | Refresh-token reuse (rotated token replayed) revokes the whole token family. | Auth service; integration |
| R23 | Archived incidents excluded from dashboards/derived status; still searchable via `archived=true` filter. | Query defaults |

Rules added beyond the brief (R16–R23) exist because they close real integrity gaps:
composite FKs make tenant-mismatched references unrepresentable (R16), immutable customer
communications preserve trust and legal trail (R19), and reuse detection turns a leaked
refresh token into a contained incident rather than a silent account takeover (R22).

---

## 5. Concurrency Policy

- `incidents.version BIGINT` with JPA `@Version`. **All** incident mutations (PATCH,
  transitions, severity, participants, archive) carry `expectedVersion` in the request.
- Stale write → Spring's `ObjectOptimisticLockingFailureException` mapped to
  `409 STALE_WRITE { details: { currentVersion } }`.
- Append-only children (comments, timeline, audit, public updates) need no version — they
  don't lose updates by design.
- Frontend shows a conflict dialog: "Someone updated this incident" with Reload /
  copy-my-text actions. Never silently retries.

## 6. Time Policy

- Java `Instant`, PostgreSQL `timestamptz`, Jackson emits ISO-8601 UTC (`…Z`) only.
- Clock injected (`Clock` bean) so duration/MTTR logic is deterministically testable.
- Display timezone is purely a frontend concern.

## 7. IDs and Enums

- PKs: UUID v4 (simple, no coordination). Known trade-off: random UUIDs fragment B-tree
  locality; at portfolio scale this is irrelevant, and swapping to time-sortable IDs
  (TSID/UUIDv7) later touches only type adapters. Documented, deliberate.
- Enum values stored as `TEXT` + CHECK constraints (not PG native enums): additive
  migration-friendly, readable in psql, same JPA mapping via `@Enumerated(STRING)`.
