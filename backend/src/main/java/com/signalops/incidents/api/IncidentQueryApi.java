package com.signalops.incidents.api;

import java.util.List;
import java.util.UUID;

/** Read port for incident listings needed by other modules (e.g. service detail page). */
public interface IncidentQueryApi {

  List<IncidentSummary> findByService(UUID organizationId, UUID serviceId, int page, int size);

  long countByService(UUID organizationId, UUID serviceId);

  record IncidentSummary(
      UUID id,
      String key,
      String title,
      Severity severity,
      IncidentStatus status,
      java.time.Instant createdAt) {}
}
