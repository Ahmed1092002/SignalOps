package com.signalops.organizations.api;

import com.signalops.shared.domain.Role;
import java.time.Instant;
import java.util.UUID;

/** Events published by the organizations module; consumed by the audit module (in-transaction). */
public final class OrganizationEvents {

  public record UserInvited(
      UUID organizationId, UUID actorUserId, String email, Role role, Instant occurredAt)
      implements com.signalops.shared.domain.DomainEvent {}

  public record MemberRoleChanged(
      UUID organizationId,
      UUID actorUserId,
      UUID memberId,
      Role from,
      Role to,
      String memberEmail,
      Instant occurredAt)
      implements com.signalops.shared.domain.DomainEvent {}

  public record MemberDisabled(
      UUID organizationId, UUID actorUserId, UUID memberId, String memberEmail, Instant occurredAt)
      implements com.signalops.shared.domain.DomainEvent {}

  public record MemberRemoved(
      UUID organizationId, UUID actorUserId, UUID memberId, String memberEmail, Instant occurredAt)
      implements com.signalops.shared.domain.DomainEvent {}

  public record OrganizationSettingsUpdated(
      UUID organizationId, UUID actorUserId, String summary, Instant occurredAt)
      implements com.signalops.shared.domain.DomainEvent {}

  private OrganizationEvents() {}
}
