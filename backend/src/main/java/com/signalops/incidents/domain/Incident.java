package com.signalops.incidents.domain;

import com.signalops.incidents.api.IncidentStatus;
import com.signalops.incidents.api.Severity;
import com.signalops.shared.api.ApiException;
import com.signalops.shared.api.ErrorCodes;
import com.signalops.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "incidents")
public class Incident extends BaseEntity {

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(nullable = false)
  private String key;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private String description = "";

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Severity severity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private IncidentStatus status;

  @Column(name = "commander_user_id")
  private UUID commanderUserId;

  @Column(name = "resolver_user_id")
  private UUID resolverUserId;

  @Column(name = "resolution_summary")
  private String resolutionSummary;

  @Column(name = "acknowledged_at")
  private Instant acknowledgedAt;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "duration_seconds")
  private Long durationSeconds;

  @Column(name = "archived_at")
  private Instant archivedAt;

  @Column(name = "created_by_user_id", nullable = false)
  private UUID createdByUserId;

  @Version
  @Column(nullable = false)
  private long version;

  public boolean isArchived() {
    return archivedAt != null;
  }

  public boolean hasCommander() {
    return commanderUserId != null;
  }

  /** R7/R13: only the documented transitions, with their guards. */
  public void applyTransition(IncidentStatus to, String resolutionSummary, String reason, UUID actorUserId, Instant now) {
    requireNotArchived();
    if (status == to) {
      throw invalidTransition(to);
    }
    boolean legal =
        switch (status) {
          case DETECTED -> to == IncidentStatus.INVESTIGATING;
          case INVESTIGATING -> to == IncidentStatus.IDENTIFIED;
          case IDENTIFIED -> to == IncidentStatus.MONITORING;
          case MONITORING -> to == IncidentStatus.RESOLVED || to == IncidentStatus.INVESTIGATING;
          case RESOLVED -> to == IncidentStatus.INVESTIGATING;
        };
    if (!legal) {
      throw invalidTransition(to);
    }
    if (to == IncidentStatus.RESOLVED) {
      if (resolutionSummary == null || resolutionSummary.isBlank()) {
        throw ApiException.badRequest(
            ErrorCodes.MISSING_RESOLUTION_SUMMARY, "A resolution summary is required to resolve an incident.");
      }
      resolvedAt = now;
      durationSeconds = java.time.Duration.between(getCreatedAt(), now).getSeconds();
      this.resolutionSummary = resolutionSummary.trim();
      this.resolverUserId = actorUserId;
    }
    if (status == IncidentStatus.RESOLVED && to == IncidentStatus.INVESTIGATING) {
      if (reason == null || reason.isBlank()) {
        throw ApiException.badRequest(
            ErrorCodes.REOPEN_REASON_REQUIRED, "A reason is required to reopen an incident.");
      }
      resolvedAt = null;
      durationSeconds = null;
      this.resolutionSummary = null;
      this.resolverUserId = null;
    }
    if (to == IncidentStatus.INVESTIGATING
        && status != IncidentStatus.RESOLVED
        && acknowledgedAt == null) {
      acknowledgedAt = now;
    }
    status = to;
  }

  /** R1: a CRITICAL incident requires a commander. */
  public void changeSeverity(Severity newSeverity) {
    requireNotArchived();
    if (newSeverity == Severity.CRITICAL && commanderUserId == null) {
      throw ApiException.badRequest(
          ErrorCodes.CRITICAL_REQUIRES_COMMANDER,
          "Assign an Incident Commander before setting Critical severity.");
    }
    this.severity = newSeverity;
  }

  public void assignCommander(UUID commanderUserId) {
    requireNotArchived();
    this.commanderUserId = commanderUserId;
  }

  /** R1: the commander cannot be removed while the incident is CRITICAL. */
  public void removeCommander() {
    requireNotArchived();
    if (severity == Severity.CRITICAL) {
      throw ApiException.conflict(
          ErrorCodes.CRITICAL_REQUIRES_COMMANDER,
          "A Critical incident must keep an Incident Commander.");
    }
    this.commanderUserId = null;
  }

  public void editDetails(String newTitle, String newDescription) {
    requireNotArchived();
    if (newTitle != null) {
      this.title = newTitle.trim();
    }
    if (newDescription != null) {
      this.description = newDescription.trim();
    }
  }

  public void archive(Instant now) {
    if (isArchived()) {
      throw ApiException.conflict(ErrorCodes.ARCHIVED_INCIDENT_READONLY, "Incident is already archived.");
    }
    this.archivedAt = now;
  }

  private void requireNotArchived() {
    if (isArchived()) {
      throw ApiException.conflict(
          ErrorCodes.ARCHIVED_INCIDENT_READONLY, "Archived incidents are read-only.");
    }
  }

  private ApiException invalidTransition(IncidentStatus to) {
    return ApiException.conflict(
        ErrorCodes.INVALID_INCIDENT_TRANSITION,
        "Incident cannot move from " + status + " to " + to + ".");
  }
}
