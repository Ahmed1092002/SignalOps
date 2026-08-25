package com.signalops.shared.tenancy;

import com.signalops.shared.api.ApiException;
import com.signalops.shared.api.ErrorCodes;
import com.signalops.shared.security.MembershipLookup;
import com.signalops.shared.security.TenantContext;
import com.signalops.shared.security.TenantContextValue;
import com.signalops.shared.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TenantContextInterceptor implements HandlerInterceptor {

  public static final String ORG_HEADER = "X-Organization-Id";

  private final ObjectProvider<MembershipLookup> lookupProvider;

  public TenantContextInterceptor(ObjectProvider<MembershipLookup> lookupProvider) {
    this.lookupProvider = lookupProvider;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    response.setHeader("Cache-Control", "no-store");
    MembershipLookup lookup = lookupProvider.getIfAvailable();
    if (lookup == null) {
      throw ApiException.notFound("Organization not found.");
    }
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
      return true;
    }
    String header = request.getHeader(ORG_HEADER);
    if (!StringUtils.hasText(header)) {
      throw ApiException.badRequest(
          ErrorCodes.ORG_CONTEXT_REQUIRED, "Missing X-Organization-Id header.");
    }
    UUID organizationId = parseUuid(header);
    MembershipLookup.TenantMembership membership;
    try {
      membership = lookup.lookup(principal.userId(), organizationId);
    } catch (IllegalArgumentException ex) {
      membership = null;
    }
    if (membership == null) {
      throw ApiException.notFound("Organization not found.");
    }
    TenantContext.set(
        new TenantContextValue(
            principal.userId(),
            membership.organizationId(),
            membership.memberId(),
            membership.role(),
            membership.memberFullName()));
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    TenantContext.clear();
  }

  private UUID parseUuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException ex) {
      throw ApiException.notFound("Organization not found.");
    }
  }
}
