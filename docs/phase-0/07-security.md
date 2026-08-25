# SignalOps — Phase 0 · Security, RBAC & Multi-Tenancy

Status: DRAFT — awaiting approval

## 1. Authentication Design

**Passwords:** BCrypt (cost 12) via Spring Security crypto. Login is timing-safe and
returns one generic error for unknown-email vs wrong-password (no user enumeration).
Password change requires current password; writes `PASSWORD_CHANGED` audit event.

**Access token:** JWT, HS256 with a ≥256-bit env secret (RS256 + JWKS noted as upgrade
when multiple services/verifiers exist). TTL **15 minutes**. Claims: `iss`, `sub` (userId),
`iat`, `exp`, `jti`. Deliberately **no roles and no orgId in the JWT** — roles are
org-scoped and mutable; baking them into a token means stale privileges until expiry.
Authorization data is read from memberships per request (cheap indexed lookup).

**Token storage on the client:**

| Option | XSS | CSRF | Verdict |
|---|---|---|---|
| Both tokens in localStorage | steals both | n/a | rejected |
| Access in memory, refresh in cookie | access lost only on tab close | must mitigate | **chosen** |
| Cookie for access too | HttpOnly helps | full CSRF surface + server sessions creep | rejected |

Chosen model: short-lived access JWT held in JS memory (lost on refresh → silent re-auth
via cookie makes this invisible); refresh token = opaque random 256-bit value, stored
hashed in `refresh_token_sessions`, delivered as `HttpOnly; Secure; SameSite=Lax;
Path=/api/v1/auth` cookie.

**CSRF posture:** the only cookie-authenticated endpoints are `/auth/refresh` and
`/auth/logout`. SameSite=Lax blocks cross-site POSTs carrying it in modern browsers;
defense-in-depth: CORS locked to exact frontend origin with credentials, and refresh
additionally requires a custom header (`X-Requested-With: XMLHttpRequest`) which cross-site
forms cannot send without a preflight we'd reject. Documented so the reviewer sees the chain.

**Rotation & reuse detection (R22):** every `/auth/refresh` rotates: new family member row,
old marked revoked+replaced-by. Presenting an already-rotated token ⇒ treat as theft ⇒
revoke the entire family, force re-login, audit `AUTH_LOGIN_FAILED`. Logout revokes family.

## 2. Multi-Tenancy Enforcement (R11)

Shared-schema, discriminator strategy (`organization_id`) — right-sized for MVP; Postgres
RLS listed as Phase-3 hardening, not needed to be correct today.

Four enforcement layers:

1. **Resolution:** `X-Organization-Id` header → ACTIVE membership check per request →
   `TenantContext`. No header + multiple orgs → 400; foreign org → 404.
2. **Query scoping:** tenant repositories require `organizationId` parameters by signature;
   no global finders on tenant aggregates exposed to services.
3. **Referential integrity:** composite FKs `(organization_id,id)` between incidents and
   services make cross-org references unrepresentable in the DB itself.
4. **Verification:** integration tests hit endpoints/repositories with org-A context
   against org-B ids and assert 404 + zero rows leaked; ArchUnit later guards the pattern.

## 3. Roles & Permission Matrix

Org role lives on the membership. Single role per member (approved Phase 0 decision);
multi-role can migrate additively later if a real need appears.
Commander eligibility: role ∈ {OWNER, ADMIN, INCIDENT_COMMANDER} AND membership ACTIVE.

Legend: ✔ allowed · ✱ conditional (rule in footnote) · – denied

| Capability | Owner | Admin | Incident Commander | Responder | Viewer |
|---|---|---|---|---|---|
| Manage org settings | ✔ | ✔ | – | – | – |
| Invite / remove members | ✔ | ✔ | – | – | – |
| Change member roles | ✔ | ✔¹ | – | – | – |
| Create / edit / archive services | ✔ | ✔ | – | – | – |
| Read audit log | ✔ | ✔ | – | – | – |
| View members directory | ✔ | ✔ | ✔ | ✔ | ✔ |
| View incidents, timelines, comments | ✔ | ✔ | ✔ | ✔² | ✔ |
| Create incident | ✔ | ✔ | ✔³ | – | – |
| Edit incident title/description | ✔ | ✔⁴ | ✱⁵ | – | – |
| Change severity | ✔ | ✔ | ✱⁶ | – | – |
| Status transitions / resolve / reopen | ✔ | ✔ | ✱⁶ | – | – |
| Assign/remove commander | ✔ | ✔ | ✱⁷ | – | – |
| Assign/remove responders | ✔ | ✔ | ✱⁶ | – | – |
| Post internal comment | ✔ | ✔ | ✱⁶ | ✱⁸ | – |
| Publish public update | ✔ | ✔ | ✱⁶ | – | – |
| Archive incident | ✔ | ✔ | ✱⁶ | – | – |
| Manage follow-up tasks (P3) | ✔ | ✔ | ✱⁶ | ✱⁹ | – |
| Receive notifications | ✔ | ✔ | ✔ | ✔ | ✔ |
| Public status page | anon | anon | anon | anon | anon |

¹ Cannot demote/remove the last Owner (`CANNOT_REMOVE_LAST_OWNER`).
² Responders see org-wide incident lists but work only their assignments.
³ IC creating an incident may set themselves as commander (required if CRITICAL).
⁴ Any admin edit of an incident they didn't create/command writes `INCIDENT_EDITED` audit.
⁵ Only while commanding that incident.
⁶ Only while commanding that incident ("commanding" = listed commander on it).
⁷ ICs may claim an uncommanded incident or hand command to another eligible member;
   removing a commander from a CRITICAL incident is blocked (R1).
⁸ Only on incidents where assigned as responder or commander (R4).
⁹ Tasks they created or that are assigned to them (Phase 3).

Enforcement: matrix encoded once — `Permissions` constants + `IncidentAuthorizationService`
(relationship logic) + `@RequirePermission` annotations at controllers; services never
compare raw role strings. Every cell above has a named integration test.

## 4. Public Surface Guarantees

- Unauthenticated endpoints limited to `/public/**` (+ auth endpoints).
- Public DTOs are separate classes with field allowlists mapped exclusively from
  public-only tables (structural R5/R14). Integration test asserts serializer output keys.
- Unknown slug/incident → 404; no cache headers that could leak internal variants
  (`Cache-Control: no-store` on all authenticated API responses).

## 5. Audit Trail Discipline (R15)

Audit/timeline repositories expose insert+query only. Metadata JSONB carries safe summaries
(ids, enum values, titles) — never passwords, tokens, or raw bodies. Phase 3 adds DB-level
`REVOKE UPDATE, DELETE` grants plus optional hash-chaining for tamper evidence.

## 6. Transport & Config Hygiene

CORS: explicit origin allowlist from env (no `*` with credentials). HSTS/CSP headers added
in prod profile (Phase 3 hardening pass). Secrets only via env vars; `.env.example` has
placeholders; demo seed accounts use documented throwaway passwords. Rate limiting
(bucket4j + Redis) deferred with a reserved `RATE_LIMITED` error code — honest backlog,
not fake security.

## 7. Threat Notes (what each decision buys)

| Threat | Mitigation |
|---|---|
| XSS token theft | access token memory-only; refresh HttpOnly |
| Stolen refresh replayed | rotation + family revocation (R22) |
| Cross-org data probing | 404-uniform responses + layered tenancy checks |
| Privilege escalation via stale claims | roles never in JWT; evaluated server-side |
| Silent update loss | optimistic locking + 409 contract (R12) |
| Internal leak to public page | structural table separation + allowlist DTOs (R5/R14) |
