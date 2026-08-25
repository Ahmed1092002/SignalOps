package com.signalops.audit.domain;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Insert + query only by design (R15). No update or delete methods. */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

  @Query(
      value =
          "SELECT * FROM audit_events a"
              + " WHERE a.organization_id = :organizationId"
              + " AND (CAST(:action AS text) IS NULL OR a.action = CAST(:action AS text))"
              + " AND (CAST(:entityType AS text) IS NULL OR a.entity_type = CAST(:entityType AS text))"
              + " AND (CAST(:actorUserId AS uuid) IS NULL OR a.actor_user_id = CAST(:actorUserId AS uuid))"
              + " AND (CAST(:from AS timestamptz) IS NULL OR a.occurred_at >= CAST(:from AS timestamptz))"
              + " AND (CAST(:to AS timestamptz) IS NULL OR a.occurred_at <= CAST(:to AS timestamptz))"
              + " ORDER BY a.occurred_at DESC",
      countQuery =
          "SELECT count(*) FROM audit_events a"
              + " WHERE a.organization_id = :organizationId"
              + " AND (CAST(:action AS text) IS NULL OR a.action = CAST(:action AS text))"
              + " AND (CAST(:entityType AS text) IS NULL OR a.entity_type = CAST(:entityType AS text))"
              + " AND (CAST(:actorUserId AS uuid) IS NULL OR a.actor_user_id = CAST(:actorUserId AS uuid))"
              + " AND (CAST(:from AS timestamptz) IS NULL OR a.occurred_at >= CAST(:from AS timestamptz))"
              + " AND (CAST(:to AS timestamptz) IS NULL OR a.occurred_at <= CAST(:to AS timestamptz))",
      nativeQuery = true)
  Page<AuditEvent> search(
      @Param("organizationId") UUID organizationId,
      @Param("action") String action,
      @Param("entityType") String entityType,
      @Param("actorUserId") UUID actorUserId,
      @Param("from") java.time.Instant from,
      @Param("to") java.time.Instant to,
      Pageable pageable);
}
