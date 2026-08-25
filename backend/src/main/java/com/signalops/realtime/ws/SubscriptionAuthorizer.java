package com.signalops.realtime.ws;

import com.signalops.organizations.domain.Membership;
import com.signalops.organizations.domain.MembershipRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SubscriptionAuthorizer {

  private final MembershipRepository membershipRepository;

  @Transactional(readOnly = true)
  public boolean isActiveMember(UUID userId, UUID organizationId) {
    return membershipRepository
        .findByOrganizationIdAndUserId(organizationId, userId)
        .map(Membership::isActive)
        .orElse(false);
  }
}
