package com.signalops.servicecatalog.api;

import java.util.List;
import java.util.UUID;

/** Port used by other modules (e.g. incidents) to validate service references (R16). */
public interface ServiceCatalogApi {

  /**
   * @throws com.signalops.shared.api.ApiException NOT_FOUND when any id is unknown to the org
   */
  void assertAllExistInOrganization(UUID organizationId, List<UUID> serviceIds);

  boolean allExistInOrganization(UUID organizationId, List<UUID> serviceIds);
}
