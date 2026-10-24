package com.signalops.organizations.app;

import com.signalops.organizations.api.OrganizationDtos.MembershipView;
import com.signalops.organizations.api.OrganizationDtos.OrganizationView;
import com.signalops.organizations.api.OrganizationEvents.MemberDisabled;
import com.signalops.organizations.api.OrganizationEvents.MemberRemoved;
import com.signalops.organizations.api.OrganizationEvents.MemberRoleChanged;
import com.signalops.organizations.api.OrganizationEvents.OrganizationSettingsUpdated;
import com.signalops.organizations.domain.Membership;
import com.signalops.organizations.domain.MembershipRepository;
import com.signalops.organizations.domain.MembershipStatus;
import com.signalops.organizations.domain.Organization;
import com.signalops.organizations.domain.OrganizationRepository;
import com.signalops.shared.api.ApiException;
import com.signalops.shared.api.ErrorCodes;
import com.signalops.shared.domain.Role;
import com.signalops.shared.security.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

  private final OrganizationRepository organizationRepository;
  private final MembershipRepository membershipRepository;
  private final OrgAccessPolicy accessPolicy;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  @Transactional(readOnly = true)
  public List<OrganizationView> myOrganizations(UUID userId) {
    return membershipRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
        .filter(Membership::isActive)
        .map(
            membership ->
                organizationRepository
                    .findById(membership.getOrganizationId())
                    .map(
                        organization ->
                            new OrganizationView(
                                organization.getId(), organization.getName(), organization.getSlug()))
                    .orElseThrow(
                        () -> ApiException.notFound("Organization not found.")))
        .toList();
  }

  @Transactional(readOnly = true)
  public OrganizationView currentOrganization() {
    Organization organization =
        organizationRepository
            .findById(TenantContext.require().organizationId())
            .orElseThrow(() -> ApiException.notFound("Organization not found."));
    return new OrganizationView(organization.getId(), organization.getName(), organization.getSlug());
  }

  @Transactional
  public OrganizationView updateOrganization(String newName) {
    var context = TenantContext.require();
    accessPolicy.requireAdminLike(context, "update organization settings");
    Organization organization =
        organizationRepository
            .findById(context.organizationId())
            .orElseThrow(() -> ApiException.notFound("Organization not found."));
    organization.setName(newName.trim());
    events.publishEvent(
        new OrganizationSettingsUpdated(
            organization.getId(), context.userId(), "Organization renamed to " + newName, Instant.now(clock)));
    return new OrganizationView(organization.getId(), organization.getName(), organization.getSlug());
  }

  @Transactional(readOnly = true)
  public List<MembershipView> listMembers() {
    var context = TenantContext.require();
    return membershipRepository.findByOrganizationId(context.organizationId()).stream()
        .map(this::toView)
        .toList();
  }

  @Transactional
  public MembershipView updateMember(UUID memberId, Role newRole, String newStatus) {
    var context = TenantContext.require();
    accessPolicy.requireAdminLike(context, "manage members");
    Membership membership = loadMember(memberId);

    if (newRole != null && newRole != membership.getRole()) {
      guardLastOwner(membership, "demote the last Owner");
      Role previous = membership.getRole();
      membership.setRole(newRole);
      events.publishEvent(
          new MemberRoleChanged(
              context.organizationId(),
              context.userId(),
              memberId,
              previous,
              newRole,
              emailOf(membership),
              Instant.now(clock)));
    }
    if (newStatus != null && !newStatus.equals(membership.getStatus().name())) {
      if ("DISABLED".equals(newStatus)) {
        guardLastOwner(membership, "disable the last Owner");
        membership.setStatus(MembershipStatus.DISABLED);
        events.publishEvent(
            new MemberDisabled(
                context.organizationId(),
                context.userId(),
                memberId,
                membership.getUserId(),
                emailOf(membership),
                Instant.now(clock)));
      } else {
        membership.setStatus(MembershipStatus.ACTIVE);
      }
    }
    return toView(membership);
  }

  @Transactional
  public void removeMember(UUID memberId) {
    var context = TenantContext.require();
    accessPolicy.requireAdminLike(context, "remove members");
    Membership membership = loadMember(memberId);
    guardLastOwner(membership, "remove the last Owner");
    String email = emailOf(membership);
    membershipRepository.delete(membership);
    events.publishEvent(
        new MemberRemoved(
            context.organizationId(),
            context.userId(),
            memberId,
            membership.getUserId(),
            email,
            Instant.now(clock)));
  }

  private Membership loadMember(UUID memberId) {
    Membership membership =
        membershipRepository
            .findById(memberId)
            .orElseThrow(() -> ApiException.notFound("Member not found."));
    accessPolicy.requireSameOrganization(TenantContext.require(), membership.getOrganizationId());
    return membership;
  }

  private void guardLastOwner(Membership membership, String action) {
    if (membership.getRole() == Role.OWNER && membership.isActive()) {
      organizationRepository
          .findByIdWithLock(membership.getOrganizationId())
          .orElseThrow(() -> ApiException.notFound("Organization not found."));
      if (membershipRepository.countActiveOwners(membership.getOrganizationId()) <= 1) {
        throw ApiException.conflict(
            ErrorCodes.CANNOT_REMOVE_LAST_OWNER, "You cannot " + action + " of the organization.");
      }
    }
  }

  private String emailOf(Membership membership) {
    return toView(membership).email();
  }

  private MembershipView toView(Membership membership) {
    var user =
        userDirectory
            .findSummary(membership.getUserId())
            .orElseThrow(() -> ApiException.notFound("Member not found."));
    return new MembershipView(
        membership.getId(),
        membership.getUserId(),
        user.fullName(),
        user.email(),
        membership.getRole(),
        membership.getStatus().name());
  }

  private final com.signalops.users.api.UserDirectory userDirectory;
}
