package com.signalops.shared.security;

import com.signalops.shared.domain.Role;
import java.util.UUID;

public record TenantContextValue(
    UUID userId, UUID organizationId, UUID memberId, Role role, String memberFullName) {

  public boolean hasRole(Role... roles) {
    for (Role candidate : roles) {
      if (role == candidate) {
        return true;
      }
    }
    return false;
  }

  public boolean isAdminLike() {
    return role == Role.OWNER || role == Role.ADMIN;
  }
}
