package com.signalops.incidents.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentCommentRepository extends JpaRepository<IncidentComment, UUID> {

  List<IncidentComment> findByIncidentIdAndOrganizationIdOrderByCreatedAtDesc(
      UUID incidentId, UUID organizationId);

  Optional<IncidentComment> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
