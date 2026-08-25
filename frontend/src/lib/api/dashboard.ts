import { http } from "@/lib/http";
import type { AuditEventView, DashboardSummary, PageResponse } from "@/lib/types";

export async function dashboardSummary(signal?: AbortSignal): Promise<DashboardSummary> {
  return http<DashboardSummary>("/dashboard/summary", { signal });
}

export interface AuditFilters {
  action?: string;
  entityType?: string;
  page: number;
  size: number;
}

export async function listAuditEvents(
  filters: AuditFilters,
  signal?: AbortSignal
): Promise<PageResponse<AuditEventView>> {
  const params = new URLSearchParams();
  if (filters.action) params.set("action", filters.action);
  if (filters.entityType) params.set("entityType", filters.entityType);
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));
  return http<PageResponse<AuditEventView>>(`/audit-events?${params.toString()}`, { signal });
}
