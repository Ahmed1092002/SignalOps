"use client";

import { useState } from "react";
import {
  Binary,
  Check,
  Code2,
  Copy,
  Cpu,
  Database,
  Layers,
  Radio,
  Server,
  ShieldCheck,
  Terminal,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

const ARCH_STACK = [
  {
    category: "Backend Engine",
    tech: "Spring Boot 3.5 · Java 21",
    desc: "Modular monolith with ArchUnit package isolation, Flyway migrations, and record-based domain invariants.",
  },
  {
    category: "Data Layer",
    tech: "PostgreSQL 16",
    desc: "Single source of truth with strict tenant foreign keys, append-only audit tables, and optimistic lock versioning.",
  },
  {
    category: "Real-Time Streaming",
    tech: "WebSocket + STOMP",
    desc: "Transactional event dispatching after commit to guarantee zero stale broadcasts across responder war rooms.",
  },
  {
    category: "Frontend Architecture",
    tech: "Next.js 16 · React 19 · Tailwind v4",
    desc: "TypeScript strict mode, TanStack Query cache invalidation, Zustand session store, and Radix/shadcn UI primitives.",
  },
];

const CODE_SNIPPETS = {
  api: {
    title: "OpenAPI REST Contract",
    file: "PATCH /api/v1/incidents/inc_1049/status",
    lang: "http",
    code: `PATCH /api/v1/incidents/inc_1049/status HTTP/1.1
Host: api.signalops.io
Authorization: Bearer <jwt_access_token>
X-Organization-Id: org_northstar_01
Content-Type: application/json
If-Match: "4"

{
  "targetStatus": "IDENTIFIED",
  "reason": "Root cause isolated to memory leak in redis connection pool",
  "rootCauseSummary": "Connection pool was leaking handles during retry spikes."
}

/* HTTP/1.1 200 OK — ETag: "5" · STOMP broadcast triggered */`,
  },
  stomp: {
    title: "STOMP WebSocket Broadcast",
    file: "SUB /topic/incidents/org_northstar_01",
    lang: "json",
    code: `MESSAGE
subscription: sub-0
destination: /topic/incidents/org_northstar_01
message-id: msg-8921471-102
content-type: application/json

{
  "eventType": "INCIDENT_STATUS_TRANSITIONED",
  "incidentId": "inc_1049",
  "organizationId": "org_northstar_01",
  "fromStatus": "INVESTIGATING",
  "toStatus": "IDENTIFIED",
  "commander": { "id": "usr_alex", "name": "Alex Mercer" },
  "version": 5,
  "timestamp": "2026-08-28T00:45:00Z"
}`,
  },
  domain: {
    title: "Java 21 State Machine Guard",
    file: "IncidentStateMachine.java",
    lang: "java",
    code: `public record IncidentTransition(
    IncidentId incidentId,
    IncidentState from,
    IncidentState to,
    UserId commanderId,
    String summary
) {
    public void validateInvariants() {
        if (to == IncidentState.RESOLVED && (summary == null || summary.isBlank())) {
            throw new DomainRuleViolationException("R7: Resolution requires root cause summary.");
        }
        if (to.isCritical() && commanderId == null) {
            throw new DomainRuleViolationException("R3: Critical incident must have an assigned Commander.");
        }
    }
}`,
  },
  query: {
    title: "TanStack Mutation Client",
    file: "use-incident-transition.ts",
    lang: "typescript",
    code: `export function useTransitionIncident(incidentId: string) {
  const queryClient = useQueryClient();
  
  return useMutation({
    mutationFn: (data: TransitionPayload) => 
      apiClient.patch(\`/api/v1/incidents/\${incidentId}/status\`, data),
    onSuccess: (updated) => {
      queryClient.setQueryData(["incident", incidentId], updated);
      toast.success(\`Incident moved to \${updated.status}\`);
    },
    onError: (err) => {
      if (err.status === 409) toast.error("Stale write conflict — reloading latest state.");
    }
  });
}`,
  },
};

export function LandingTechStack() {
  const [activeSnippet, setActiveSnippet] = useState<keyof typeof CODE_SNIPPETS>("api");
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(CODE_SNIPPETS[activeSnippet].code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <section id="architecture" className="py-20">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="text-center max-w-3xl mx-auto">
          <Badge variant="outline" className="mb-3 border-primary/30 text-primary bg-primary/5">
            Engineering Architecture
          </Badge>
          <h2 className="text-3xl font-extrabold tracking-tight sm:text-4xl text-foreground">
            Engineered with Zero Compromises
          </h2>
          <p className="mt-4 text-base sm:text-lg text-muted-foreground">
            A battle-tested modular monolith built on Spring Boot 3.5, Java 21, PostgreSQL 16, and Next.js 16.
          </p>
        </div>

        {/* Stack Highlight Cards */}
        <div className="mt-12 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {ARCH_STACK.map((item, i) => (
            <div
              key={i}
              className="rounded-xl border border-border/80 bg-card/60 p-5 space-y-2.5 hover:border-primary/40 transition-colors"
            >
              <span className="text-[11px] font-bold uppercase tracking-wider text-primary">
                {item.category}
              </span>
              <h3 className="text-base font-bold text-foreground">{item.tech}</h3>
              <p className="text-xs text-muted-foreground leading-relaxed">{item.desc}</p>
            </div>
          ))}
        </div>

        {/* Interactive Code / Architecture Console */}
        <div className="mt-12 rounded-2xl border border-zinc-800 bg-zinc-950 shadow-2xl overflow-hidden">
          {/* Console Header Bar */}
          <div className="flex flex-wrap items-center justify-between border-b border-zinc-800 bg-zinc-900/80 px-4 py-2.5 gap-2">
            <div className="flex items-center gap-2">
              <Terminal className="h-4 w-4 text-sky-400" />
              <span className="text-xs font-mono font-semibold text-zinc-300">
                {CODE_SNIPPETS[activeSnippet].file}
              </span>
            </div>

            {/* Code Tabs */}
            <div className="flex items-center gap-1">
              {(Object.keys(CODE_SNIPPETS) as Array<keyof typeof CODE_SNIPPETS>).map((key) => (
                <button
                  key={key}
                  type="button"
                  onClick={() => setActiveSnippet(key)}
                  className={`px-3 py-1 rounded text-xs font-mono transition-colors ${
                    activeSnippet === key
                      ? "bg-primary text-primary-foreground font-semibold"
                      : "text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800"
                  }`}
                >
                  {CODE_SNIPPETS[key].title}
                </button>
              ))}
              <Button
                variant="ghost"
                size="icon"
                onClick={handleCopy}
                className="h-7 w-7 text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800 ml-2"
                title="Copy snippet"
              >
                {copied ? <Check className="h-3.5 w-3.5 text-emerald-400" /> : <Copy className="h-3.5 w-3.5" />}
              </Button>
            </div>
          </div>

          {/* Code Window */}
          <div className="p-6 font-mono text-xs text-emerald-400 leading-relaxed overflow-x-auto bg-zinc-950/90">
            <pre>
              <code>{CODE_SNIPPETS[activeSnippet].code}</code>
            </pre>
          </div>
        </div>
      </div>
    </section>
  );
}
