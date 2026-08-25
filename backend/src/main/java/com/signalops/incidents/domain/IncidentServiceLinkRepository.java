package com.signalops.incidents.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentServiceLinkRepository extends JpaRepository<IncidentServiceLink, Long> {

  List<IncidentServiceLink> findByIncidentIdAndOrganizationId(UUID incidentId, UUID organizationId);

  Optional<IncidentServiceLink> findByIncidentIdAndServiceIdAndOrganizationId(
      UUID incidentId, UUID serviceId, UUID organizationId);
}
