package com.signalops.timeline.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimelineEventRepository extends JpaRepository<TimelineEventEntity, UUID> {

  List<TimelineEventEntity> findByIncidentIdAndOrganizationIdOrderByOccurredAtDesc(
      UUID incidentId, UUID organizationId, Pageable pageable);

  long countByIncidentIdAndOrganizationId(UUID incidentId, UUID organizationId);

  List<TimelineEventEntity> findByOrganizationIdOrderByOccurredAtDesc(UUID organizationId, Pageable pageable);
}
