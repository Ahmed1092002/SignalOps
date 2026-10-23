package com.signalops.incidents.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentResponderRepository
    extends JpaRepository<IncidentResponder, IncidentResponder.Pk> {

  List<IncidentResponder> findByIncidentIdAndOrganizationId(UUID incidentId, UUID organizationId);

  Optional<IncidentResponder> findByIncidentIdAndUserIdAndOrganizationId(
      UUID incidentId, UUID userId, UUID organizationId);

  @Query(
      "select r.userId from IncidentResponder r where r.organizationId = :organizationId"
          + " and r.incidentId in :incidentIds")
  List<UUID> findResponderUserIds(
      @Param("organizationId") UUID organizationId, @Param("incidentIds") Collection<UUID> incidentIds);
}
