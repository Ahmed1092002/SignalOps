package com.signalops.organizations.api;

import com.signalops.shared.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class OrganizationDtos {

  public record OrganizationView(UUID id, String name, String slug) {}

  public record MembershipView(
      UUID memberId, UUID userId, String fullName, String email, Role role, String status) {}

  public record UpdateOrganizationRequest(@NotBlank @Size(min = 2, max = 80) String name) {}

  public record UpdateMemberRequest(Role role, MemberStatus status) {}

  public enum MemberStatus {
    ACTIVE,
    DISABLED
  }

  public record InvitationCreateRequest(@NotBlank @Email @Size(max = 254) String email, @NotNull Role role) {}

  public record InvitationView(
      UUID id,
      String email,
      Role role,
      String status,
      String inviteToken,
      Instant createdAt) {}

  public record InvitationAcceptRequest(@NotBlank String token) {}

  public record InvitationAcceptResponse(UUID organizationId, String organizationName, Role role) {}

  private OrganizationDtos() {}
}
