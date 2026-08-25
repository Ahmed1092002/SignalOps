import type { IncidentStatus, Role, Severity } from "@/lib/domain";

export type { IncidentStatus, Role, Severity };

export type IncidentSortField = "createdAt" | "updatedAt" | "severity";
export type SortDirection = "asc" | "desc";

export interface UserDto {
  id: string;
  email: string;
  fullName: string;
}

export interface OrganizationDto {
  id: string;
  name: string;
  slug: string;
}

export interface MembershipDto {
  organizationId: string;
  organizationName: string;
  organizationSlug: string;
  role: Role;
  status: "ACTIVE" | "DISABLED";
}

export interface MeResponse {
  user: UserDto;
  memberships: MembershipDto[];
}

export interface AuthResponse {
  accessToken: string;
  user: UserDto;
  organization: OrganizationDto;
}

export interface ProfileView {
  userId: string;
  fullName: string;
  email: string;
}

export interface MemberRef {
  userId: string;
  fullName: string;
  initials: string;
}

export interface ServiceRef {
  id: string;
  name: string;
  slug: string;
}

export interface ServiceView {
  id: string;
  name: string;
  slug: string;
  description: string;
  owningTeam: string;
  isPublic: boolean;
  archivedAt: string | null;
  createdAt: string;
}

export interface CreateServiceRequest {
  name: string;
  description?: string;
  owningTeam?: string;
  isPublic?: boolean;
}

export interface UpdateServiceRequest {
  name?: string;
  description?: string;
  owningTeam?: string;
  isPublic?: boolean;
}

export interface MembershipView {
  memberId: string;
  userId: string;
  fullName: string;
  email: string;
  role: Role;
  status: "ACTIVE" | "DISABLED";
}

export interface InvitationView {
  id: string;
  email: string;
  role: Role;
  status: string;
  inviteToken: string | null;
  createdAt: string;
}

export interface AuditEventView {
  id: string;
  actorUserId: string | null;
  action: string;
  entityType: string;
  entityId: string | null;
  metadata: Record<string, unknown>;
  occurredAt: string;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface IncidentView {
  id: string;
  key: string;
  title: string;
  description: string;
  severity: Severity;
  status: IncidentStatus;
  commander: MemberRef | null;
  responders: MemberRef[];
  services: ServiceRef[];
  resolutionSummary: string | null;
  acknowledgedAt: string | null;
  resolvedAt: string | null;
  durationSeconds: number | null;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface TimelineEntryView {
  id: string;
  type: string;
  actor: MemberRef | null;
  occurredAt: string;
  payload: Record<string, unknown>;
}

export interface IncidentDetailView {
  incident: IncidentView;
  timeline: TimelineEntryView[];
}

export interface IncidentRowView {
  id: string;
  key: string;
  title: string;
  severity: Severity;
  status: IncidentStatus;
  serviceNames: string[];
  commanderName: string | null;
  createdAt: string;
  updatedAt: string;
  durationSeconds: number | null;
  archived: boolean;
}

export interface CommentView {
  id: string;
  author: MemberRef;
  body: string;
  createdAt: string;
}

export interface PublicUpdateView {
  id: string;
  author: MemberRef;
  message: string;
  statusAtPublish: IncidentStatus;
  severityAtPublish: Severity;
  publishedAt: string;
}

export interface CreateIncidentRequest {
  title: string;
  description: string;
  severity: Severity;
  serviceIds: string[];
  commanderUserId: string | null;
  responderUserIds: string[];
}

export interface IncidentListFilters {
  q?: string;
  status?: IncidentStatus;
  severity?: Severity;
  serviceId?: string;
  responderId?: string;
  createdFrom?: string;
  createdTo?: string;
  archived?: boolean;
  sort?: IncidentSortField;
  direction?: SortDirection;
  page: number;
  size: number;
}

export interface ServiceHealth {
  serviceId: string;
  name: string;
  status: string;
  activeIncidents: number;
}

export interface ActivityItem {
  incidentKey: string;
  incidentTitle: string;
  eventType: string;
  actorName: string | null;
  occurredAt: string;
}

export interface DashboardSummary {
  activeIncidents: number;
  criticalIncidents: number;
  incidentsThisMonth: number;
  meanResolutionSeconds: number | null;
  serviceHealth: ServiceHealth[];
  recentActivity: ActivityItem[];
}

export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  readonly details: Record<string, unknown>[];
  readonly requestId: string | null;

  constructor(
    code: string,
    message: string,
    status: number,
    details: Record<string, unknown>[] = [],
    requestId: string | null = null
  ) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = status;
    this.details = details;
    this.requestId = requestId;
  }
}
