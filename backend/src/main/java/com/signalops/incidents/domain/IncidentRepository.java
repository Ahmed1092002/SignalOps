package com.signalops.incidents.domain;

import com.signalops.incidents.api.IncidentStatus;
import com.signalops.incidents.api.Severity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {

  Optional<Incident> findByIdAndOrganizationId(UUID id, UUID organizationId);

  boolean existsByIdAndOrganizationId(UUID id, UUID organizationId);

  @Query(
      value =
          "SELECT * FROM incidents i"
              + " WHERE i.organization_id = :organizationId"
              + " AND (:archived = TRUE OR i.archived_at IS NULL)"
              + " AND (CAST(:status AS text) IS NULL OR i.status = CAST(:status AS text))"
              + " AND (CAST(:severity AS text) IS NULL OR i.severity = CAST(:severity AS text))"
              + " AND (CAST(:q AS text) IS NULL OR i.title ILIKE '%' || CAST(:q AS text) || '%'"
              + "      OR i.key ILIKE '%' || CAST(:q AS text) || '%')"
              + " AND (CAST(:createdFrom AS timestamptz) IS NULL OR i.created_at >= CAST(:createdFrom AS timestamptz))"
              + " AND (CAST(:createdTo AS timestamptz) IS NULL OR i.created_at <= CAST(:createdTo AS timestamptz))"
              + " AND (CAST(:responderUserId AS uuid) IS NULL OR EXISTS (SELECT 1 FROM incident_responders r"
              + "      WHERE r.incident_id = i.id AND r.user_id = CAST(:responderUserId AS uuid)))"
              + " AND (CAST(:serviceId AS uuid) IS NULL OR EXISTS (SELECT 1 FROM incident_services s"
              + "      WHERE s.incident_id = i.id AND s.service_id = CAST(:serviceId AS uuid)))"
              + " ORDER BY i.created_at DESC",
      countQuery =
          "SELECT count(*) FROM incidents i"
              + " WHERE i.organization_id = :organizationId"
              + " AND (:archived = TRUE OR i.archived_at IS NULL)"
              + " AND (CAST(:status AS text) IS NULL OR i.status = CAST(:status AS text))"
              + " AND (CAST(:severity AS text) IS NULL OR i.severity = CAST(:severity AS text))"
              + " AND (CAST(:q AS text) IS NULL OR i.title ILIKE '%' || CAST(:q AS text) || '%'"
              + "      OR i.key ILIKE '%' || CAST(:q AS text) || '%')"
              + " AND (CAST(:createdFrom AS timestamptz) IS NULL OR i.created_at >= CAST(:createdFrom AS timestamptz))"
              + " AND (CAST(:createdTo AS timestamptz) IS NULL OR i.created_at <= CAST(:createdTo AS timestamptz))"
              + " AND (CAST(:responderUserId AS uuid) IS NULL OR EXISTS (SELECT 1 FROM incident_responders r"
              + "      WHERE r.incident_id = i.id AND r.user_id = CAST(:responderUserId AS uuid)))"
              + " AND (CAST(:serviceId AS uuid) IS NULL OR EXISTS (SELECT 1 FROM incident_services s"
              + "      WHERE s.incident_id = i.id AND s.service_id = CAST(:serviceId AS uuid)))",
      nativeQuery = true)
  Page<Incident> search(
      @Param("organizationId") UUID organizationId,
      @Param("archived") boolean archived,
      @Param("status") String status,
      @Param("severity") String severity,
      @Param("q") String q,
      @Param("createdFrom") Instant createdFrom,
      @Param("createdTo") Instant createdTo,
      @Param("responderUserId") UUID responderUserId,
      @Param("serviceId") UUID serviceId,
      Pageable pageable);

  @Query(
      "select (count(i) > 0) from Incident i where i.organizationId = :organizationId"
          + " and i.archivedAt is null and i.status <> com.signalops.incidents.api.IncidentStatus.RESOLVED"
          + " and exists (select 1 from IncidentServiceLink s where s.incidentId = i.id"
          + " and s.serviceId = :serviceId)")
  boolean hasActiveIncidentForService(
      @Param("organizationId") UUID organizationId, @Param("serviceId") UUID serviceId);

  @Query(
      "select coalesce(max(cast(substring(i.key, 5, 20) as integer)), 0) from Incident i"
          + " where i.organizationId = :organizationId")
  int maxKeyNumber(@Param("organizationId") UUID organizationId);

  @Query(
      "select i from Incident i where i.organizationId = :organizationId"
          + " and i.archivedAt is null and i.status <> com.signalops.incidents.api.IncidentStatus.RESOLVED")
  List<Incident> findAllInOrganizationActive(@Param("organizationId") UUID organizationId);

  long countByOrganizationIdAndArchivedAtIsNullAndStatusNot(
      UUID organizationId, IncidentStatus status);

  long countByOrganizationIdAndArchivedAtIsNullAndStatusNotAndSeverity(
      UUID organizationId, IncidentStatus status, Severity severity);

  long countByOrganizationIdAndArchivedAtIsNullAndCreatedAtGreaterThanEqual(
      UUID organizationId, Instant createdAt);

  List<Incident> findByOrganizationIdAndArchivedAtIsNullAndDurationSecondsNotNull(UUID organizationId);

  List<Incident> findByOrganizationIdAndArchivedAtIsNullAndCreatedAtGreaterThanEqual(
      UUID organizationId, java.time.Instant createdAt);
}
