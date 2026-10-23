package com.signalops.incidents.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

  List<Attachment> findByIncidentIdAndOrganizationIdOrderByCreatedAtDesc(
      UUID incidentId, UUID organizationId);

  Optional<Attachment> findByIdAndOrganizationId(UUID id, UUID organizationId);

  Optional<Attachment> findByIdAndIncidentIdAndOrganizationId(
      UUID id, UUID incidentId, UUID organizationId);
}
