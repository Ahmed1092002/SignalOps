"use client";

import { useState } from "react";
import Link from "next/link";
import {
  Activity,
  AlertTriangle,
  ArrowRight,
  CheckCircle2,
  Clock,
  Layers,
  Radio,
  RefreshCw,
  Server,
  Shield,
  Sparkles,
  Users,
  Zap,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";

type SimState = "DETECTED" | "INVESTIGATING" | "IDENTIFIED" | "MONITORING" | "RESOLVED";

interface SimEvent {
  time: string;
  title: string;
  desc: string;
  type: "system" | "commander" | "update" | "resolve";
}

export function LandingHero() {
  const [stage, setStage] = useState<SimState>("INVESTIGATING");
  const [commander, setCommander] = useState<string>("Alex Mercer (Lead SRE)");
  const [stompEventCount, setStompEventCount] = useState<number>(4);

  const STAGES: { state: SimState; label: string; color: string; badgeCls: string }[] = [
    { state: "DETECTED", label: "Detected", color: "text-slate-400", badgeCls: "bg-slate-500/10 text-slate-400 border-slate-500/20" },
    { state: "INVESTIGATING", label: "Investigating", color: "text-amber-400", badgeCls: "bg-amber-500/10 text-amber-400 border-amber-500/20" },
    { state: "IDENTIFIED", label: "Identified", color: "text-purple-400", badgeCls: "bg-purple-500/10 text-purple-400 border-purple-500/20" },
    { state: "MONITORING", label: "Monitoring", color: "text-cyan-400", badgeCls: "bg-cyan-500/10 text-cyan-400 border-cyan-500/20" },
    { state: "RESOLVED", label: "Resolved", color: "text-emerald-400", badgeCls: "bg-emerald-500/10 text-emerald-400 border-emerald-500/20" },
  ];

  const handleAdvance = (next: SimState) => {
    setStage(next);
    setStompEventCount((c) => c + 1);
  };

  return (
    <section className="relative overflow-hidden pt-12 pb-20 md:pt-20 md:pb-28">
      {/* Dynamic Background Glows & Grids */}
      <div
        className="pointer-events-none absolute -top-40 left-1/2 -z-10 h-[500px] w-[800px] -translate-x-1/2 rounded-full bg-gradient-to-tr from-primary/20 via-sky-500/15 to-purple-500/10 blur-[130px]"
        aria-hidden="true"
      />
      <div
        className="pointer-events-none absolute inset-0 -z-20 bg-[radial-gradient(#6366f1_1px,transparent_1px)] [background-size:24px_24px] opacity-10 dark:opacity-15"
        aria-hidden="true"
      />

      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        {/* Top Badging */}
        <div className="flex flex-col items-center text-center">
          <div className="inline-flex items-center gap-2 rounded-full border border-primary/20 bg-primary/10 px-3.5 py-1 text-xs font-semibold text-primary transition-all hover:bg-primary/15 mb-6">
            <span className="flex h-2 w-2 rounded-full bg-primary animate-pulse"></span>
            <span>SignalOps 1.0 Production Platform</span>
            <span className="text-muted-foreground font-normal">|</span>
            <span className="text-foreground/80 font-medium flex items-center gap-1">
              Real-Time Incident Control <Sparkles className="h-3 w-3 text-amber-400" />
            </span>
          </div>

          {/* Main Headline */}
          <h1 className="max-w-4xl text-4xl font-extrabold tracking-tight sm:text-5xl md:text-6xl lg:text-7xl">
            When Production Breaks,{" "}
            <span className="bg-gradient-to-r from-primary via-indigo-400 to-sky-400 bg-clip-text text-transparent">
              SignalOps
            </span>{" "}
            Takes Command.
          </h1>

          {/* Subtitle */}
          <p className="mt-6 max-w-2xl text-lg text-muted-foreground sm:text-xl leading-relaxed">
            The mission-critical incident response platform for modern engineering teams. Declare
            incidents, enforce strict state machine lifecycles, coordinate responders with zero-latency
            STOMP WebSockets, and broadcast customer-safe status pages.
          </p>

          {/* Hero CTAs */}
          <div className="mt-8 flex flex-wrap items-center justify-center gap-4">
            <Button size="lg" asChild className="gap-2 px-6 h-12 text-base shadow-lg shadow-primary/25">
              <Link href="/register">
                <span>Start Free Organization</span>
                <ArrowRight className="h-4 w-4" />
              </Link>
            </Button>
            <Button size="lg" variant="outline" asChild className="gap-2 px-6 h-12 text-base bg-card/60 backdrop-blur-sm border-border/80 hover:bg-accent">
              <Link href="/login">
                <Radio className="h-4 w-4 text-primary animate-pulse" />
                <span>Launch Live Demo</span>
              </Link>
            </Button>
            <Button size="lg" variant="ghost" asChild className="gap-2 text-base text-muted-foreground hover:text-foreground">
              <Link href="/status/northstar-cloud">
                <Server className="h-4 w-4 text-emerald-500" />
                <span>Public Status Page</span>
              </Link>
            </Button>
          </div>

          <div className="mt-4 flex items-center gap-4 text-xs text-muted-foreground">
            <span className="flex items-center gap-1">
              <CheckCircle2 className="h-3.5 w-3.5 text-emerald-500" /> Multi-tenant by design
            </span>
            <span>•</span>
            <span className="flex items-center gap-1">
              <CheckCircle2 className="h-3.5 w-3.5 text-emerald-500" /> Spring Boot + Postgres 16
            </span>
            <span>•</span>
            <span className="flex items-center gap-1">
              <CheckCircle2 className="h-3.5 w-3.5 text-emerald-500" /> Instant STOMP Sync
            </span>
          </div>
        </div>

        {/* Live Interactive Incident Simulation Card */}
        <div className="mt-14 relative mx-auto max-w-5xl rounded-2xl border border-border/80 bg-card/85 p-4 sm:p-6 shadow-2xl backdrop-blur-xl ring-1 ring-white/10">
          {/* Header Bar */}
          <div className="flex flex-wrap items-center justify-between gap-4 border-b border-border/60 pb-4">
            <div className="flex items-center gap-3">
              <div className="flex h-3 w-3 gap-1.5">
                <div className="h-3 w-3 rounded-full bg-rose-500/80" />
                <div className="h-3 w-3 rounded-full bg-amber-500/80" />
                <div className="h-3 w-3 rounded-full bg-emerald-500/80" />
              </div>
              <div className="h-4 w-px bg-border" />
              <div className="flex items-center gap-2">
                <span className="font-mono text-xs font-semibold text-muted-foreground">INC-1049</span>
                <span className="text-sm font-semibold text-foreground">API Gateway Latency Spike & 504 Timeouts</span>
                <Badge variant="destructive" className="text-[10px] uppercase font-bold tracking-wider px-1.5 py-0">
                  CRITICAL (P1)
                </Badge>
              </div>
            </div>

            {/* Live STOMP Badge */}
            <div className="flex items-center gap-2 rounded-full border border-sky-500/20 bg-sky-500/10 px-3 py-1 text-xs font-medium text-sky-400">
              <span className="relative flex h-2 w-2">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-sky-400 opacity-75"></span>
                <span className="relative inline-flex rounded-full h-2 w-2 bg-sky-500"></span>
              </span>
              <span>STOMP /ws/events connected ({stompEventCount} updates received)</span>
            </div>
          </div>

          {/* Interactive Simulation Controls */}
          <div className="mt-4 rounded-lg bg-accent/40 p-3 border border-border/50">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="text-xs font-semibold text-muted-foreground uppercase tracking-wider flex items-center gap-1.5">
                <Zap className="h-3.5 w-3.5 text-primary" />
                <span>Interactive State Machine Simulation — Click to transition:</span>
              </div>
              <Button
                variant="ghost"
                size="sm"
                className="h-7 text-xs text-muted-foreground hover:text-foreground gap-1"
                onClick={() => {
                  setStage("INVESTIGATING");
                  setCommander("Alex Mercer (Lead SRE)");
                  setStompEventCount(4);
                }}
              >
                <RefreshCw className="h-3 w-3" /> Reset Demo
              </Button>
            </div>

            {/* Stepper Buttons */}
            <div className="mt-2.5 grid grid-cols-2 sm:grid-cols-5 gap-2">
              {STAGES.map((s, idx) => {
                const isActive = stage === s.state;
                return (
                  <button
                    key={s.state}
                    type="button"
                    onClick={() => handleAdvance(s.state)}
                    className={`flex flex-col items-center justify-center p-2 rounded-md border text-xs font-medium transition-all ${
                      isActive
                        ? "bg-primary text-primary-foreground border-primary shadow-md scale-[1.02]"
                        : "bg-background/60 hover:bg-background border-border text-muted-foreground hover:text-foreground"
                    }`}
                  >
                    <span className="text-[10px] opacity-75">Step {idx + 1}</span>
                    <span className="font-semibold">{s.label}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Mock Incident Body Grid */}
          <div className="mt-4 grid grid-cols-1 lg:grid-cols-3 gap-4">
            {/* Left: Incident Details & Commander */}
            <div className="space-y-3 rounded-lg border border-border/60 bg-background/50 p-4">
              <div className="text-xs font-semibold uppercase text-muted-foreground tracking-wider flex items-center justify-between">
                <span>Incident Controls</span>
                <Badge variant="outline" className="text-[10px]">Rule: R3 Enforced</Badge>
              </div>

              <div className="space-y-2 text-xs">
                <div className="flex justify-between py-1 border-b border-border/40">
                  <span className="text-muted-foreground">Affected Service:</span>
                  <span className="font-medium text-foreground">API Gateway / Ingress</span>
                </div>
                <div className="flex justify-between py-1 border-b border-border/40">
                  <span className="text-muted-foreground">Commander:</span>
                  <span className="font-semibold text-primary">{commander}</span>
                </div>
                <div className="flex justify-between py-1 border-b border-border/40">
                  <span className="text-muted-foreground">Current State:</span>
                  <span className="font-bold text-foreground">{stage}</span>
                </div>
                <div className="flex justify-between py-1 border-b border-border/40">
                  <span className="text-muted-foreground">Tenancy:</span>
                  <span className="font-mono text-foreground">org_northstar_01</span>
                </div>
              </div>

              <div className="pt-1">
                <div className="text-[11px] font-semibold text-muted-foreground mb-1.5">Active Responders in War Room</div>
                <div className="flex items-center gap-1.5">
                  <span className="inline-flex h-6 w-6 items-center justify-center rounded-full bg-indigo-500/20 text-indigo-400 font-bold text-[10px] border border-indigo-500/30">AM</span>
                  <span className="inline-flex h-6 w-6 items-center justify-center rounded-full bg-emerald-500/20 text-emerald-400 font-bold text-[10px] border border-emerald-500/30">SK</span>
                  <span className="inline-flex h-6 w-6 items-center justify-center rounded-full bg-amber-500/20 text-amber-400 font-bold text-[10px] border border-amber-500/30">JD</span>
                  <span className="text-xs text-muted-foreground ml-1">+3 observing</span>
                </div>
              </div>
            </div>

            {/* Center: Live Append-Only Timeline */}
            <div className="space-y-3 rounded-lg border border-border/60 bg-background/50 p-4">
              <div className="text-xs font-semibold uppercase text-muted-foreground tracking-wider flex items-center justify-between">
                <span>Append-Only Timeline</span>
                <span className="text-[11px] text-emerald-500 font-mono">● LIVE</span>
              </div>

              <div className="space-y-2.5 text-xs">
                <div className="flex gap-2.5 items-start">
                  <div className="h-2 w-2 rounded-full bg-rose-500 mt-1.5 shrink-0" />
                  <div>
                    <div className="font-semibold text-foreground">Incident Detected</div>
                    <div className="text-[11px] text-muted-foreground">Automatic telemetry alert breached 95th percentile latency threshold.</div>
                  </div>
                </div>

                <div className="flex gap-2.5 items-start">
                  <div className="h-2 w-2 rounded-full bg-indigo-500 mt-1.5 shrink-0" />
                  <div>
                    <div className="font-semibold text-foreground">Commander Assigned</div>
                    <div className="text-[11px] text-muted-foreground">{commander} assumed command. War room opened.</div>
                  </div>
                </div>

                {stage !== "DETECTED" && (
                  <div className="flex gap-2.5 items-start animate-in fade-in slide-in-from-left-2 duration-300">
                    <div className="h-2 w-2 rounded-full bg-amber-500 mt-1.5 shrink-0" />
                    <div>
                      <div className="font-semibold text-foreground">Transitioned to {stage}</div>
                      <div className="text-[11px] text-muted-foreground">
                        {stage === "RESOLVED"
                          ? "Root cause identified as memory leak in Redis pool; patch deployed & verified."
                          : "Responders analyzing ingress rate limiting & downstream connection pools."}
                      </div>
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* Right: Broadcast & Public Status Sync */}
            <div className="space-y-3 rounded-lg border border-border/60 bg-background/50 p-4">
              <div className="text-xs font-semibold uppercase text-muted-foreground tracking-wider flex items-center justify-between">
                <span>Customer Status Page</span>
                <Badge variant="outline" className="text-[10px] text-sky-400 border-sky-400/30">
                  Decoupled
                </Badge>
              </div>

              <div className="rounded-md bg-accent/60 p-2.5 border border-border/50 text-xs">
                <div className="flex items-center justify-between mb-1.5">
                  <span className="font-semibold text-foreground">Northstar Status Page</span>
                  <span className="text-[10px] font-mono text-emerald-400">status.northstar.io</span>
                </div>
                <div className="text-[11px] text-muted-foreground leading-relaxed">
                  {stage === "RESOLVED" ? (
                    <span className="text-emerald-400 font-medium">
                      All Systems Operational — Incidents resolved.
                    </span>
                  ) : (
                    <span className="text-amber-400 font-medium">
                      Investigating: Degraded performance on API Ingress.
                    </span>
                  )}
                </div>
              </div>

              <div className="rounded-md bg-zinc-950 p-2.5 font-mono text-[11px] text-emerald-400 border border-zinc-800">
                <div className="text-zinc-400 text-[10px] mb-1">// STOMP /topic/incidents/org_northstar_01</div>
                <div>{`{ "event": "STATUS_CHANGED", "to": "${stage}", "version": 4 }`}</div>
              </div>
            </div>
          </div>
        </div>

        {/* Bottom Trust Metrics Strip */}
        <div className="mt-14 grid grid-cols-2 gap-4 md:grid-cols-4">
          <div className="flex flex-col items-center text-center p-4 rounded-xl border border-border/50 bg-card/40 backdrop-blur-sm">
            <span className="text-3xl font-extrabold text-foreground tracking-tight">45%</span>
            <span className="text-xs font-medium text-muted-foreground mt-1">Average MTTR Reduction</span>
          </div>
          <div className="flex flex-col items-center text-center p-4 rounded-xl border border-border/50 bg-card/40 backdrop-blur-sm">
            <span className="text-3xl font-extrabold text-foreground tracking-tight">&lt; 50ms</span>
            <span className="text-xs font-medium text-muted-foreground mt-1">STOMP Broadcast Latency</span>
          </div>
          <div className="flex flex-col items-center text-center p-4 rounded-xl border border-border/50 bg-card/40 backdrop-blur-sm">
            <span className="text-3xl font-extrabold text-foreground tracking-tight">100%</span>
            <span className="text-xs font-medium text-muted-foreground mt-1">Append-Only Audit Trail</span>
          </div>
          <div className="flex flex-col items-center text-center p-4 rounded-xl border border-border/50 bg-card/40 backdrop-blur-sm">
            <span className="text-3xl font-extrabold text-foreground tracking-tight">99.99%</span>
            <span className="text-xs font-medium text-muted-foreground mt-1">Platform High Availability</span>
          </div>
        </div>
      </div>
    </section>
  );
}
