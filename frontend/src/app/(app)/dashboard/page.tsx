"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { ServiceStatusBadge } from "@/components/app/badges";
import { PageHeader } from "@/components/app/page-header";
import { StatCard } from "@/components/app/stat-card";
import { CardSkeleton, EmptyState, ErrorState } from "@/components/app/states";
import { PhaseBadge } from "@/components/app/phase-badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { dashboardSummary } from "@/lib/api/dashboard";
import { formatMeanDuration, formatRelative } from "@/lib/format";
import { qk } from "@/lib/query-keys";
import { useSessionStore } from "@/stores/session-store";

function eventLabel(type: string): string {
  return type
    .toLowerCase()
    .replace(/^incident_/, "")
    .replace(/_/g, " ");
}

export default function DashboardPage() {
  const activeOrgId = useSessionStore((s) => s.activeOrgId);
  const summaryQuery = useQuery({
    queryKey: qk.dashboard.summary(activeOrgId ?? "?"),
    queryFn: ({ signal }) => dashboardSummary(signal),
    enabled: Boolean(activeOrgId),
  });

  const summary = summaryQuery.data;

  return (
    <div>
      <PageHeader
        title="Operations Dashboard"
        description="Live state of your organization at a glance."
      />

      {summaryQuery.isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-busy="true">
          {Array.from({ length: 4 }).map((_, index) => (
            <CardSkeleton key={index} lines={2} />
          ))}
        </div>
      )}
      {summaryQuery.isError && (
        <ErrorState error={summaryQuery.error} onRetry={() => summaryQuery.refetch()} />
      )}

      {summary && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <StatCard label="Active incidents" value={String(summary.activeIncidents)} tone={summary.activeIncidents > 0 ? "warning" : "success"} />
            <StatCard
              label="Critical incidents"
              value={String(summary.criticalIncidents)}
              tone={summary.criticalIncidents > 0 ? "critical" : "default"}
              hint={summary.criticalIncidents > 0 ? "Requires action" : "All clear"}
            />
            <StatCard label="Incidents this month" value={String(summary.incidentsThisMonth)} />
            <StatCard
              label="Mean resolution"
              value={formatMeanDuration(summary.meanResolutionSeconds)}
              hint={summary.meanResolutionSeconds == null ? "No resolved incidents yet" : undefined}
            />
          </div>

          <div className="mt-6 grid gap-4 lg:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle className="text-base">Service health</CardTitle>
              </CardHeader>
              <CardContent>
                {summary.serviceHealth.length === 0 ? (
                  <EmptyState
                    title="No services yet"
                    description="Create services to track their derived health."
                    className="py-8"
                  />
                ) : (
                  <ul className="divide-y">
                    {summary.serviceHealth.map((health) => (
                      <li key={health.serviceId} className="flex items-center justify-between gap-3 py-2.5">
                        <div className="min-w-0">
                          <p className="truncate text-sm font-medium">{health.name}</p>
                          <p className="text-xs text-muted-foreground">
                            {health.activeIncidents} active incident{health.activeIncidents === 1 ? "" : "s"}
                          </p>
                        </div>
                        <ServiceStatusBadge status={health.status} />
                      </li>
                    ))}
                  </ul>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="flex-row items-center justify-between space-y-0">
                <CardTitle className="text-base">Recent activity</CardTitle>
                <Link href="/incidents" className="text-xs font-medium text-primary hover:underline">
                  View all
                </Link>
              </CardHeader>
              <CardContent>
                {summary.recentActivity.length === 0 ? (
                  <EmptyState
                    title="No activity yet"
                    description="Timeline events will appear here as your team works incidents."
                    className="py-8"
                  />
                ) : (
                  <ul className="space-y-3" aria-live="polite">
                    {summary.recentActivity.map((item, index) => (
                      <li key={index} className="flex items-start justify-between gap-3 text-sm">
                        <div className="min-w-0">
                          <span className="text-num text-muted-foreground">{item.incidentKey}</span>{" "}
                          <span className="font-medium">{eventLabel(item.eventType)}</span>
                          {item.actorName && (
                            <span className="text-muted-foreground"> · {item.actorName}</span>
                          )}
                        </div>
                        <time className="shrink-0 text-xs text-muted-foreground text-num" dateTime={item.occurredAt}>
                          {formatRelative(item.occurredAt)}
                        </time>
                      </li>
                    ))}
                  </ul>
                )}
              </CardContent>
            </Card>
          </div>

          <Card className="mt-6 opacity-70">
            <CardContent className="flex items-center justify-between gap-3 p-4">
              <p className="text-sm text-muted-foreground">Incident trend charts</p>
              <PhaseBadge phase="Phase 3 — not part of the MVP" />
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}
