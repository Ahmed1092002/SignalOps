package com.signalops.incidents.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read ports other modules use to observe incident data without touching incident internals. */
public interface IncidentInsightsApi {

  long countActive(UUID organizationId);

  long countActiveCritical(UUID organizationId);

  long countCreatedSince(UUID organizationId, Instant from);

  /** Mean of frozen resolution durations for non-archived resolved incidents; null if none. */
  Double meanResolutionSeconds(UUID organizationId);

  /** Active (non-resolved, non-archived) incident severities grouped per service. */
  List<ServiceIncidentLoad> activeSeveritiesByService(UUID organizationId);

  List<ActivityItem> recentActivity(UUID organizationId, int limit);

  record ServiceIncidentLoad(UUID serviceId, List<Severity> activeSeverities) {}

  record ActivityItem(
      UUID incidentId,
      String incidentKey,
      String incidentTitle,
      String eventType,
      UUID actorUserId,
      String actorName,
      Instant occurredAt,
      Map<String, Object> payload) {}
}
