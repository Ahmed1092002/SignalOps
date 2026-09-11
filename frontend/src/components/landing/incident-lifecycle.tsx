"use client";

import { useState } from "react";
import {
  Activity,
  AlertCircle,
  ArrowRight,
  CheckCircle,
  Eye,
  Lock,
  RotateCcw,
  Search,
  ShieldAlert,
  Terminal,
  Zap,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";

type PhaseKey = "DETECTED" | "INVESTIGATING" | "IDENTIFIED" | "MONITORING" | "RESOLVED";

interface PhaseInfo {
  key: PhaseKey;
  label: string;
  badgeColor: string;
  tagline: string;
  description: string;
  rulesEnforced: string[];
  actionsAvailable: string[];
  samplePayload: string;
}

const PHASES: PhaseInfo[] = [
  {
    key: "DETECTED",
    label: "Phase 1: Detected",
    badgeColor: "bg-slate-500/15 text-slate-400 border-slate-500/30",
    tagline: "Outage or anomaly triggered via alerts, APM, or responder declaration",
    description:
      "Incidents are initialized with severity (LOW, MEDIUM, HIGH, CRITICAL) and mapped to the affected service in the Service Catalog. Telemetry timestamps establish MTTD (Mean Time to Detect).",
    rulesEnforced: [
      "Rule R1: Severity & Affected Service must be assigned.",
      "Rule R3: If severity is CRITICAL, an Incident Commander must be designated.",
      "Rule R14: Immediate creation event logged in append-only timeline.",
    ],
    actionsAvailable: [
      "Declare Severity Level",
      "Assign Incident Commander",
      "Link Service from Catalog",
      "Open War Room WebSocket channel",
    ],
    samplePayload: `{
  "title": "Database Connection Pool Exhaustion",
  "severity": "CRITICAL",
  "serviceId": "svc_core_postgres_01",
  "commanderId": "usr_alex_lead_sre"
}`,
  },
  {
    key: "INVESTIGATING",
    label: "Phase 2: Investigating",
    badgeColor: "bg-amber-500/15 text-amber-400 border-amber-500/30",
    tagline: "Responders assembled, diagnostics underway, internal notes streamed live",
    description:
      "Responders coordinate in real time over STOMP WebSockets. Team members post internal investigation comments, examine log attachments, and run diagnostic tasks without page reloads.",
    rulesEnforced: [
      "Rule R4: All responders view live synchronization of timeline events.",
      "Rule R8: Optimistic locking version check ensures zero lost updates.",
      "Rule R12: Internal comments stay strictly private and isolated from public eyes.",
    ],
    actionsAvailable: [
      "Post Real-Time Investigation Comments",
      "Attach Diagnostic Log Files (up to 5MB)",
      "Assign Follow-up Subtasks",
      "Escalate Severity if blast radius widens",
    ],
    samplePayload: `{
  "action": "ADD_COMMENT",
  "visibility": "INTERNAL_ONLY",
  "content": "Thread pool spike confirmed on worker nodes 3 & 4. Isolating traffic."
}`,
  },
  {
    key: "IDENTIFIED",
    label: "Phase 3: Identified",
    badgeColor: "bg-purple-500/15 text-purple-400 border-purple-500/30",
    tagline: "Root cause isolated, remediation strategy established",
    description:
      "The engineering team pinpoints the causal factor (e.g. faulty deployment, memory leak, upstream provider disruption) and coordinates the fix or mitigation rollback.",
    rulesEnforced: [
      "Rule R5: Valid state machine transition validated (INVESTIGATING → IDENTIFIED).",
      "Rule R9: Customer communication officer drafted customer-safe update.",
      "Rule R18: Public status page updates are derived and published safely.",
    ],
    actionsAvailable: [
      "Record Identified Root Cause Summary",
      "Draft & Publish Customer-Safe Status Update",
      "Trigger Rollback or Hotfix Deployment",
      "Adjust Service Impact Matrix",
    ],
    samplePayload: `{
  "status": "IDENTIFIED",
  "publicUpdate": {
    "status": "IDENTIFIED",
    "message": "We have identified an issue with ingress routing and are rolling out a mitigation patch."
  }
}`,
  },
  {
    key: "MONITORING",
    label: "Phase 4: Monitoring",
    badgeColor: "bg-cyan-500/15 text-cyan-400 border-cyan-500/30",
    tagline: "Fix deployed, telemetry metrics stabilizing, verifying health",
    description:
      "The mitigation is active. The team observes error rates, latency percentiles, and database metrics to ensure the system is returning to steady state.",
    rulesEnforced: [
      "Rule R6: Service state dynamically updates to DEGRADED on status page.",
      "Rule R10: MTTR timer tracks actively until formal resolution.",
      "Rule R15: Audit trail registers verification checks.",
    ],
    actionsAvailable: [
      "Verify APM & Synthetics Latency Drop",
      "Broadcast Monitoring Notice to Customers",
      "Create Follow-up Remediation Tasks",
      "Prepare Post-Mortem Template",
    ],
    samplePayload: `{
  "status": "MONITORING",
  "message": "Mitigation applied. Latency metrics have returned to nominal levels."
}`,
  },
  {
    key: "RESOLVED",
    label: "Phase 5: Resolved",
    badgeColor: "bg-emerald-500/15 text-emerald-400 border-emerald-500/30",
    tagline: "Incident closed, post-incident review and prevention tasks scheduled",
    description:
      "The incident is formally resolved. Final MTTR/MTTD metrics are stamped into the Analytics engine. Services return to OPERATIONAL status across all public pages.",
    rulesEnforced: [
      "Rule R7: Resolution requires a non-empty Root Cause Summary.",
      "Rule R11: Reopening a resolved incident requires an explicit justification reason.",
      "Rule R20: Analytics automatically recalculates org DORA metrics.",
    ],
    actionsAvailable: [
      "Submit Comprehensive Resolution Summary",
      "Export Full Audit Trail & Event Timeline",
      "Generate Post-Mortem Review Document",
      "Close Customer Public Status Update",
    ],
    samplePayload: `{
  "status": "RESOLVED",
  "resolutionSummary": "Upstream connection pool leak resolved via configuration patch v2.4.1.",
  "durationSeconds": 1420
}`,
  },
];

export function LandingLifecycle() {
  const [activePhase, setActivePhase] = useState<PhaseKey>("INVESTIGATING");
  const current = PHASES.find((p) => p.key === activePhase) || PHASES[0];

  return (
    <section id="lifecycle" className="py-20 bg-accent/20 border-y border-border/50">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto">
          <Badge variant="outline" className="mb-3 border-primary/30 text-primary bg-primary/5">
            Strict State Machine
          </Badge>
          <h2 className="text-3xl font-extrabold tracking-tight sm:text-4xl text-foreground">
            Controlled Incident Lifecycle. Zero Chaos.
          </h2>
          <p className="mt-4 text-base sm:text-lg text-muted-foreground">
            No skipped steps or uncoordinated chaos. SignalOps enforces a state machine with hard
            business rules that ensure every incident is properly commanded, audited, and resolved.
          </p>
        </div>

        {/* Phase Stepper Tabs */}
        <div className="mt-12 flex flex-wrap items-center justify-center gap-2 md:gap-3">
          {PHASES.map((p, idx) => {
            const isSelected = p.key === activePhase;
            return (
              <button
                key={p.key}
                type="button"
                onClick={() => setActivePhase(p.key)}
                className={`flex items-center gap-2 px-4 py-2.5 rounded-lg border text-sm font-semibold transition-all ${
                  isSelected
                    ? "bg-primary text-primary-foreground border-primary shadow-lg shadow-primary/20 scale-[1.03]"
                    : "bg-card hover:bg-accent border-border text-muted-foreground hover:text-foreground"
                }`}
              >
                <span className="flex h-5 w-5 items-center justify-center rounded-full bg-background/20 text-xs font-mono">
                  {idx + 1}
                </span>
                <span>{p.key}</span>
              </button>
            );
          })}
        </div>

        {/* Detailed Active Phase Card */}
        <div className="mt-8 rounded-2xl border border-border/80 bg-card p-6 sm:p-8 shadow-xl backdrop-blur-sm">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            {/* Left Column: Description & Rules */}
            <div className="lg:col-span-7 space-y-6">
              <div>
                <div className="flex items-center gap-3 mb-2">
                  <span className={`px-3 py-1 rounded-full text-xs font-bold uppercase tracking-wider border ${current.badgeColor}`}>
                    {current.label}
                  </span>
                  <span className="text-xs font-mono text-muted-foreground">State: {current.key}</span>
                </div>
                <h3 className="text-xl sm:text-2xl font-bold text-foreground">{current.tagline}</h3>
                <p className="mt-2 text-sm sm:text-base text-muted-foreground leading-relaxed">
                  {current.description}
                </p>
              </div>

              {/* Enforced Rules */}
              <div className="rounded-xl border border-border/60 bg-accent/30 p-4 space-y-2.5">
                <div className="text-xs font-bold uppercase tracking-wider text-primary flex items-center gap-1.5">
                  <ShieldAlert className="h-4 w-4" />
                  <span>Enforced Domain Invariants & Rules</span>
                </div>
                <ul className="space-y-1.5">
                  {current.rulesEnforced.map((rule, i) => (
                    <li key={i} className="text-xs sm:text-sm text-foreground/90 flex items-start gap-2">
                      <CheckCircle className="h-4 w-4 text-emerald-500 shrink-0 mt-0.5" />
                      <span>{rule}</span>
                    </li>
                  ))}
                </ul>
              </div>

              {/* Allowed Actions */}
              <div>
                <div className="text-xs font-bold uppercase tracking-wider text-muted-foreground mb-2.5">
                  Available Operator Actions
                </div>
                <div className="flex flex-wrap gap-2">
                  {current.actionsAvailable.map((act, i) => (
                    <span
                      key={i}
                      className="px-3 py-1 rounded-md bg-secondary text-secondary-foreground text-xs font-medium border border-border/40"
                    >
                      {act}
                    </span>
                  ))}
                </div>
              </div>
            </div>

            {/* Right Column: Code & STOMP Contract Preview */}
            <div className="lg:col-span-5 rounded-xl border border-zinc-800 bg-zinc-950 p-4 text-zinc-300 font-mono text-xs shadow-inner">
              <div className="flex items-center justify-between border-b border-zinc-800 pb-2 mb-3">
                <div className="flex items-center gap-2">
                  <Terminal className="h-3.5 w-3.5 text-emerald-400" />
                  <span className="text-[11px] font-semibold text-zinc-400">REST Contract / STOMP Payload</span>
                </div>
                <span className="text-[10px] text-emerald-400">v1/incidents/state</span>
              </div>
              <pre className="overflow-x-auto text-[11px] leading-relaxed text-emerald-400 p-2 bg-zinc-900/60 rounded">
                <code>{current.samplePayload}</code>
              </pre>
              <div className="mt-3 text-[10px] text-zinc-500 flex items-center justify-between">
                <span>Atomic Transaction Committed</span>
                <span className="text-sky-400">PostgreSQL 16 · Flyway</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
