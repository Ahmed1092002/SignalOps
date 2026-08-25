package com.signalops.shared.security;

import com.signalops.shared.domain.Role;
import java.util.UUID;

public interface MembershipLookup {

  TenantMembership lookup(UUID userId, UUID organizationId);

  record TenantMembership(UUID memberId, UUID organizationId, Role role, String memberFullName) {}
}
