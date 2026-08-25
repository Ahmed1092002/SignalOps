import { type Severity } from "./severity";

export const SERVICE_STATUSES = [
  "OPERATIONAL",
  "DEGRADED_PERFORMANCE",
  "PARTIAL_OUTAGE",
  "MAJOR_OUTAGE",
  "MAINTENANCE",
] as const;
export type ServiceStatus = (typeof SERVICE_STATUSES)[number];

export const serviceStatusLabel: Record<ServiceStatus, string> = {
  OPERATIONAL: "Operational",
  DEGRADED_PERFORMANCE: "Degraded performance",
  PARTIAL_OUTAGE: "Partial outage",
  MAJOR_OUTAGE: "Major outage",
  MAINTENANCE: "Maintenance",
};

export const serviceStatusToken: Record<ServiceStatus, string> = {
  OPERATIONAL: "var(--svc-operational)",
  DEGRADED_PERFORMANCE: "var(--svc-degraded)",
  PARTIAL_OUTAGE: "var(--svc-partial)",
  MAJOR_OUTAGE: "var(--svc-major)",
  MAINTENANCE: "var(--svc-maintenance)",
};

const SERVICE_STATUS_RANK: Record<ServiceStatus, number> = {
  OPERATIONAL: 0,
  MAINTENANCE: 1,
  DEGRADED_PERFORMANCE: 2,
  PARTIAL_OUTAGE: 3,
  MAJOR_OUTAGE: 4,
};

const SEVERITY_TO_SERVICE_STATUS: Record<Severity, ServiceStatus | null> = {
  LOW: null,
  MEDIUM: "DEGRADED_PERFORMANCE",
  HIGH: "PARTIAL_OUTAGE",
  CRITICAL: "MAJOR_OUTAGE",
};

export function severityToServiceStatus(severity: Severity): ServiceStatus {
  return SEVERITY_TO_SERVICE_STATUS[severity] ?? "OPERATIONAL";
}

export function worstServiceStatus(statuses: readonly ServiceStatus[]): ServiceStatus {
  return statuses.reduce<ServiceStatus>(
    (worst, s) => (SERVICE_STATUS_RANK[s] > SERVICE_STATUS_RANK[worst] ? s : worst),
    "OPERATIONAL"
  );
}

export function deriveServiceStatus(activeSeverities: readonly Severity[]): ServiceStatus {
  return worstServiceStatus(activeSeverities.map(severityToServiceStatus));
}

export function serviceStatusRank(status: ServiceStatus): number {
  return SERVICE_STATUS_RANK[status];
}

export function isMoreUrgentServiceStatus(a: ServiceStatus, b: ServiceStatus): boolean {
  return SERVICE_STATUS_RANK[a] > SERVICE_STATUS_RANK[b];
}
