package com.signalops.incidents.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowUpTaskRepository extends JpaRepository<FollowUpTask, UUID> {

  List<FollowUpTask> findByIncidentIdAndOrganizationIdOrderByCreatedAtDesc(
      UUID incidentId, UUID organizationId);

  Optional<FollowUpTask> findByIdAndOrganizationId(UUID id, UUID organizationId);

  Optional<FollowUpTask> findByIdAndIncidentIdAndOrganizationId(
      UUID id, UUID incidentId, UUID organizationId);
}
