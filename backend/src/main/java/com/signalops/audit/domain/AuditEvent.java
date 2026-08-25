package com.signalops.audit.domain;

import com.signalops.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Organization-level security/administrative audit record. Append-only from the application:
 * the repository exposes insert and query only (R15).
 */
@Getter
@Setter
@Entity
@Table(name = "audit_events")
public class AuditEvent extends BaseEntity {

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "actor_user_id")
  private UUID actorUserId;

  @Column(nullable = false)
  private String action;

  @Column(name = "entity_type", nullable = false)
  private String entityType;

  @Column(name = "entity_id")
  private UUID entityId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private Map<String, Object> metadata = Map.of();

  @Column(name = "occurred_at", nullable = false)
  private java.time.Instant occurredAt;
}
