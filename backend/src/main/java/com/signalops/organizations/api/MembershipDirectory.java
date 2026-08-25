package com.signalops.organizations.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only membership lookups for other modules. */
public interface MembershipDirectory {

  /** True when the user holds an ACTIVE membership in the org with a commander-eligible role. */
  boolean isCommanderEligible(UUID organizationId, UUID userId);

  Optional<MembershipSummary> findMembership(UUID organizationId, UUID userId);

  List<UUID> membershipOrganizations(UUID userId);

  boolean isActiveMember(UUID organizationId, UUID userId);

  record MembershipSummary(UUID memberId, UUID userId, String role, boolean active) {}
}
