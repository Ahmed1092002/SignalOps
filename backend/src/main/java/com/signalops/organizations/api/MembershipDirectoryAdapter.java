package com.signalops.organizations.api;

import com.signalops.organizations.domain.Membership;
import com.signalops.organizations.domain.MembershipRepository;
import com.signalops.shared.domain.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MembershipDirectoryAdapter implements MembershipDirectory {

  private final MembershipRepository membershipRepository;

  @Override
  @Transactional(readOnly = true)
  public boolean isCommanderEligible(UUID organizationId, UUID userId) {
    return membershipRepository
        .findByOrganizationIdAndUserId(organizationId, userId)
        .filter(Membership::isActive)
        .map(
            membership ->
                membership.getRole() == Role.OWNER
                    || membership.getRole() == Role.ADMIN
                    || membership.getRole() == Role.INCIDENT_COMMANDER)
        .orElse(false);
  }

  @Override
  @Transactional(readOnly = true)
  public List<UUID> membershipOrganizations(UUID userId) {
    return membershipRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
        .map(Membership::getOrganizationId)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isActiveMember(UUID organizationId, UUID userId) {
    return membershipRepository
        .findByOrganizationIdAndUserId(organizationId, userId)
        .map(Membership::isActive)
        .orElse(false);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<MembershipSummary> findMembership(UUID organizationId, UUID userId) {
    return membershipRepository
        .findByOrganizationIdAndUserId(organizationId, userId)
        .map(
            membership ->
                new MembershipSummary(
                    membership.getId(),
                    membership.getUserId(),
                    membership.getRole().name(),
                    membership.isActive()));
  }
}
