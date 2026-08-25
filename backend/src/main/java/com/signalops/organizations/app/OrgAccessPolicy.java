package com.signalops.organizations.app;

import com.signalops.shared.api.ApiException;
import com.signalops.shared.api.ErrorCodes;
import com.signalops.shared.domain.Role;
import com.signalops.shared.security.TenantContext;
import com.signalops.shared.security.TenantContextValue;
import org.springframework.stereotype.Component;

/** Org-level permission checks (permission matrix, 07-security §3). */
@Component
public class OrgAccessPolicy {

  public void requireAdminLike(TenantContextValue context, String action) {
    if (!context.isAdminLike()) {
      throw ApiException.forbidden(
          "Only Owners and Admins can " + action + ".");
    }
  }

  public void requireRole(TenantContextValue context, Role... roles) {
    for (Role role : roles) {
      if (context.role() == role) {
        return;
      }
    }
    throw ApiException.forbidden("You do not have permission to perform this action.");
  }

  public void requireSameOrganization(TenantContextValue context, java.util.UUID organizationId) {
    if (!context.organizationId().equals(organizationId)) {
      throw ApiException.notFound("Resource not found.");
    }
  }

  public void requireMember(TenantContextValue context) {
    if (context == null) {
      throw new ApiException(ErrorCodes.UNAUTHENTICATED, "Authentication required.", 401);
    }
  }
}
