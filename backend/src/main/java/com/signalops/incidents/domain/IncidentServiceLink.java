package com.signalops.incidents.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Join row with database-level composite FKs to incidents(organization_id, id) and
 * services(organization_id, id) — cross-organization references are unrepresentable (R11/R16).
 * JPA maps plain ids only; integrity is enforced by the schema.
 */
@Getter
@Setter
@Entity
@Table(name = "incident_services")
@IdClass(IncidentServiceLink.Pk.class)
public class IncidentServiceLink {

  @Id
  @Column(name = "incident_id", nullable = false)
  private UUID incidentId;

  @Id
  @Column(name = "service_id", nullable = false)
  private UUID serviceId;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  public record Pk(UUID incidentId, UUID serviceId) implements Serializable {}
}
