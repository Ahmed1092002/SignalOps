package com.signalops.servicecatalog.app;

import com.signalops.shared.api.ApiException;
import com.signalops.shared.security.TenantContextValue;
import org.springframework.stereotype.Component;

/** Catalog-scoped permission checks (Owner/Admin only for mutations). */
@Component
public class CatalogAccessPolicy {

  public void requireAdminLike(TenantContextValue context, String action) {
    if (!context.isAdminLike()) {
      throw ApiException.forbidden("Only Owners and Admins can " + action + ".");
    }
  }
}
