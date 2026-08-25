import { http } from "@/lib/http";

export interface PublicServiceStatus {
  name: string;
  status: string;
}

export interface PublicUpdateView {
  message: string;
  statusAtPublish: string;
  publishedAt: string;
}

export interface PublicActiveIncident {
  title: string;
  severity: string;
  status: string;
  updatedAt: string;
  updates: PublicUpdateView[];
}

export interface PublicPastIncident {
  date: string;
  title: string;
  latestUpdate: string;
  resolvedAt: string;
}

export interface PublicStatusView {
  organizationName: string;
  overallStatus: string;
  services: PublicServiceStatus[];
  activeIncidents: PublicActiveIncident[];
  pastIncidents: PublicPastIncident[];
}

export async function fetchPublicStatus(
  orgSlug: string,
  signal?: AbortSignal
): Promise<PublicStatusView> {
  return http<PublicStatusView>(`/public/status/${orgSlug}`, {
    anonymous: true,
    skipOrgHeader: true,
    signal,
  });
}
