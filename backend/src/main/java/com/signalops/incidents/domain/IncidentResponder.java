package com.signalops.incidents.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@Entity
@Table(name = "incident_responders")
@IdClass(IncidentResponder.Pk.class)
public class IncidentResponder {

  @Id
  @Column(name = "incident_id", nullable = false)
  private UUID incidentId;

  @Id
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "assigned_by_user_id", nullable = false)
  private UUID assignedByUserId;

  @CreationTimestamp
  @Column(name = "assigned_at", nullable = false, updatable = false)
  private Instant assignedAt;

  public record Pk(UUID incidentId, UUID userId) implements Serializable {}
}
