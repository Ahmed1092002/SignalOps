package com.signalops.incidents.domain;

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

@Getter
@Setter
@Entity
@Table(name = "follow_up_tasks")
public class FollowUpTask extends BaseEntity {

  public enum Status {
    OPEN,
    DONE
  }

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "incident_id", nullable = false)
  private UUID incidentId;

  @Column(nullable = false)
  private String title;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status = Status.OPEN;

  @Column(name = "assignee_user_id")
  private UUID assigneeUserId;

  @Column(name = "created_by_user_id", nullable = false)
  private UUID createdByUserId;

  @Column(name = "due_at")
  private Instant dueAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  public boolean isOpen() {
    return status == Status.OPEN;
  }
}
