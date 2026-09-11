"use client";

import { useState } from "react";
import Link from "next/link";
import {
  ArrowRight,
  Check,
  Copy,
  KeyRound,
  Lock,
  Radio,
  Shield,
  Sparkles,
  UserCheck,
  Users,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

const DEMO_ACCOUNTS = [
  {
    role: "Owner",
    email: "owner@demo.signalops.io",
    badge: "Full Org Admin & Billing",
    desc: "Complete access to create organizations, manage team permissions, and configure services.",
  },
  {
    role: "Incident Commander",
    email: "commander@demo.signalops.io",
    badge: "War Room Leadership",
    desc: "Can assume command on CRITICAL outages, transition lifecycle states, and publish public notices.",
  },
  {
    role: "Responder",
    email: "responder@demo.signalops.io",
    badge: "Active Troubleshooting",
    desc: "Can participate in war rooms, post investigation comments, and manage follow-up remediation tasks.",
  },
  {
    role: "Viewer",
    email: "viewer@demo.signalops.io",
    badge: "Read-Only Observer",
    desc: "Read-only access for stakeholders, customer success agents, and executive leadership.",
  },
];

export function LandingDemoCredentials() {
  const [copiedEmail, setCopiedEmail] = useState<string | null>(null);
  const [copiedPass, setCopiedPass] = useState(false);

  const copyToClipboard = (text: string, isPassword = false) => {
    navigator.clipboard.writeText(text);
    if (isPassword) {
      setCopiedPass(true);
      setTimeout(() => setCopiedPass(false), 2000);
    } else {
      setCopiedEmail(text);
      setTimeout(() => setCopiedEmail(null), 2000);
    }
  };

  return (
    <section id="demo" className="py-20 bg-accent/20 border-y border-border/50">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="text-center max-w-3xl mx-auto">
          <Badge variant="outline" className="mb-3 border-primary/30 text-primary bg-primary/5">
            Instant Test Drive
          </Badge>
          <h2 className="text-3xl font-extrabold tracking-tight sm:text-4xl text-foreground">
            Test Drive SignalOps with Pre-Seeded Environments
          </h2>
          <p className="mt-4 text-base sm:text-lg text-muted-foreground">
            Evaluate the platform immediately without filling out sign-up forms. Use any of the pre-seeded demo accounts in the **Northstar Cloud** organization.
          </p>
        </div>

        {/* Global Password Banner */}
        <div className="mt-8 max-w-md mx-auto rounded-xl border border-primary/30 bg-primary/10 p-4 flex items-center justify-between shadow-sm">
          <div className="flex items-center gap-3">
            <KeyRound className="h-5 w-5 text-primary shrink-0" />
            <div>
              <div className="text-xs text-muted-foreground font-medium">Universal Demo Password:</div>
              <div className="font-mono text-sm font-bold text-foreground">Demo1234!</div>
            </div>
          </div>
          <Button
            size="sm"
            variant="outline"
            onClick={() => copyToClipboard("Demo1234!", true)}
            className="text-xs gap-1 bg-background/80"
          >
            {copiedPass ? <Check className="h-3.5 w-3.5 text-emerald-500" /> : <Copy className="h-3.5 w-3.5" />}
            <span>{copiedPass ? "Copied" : "Copy Password"}</span>
          </Button>
        </div>

        {/* Demo Account Cards */}
        <div className="mt-8 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {DEMO_ACCOUNTS.map((acc, i) => (
            <div
              key={i}
              className="rounded-xl border border-border/80 bg-card p-5 space-y-3 shadow-md flex flex-col justify-between"
            >
              <div>
                <div className="flex items-center justify-between mb-1.5">
                  <span className="font-bold text-sm text-foreground">{acc.role}</span>
                  <Badge variant="secondary" className="text-[10px]">
                    {acc.badge}
                  </Badge>
                </div>
                <p className="text-xs text-muted-foreground leading-relaxed mb-3">{acc.desc}</p>
              </div>

              <div className="pt-2 border-t border-border/50 space-y-2">
                <div className="rounded bg-accent/60 p-1.5 font-mono text-[11px] text-foreground flex items-center justify-between">
                  <span className="truncate mr-1">{acc.email}</span>
                  <button
                    type="button"
                    onClick={() => copyToClipboard(acc.email)}
                    className="p-1 hover:text-primary transition-colors text-muted-foreground"
                    title="Copy email"
                  >
                    {copiedEmail === acc.email ? (
                      <Check className="h-3.5 w-3.5 text-emerald-500" />
                    ) : (
                      <Copy className="h-3.5 w-3.5" />
                    )}
                  </button>
                </div>
                <Button size="sm" variant="default" asChild className="w-full text-xs h-8">
                  <Link href="/login">Sign In as {acc.role.split(" ")[0]}</Link>
                </Button>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
