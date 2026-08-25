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
@Table(name = "maintenance_windows")
public class MaintenanceWindow extends BaseEntity {

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "service_id", nullable = false)
  private UUID serviceId;

  @Column(nullable = false)
  private String title;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  private Instant endsAt;

  @Column(name = "created_by_user_id", nullable = false)
  private UUID createdByUserId;

  public boolean isActive(Instant now) {
    return !now.isBefore(startsAt) && now.isBefore(endsAt);
  }
}
