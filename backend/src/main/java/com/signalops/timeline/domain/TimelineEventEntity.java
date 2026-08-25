package com.signalops.timeline.domain;

import com.signalops.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only incident timeline (R9/R15-analog). No update or delete paths exist. */
@Getter
@Setter
@Entity
@Table(name = "timeline_events")
public class TimelineEventEntity extends BaseEntity {

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "incident_id", nullable = false)
  private UUID incidentId;

  @Column(nullable = false)
  private String type;

  @Column(name = "actor_user_id")
  private UUID actorUserId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private Map<String, Object> payload = Map.of();

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;
}
