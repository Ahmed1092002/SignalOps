import { http, refreshSession } from "@/lib/http";
import { API_BASE_URL } from "@/lib/env";
import { useSessionStore } from "@/stores/session-store";

export interface FollowUpTaskView {
  id: string;
  title: string;
  status: "OPEN" | "DONE";
  assigneeUserId: string | null;
  assigneeName: string | null;
  dueAt: string | null;
  completedAt: string | null;
  createdAt: string;
}

export async function listFollowUpTasks(
  incidentId: string,
  signal?: AbortSignal
): Promise<FollowUpTaskView[]> {
  return http<FollowUpTaskView[]>(`/incidents/${incidentId}/follow-up-tasks`, { signal });
}

export async function createFollowUpTask(
  incidentId: string,
  input: { title: string; assigneeUserId?: string | null; dueAt?: string | null }
): Promise<FollowUpTaskView> {
  return http<FollowUpTaskView>(`/incidents/${incidentId}/follow-up-tasks`, {
    method: "POST",
    body: {
      title: input.title,
      assigneeUserId: input.assigneeUserId ?? null,
      dueAt: input.dueAt || null,
    },
  });
}

export async function toggleFollowUpTask(
  incidentId: string,
  taskId: string
): Promise<FollowUpTaskView> {
  return http<FollowUpTaskView>(`/incidents/${incidentId}/follow-up-tasks/${taskId}/toggle`, {
    method: "POST",
  });
}

export interface AttachmentView {
  id: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
  createdAt: string;
}

export async function listAttachments(
  incidentId: string,
  signal?: AbortSignal
): Promise<AttachmentView[]> {
  return http<AttachmentView[]>(`/incidents/${incidentId}/attachments`, { signal });
}

export function buildAuthHeaders(customHeaders?: HeadersInit): Record<string, string> {
  const state = useSessionStore.getState();
  const headers: Record<string, string> = {
    "X-Requested-With": "XMLHttpRequest",
  };
  if (customHeaders) {
    if (customHeaders instanceof Headers) {
      customHeaders.forEach((val, key) => {
        headers[key] = val;
      });
    } else if (Array.isArray(customHeaders)) {
      customHeaders.forEach(([key, val]) => {
        headers[key] = val;
      });
    } else {
      Object.assign(headers, customHeaders);
    }
  }
  if (state.accessToken) {
    headers["Authorization"] = `Bearer ${state.accessToken}`;
  }
  if (state.activeOrgId) {
    headers["X-Organization-Id"] = state.activeOrgId;
  }
  return headers;
}

export async function authenticatedRawFetch(
  url: string,
  options: RequestInit = {},
  isRetry = false
): Promise<Response> {
  const headers = buildAuthHeaders(options.headers);
  const response = await fetch(url, {
    ...options,
    headers,
    credentials: "include",
  });

  if (response.status === 401 && !isRetry) {
    const refreshed = await refreshSession();
    if (refreshed) {
      return authenticatedRawFetch(url, options, true);
    }
    useSessionStore.getState().clearSession();
  }

  return response;
}

export async function uploadAttachment(
  incidentId: string,
  file: File
): Promise<AttachmentView> {
  const form = new FormData();
  form.append("file", file);
  const response = await authenticatedRawFetch(
    `${API_BASE_URL}/incidents/${incidentId}/attachments`,
    {
      method: "POST",
      body: form,
    }
  );
  if (!response.ok) {
    let message = "Upload failed.";
    try {
      const body = await response.json();
      if (typeof body.message === "string") message = body.message;
    } catch {
      /* keep default */
    }
    throw new Error(message);
  }
  return response.json();
}

export function attachmentDownloadUrl(incidentId: string, attachmentId: string): string {
  return `${API_BASE_URL}/incidents/${incidentId}/attachments/${attachmentId}/download`;
}

export async function downloadAttachment(
  incidentId: string,
  attachmentId: string,
  filename: string
): Promise<void> {
  const response = await authenticatedRawFetch(
    `${API_BASE_URL}/incidents/${incidentId}/attachments/${attachmentId}/download`,
    {
      method: "GET",
    }
  );
  if (!response.ok) {
    let message = "Download failed.";
    try {
      const body = await response.json();
      if (typeof body.message === "string") message = body.message;
    } catch {
      /* keep default */
    }
    throw new Error(message);
  }
  const blob = await response.blob();
  if (typeof window !== "undefined" && typeof document !== "undefined") {
    const blobUrl = window.URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = blobUrl;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(blobUrl);
  }
}

export interface MaintenanceWindowView {
  id: string;
  serviceId: string;
  title: string;
  startsAt: string;
  endsAt: string;
  active: boolean;
}

export async function listMaintenanceWindows(signal?: AbortSignal): Promise<MaintenanceWindowView[]> {
  return http<MaintenanceWindowView[]>("/maintenance-windows", { signal });
}

export async function createMaintenanceWindow(input: {
  serviceId: string;
  title: string;
  startsAt: string;
  endsAt: string;
}): Promise<MaintenanceWindowView> {
  return http<MaintenanceWindowView>("/maintenance-windows", { method: "POST", body: input });
}

export async function deleteMaintenanceWindow(id: string): Promise<void> {
  return http<void>(`/maintenance-windows/${id}`, { method: "DELETE" });
}
