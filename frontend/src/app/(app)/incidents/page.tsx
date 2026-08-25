"use client";

import { useQuery } from "@tanstack/react-query";
import { Plus, Search } from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { IncidentStatusBadge, SeverityBadge } from "@/components/app/badges";
import { PageHeader } from "@/components/app/page-header";
import { EmptyState, ErrorState, TableSkeleton } from "@/components/app/states";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { listIncidents } from "@/lib/api/incidents";
import { listServices } from "@/lib/api/services";
import type { IncidentListFilters } from "@/lib/types";
import { formatDuration, formatRelative } from "@/lib/format";
import { INCIDENT_STATUSES } from "@/lib/domain/incident";
import { SEVERITIES } from "@/lib/domain/severity";
import { qk } from "@/lib/query-keys";
import { useSessionStore } from "@/stores/session-store";
import { useUiStore } from "@/stores/ui-store";

function IncidentsContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const activeOrgId = useSessionStore((s) => s.activeOrgId);
  const setCreateOpen = useUiStore((s) => s.setCreateIncidentOpen);

  const page = Math.max(0, Number.parseInt(searchParams.get("page") ?? "0", 10) || 0);
  const q = searchParams.get("q") ?? "";
  const status = searchParams.get("status") ?? "";
  const severity = searchParams.get("severity") ?? "";
  const serviceId = searchParams.get("service") ?? "";
  const archived = searchParams.get("archived") === "true";

  const servicesQuery = useQuery({
    queryKey: qk.services.list(activeOrgId ?? "?", false),
    queryFn: ({ signal }) => listServices(false, signal),
    enabled: Boolean(activeOrgId),
  });

  const incidentsQuery = useQuery({
    queryKey: qk.incidents.list(activeOrgId ?? "?", {
      q, status, severity, serviceId, archived, page, size: 10,
    }),
    queryFn: ({ signal }) =>
      listIncidents(
        {
          q: q || undefined,
          status: (status || undefined) as IncidentListFilters["status"],
          severity: (severity || undefined) as IncidentListFilters["severity"],
          serviceId: serviceId || undefined,
          archived,
          page,
          size: 10,
        },
        signal
      ),
    enabled: Boolean(activeOrgId),
    placeholderData: (previous) => previous,
  });

  function setParam(name: string, value: string) {
    const params = new URLSearchParams(searchParams.toString());
    if (value) params.set(name, value);
    else params.delete(name);
    if (name !== "page") params.delete("page");
    router.replace(`/incidents${params.size ? "?" + params.toString() : ""}`, {
      scroll: false,
    });
  }

  const data = incidentsQuery.data;
  const isFetching = incidentsQuery.isFetching;

  return (
    <div>
      <PageHeader
        title="Incidents"
        description="Search, filter, and work your organization's incidents."
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <Plus className="mr-1 h-4 w-4" aria-hidden="true" /> New incident
          </Button>
        }
      />

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <form
          role="search"
          className="relative"
          onSubmit={(event) => {
            event.preventDefault();
            const value = new FormData(event.currentTarget).get("q");
            setParam("q", String(value ?? "").trim());
          }}
        >
          <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
          <input
            name="q"
            defaultValue={q}
            placeholder="Search title or key…"
            aria-label="Search incidents"
            className="h-9 w-56 rounded-md border border-input bg-transparent px-8 text-sm focus-visible:outline-2 focus-visible:outline-ring"
          />
        </form>

        <Select value={status || "all"} onValueChange={(value) => setParam("status", value === "all" ? "" : value)}>
          <SelectTrigger className="w-36" aria-label="Filter by status">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All statuses</SelectItem>
            {INCIDENT_STATUSES.map((value) => (
              <SelectItem key={value} value={value}>
                {value.charAt(0) + value.slice(1).toLowerCase()}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select value={severity || "all"} onValueChange={(value) => setParam("severity", value === "all" ? "" : value)}>
          <SelectTrigger className="w-32" aria-label="Filter by severity">
            <SelectValue placeholder="Severity" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All severities</SelectItem>
            {SEVERITIES.map((value) => (
              <SelectItem key={value} value={value}>
                {value.charAt(0) + value.slice(1).toLowerCase()}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select value={serviceId || "all"} onValueChange={(value) => setParam("service", value === "all" ? "" : value)}>
          <SelectTrigger className="w-40" aria-label="Filter by service">
            <SelectValue placeholder="Service" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All services</SelectItem>
            {servicesQuery.data?.map((service) => (
              <SelectItem key={service.id} value={service.id}>
                {service.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <label className="flex items-center gap-2 text-sm text-muted-foreground">
          <Switch checked={archived} onCheckedChange={(checked) => setParam("archived", checked ? "true" : "")} />
          Archived
        </label>

        {(q || status || severity || serviceId || archived) && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => router.replace("/incidents")}
          >
            Clear filters
          </Button>
        )}
      </div>

      {incidentsQuery.isLoading && <TableSkeleton rows={6} columns={6} />}
      {incidentsQuery.isError && (
        <ErrorState error={incidentsQuery.error} onRetry={() => incidentsQuery.refetch()} />
      )}

      {data && data.items.length === 0 && (
        <EmptyState
          title={q || status || severity || serviceId ? "No incidents match your filters" : "No incidents yet"}
          description={
            q || status || severity || serviceId
              ? "Try adjusting or clearing the filters."
              : "When something breaks, declare an incident to coordinate the response."
          }
          action={
            !q && !status && !severity && !serviceId && !archived ? (
              <Button onClick={() => setCreateOpen(true)}>
                <Plus className="mr-1 h-4 w-4" aria-hidden="true" /> Declare incident
              </Button>
            ) : undefined
          }
        />
      )}

      {data && data.items.length > 0 && (
        <div className="relative" aria-busy={isFetching}>
          {isFetching && (
            <div className="absolute inset-x-0 top-0 h-0.5 animate-pulse bg-primary" aria-hidden="true" />
          )}
          <ul className="grid gap-2 md:hidden">
            {data.items.map((incident) => (
              <li key={incident.id}>
                <Link
                  href={`/incidents/${incident.id}`}
                  className="block rounded-lg border p-3 transition-colors hover:bg-accent focus-visible:outline-2 focus-visible:outline-ring"
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-num text-xs text-muted-foreground">{incident.key}</span>
                    <SeverityBadge severity={incident.severity} />
                  </div>
                  <p className="mt-1 line-clamp-2 text-sm font-medium">{incident.title}</p>
                  <div className="mt-2 flex items-center justify-between gap-2">
                    <IncidentStatusBadge status={incident.status} />
                    <span className="text-xs text-muted-foreground">
                      {formatRelative(incident.createdAt)}
                    </span>
                  </div>
                </Link>
              </li>
            ))}
          </ul>

          <div className="hidden overflow-x-auto rounded-lg border md:block">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-muted/40 text-left text-xs uppercase tracking-wider text-muted-foreground">
                  <th scope="col" className="px-4 py-2.5 font-medium">Key</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">Title</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">Severity</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">Status</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">Services</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">Commander</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">Created</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">Duration</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((incident) => (
                  <tr
                    key={incident.id}
                    className="border-b transition-colors last:border-0 hover:bg-accent/50"
                  >
                    <td className="px-4 py-2.5">
                      <Link
                        href={`/incidents/${incident.id}`}
                        className="text-num text-primary hover:underline focus-visible:outline-2 focus-visible:outline-ring"
                      >
                        {incident.key}
                      </Link>
                    </td>
                    <td className="max-w-[22rem] px-4 py-2.5">
                      <Link href={`/incidents/${incident.id}`} className="line-clamp-1 font-medium hover:underline">
                        {incident.title}
                      </Link>
                    </td>
                    <td className="px-4 py-2.5"><SeverityBadge severity={incident.severity} /></td>
                    <td className="px-4 py-2.5"><IncidentStatusBadge status={incident.status} /></td>
                    <td className="max-w-[12rem] px-4 py-2.5">
                      <span className="line-clamp-1 text-muted-foreground">
                        {incident.serviceNames.join(", ") || "—"}
                      </span>
                    </td>
                    <td className="px-4 py-2.5 text-muted-foreground">{incident.commanderName ?? "—"}</td>
                    <td className="px-4 py-2.5 text-muted-foreground">{formatRelative(incident.createdAt)}</td>
                    <td className="px-4 py-2.5 text-num">{formatDuration(incident.durationSeconds)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="mt-3 flex items-center justify-between text-sm text-muted-foreground">
            <span aria-live="polite">
              {data.totalElements === 0
                ? "No results"
                : `Showing ${data.page * data.size + 1}–${Math.min((data.page + 1) * data.size, data.totalElements)} of ${data.totalElements}`}
            </span>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={page === 0}
                onClick={() => setParam("page", String(page - 1))}
              >
                Previous
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={page + 1 >= data.totalPages}
                onClick={() => setParam("page", String(page + 1))}
              >
                Next
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default function IncidentsPage() {
  return (
    <Suspense fallback={<TableSkeleton rows={6} />}>
      <IncidentsContent />
    </Suspense>
  );
}
