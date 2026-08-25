import { incidentStatusLabel, incidentStatusToken } from "@/lib/domain/incident";
import { severityLabel, severityToken } from "@/lib/domain/severity";
import { serviceStatusLabel, serviceStatusToken, type ServiceStatus } from "@/lib/domain/service-status";
import { roleLabel, type Role } from "@/lib/domain/permissions";
import { cn } from "@/lib/utils";

function Badge({
  color,
  label,
  dot = true,
  className,
}: {
  color: string;
  label: string;
  dot?: boolean;
  className?: string;
}) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium",
        className
      )}
      style={{
        color,
        backgroundColor: "color-mix(in srgb, " + color + " 12%, transparent)",
        border: "1px solid color-mix(in srgb, " + color + " 30%, transparent)",
      }}
    >
      {dot && (
        <span
          aria-hidden="true"
          className="h-1.5 w-1.5 rounded-full"
          style={{ backgroundColor: color }}
        />
      )}
      {label}
    </span>
  );
}

export function SeverityBadge({ severity, className }: { severity: keyof typeof severityLabel; className?: string }) {
  return <Badge color={severityToken[severity]} label={severityLabel[severity]} className={className} />;
}

export function IncidentStatusBadge({
  status,
  className,
}: {
  status: keyof typeof incidentStatusLabel;
  className?: string;
}) {
  return <Badge color={incidentStatusToken[status]} label={incidentStatusLabel[status]} className={className} />;
}

export function ServiceStatusBadge({ status, className }: { status: string; className?: string }) {
  const known = (Object.keys(serviceStatusLabel) as ServiceStatus[]).includes(status as ServiceStatus);
  if (!known) {
    return <Badge color="var(--muted-foreground)" label={status} className={className} />;
  }
  const key = status as ServiceStatus;
  return <Badge color={serviceStatusToken[key]} label={serviceStatusLabel[key]} className={className} />;
}

export function RoleBadge({ role, className }: { role: Role; className?: string }) {
  return (
    <span
      className={cn(
        "inline-flex items-center whitespace-nowrap rounded-md border border-border bg-muted px-1.5 py-0.5 text-[11px] font-medium text-muted-foreground",
        className
      )}
    >
      {roleLabel[role]}
    </span>
  );
}
