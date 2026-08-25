"use client";

import { useQuery } from "@tanstack/react-query";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { PageHeader } from "@/components/app/page-header";
import { StatCard } from "@/components/app/stat-card";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { CardSkeleton, EmptyState, ErrorState } from "@/components/app/states";
import { http } from "@/lib/http";
import { formatMeanDuration } from "@/lib/format";
import { useSessionStore } from "@/stores/session-store";

interface Overview {
  days: number;
  series: { date: string; created: number; resolved: number }[];
  severityDistribution: { severity: string; count: number }[];
  perService: { serviceName: string; count: number }[];
  meanTimeToResolveSeconds: number | null;
  meanTimeToDetectSeconds: number | null;
  resolvedCount: number;
}

function fetchOverview(signal?: AbortSignal): Promise<Overview> {
  return http<Overview>("/analytics/overview?days=30", { signal });
}

export default function AnalyticsPage() {
  const activeOrgId = useSessionStore((s) => s.activeOrgId);
  const overviewQuery = useQuery({
    queryKey: ["organization", activeOrgId ?? "?", "analytics", 30],
    queryFn: ({ signal }) => fetchOverview(signal),
    enabled: Boolean(activeOrgId),
  });

  const overview = overviewQuery.data;

  return (
    <div>
      <PageHeader
        title="Analytics"
        description="Incident trends, severity distribution, and resolution performance over the last 30 days."
      />

      {overviewQuery.isLoading && <CardSkeleton lines={8} />}
      {overviewQuery.isError && (
        <ErrorState error={overviewQuery.error} onRetry={() => overviewQuery.refetch()} />
      )}

      {overview && (
        <div className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-3">
            <StatCard
              label="Mean time to resolve"
              value={formatMeanDuration(overview.meanTimeToResolveSeconds)}
              hint={overview.resolvedCount + " resolved incidents in window"}
            />
            <StatCard
              label="Mean time to detect"
              value={formatMeanDuration(overview.meanTimeToDetectSeconds)}
              hint="Created → acknowledged"
            />
            <StatCard
              label="Resolved in window"
              value={String(overview.resolvedCount)}
              tone="success"
            />
          </div>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">Created vs resolved per day</CardTitle>
            </CardHeader>
            <CardContent>
              {overview.series.every((point) => point.created === 0 && point.resolved === 0) ? (
                <EmptyState title="No incidents in this window" className="py-10" />
              ) : (
                <div className="h-72" role="img" aria-label="Incident trend chart">
                  <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={overview.series}>
                      <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                      <XAxis dataKey="date" tick={{ fontSize: 11, fill: "var(--muted-foreground)" }} />
                      <YAxis allowDecimals={false} tick={{ fontSize: 11, fill: "var(--muted-foreground)" }} />
                      <Tooltip
                        contentStyle={{
                          background: "var(--popover)",
                          border: "1px solid var(--border)",
                          borderRadius: 8,
                          color: "var(--popover-foreground)",
                        }}
                      />
                      <Legend />
                      <Line
                        type="monotone"
                        dataKey="created"
                        name="Created"
                        stroke="var(--chart-4)"
                        strokeWidth={2}
                        dot={false}
                      />
                      <Line
                        type="monotone"
                        dataKey="resolved"
                        name="Resolved"
                        stroke="var(--chart-3)"
                        strokeWidth={2}
                        dot={false}
                      />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              )}
            </CardContent>
          </Card>

          <div className="grid gap-4 lg:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle className="text-base">Severity distribution</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="h-64" role="img" aria-label="Severity distribution chart">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={overview.severityDistribution}>
                      <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                      <XAxis dataKey="severity" tick={{ fontSize: 11, fill: "var(--muted-foreground)" }} />
                      <YAxis allowDecimals={false} tick={{ fontSize: 11, fill: "var(--muted-foreground)" }} />
                      <Tooltip
                        contentStyle={{
                          background: "var(--popover)",
                          border: "1px solid var(--border)",
                          borderRadius: 8,
                          color: "var(--popover-foreground)",
                        }}
                      />
                      <Bar dataKey="count" name="Incidents" fill="var(--chart-1)" radius={[4, 4, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-base">Incidents per service</CardTitle>
              </CardHeader>
              <CardContent>
                {overview.perService.length === 0 ? (
                  <EmptyState title="No incidents in this window" className="py-10" />
                ) : (
                  <div className="h-64" role="img" aria-label="Incidents per service chart">
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={overview.perService} layout="vertical">
                        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                        <XAxis type="number" allowDecimals={false} tick={{ fontSize: 11, fill: "var(--muted-foreground)" }} />
                        <YAxis
                          type="category"
                          dataKey="serviceName"
                          width={140}
                          tick={{ fontSize: 11, fill: "var(--muted-foreground)" }}
                        />
                        <Tooltip
                          contentStyle={{
                            background: "var(--popover)",
                            border: "1px solid var(--border)",
                            borderRadius: 8,
                            color: "var(--popover-foreground)",
                          }}
                        />
                        <Bar dataKey="count" name="Incidents" fill="var(--chart-2)" radius={[0, 4, 4, 0]} />
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                )}
              </CardContent>
            </Card>
          </div>
        </div>
      )}
    </div>
  );
}
