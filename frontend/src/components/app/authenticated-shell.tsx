"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import { AppShell } from "@/components/app/app-shell";
import { CreateIncidentDialog } from "@/components/app/create-incident-dialog";
import { fetchMe } from "@/lib/api/auth";
import { connectRealtime, disconnectRealtime } from "@/lib/ws-client";
import { useSessionStore } from "@/stores/session-store";

/**
 * Guard for the authenticated tree: requires a restored session and a valid
 * active organization. Once both exist, the Phase 2 realtime client connects;
 * organization-scoped caches are keyed by organization ID, so a switch can
 * never leak data across tenants.
 */
export function AuthenticatedShell({ children }: { children: React.ReactNode }) {
  const queryClient = useQueryClient();
  const status = useSessionStore((s) => s.status);
  const activeOrgId = useSessionStore((s) => s.activeOrgId);
  const memberships = useSessionStore((s) => s.memberships);
  const setMemberships = useSessionStore((s) => s.setMemberships);
  const setActiveOrg = useSessionStore((s) => s.setActiveOrg);

  useEffect(() => {
    if (status !== "authenticated") return;
    if (memberships.length === 0) {
      fetchMe()
        .then((me) => {
          setMemberships(me.memberships);
          const firstActive = me.memberships.find((m) => m.status === "ACTIVE");
          if (firstActive && !activeOrgId) {
            setActiveOrg(firstActive.organizationId);
          }
        })
        .catch(() => {});
    } else if (!activeOrgId) {
      const firstActive = memberships.find((m) => m.status === "ACTIVE");
      if (firstActive) {
        setActiveOrg(firstActive.organizationId);
      }
    }
  }, [status, activeOrgId, memberships, setActiveOrg, setMemberships]);

  useEffect(() => {
    if (status === "authenticated" && activeOrgId) {
      connectRealtime(queryClient, () => {
        window.dispatchEvent(new CustomEvent("signalops:notification"));
      });
    } else {
      disconnectRealtime();
    }
    return () => disconnectRealtime();
  }, [status, activeOrgId, queryClient]);

  if (status === "anonymous") {
    return (
      <div className="flex min-h-dvh flex-col items-center justify-center gap-4 bg-background p-6 text-center">
        <p className="text-sm text-muted-foreground">Your session has ended.</p>
        <a href="/login" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
          Sign in again
        </a>
      </div>
    );
  }

  const membership = memberships.find(
    (m) => m.organizationId === activeOrgId && m.status === "ACTIVE"
  );

  if (status === "authenticated" && !membership) {
    return (
      <div className="flex min-h-dvh flex-col items-center justify-center gap-4 bg-background p-6 text-center">
        <h1 className="text-lg font-semibold">No active organization</h1>
        <p className="max-w-sm text-sm text-muted-foreground">
          You are not an active member of an organization. Accept an invitation or contact your
          organization owner.
        </p>
      </div>
    );
  }

  return (
    <AppShell>
      <main className="flex-1 px-4 py-6 md:px-6 lg:px-8">{children}</main>
      <CreateIncidentDialog />
    </AppShell>
  );
}
