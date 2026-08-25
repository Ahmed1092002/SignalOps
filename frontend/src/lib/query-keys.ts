import type {
  IncidentListFilters,
  IncidentSortField,
  SortDirection,
} from "@/lib/types";

export const qk = {
  auth: {
    me: ["auth", "me"] as const,
  },
  organizations: {
    mine: ["organizations", "mine"] as const,
    current: (orgId: string) => ["organization", orgId] as const,
    members: (orgId: string) => ["organization", orgId, "members"] as const,
    invitations: (orgId: string) => ["organization", orgId, "invitations"] as const,
  },
  services: {
    list: (orgId: string, includeArchived: boolean) =>
      ["organization", orgId, "services", { includeArchived }] as const,
  },
  incidents: {
    list: (orgId: string, filters: object) =>
      ["organization", orgId, "incidents", filters] as const,
    detail: (orgId: string, id: string) =>
      ["organization", orgId, "incident", id] as const,
    comments: (orgId: string, id: string) =>
      ["organization", orgId, "incident", id, "comments"] as const,
    publicUpdates: (orgId: string, id: string) =>
      ["organization", orgId, "incident", id, "public-updates"] as const,
    tasks: (orgId: string, id: string) =>
      ["organization", orgId, "incident", id, "tasks"] as const,
    attachments: (orgId: string, id: string) =>
      ["organization", orgId, "incident", id, "attachments"] as const,
    byService: (orgId: string, serviceId: string) =>
      ["organization", orgId, "incidents", "service", serviceId] as const,
  },
  dashboard: {
    summary: (orgId: string) => ["organization", orgId, "dashboard"] as const,
  },
  notifications: {
    list: (orgId: string) => ["notifications", "list", orgId] as const,
    unread: (orgId: string) => ["notifications", "unread", orgId] as const,
  },
  audit: {
    list: (orgId: string, filters: Record<string, unknown>) =>
      ["organization", orgId, "audit", filters] as const,
  },
};

export const DEFAULT_PAGE_SIZE = 10;

export function filtersFromParams(params: Record<string, string | undefined>): IncidentListFilters & {
  sort: IncidentSortField;
  direction: SortDirection;
} {
  return {
    q: params.q || undefined,
    status: (params.status as IncidentListFilters["status"]) || undefined,
    severity: (params.severity as IncidentListFilters["severity"]) || undefined,
    serviceId: params.service || undefined,
    responderId: params.responder || undefined,
    createdFrom: params.from || undefined,
    createdTo: params.to || undefined,
    archived: params.archived === "true",
    sort: (params.sort as IncidentSortField) || "createdAt",
    direction: (params.direction as SortDirection) || "desc",
    page: Math.max(0, Number.parseInt(params.page ?? "0", 10) || 0),
    size: DEFAULT_PAGE_SIZE,
  };
}
