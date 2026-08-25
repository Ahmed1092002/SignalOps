package com.signalops.organizations.api;

import com.signalops.shared.security.MembershipLookup;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.signalops.organizations.domain.Membership;
import com.signalops.organizations.domain.MembershipRepository;
import com.signalops.users.domain.UserRepository;

@Component
@RequiredArgsConstructor
public class MembershipLookupAdapter implements MembershipLookup {

  private final MembershipRepository membershipRepository;
  private final UserRepository userRepository;

  @Override
  @Transactional(readOnly = true)
  public TenantMembership lookup(UUID userId, UUID organizationId) {
    return membershipRepository
        .findByOrganizationIdAndUserId(organizationId, userId)
        .filter(Membership::isActive)
        .flatMap(
            membership ->
                userRepository
                    .findById(userId)
                    .filter(user -> user.isActive())
                    .map(
                        user ->
                            new TenantMembership(
                                membership.getId(),
                                organizationId,
                                membership.getRole(),
                                user.getFullName())))
        .orElse(null);
  }
}
