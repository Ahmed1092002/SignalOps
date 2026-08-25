package com.signalops.incidents.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentPublicUpdateRepository extends JpaRepository<IncidentPublicUpdate, UUID> {

  List<IncidentPublicUpdate> findByIncidentIdAndOrganizationIdOrderByPublishedAtDesc(
      UUID incidentId, UUID organizationId);

  Optional<IncidentPublicUpdate> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
