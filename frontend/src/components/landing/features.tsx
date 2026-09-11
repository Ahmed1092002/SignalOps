import {
  Activity,
  BarChart3,
  Bell,
  Boxes,
  Calendar,
  CheckCircle2,
  Cpu,
  Globe,
  Lock,
  Radio,
  ScrollText,
  ShieldCheck,
  Siren,
  Users2,
  Workflow,
  Zap,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

const FEATURES = [
  {
    icon: ShieldCheck,
    color: "text-indigo-500 bg-indigo-500/10 border-indigo-500/20",
    badge: "Enterprise Security",
    title: "Strict Multi-Tenant Isolation",
    description:
      "Engineered from the database layer up with dedicated organization scoping. Cross-tenant access is rejected with indistinguishable 404s. Full RBAC with Owner, Admin, Commander, Responder, and Viewer roles.",
    points: ["Query-level org scoping", "Rotating refresh JWT cookies", "Granular role-based access control"],
  },
  {
    icon: Radio,
    color: "text-sky-500 bg-sky-500/10 border-sky-500/20",
    badge: "Real-Time Engine",
    title: "Sub-Second STOMP Sync",
    description:
      "Experience seamless live collaboration. Responders in the war room receive incident updates, new timeline events, and commander notes instantly via STOMP WebSockets over transactional PostgreSQL commits.",
    points: ["Zero page reload needed", "Post-commit event broadcast", "Optimistic concurrency locking"],
  },
  {
    icon: Siren,
    color: "text-rose-500 bg-rose-500/10 border-rose-500/20",
    badge: "State Machine",
    title: "Enforced Commander & Rules",
    description:
      "Eliminate responder confusion. Critical (P1) incidents strictly enforce designated Incident Commanders. Reopening closed incidents requires an explicit justification, keeping records auditable.",
    points: ["Mandatory critical commander", "Central state machine engine", "No illegal transition bypass"],
  },
  {
    icon: Globe,
    color: "text-emerald-500 bg-emerald-500/10 border-emerald-500/20",
    badge: "Public Trust",
    title: "Decoupled Public Status Pages",
    description:
      "Keep customers informed without exposing sensitive internal war room notes. Automatically derives service status using the worst-active-incident algorithm with branded status pages.",
    points: ["Internal vs Public separation", "Automated service degradation", "Custom domain status slugs"],
  },
  {
    icon: BarChart3,
    color: "text-amber-500 bg-amber-500/10 border-amber-500/20",
    badge: "DORA Metrics",
    title: "MTTR, MTTD & Reliability Analytics",
    description:
      "Understand your system reliability with automated analytics. Track Mean Time to Detect (MTTD), Mean Time to Resolve (MTTR), incident volume per service, and responder load over time.",
    points: ["Automated MTTR/MTTD calculation", "Service incident distribution", "Severity trend breakdowns"],
  },
  {
    icon: Calendar,
    color: "text-purple-500 bg-purple-500/10 border-purple-500/20",
    badge: "Continuous Ops",
    title: "Maintenance & Follow-Up Tasks",
    description:
      "Ensure recurring problems never resurface. Schedule planned maintenance windows to avoid false alerts and assign follow-up action items with ownership and due dates during post-mortems.",
    points: ["Scheduled maintenance windows", "Post-incident action items", "Append-only immutable audit log"],
  },
];

export function LandingFeatures() {
  return (
    <section id="features" className="py-20">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto">
          <Badge variant="outline" className="mb-3 border-primary/30 text-primary bg-primary/5">
            Platform Capabilities
          </Badge>
          <h2 className="text-3xl font-extrabold tracking-tight sm:text-4xl text-foreground">
            Everything Required to Run Mission-Critical Operations
          </h2>
          <p className="mt-4 text-base sm:text-lg text-muted-foreground">
            Built from scratch for teams that can't afford operational ambiguity during production incidents.
          </p>
        </div>

        {/* Feature Cards Grid */}
        <div className="mt-14 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {FEATURES.map((feat, idx) => (
            <Card
              key={idx}
              className="relative overflow-hidden border-border/80 bg-card/60 hover:bg-card hover:border-primary/50 transition-all duration-300 hover:shadow-xl hover:-translate-y-1"
            >
              <CardHeader className="space-y-3">
                <div className="flex items-center justify-between">
                  <div className={`p-2.5 rounded-xl border ${feat.color}`}>
                    <feat.icon className="h-6 w-6" />
                  </div>
                  <Badge variant="secondary" className="text-[11px] font-medium">
                    {feat.badge}
                  </Badge>
                </div>
                <CardTitle className="text-xl font-bold text-foreground">{feat.title}</CardTitle>
                <CardDescription className="text-sm text-muted-foreground leading-relaxed">
                  {feat.description}
                </CardDescription>
              </CardHeader>
              <CardContent>
                <ul className="space-y-2 pt-2 border-t border-border/50">
                  {feat.points.map((pt, pIdx) => (
                    <li key={pIdx} className="flex items-center gap-2 text-xs font-medium text-foreground/80">
                      <CheckCircle2 className="h-3.5 w-3.5 text-primary shrink-0" />
                      <span>{pt}</span>
                    </li>
                  ))}
                </ul>
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    </section>
  );
}
