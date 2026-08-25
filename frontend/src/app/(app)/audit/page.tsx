"use client";

import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { PageHeader } from "@/components/app/page-header";
import { EmptyState, ErrorState, TableSkeleton } from "@/components/app/states";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { listAuditEvents } from "@/lib/api/dashboard";
import { formatUtcTimestamp } from "@/lib/format";
import { qk } from "@/lib/query-keys";
import { activeMembership, useSessionStore } from "@/stores/session-store";

const ACTIONS = [
  "USER_INVITED",
  "MEMBER_ROLE_CHANGED",
  "MEMBER_DISABLED",
  "MEMBER_REMOVED",
  "SERVICE_CREATED",
  "SERVICE_UPDATED",
  "SERVICE_ARCHIVED",
  "ORG_SETTINGS_UPDATED",
  "INCIDENT_EDITED",
  "PASSWORD_CHANGED",
];

function AuditContent() {
  const activeOrgId = useSessionStore((s) => s.activeOrgId);
  const [action, setAction] = useState("");
  const [page, setPage] = useState(0);

  const auditQuery = useQuery({
    queryKey: qk.audit.list(activeOrgId ?? "?", { action, page }),
    queryFn: ({ signal }) =>
      listAuditEvents({ action: action || undefined, page, size: 15 }, signal),
    enabled: Boolean(activeOrgId),
    placeholderData: (previous) => previous,
  });

  return (
    <div>
      <PageHeader
        title="Audit Log"
        description="Organization-level security and administrative activity. Read-only."
      />
      <div className="mb-4">
        <Select
          value={action || "all"}
          onValueChange={(value) => {
            setAction(value === "all" ? "" : value);
            setPage(0);
          }}
        >
          <SelectTrigger className="w-56" aria-label="Filter by action">
            <SelectValue placeholder="All actions" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All actions</SelectItem>
            {ACTIONS.map((value) => (
              <SelectItem key={value} value={value}>
                {value.replaceAll("_", " ")}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {auditQuery.isError && (auditQuery.error as { status?: number }).status === 403 ? (
        <EmptyState
          title="Only Owners and Admins can view the audit log"
          description="Your role does not include audit visibility."
        />
      ) : (
        <>
          {auditQuery.isLoading && <TableSkeleton rows={6} columns={4} />}
          {auditQuery.isError && (
            <ErrorState error={auditQuery.error} onRetry={() => auditQuery.refetch()} />
          )}

          {auditQuery.data && auditQuery.data.items.length === 0 && (
            <EmptyState
              title="No audit events"
              description="Administrative actions (invites, role changes, service edits) appear here."
            />
          )}

          {auditQuery.data && auditQuery.data.items.length > 0 && (
            <>
              <div className="overflow-x-auto rounded-lg border">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b bg-muted/40 text-left text-xs uppercase tracking-wider text-muted-foreground">
                      <th scope="col" className="px-4 py-2.5 font-medium">When</th>
                      <th scope="col" className="px-4 py-2.5 font-medium">Action</th>
                      <th scope="col" className="px-4 py-2.5 font-medium">Entity</th>
                      <th scope="col" className="px-4 py-2.5 font-medium">Details</th>
                    </tr>
                  </thead>
                  <tbody>
                    {auditQuery.data.items.map((event) => (
                      <tr key={event.id} className="border-b last:border-0">
                        <td className="whitespace-nowrap px-4 py-2.5 text-num text-muted-foreground">
                          {formatUtcTimestamp(event.occurredAt)}
                        </td>
                        <td className="px-4 py-2.5 font-medium">
                          {event.action.replaceAll("_", " ")}
                        </td>
                        <td className="px-4 py-2.5 text-muted-foreground">{event.entityType}</td>
                        <td className="px-4 py-2.5">
                          <span className="line-clamp-1 text-muted-foreground">
                            {Object.entries(event.metadata)
                              .map(([key, value]) => key + ": " + String(value))
                              .join(" · ") || "—"}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="mt-3 flex items-center justify-between text-sm text-muted-foreground">
                <span aria-live="polite">
                  Page {auditQuery.data.page + 1} of {Math.max(auditQuery.data.totalPages, 1)}
                </span>
                <div className="flex gap-2">
                  <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>
                    Previous
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={page + 1 >= auditQuery.data.totalPages}
                    onClick={() => setPage(page + 1)}
                  >
                    Next
                  </Button>
                </div>
              </div>
            </>
          )}
        </>
      )}
    </div>
  );
}

export default function AuditPage() {
  const membership = activeMembership();
  const isAdminLike = membership?.role === "OWNER" || membership?.role === "ADMIN";

  if (!isAdminLike) {
    return (
      <div>
        <PageHeader title="Audit Log" />
        <EmptyState
          title="Only Owners and Admins can view the audit log"
          description="Your role does not include audit visibility. The backend enforces this even if this page is opened directly."
        />
      </div>
    );
  }
  return <AuditContent />;
}
