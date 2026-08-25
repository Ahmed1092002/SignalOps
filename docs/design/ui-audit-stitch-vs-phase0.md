# SignalOps — Stitch UI Export Audit vs Phase 0 Documentation

Date: 2026-08-25 · Scope: 5 Stitch screens (`operations_dashboard`, `incidents_list`,
`incident_details`, `create_incident`, `public_status_page`) + `DESIGN.md`.
Verdict per conflict: **documentation wins**; Stitch is the visual/spacing reference only.

## 1. Cross-screen visual inconsistencies

| # | Finding | Resolution |
|---|---|---|
| V1 | **Typography split**: `incident_details` never imports Geist (no font link in its `<head>`), so titles, sidebar brand, "Timeline", section headers render in a fallback **serif**. Dashboard/list/status pages use Geist. | Geist Sans everywhere; Geist Mono only for IDs, timestamps, metrics, error codes, technical snippets. No serif anywhere. |
| V2 | **Three different shells**: Dashboard has sidebar+topbar; Incidents List has **no shell at all** (floating page, broken `{{DATA:COMPONENTS:COMPONENTS_3}}` placeholder top-left); Incident Details has a *different* topbar ("SignalOps" wordmark + search, no bell/theme cluster). | One `AppShell` (sidebar + topbar) for all authenticated routes. |
| V3 | **Clipped actions**: Incident Details "Create Inc…" button overflows the viewport; header actions have no overflow strategy. | Topbar actions in a flex container with wrap/overflow handling; primary CTA lives in the shell, not per-page headers. |
| V4 | **Severity vocabulary conflict**: List/Create use `SEV-1..SEV-4` with labels "Critical Outage / Major Degradation / Minor Issue / Observation". Domain defines `LOW/MEDIUM/HIGH/CRITICAL` with fixed meanings (02-domain-rules §2). | Domain enums win. Severity selector shows the four domain levels + their documented definitions. |
| V5 | **Badge styles differ** between screens (dot+text vs icon pills). | One badge system: subtle background (10%), full-strength text, leading dot/icon + text label (never color alone). |
| V6 | **Status-page status mismatch**: Dashboard shows an active **Critical** incident on Payments; the status page shows Payment API at **Partial Outage** (amber). R10 derivation of CRITICAL ⇒ `MAJOR_OUTAGE` (red) — the two screens contradict each other. | Derived-status function is the single source; status page mock follows R10. |
| V7 | **Fabricated operational metrics**: "Latency: 1240ms", "Uptime: 98.2%", "91.6% Uptime", "ERR 5xx" — no such data exists in the Phase 0 model or API. | Removed. Service panels show derived status + active-incident counts only (both real). |
| V8 | **Fake chart content**: "Incident Volume (30D)" renders a decorative curve labeled "Trend Visualization Data"; donut chart shows invented percentages. | Analytics is Phase 3. Replaced by one clearly-marked "Trend charts — Phase 3" placeholder card. No chart lib installed. |
| V9 | **Date/branding drift**: status page mixes "Oct 2023" dates and "© 2023"; dashboard greets "Ahmed" while create-dialog says "Elena R."; org wordmark placement varies. | Single mock dataset, one clock, consistent branding (SignalOps = product, Northstar Cloud = org). |

## 2. Stitch features deferred by the roadmap (must not ship as active)

| Stitch element | Where | Disposition |
|---|---|---|
| "Join Bridge" button | incident_details | **Removed** — not defined anywhere in Phase 0. |
| Follow-up Tasks tab (active) | incident_details | Rendered **disabled with "Phase 3" tag** (explicitly allowed marking). |
| Attachment / image buttons in composers (B/I/list/code/**image** toolbar) | incident_details, create_incident | **Removed** — attachments are Phase 3; composer is plain textarea. |
| Rich-text toolbar | both composers | Removed (plain text; markdown rendering is a later, separate decision). |
| "Save Draft" action | create_incident | **Removed** — the domain has no draft state; incidents are created live at `DETECTED` (R7 lifecycle starts there). |
| **Visibility: Internal/Public toggle at create** | create_incident | **Removed** — conflicts with R14: public visibility is *derived from published public updates* (Phase 2 flow), never a create-time flag. |
| Subscribe buttons + email capture (header & footer) | public_status_page | **Removed** — email delivery/subscriptions are Phase 3. |
| Analytics as a first-class nav destination | dashboard sidebar | Shown as **disabled "Phase 3"** item, not a feature. |
| "Detection: Datadog" external link + alert snippet | incident_details | Kept *only* as a mock timeline-event payload (technical snippet in mono) — detection integrations are not a feature. |

## 3. Missing UI states required by 06-frontend-architecture

- No **loading** skeletons anywhere (Stitch pages are static snapshots).
- No **empty** states (zero incidents / no org data) and no **no-results** state distinct from empty.
- No **error + retry** state.
- Incidents list: no **archived filter** (R23), no sort control, no page-size, broken pagination
  ("1 to 4 of 42" with only Prev/Next, no page numbers/range).
- No **mobile** story: fixed desktop layouts, tables don't collapse to cards, no drawer nav.
- No **conflict UI** for `409 STALE_WRITE` (R12) and no visible incident **version** indicator.
- No **permission-aware** rendering (all actions visible regardless of role; Viewer would see
  mutation controls).
- No **connection status** indicator (required shell element; Phase 2 live, honest "demo data" now).
- Public page lacks **MAJOR_OUTAGE/MAINTENANCE** visual states and an overall-status banner
  derivation rule (banner exists but doesn't follow R10).

## 4. Business-rule conflicts (documentation wins)

1. **R1 (CRITICAL requires commander)**: create form has commander as an optional dropdown
   with no CRITICAL linkage and no inline validation. → Inline rule: selecting CRITICAL with
   no commander blocks submit with a clear message; commander options restricted to
   Owner/Admin/IC roles.
2. **R2/R13 (resolve summary / reopen reason)**: details screen has a bare "Resolve Incident"
   button; no summary dialog, no reopen affordance at all. → Resolve opens a required-summary
   dialog; reopen (RESOLVED→INVESTIGATING) requires a reason; MONITORING→INVESTIGATING asks
   for a reason too.
3. **R7 (transitions)**: no lifecycle control beyond Resolve; nothing prevents illegal jumps.
   → Transition menu generated from the central allowed-transition map; impossible states are
   not rendered.
4. **R6/R23 (archive)**: no archive affordance and no archived filter. → Archive action
   (permission-gated, confirm dialog) + `archived` filter on the list; archived incidents
   render read-only.
5. **R12 (optimistic locking)**: no version anywhere. → `v{n}` mono chip on detail; all mock
   mutations send `expectedVersion`; mismatch raises the conflict dialog.
6. **R14/R5 (public vs internal)**: composer mixes "Team Update / Public Update" tabs with a
   rich toolbar and no permission gating; public page shows incident key `INC-1042`.
   → Composer tabs gated by permission (public tab = IC/Admin only, tagged Phase 2);
   public page drops internal keys (payload allowlist per API doc §3).
7. **R10 (derived status)**: see V6. → `worstActiveStatus()` util shared by dashboard health
   and status page.
8. **R17 (active same-org members)**: commander/responders pickers unconstrained. → Pickers
   source from ACTIVE members of the active org only.
9. **R16 (≥1 service)**: no inline validation for empty affected-services. → Zod refinement
   + inline error.

## 5. Permission / security concerns

- Stitch shows every action to everyone (no role model in UI). → `PermissionGate` +
  matrix helper mirroring 07-security (UI-only convenience; backend stays authoritative).
- Public page shows internal incident keys; email subscribe implies data collection. Both removed.
- No tenant switcher behavior defined; org identity is baked into markup. → Org switcher
  invalidates all tenant-scoped queries; mock data is partitioned per org to demonstrate R11.

## 6. Shared components to extract (from duplicated Stitch markup)

`AppShell`, `Sidebar`, `Topbar`, `OrganizationSwitcher`, `ThemeToggle`, `ConnectionIndicator`,
`ProfileMenu`, `PageHeader`, `StatCard`, `SeverityBadge`, `IncidentStatusBadge`,
`ServiceStatusBadge`, `DataTable` (with mobile card mode), `IncidentCard`, `Timeline`,
`TimelineEvent`, `IncidentComposer`, `TransitionMenu`, `ResolveDialog`, `ReasonDialog`
(reopen/back-to-investigating), `ConflictDialog`, `CreateIncidentDialog`, `PermissionGate`,
`EmptyState`, `ErrorState`, `LoadingSkeleton`, `PhaseBadge`.

## 7. Responsive & accessibility issues found

- Fixed ≥1440 layouts; sidebar never collapses; no mobile drawer; no touch-target sizing
  (many 24–28px icon buttons).
- Tables have 9 columns at fixed widths → horizontal scroll on laptop; titles truncate to
  "Payme…" without tooltip/expand.
- Dialogs (create incident) exceed small-viewport height with no scroll strategy.
- Icon-only controls (search, bell, theme) have no accessible names; tabs lack
  `aria-controls`; status conveyed by color+text (good) but severity dots alone in list rows.
- No visible focus styles defined anywhere in the export.
→ Addressed via the responsive + a11y checklist in the implementation plan.

## 8. Data the public page must never expose (verified against API doc §3)

Internal comments, audit events, member emails, responder identities/teams, internal event
payloads, organization IDs/tenant headers, internal incident keys, detection sources.
Stitch violated one (incident key) and implied another (email capture). Both corrected.

## 9. Model gaps surfaced by the design (documented, NOT fixed in UI cleanup)

- **Customer impact** block on incident details and **human incident keys** (`INC-1042`) and
  **detection source** are shown by the design but do not exist in the Phase 0 ERD/API.
  UI renders description as "Context & impact" and uses mock-layer display keys only.
  Adding `customer_impact` / `display_key` columns is a *backend* decision requiring a
  migration — deferred to the backend milestone, flagged here for the roadmap discussion.
