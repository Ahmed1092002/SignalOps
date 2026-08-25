package com.signalops.servicecatalog.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Read port over the service catalog for other modules. */
public interface ServiceReader {

  Optional<ServiceSummary> summary(UUID organizationId, UUID serviceId);

  /** Service names per incident id for the given org (batched lookup for list views). */
  Map<UUID, List<String>> namesByIncident(UUID organizationId, List<UUID> incidentIds);

  List<ServiceSummary> listActive(UUID organizationId);

  record ServiceSummary(UUID id, String name, String slug, boolean isPublic) {}
}
