package com.signalops.servicecatalog.domain;

import com.signalops.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "services")
public class ServiceEntity extends BaseEntity {

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String slug;

  @Column(nullable = false)
  private String description = "";

  @Column(name = "owning_team", nullable = false)
  private String owningTeam = "";

  @Column(name = "is_public", nullable = false)
  private boolean isPublic = true;

  @Column(name = "archived_at")
  private Instant archivedAt;

  public boolean isArchived() {
    return archivedAt != null;
  }
}
