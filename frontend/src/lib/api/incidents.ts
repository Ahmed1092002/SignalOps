import { http } from "@/lib/http";
import type {
  CommentView,
  CreateIncidentRequest,
  IncidentDetailView,
  IncidentListFilters,
  IncidentView,
  IncidentRowView,
  PageResponse,
  PublicUpdateView,
} from "@/lib/types";

function toQuery(filters: IncidentListFilters): string {
  const params = new URLSearchParams();
  if (filters.q) params.set("q", filters.q);
  if (filters.status) params.set("status", filters.status);
  if (filters.severity) params.set("severity", filters.severity);
  if (filters.serviceId) params.set("serviceId", filters.serviceId);
  if (filters.responderId) params.set("responderId", filters.responderId);
  if (filters.createdFrom) params.set("createdFrom", filters.createdFrom);
  if (filters.createdTo) params.set("createdTo", filters.createdTo);
  if (filters.archived) params.set("archived", "true");
  params.set("page", String(filters.page));
  params.set("size", String(filters.size));
  return params.toString();
}

export async function listIncidents(
  filters: IncidentListFilters,
  signal?: AbortSignal
): Promise<PageResponse<IncidentRowView>> {
  return http<PageResponse<IncidentRowView>>(`/incidents?${toQuery(filters)}`, { signal });
}

export async function getIncident(
  incidentId: string,
  signal?: AbortSignal
): Promise<IncidentDetailView> {
  return http<IncidentDetailView>(`/incidents/${incidentId}`, { signal });
}

export async function createIncident(input: CreateIncidentRequest): Promise<IncidentView> {
  return http<IncidentView>("/incidents", { method: "POST", body: input });
}

export async function updateIncident(
  incidentId: string,
  input: { title?: string; description?: string; version: number }
): Promise<IncidentView> {
  return http<IncidentView>(`/incidents/${incidentId}`, { method: "PATCH", body: input });
}

export async function transitionIncident(
  incidentId: string,
  input: {
    to: string;
    resolutionSummary?: string;
    reason?: string;
    version: number;
  }
): Promise<IncidentView> {
  return http<IncidentView>(`/incidents/${incidentId}/transitions`, {
    method: "POST",
    body: input,
  });
}

export async function changeSeverity(
  incidentId: string,
  input: { severity: string; version: number }
): Promise<IncidentView> {
  return http<IncidentView>(`/incidents/${incidentId}/severity`, {
    method: "PUT",
    body: input,
  });
}

export async function setCommander(
  incidentId: string,
  input: { commanderUserId: string | null; version: number }
): Promise<IncidentView> {
  return http<IncidentView>(`/incidents/${incidentId}/commander`, {
    method: "PUT",
    body: input,
  });
}

export async function addResponder(
  incidentId: string,
  input: { responderUserId: string; version: number }
): Promise<IncidentView> {
  return http<IncidentView>(`/incidents/${incidentId}/responders`, {
    method: "POST",
    body: input,
  });
}

export async function removeResponder(
  incidentId: string,
  responderUserId: string,
  version: number
): Promise<IncidentView> {
  return http<IncidentView>(
    `/incidents/${incidentId}/responders/${responderUserId}?version=${version}`,
    { method: "DELETE" }
  );
}

export async function addComment(incidentId: string, body: string): Promise<IncidentDetailView> {
  return http<IncidentDetailView>(`/incidents/${incidentId}/comments`, {
    method: "POST",
    body: { body },
  });
}

export async function listComments(
  incidentId: string,
  signal?: AbortSignal
): Promise<CommentView[]> {
  return http<CommentView[]>(`/incidents/${incidentId}/comments`, { signal });
}

export async function publishPublicUpdate(
  incidentId: string,
  message: string
): Promise<IncidentDetailView> {
  return http<IncidentDetailView>(`/incidents/${incidentId}/public-updates`, {
    method: "POST",
    body: { message },
  });
}

export async function listPublicUpdates(
  incidentId: string,
  signal?: AbortSignal
): Promise<PublicUpdateView[]> {
  return http<PublicUpdateView[]>(`/incidents/${incidentId}/public-updates`, { signal });
}

export async function archiveIncident(
  incidentId: string,
  version: number
): Promise<IncidentView> {
  return http<IncidentView>(`/incidents/${incidentId}/archive`, {
    method: "POST",
    body: { version },
  });
}
