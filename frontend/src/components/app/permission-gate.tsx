"use client";

import {
  hasIncidentPermission,
  hasOrgPermission,
  permissionExplanation,
  type IncidentPermission,
  type OrgPermission,
  type PermissionSubject,
} from "@/lib/domain/permissions";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { activeMembership } from "@/stores/session-store";
import type { IncidentView } from "@/lib/types";

export function currentSubject(incident?: IncidentView | null, userId?: string | null): PermissionSubject {
  const membership = activeMembership();
  const role = membership?.role ?? "VIEWER";
  if (!incident || !userId) return { role };
  return {
    role,
    isCommander: incident.commander?.userId === userId,
    isResponder: incident.responders.some((r) => r.userId === userId),
  };
}

export function PermissionGate({
  permission,
  incident,
  children,
  fallback = "hide",
}: {
  permission: IncidentPermission | OrgPermission;
  incident?: IncidentView | null;
  children: React.ReactNode;
  fallback?: "hide" | "disable" | null;
}) {
  const user = useCurrentUser();
  const subject = currentSubject(incident, user?.id ?? null);
  const allowed =
    permission.includes(":") && !permission.startsWith("incident:")
      ? hasOrgPermission(subject, permission as OrgPermission)
      : hasIncidentPermission(subject, permission as IncidentPermission);

  if (allowed) return <>{children}</>;
  if (fallback === "hide" || fallback === null) return null;
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <span tabIndex={0} className="inline-flex cursor-not-allowed opacity-50">
            {children}
          </span>
        </TooltipTrigger>
        <TooltipContent>{permissionExplanation(subject, permission)}</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}

import { useSessionStore } from "@/stores/session-store";

function useCurrentUser() {
  return useSessionStore((s) => s.user);
}
