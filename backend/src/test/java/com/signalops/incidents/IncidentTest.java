package com.signalops.incidents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.signalops.incidents.api.IncidentStatus;
import com.signalops.incidents.api.Severity;
import com.signalops.incidents.domain.Incident;
import com.signalops.shared.api.ApiException;
import com.signalops.shared.api.ErrorCodes;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IncidentTest {

  private Incident incident(IncidentStatus status, Severity severity) {
    Incident incident = new Incident();
    incident.setOrganizationId(UUID.randomUUID());
    incident.setKey("INC-1");
    incident.setTitle("Test incident");
    incident.setSeverity(severity);
    incident.setStatus(status);
    incident.setCreatedByUserId(UUID.randomUUID());
    incident.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    return incident;
  }

  @Test
  void documentedForwardTransitionsAreAccepted() {
    var incident = incident(IncidentStatus.DETECTED, Severity.LOW);
    incident.applyTransition(IncidentStatus.INVESTIGATING, null, null, UUID.randomUUID(), Instant.now());
    assertThat(incident.getStatus()).isEqualTo(IncidentStatus.INVESTIGATING);
    assertThat(incident.getAcknowledgedAt()).isNotNull();

    incident.applyTransition(IncidentStatus.IDENTIFIED, null, null, UUID.randomUUID(), Instant.now());
    incident.applyTransition(IncidentStatus.MONITORING, null, null, UUID.randomUUID(), Instant.now());
    assertThat(incident.getStatus()).isEqualTo(IncidentStatus.MONITORING);
  }

  @Test
  void resolvingRequiresNonBlankSummaryAndFreezesDuration() {
    var incident = incident(IncidentStatus.MONITORING, Severity.HIGH);
    assertThatThrownBy(
            () ->
                incident.applyTransition(
                    IncidentStatus.RESOLVED, "   ", null, UUID.randomUUID(), Instant.now()))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).getCode())
        .isEqualTo(ErrorCodes.MISSING_RESOLUTION_SUMMARY);

    Instant resolveTime = Instant.parse("2026-01-01T02:00:00Z");
    incident.applyTransition(
        IncidentStatus.RESOLVED, "Rolled back the bad deploy", null, UUID.randomUUID(), resolveTime);
    assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
    assertThat(incident.getResolvedAt()).isEqualTo(resolveTime);
    assertThat(incident.getDurationSeconds()).isEqualTo(7200L);
    assertThat(incident.getResolutionSummary()).isEqualTo("Rolled back the bad deploy");
  }

  @Test
  void illegalTransitionsAreRejectedWithTypedError() {
    var resolved = incident(IncidentStatus.RESOLVED, Severity.MEDIUM);
    assertThatThrownBy(
            () ->
                resolved.applyTransition(
                    IncidentStatus.IDENTIFIED, null, null, UUID.randomUUID(), Instant.now()))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).getCode())
        .isEqualTo(ErrorCodes.INVALID_INCIDENT_TRANSITION);

    var detected = incident(IncidentStatus.DETECTED, Severity.MEDIUM);
    assertThatThrownBy(
            () ->
                detected.applyTransition(
                    IncidentStatus.RESOLVED, "summary", null, UUID.randomUUID(), Instant.now()))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void reopenRequiresReasonAndClearsCurrentDuration() {
    var incident = incident(IncidentStatus.RESOLVED, Severity.MEDIUM);
    incident.setDurationSeconds(3600L);
    incident.setResolvedAt(Instant.now());

    assertThatThrownBy(
            () ->
                incident.applyTransition(
                    IncidentStatus.INVESTIGATING, null, null, UUID.randomUUID(), Instant.now()))
        .extracting(ex -> ((ApiException) ex).getCode())
        .isEqualTo(ErrorCodes.REOPEN_REASON_REQUIRED);

    incident.applyTransition(
        IncidentStatus.INVESTIGATING, null, "Regression observed in monitoring", UUID.randomUUID(), Instant.now());
    assertThat(incident.getStatus()).isEqualTo(IncidentStatus.INVESTIGATING);
    assertThat(incident.getDurationSeconds()).isNull();
    assertThat(incident.getResolvedAt()).isNull();
    assertThat(incident.getResolutionSummary()).isNull();
  }

  @Test
  void criticalSeverityRequiresCommander() {
    var incident = incident(IncidentStatus.DETECTED, Severity.MEDIUM);
    assertThatThrownBy(() -> incident.changeSeverity(Severity.CRITICAL))
        .extracting(ex -> ((ApiException) ex).getCode())
        .isEqualTo(ErrorCodes.CRITICAL_REQUIRES_COMMANDER);

    incident.setCommanderUserId(UUID.randomUUID());
    incident.changeSeverity(Severity.CRITICAL);
    assertThat(incident.getSeverity()).isEqualTo(Severity.CRITICAL);
  }

  @Test
  void commanderCannotBeRemovedWhileCritical() {
    var incident = incident(IncidentStatus.DETECTED, Severity.CRITICAL);
    incident.setCommanderUserId(UUID.randomUUID());
    assertThatThrownBy(incident::removeCommander)
        .extracting(ex -> ((ApiException) ex).getCode())
        .isEqualTo(ErrorCodes.CRITICAL_REQUIRES_COMMANDER);

    incident.changeSeverity(Severity.LOW);
    incident.removeCommander();
    assertThat(incident.getCommanderUserId()).isNull();
  }

  @Test
  void archivedIncidentsRejectAllMutations() {
    var incident = incident(IncidentStatus.MONITORING, Severity.LOW);
    incident.archive(Instant.now());
    assertThatThrownBy(
            () ->
                incident.applyTransition(
                    IncidentStatus.RESOLVED, "summary", null, UUID.randomUUID(), Instant.now()))
        .extracting(ex -> ((ApiException) ex).getCode())
        .isEqualTo(ErrorCodes.ARCHIVED_INCIDENT_READONLY);
    assertThatThrownBy(() -> incident.editDetails("New title", null))
        .isInstanceOf(ApiException.class);
  }
}
