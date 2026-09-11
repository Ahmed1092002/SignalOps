import Link from "next/link";
import { ExternalLink, Radio, Server, Shield } from "lucide-react";
import { SignalOpsLogo } from "@/components/app/logo";

export function LandingFooter() {
  return (
    <footer className="border-t border-border/60 bg-card/60 backdrop-blur-sm">
      <div className="mx-auto max-w-7xl px-4 py-12 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-8">
          {/* Brand Info */}
          <div className="md:col-span-2 space-y-4">
            <Link href="/" className="inline-block">
              <SignalOpsLogo iconSize={34} />
            </Link>
            <p className="text-sm text-muted-foreground max-w-sm leading-relaxed">
              Real-time incident management for software, operations, and DevOps teams. Declare
              incidents, enforce controlled lifecycles, coordinate responders, and protect customer trust.
            </p>
            <div className="flex items-center gap-3 text-xs text-muted-foreground pt-2">
              <span className="flex items-center gap-1">
                <Shield className="h-3.5 w-3.5 text-primary" /> Multi-Tenant Security
              </span>
              <span>•</span>
              <span className="flex items-center gap-1">
                <Radio className="h-3.5 w-3.5 text-sky-400" /> STOMP WebSockets
              </span>
            </div>
          </div>

          {/* Product Links */}
          <div className="space-y-3">
            <h4 className="text-xs font-bold uppercase tracking-wider text-foreground">Platform</h4>
            <ul className="space-y-2 text-sm text-muted-foreground">
              <li>
                <a href="#features" className="hover:text-primary transition-colors">
                  Core Capabilities
                </a>
              </li>
              <li>
                <a href="#lifecycle" className="hover:text-primary transition-colors">
                  State Machine Workflow
                </a>
              </li>
              <li>
                <a href="#tour" className="hover:text-primary transition-colors">
                  Product Tour
                </a>
              </li>
              <li>
                <a href="#architecture" className="hover:text-primary transition-colors">
                  Architecture & Stack
                </a>
              </li>
              <li>
                <Link
                  href="/status/northstar-cloud"
                  className="inline-flex items-center gap-1 text-emerald-500 hover:text-emerald-400 font-medium transition-colors"
                >
                  <span>Public Status Demo</span>
                  <ExternalLink className="h-3 w-3" />
                </Link>
              </li>
            </ul>
          </div>

          {/* Quick Access / Authentication */}
          <div className="space-y-3">
            <h4 className="text-xs font-bold uppercase tracking-wider text-foreground">Access</h4>
            <ul className="space-y-2 text-sm text-muted-foreground">
              <li>
                <Link href="/login" className="hover:text-primary transition-colors">
                  Sign In
                </Link>
              </li>
              <li>
                <Link href="/register" className="hover:text-primary transition-colors">
                  Register Organization
                </Link>
              </li>
              <li>
                <a href="#demo" className="hover:text-primary transition-colors">
                  Pre-seeded Demo Accounts
                </a>
              </li>
              <li>
                <a href="#faq" className="hover:text-primary transition-colors">
                  DevOps FAQ
                </a>
              </li>
            </ul>
          </div>
        </div>

        {/* Bottom Bar */}
        <div className="mt-12 border-t border-border/50 pt-6 flex flex-wrap items-center justify-between gap-4 text-xs text-muted-foreground">
          <p>© {new Date().getFullYear()} SignalOps Platform. All rights reserved.</p>
          <div className="flex items-center gap-6">
            <Link href="/status/northstar-cloud" className="hover:text-foreground transition-colors flex items-center gap-1.5">
              <span className="h-2 w-2 rounded-full bg-emerald-500 inline-block" />
              <span>Systems Normal</span>
            </Link>
          </div>
        </div>
      </div>
    </footer>
  );
}
