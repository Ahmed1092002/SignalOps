package com.signalops.servicecatalog.api;

import com.signalops.servicecatalog.domain.ServiceRepository;
import com.signalops.shared.api.ApiException;
import com.signalops.shared.api.ErrorCodes;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ServiceCatalogAdapter implements ServiceCatalogApi {

  private final ServiceRepository serviceRepository;

  @Override
  @Transactional(readOnly = true)
  public void assertAllExistInOrganization(UUID organizationId, List<UUID> serviceIds) {
    if (serviceIds == null || serviceIds.isEmpty()) {
      throw ApiException.badRequest(
          ErrorCodes.INCIDENT_REQUIRES_SERVICE, "Select at least one affected service.");
    }
    if (!serviceRepository.allExistInOrganization(organizationId, serviceIds)) {
      throw ApiException.notFound("Affected service not found in this organization.");
    }
  }

  @Override
  @Transactional(readOnly = true)
  public boolean allExistInOrganization(UUID organizationId, List<UUID> serviceIds) {
    return serviceIds != null
        && !serviceIds.isEmpty()
        && serviceRepository.allExistInOrganization(organizationId, serviceIds);
  }
}
