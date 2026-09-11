"use client";

import { useState } from "react";
import {
  Activity,
  AlertCircle,
  Boxes,
  Clock,
  ExternalLink,
  Flame,
  Globe,
  LayoutDashboard,
  MessageSquare,
  Radio,
  ScrollText,
  Server,
  Shield,
  Siren,
  Users,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

type TourTab = "incidents" | "services" | "timeline" | "statuspage";

interface TourItem {
  id: TourTab;
  title: string;
  badge: string;
  icon: React.ElementType;
  description: string;
}

const TABS: TourItem[] = [
  {
    id: "incidents",
    title: "Incident Command Center",
    badge: "Real-time Triage",
    icon: Siren,
    description: "Triage and coordinate multiple concurrent incidents with severity filters and commander oversight.",
  },
  {
    id: "services",
    title: "Service Catalog & Health",
    badge: "Catalog & Derivation",
    icon: Boxes,
    description: "Inspect microservices, APIs, and databases. Service health is derived automatically from active incidents.",
  },
  {
    id: "timeline",
    title: "Live War Room Timeline",
    badge: "Append-Only Audit",
    icon: ScrollText,
    description: "Every comment, commander change, and status update is streamed instantly via STOMP and permanently recorded.",
  },
  {
    id: "statuspage",
    title: "Public Status Page",
    badge: "Customer-Safe",
    icon: Globe,
    description: "Customer-safe status pages published automatically to protect user trust while keeping internal data secure.",
  },
];

export function LandingProductTour() {
  const [activeTab, setActiveTab] = useState<TourTab>("incidents");

  return (
    <section id="tour" className="py-20 bg-accent/20 border-y border-border/50">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="text-center max-w-3xl mx-auto">
          <Badge variant="outline" className="mb-3 border-primary/30 text-primary bg-primary/5">
            Interactive Product Tour
          </Badge>
          <h2 className="text-3xl font-extrabold tracking-tight sm:text-4xl text-foreground">
            A Purpose-Built Interface for High-Pressure Outages
          </h2>
          <p className="mt-4 text-base sm:text-lg text-muted-foreground">
            Explore how SignalOps empowers commanders, SREs, and responders with lightning-fast clarity.
          </p>
        </div>

        {/* Tab Navigation */}
        <div className="mt-10 flex flex-wrap items-center justify-center gap-2">
          {TABS.map((t) => {
            const Icon = t.icon;
            const isSelected = activeTab === t.id;
            return (
              <button
                key={t.id}
                type="button"
                onClick={() => setActiveTab(t.id)}
                className={`flex items-center gap-2 px-4 py-2.5 rounded-lg border text-sm font-semibold transition-all ${
                  isSelected
                    ? "bg-primary text-primary-foreground border-primary shadow-lg shadow-primary/20 scale-[1.02]"
                    : "bg-card hover:bg-accent border-border text-muted-foreground hover:text-foreground"
                }`}
              >
                <Icon className="h-4 w-4" />
                <span>{t.title}</span>
              </button>
            );
          })}
        </div>

        {/* Mock Window Container */}
        <div className="mt-8 rounded-2xl border border-border/80 bg-card shadow-2xl overflow-hidden backdrop-blur-xl">
          {/* App Header Mockup */}
          <div className="flex items-center justify-between border-b border-border/60 bg-muted/40 px-4 py-3">
            <div className="flex items-center gap-2">
              <div className="flex gap-1.5">
                <span className="h-3 w-3 rounded-full bg-rose-500/80 inline-block" />
                <span className="h-3 w-3 rounded-full bg-amber-500/80 inline-block" />
                <span className="h-3 w-3 rounded-full bg-emerald-500/80 inline-block" />
              </div>
              <div className="ml-3 font-mono text-xs text-muted-foreground">
                app.signalops.io/org_northstar/{activeTab}
              </div>
            </div>
            <div className="flex items-center gap-2">
              <Badge variant="outline" className="text-[11px] font-mono bg-emerald-500/10 text-emerald-500 border-emerald-500/20">
                Connected: STOMP v1.2
              </Badge>
            </div>
          </div>

          {/* Screen Content Showcase */}
          <div className="p-6 sm:p-8">
            {activeTab === "incidents" && (
              <div className="space-y-4 animate-in fade-in duration-300">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <h3 className="text-lg font-bold text-foreground">Incident Command Dashboard</h3>
                    <p className="text-xs text-muted-foreground">5 active & resolved incidents across Northstar Cloud infrastructure</p>
                  </div>
                  <div className="flex gap-2">
                    <span className="px-2.5 py-1 rounded bg-rose-500/15 text-rose-400 border border-rose-500/20 text-xs font-semibold">1 Critical</span>
                    <span className="px-2.5 py-1 rounded bg-amber-500/15 text-amber-400 border border-amber-500/20 text-xs font-semibold">2 Active</span>
                    <span className="px-2.5 py-1 rounded bg-emerald-500/15 text-emerald-400 border border-emerald-500/20 text-xs font-semibold">2 Resolved</span>
                  </div>
                </div>

                {/* Table Mockup */}
                <div className="rounded-xl border border-border/70 overflow-hidden bg-background/60">
                  <div className="divide-y divide-border/60 text-xs">
                    <div className="grid grid-cols-12 p-3 bg-muted/40 font-semibold text-muted-foreground uppercase tracking-wider text-[11px]">
                      <div className="col-span-2">Severity</div>
                      <div className="col-span-4">Incident Title</div>
                      <div className="col-span-2">Service</div>
                      <div className="col-span-2">Commander</div>
                      <div className="col-span-2 text-right">Status</div>
                    </div>

                    <div className="grid grid-cols-12 p-3 items-center hover:bg-accent/40 transition-colors">
                      <div className="col-span-2">
                        <Badge variant="destructive" className="text-[10px]">CRITICAL</Badge>
                      </div>
                      <div className="col-span-4 font-semibold text-foreground">
                        Primary Payment Gateway Auth Failure (500)
                      </div>
                      <div className="col-span-2 text-muted-foreground">Payment Service</div>
                      <div className="col-span-2 font-medium text-primary">Alex Mercer (Lead)</div>
                      <div className="col-span-2 text-right">
                        <span className="px-2 py-0.5 rounded-full bg-amber-500/15 text-amber-400 border border-amber-500/20 text-[11px] font-bold">
                          INVESTIGATING
                        </span>
                      </div>
                    </div>

                    <div className="grid grid-cols-12 p-3 items-center hover:bg-accent/40 transition-colors">
                      <div className="col-span-2">
                        <Badge variant="outline" className="border-amber-500 text-amber-500 text-[10px]">HIGH</Badge>
                      </div>
                      <div className="col-span-4 font-semibold text-foreground">
                        Search Index Replication Lag &gt; 120s
                      </div>
                      <div className="col-span-2 text-muted-foreground">Search Elastic</div>
                      <div className="col-span-2 font-medium text-foreground/80">Sara Kim (SRE)</div>
                      <div className="col-span-2 text-right">
                        <span className="px-2 py-0.5 rounded-full bg-purple-500/15 text-purple-400 border border-purple-500/20 text-[11px] font-bold">
                          IDENTIFIED
                        </span>
                      </div>
                    </div>

                    <div className="grid grid-cols-12 p-3 items-center hover:bg-accent/40 transition-colors">
                      <div className="col-span-2">
                        <Badge variant="outline" className="text-slate-400 border-slate-500 text-[10px]">MEDIUM</Badge>
                      </div>
                      <div className="col-span-4 font-semibold text-foreground">
                        Slow Image Processing Worker Queues
                      </div>
                      <div className="col-span-2 text-muted-foreground">Media Ingestion</div>
                      <div className="col-span-2 font-medium text-foreground/80">John Doe</div>
                      <div className="col-span-2 text-right">
                        <span className="px-2 py-0.5 rounded-full bg-emerald-500/15 text-emerald-400 border border-emerald-500/20 text-[11px] font-bold">
                          RESOLVED
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {activeTab === "services" && (
              <div className="space-y-4 animate-in fade-in duration-300">
                <div>
                  <h3 className="text-lg font-bold text-foreground">Service Catalog & Automated Health Derivation</h3>
                  <p className="text-xs text-muted-foreground">6 monitored microservices with worst-active-incident rollup</p>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <div className="p-4 rounded-xl border border-rose-500/30 bg-rose-500/5 space-y-2">
                    <div className="flex justify-between items-center">
                      <span className="font-bold text-sm text-foreground">Payments API</span>
                      <Badge variant="destructive" className="text-[10px]">MAJOR OUTAGE</Badge>
                    </div>
                    <p className="text-xs text-muted-foreground">Handles Stripe & checkout webhooks.</p>
                    <div className="text-xs text-rose-400 font-mono">Linked to: INC-1049 (CRITICAL)</div>
                  </div>

                  <div className="p-4 rounded-xl border border-amber-500/30 bg-amber-500/5 space-y-2">
                    <div className="flex justify-between items-center">
                      <span className="font-bold text-sm text-foreground">Search Indexer</span>
                      <Badge variant="outline" className="border-amber-500 text-amber-400 text-[10px]">DEGRADED</Badge>
                    </div>
                    <p className="text-xs text-muted-foreground">Elasticsearch cluster & query proxy.</p>
                    <div className="text-xs text-amber-400 font-mono">Linked to: INC-1050 (HIGH)</div>
                  </div>

                  <div className="p-4 rounded-xl border border-emerald-500/30 bg-emerald-500/5 space-y-2">
                    <div className="flex justify-between items-center">
                      <span className="font-bold text-sm text-foreground">Authentication Core</span>
                      <Badge variant="outline" className="border-emerald-500 text-emerald-400 text-[10px]">OPERATIONAL</Badge>
                    </div>
                    <p className="text-xs text-muted-foreground">JWT issue & rotating refresh sessions.</p>
                    <div className="text-xs text-emerald-400 font-mono">99.99% Uptime (0 active incidents)</div>
                  </div>
                </div>
              </div>
            )}

            {activeTab === "timeline" && (
              <div className="space-y-4 animate-in fade-in duration-300">
                <div>
                  <h3 className="text-lg font-bold text-foreground">Append-Only Timeline Stream</h3>
                  <p className="text-xs text-muted-foreground">Immutable audit logs with milliseconds precision</p>
                </div>
                <div className="rounded-xl border border-border/70 bg-background/50 p-4 space-y-3 font-mono text-xs">
                  <div className="flex items-center gap-3 text-muted-foreground border-b border-border/40 pb-2">
                    <span className="text-primary font-bold">14:02:15 UTC</span>
                    <span className="text-rose-400 font-semibold">[SEV_ESCALATE]</span>
                    <span className="text-foreground">Incident severity escalated to CRITICAL by Alex Mercer.</span>
                  </div>
                  <div className="flex items-center gap-3 text-muted-foreground border-b border-border/40 pb-2">
                    <span className="text-primary font-bold">14:03:00 UTC</span>
                    <span className="text-indigo-400 font-semibold">[COMMANDER_SET]</span>
                    <span className="text-foreground">Alex Mercer assumed role of Incident Commander. War room open.</span>
                  </div>
                  <div className="flex items-center gap-3 text-muted-foreground border-b border-border/40 pb-2">
                    <span className="text-primary font-bold">14:08:44 UTC</span>
                    <span className="text-amber-400 font-semibold">[PUBLIC_UPDATE]</span>
                    <span className="text-foreground">Public update broadcast: "Engineers investigating gateway latency."</span>
                  </div>
                  <div className="flex items-center gap-3 text-muted-foreground">
                    <span className="text-primary font-bold">14:15:20 UTC</span>
                    <span className="text-emerald-400 font-semibold">[STATE_CHANGE]</span>
                    <span className="text-foreground">Status transitioned to IDENTIFIED. Mitigation patch building.</span>
                  </div>
                </div>
              </div>
            )}

            {activeTab === "statuspage" && (
              <div className="space-y-4 animate-in fade-in duration-300">
                <div className="flex justify-between items-center">
                  <div>
                    <h3 className="text-lg font-bold text-foreground">Decoupled Customer Status Page</h3>
                    <p className="text-xs text-muted-foreground">Rendered publicly at /status/northstar-cloud</p>
                  </div>
                  <Button size="sm" variant="outline" asChild className="gap-1.5 text-xs">
                    <a href="/status/northstar-cloud" target="_blank" rel="noopener noreferrer">
                      <span>Open Live Demo</span>
                      <ExternalLink className="h-3.5 w-3.5" />
                    </a>
                  </Button>
                </div>
                <div className="rounded-xl border border-border/70 bg-background/80 p-6 space-y-4">
                  <div className="flex items-center justify-between border-b border-border/60 pb-4">
                    <div className="flex items-center gap-2">
                      <span className="h-3.5 w-3.5 rounded-full bg-amber-500 animate-pulse inline-block" />
                      <span className="font-bold text-foreground">Northstar Cloud System Status</span>
                    </div>
                    <Badge variant="outline" className="text-amber-400 border-amber-500/30 bg-amber-500/10">
                      Partial Outage Reported
                    </Badge>
                  </div>
                  <div className="p-3 rounded-lg bg-accent/40 text-xs leading-relaxed text-foreground/90">
                    <span className="font-semibold text-primary">Active Update:</span> Our engineering team is currently addressing elevated latency on the Payment Gateway. Core authentication and storage services remain unaffected.
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}
