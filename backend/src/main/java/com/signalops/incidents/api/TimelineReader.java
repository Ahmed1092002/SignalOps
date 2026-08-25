package com.signalops.incidents.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Timeline read port (implemented by the timeline module). */
public interface TimelineReader {

  List<TimelineItem> findByIncident(UUID organizationId, UUID incidentId, int page, int size);

  List<TimelineItem> recentForOrganization(UUID organizationId, int limit);

  long countByIncident(UUID organizationId, UUID incidentId);

  record TimelineItem(
      UUID id,
      UUID incidentId,
      String type,
      UUID actorUserId,
      String actorName,
      Instant occurredAt,
      Map<String, Object> payload) {}
}
