import { http } from "@/lib/http";
import type {
  CreateServiceRequest,
  PageResponse,
  ServiceView,
  UpdateServiceRequest,
} from "@/lib/types";

export async function listServices(
  includeArchived = false,
  signal?: AbortSignal
): Promise<ServiceView[]> {
  return http<ServiceView[]>(`/services?includeArchived=${includeArchived}`, { signal });
}

export async function createService(input: CreateServiceRequest): Promise<ServiceView> {
  return http<ServiceView>("/services", { method: "POST", body: input });
}

export async function updateService(
  serviceId: string,
  input: UpdateServiceRequest
): Promise<ServiceView> {
  return http<ServiceView>(`/services/${serviceId}`, { method: "PATCH", body: input });
}

export async function archiveService(serviceId: string): Promise<ServiceView> {
  return http<ServiceView>(`/services/${serviceId}/archive`, { method: "POST" });
}

export async function serviceIncidents(
  serviceId: string,
  page = 0,
  size = 10,
  signal?: AbortSignal
): Promise<PageResponse<unknown>> {
  return http<PageResponse<unknown>>(
    `/services/${serviceId}/incidents?page=${page}&size=${size}`,
    { signal }
  );
}
