import { http } from "@/lib/http";
import type {
  InvitationView,
  MembershipView,
  OrganizationDto,
  PageResponse,
  Role,
} from "@/lib/types";

export async function myOrganizations(signal?: AbortSignal): Promise<OrganizationDto[]> {
  return http<OrganizationDto[]>("/organizations/mine", { signal, skipOrgHeader: true });
}

export async function currentOrganization(signal?: AbortSignal): Promise<OrganizationDto> {
  return http<OrganizationDto>("/organizations/current", { signal });
}

export async function listMembers(
  page = 0,
  size = 100,
  signal?: AbortSignal
): Promise<PageResponse<MembershipView>> {
  return http<PageResponse<MembershipView>>(
    `/organizations/current/members?page=${page}&size=${size}`,
    { signal }
  );
}

export async function updateMember(
  memberId: string,
  input: { role?: Role; status?: "ACTIVE" | "DISABLED" }
): Promise<MembershipView> {
  return http<MembershipView>(`/members/${memberId}`, { method: "PATCH", body: input });
}

export async function removeMember(memberId: string): Promise<void> {
  return http<void>(`/members/${memberId}`, { method: "DELETE" });
}

export async function listInvitations(signal?: AbortSignal): Promise<InvitationView[]> {
  return http<InvitationView[]>("/invitations", { signal });
}

export async function createInvitation(input: {
  email: string;
  role: Role;
}): Promise<InvitationView> {
  return http<InvitationView>("/invitations", { method: "POST", body: input });
}

export async function revokeInvitation(invitationId: string): Promise<void> {
  return http<void>(`/invitations/${invitationId}`, { method: "DELETE" });
}
