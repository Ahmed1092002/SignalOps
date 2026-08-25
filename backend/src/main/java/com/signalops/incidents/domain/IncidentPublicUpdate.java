package com.signalops.incidents.domain;

import com.signalops.incidents.api.IncidentStatus;
import com.signalops.incidents.api.Severity;
import com.signalops.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** PUBLIC ONLY (R14/R19): immutable customer-safe snapshots. */
@Getter
@Setter
@Entity
@Table(name = "incident_public_updates")
public class IncidentPublicUpdate extends BaseEntity {

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "incident_id", nullable = false)
  private UUID incidentId;

  @Column(name = "author_user_id", nullable = false)
  private UUID authorUserId;

  @Column(nullable = false)
  private String message;

  @Enumerated(EnumType.STRING)
  @Column(name = "status_at_publish", nullable = false)
  private IncidentStatus statusAtPublish;

  @Enumerated(EnumType.STRING)
  @Column(name = "severity_at_publish", nullable = false)
  private Severity severityAtPublish;

  @Column(name = "published_at", nullable = false)
  private Instant publishedAt;
}
