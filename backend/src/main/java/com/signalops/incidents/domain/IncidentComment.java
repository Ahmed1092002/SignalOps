package com.signalops.incidents.domain;

import com.signalops.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** INTERNAL ONLY (R5/R14): never exposed through public endpoints. */
@Getter
@Setter
@Entity
@Table(name = "incident_comments")
public class IncidentComment extends BaseEntity {

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "incident_id", nullable = false)
  private UUID incidentId;

  @Column(name = "author_user_id", nullable = false)
  private UUID authorUserId;

  @Column(nullable = false)
  private String body;
}
