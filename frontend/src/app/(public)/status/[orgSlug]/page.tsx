import type { Metadata } from "next";
import { SignalOpsMark } from "@/components/app/session-provider";
import { ServiceStatusBadge, SeverityBadge } from "@/components/app/badges";
import { EmptyState } from "@/components/app/states";
import type { PublicStatusView } from "@/lib/api/public-status";
import { API_BASE_URL } from "@/lib/env";
import { formatUtcTimestamp } from "@/lib/format";

export const revalidate = 30;

interface Props {
  params: Promise<{ orgSlug: string }>;
}

async function safeFetch(slug: string): Promise<PublicStatusView | null> {
  try {
    const response = await fetch(`${API_BASE_URL}/public/status/${slug}`, {
      headers: { Accept: "application/json" },
      next: { revalidate: 30 },
    });
    if (!response.ok) return null;
    return (await response.json()) as PublicStatusView;
  } catch {
    return null;
  }
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { orgSlug } = await params;
  const data = await safeFetch(orgSlug);
  if (!data) {
    return { title: "Status page not found" };
  }
  return {
    title: data.organizationName + " Status",
    description:
      "Current service status and incident history for " + data.organizationName + ", powered by SignalOps.",
  };
}

export default async function PublicStatusPage({ params }: Props) {
  const { orgSlug } = await params;
  const data = await safeFetch(orgSlug);

  if (!data) {
    return (
      <main className="mx-auto flex min-h-dvh max-w-3xl flex-col items-center justify-center gap-3 p-6 text-center">
        <SignalOpsMark className="h-10 w-10 opacity-50" />
        <h1 className="text-lg font-semibold">Status page not found</h1>
        <p className="text-sm text-muted-foreground">
          No status page exists for “{orgSlug}”.
        </p>
      </main>
    );
  }

  const banner =
    data.overallStatus === "OPERATIONAL"
      ? { tone: "success", title: "All systems operational" }
      : data.overallStatus === "MAJOR_OUTAGE"
        ? { tone: "critical", title: "Major outage in progress" }
        : data.overallStatus === "PARTIAL_OUTAGE"
          ? { tone: "critical", title: "Partial outage in progress" }
          : data.overallStatus === "MAINTENANCE"
            ? { tone: "info", title: "Scheduled maintenance underway" }
            : { tone: "warning", title: "Degraded performance" };

  return (
    <main className="mx-auto min-h-dvh max-w-4xl px-4 py-10">
      <header className="mb-8 flex items-center gap-3">
        <SignalOpsMark className="h-9 w-9" />
        <div>
          <h1 className="text-xl font-semibold tracking-tight">
            {data.organizationName} Status
          </h1>
          <p className="text-xs text-muted-foreground">
            Live service health · refreshed every 30 seconds
          </p>
        </div>
      </header>

      <section
        aria-live="polite"
        aria-label="Overall status"
        className={
          "mb-8 rounded-lg border p-5 " +
          (banner.tone === "success"
            ? "border-svc-operational/40 bg-svc-operational/10"
            : banner.tone === "critical"
              ? "border-severity-critical/40 bg-severity-critical/10"
              : banner.tone === "warning"
                ? "border-severity-medium/40 bg-severity-medium/10"
                : "border-info/40 bg-info/10")
        }
      >
        <h2 className="text-lg font-semibold">{banner.title}</h2>
      </section>

      <section aria-label="Services">
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wider text-muted-foreground">
          Services
        </h2>
        {data.services.length === 0 ? (
          <EmptyState title="No public services listed" className="py-8" />
        ) : (
          <ul className="mb-8 grid gap-2 sm:grid-cols-2">
            {data.services.map((service) => (
              <li
                key={service.name}
                className="flex items-center justify-between gap-3 rounded-lg border px-4 py-3"
              >
                <span className="min-w-0 truncate text-sm font-medium">{service.name}</span>
                <ServiceStatusBadge status={service.status} />
              </li>
            ))}
          </ul>
        )}
      </section>

      <section aria-label="Active incidents" className="mb-8">
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wider text-muted-foreground">
          Active incidents
        </h2>
        {data.activeIncidents.length === 0 ? (
          <EmptyState title="No active incidents" className="py-8" />
        ) : (
          <ul className="space-y-3">
            {data.activeIncidents.map((incident, index) => (
              <li key={index} className="rounded-lg border p-4">
                <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <SeverityBadge severity={incident.severity as never} />
                    <h3 className="text-sm font-semibold">{incident.title}</h3>
                  </div>
                  <ServiceStatusBadge status={incident.status} />
                </div>
                <ol className="space-y-2.5 border-l pl-4">
                  {incident.updates.map((update, updateIndex) => (
                    <li key={updateIndex} className="relative">
                      <span
                        aria-hidden="true"
                        className="absolute -left-[21px] top-1.5 h-2 w-2 rounded-full bg-primary"
                      />
                      <p className="text-sm">{update.message}</p>
                      <time
                        dateTime={update.publishedAt}
                        className="text-num text-xs text-muted-foreground"
                      >
                        {formatUtcTimestamp(update.publishedAt)}
                      </time>
                    </li>
                  ))}
                </ol>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section aria-label="Past incidents">
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wider text-muted-foreground">
          Past incidents
        </h2>
        {data.pastIncidents.length === 0 ? (
          <EmptyState title="No past incidents" className="py-8" />
        ) : (
          <ul className="space-y-3">
            {data.pastIncidents.map((incident, index) => (
              <li key={index} className="rounded-lg border p-4">
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <h3 className="text-sm font-semibold">{incident.title}</h3>
                  <time dateTime={incident.resolvedAt} className="text-num text-xs text-muted-foreground">
                    Resolved {formatUtcTimestamp(incident.resolvedAt)}
                  </time>
                </div>
                {incident.latestUpdate && (
                  <p className="mt-1 text-sm text-muted-foreground">{incident.latestUpdate}</p>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      <footer className="mt-12 border-t pt-4 text-xs text-muted-foreground">
        Powered by SignalOps · {API_BASE_URL ? "Live data" : "Live data"} · This page shows only
        customer-safe information.
      </footer>
    </main>
  );
}
