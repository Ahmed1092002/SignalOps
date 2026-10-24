package com.signalops.incidents.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signalops.incidents.domain.FollowUpTask;
import com.signalops.incidents.domain.FollowUpTaskRepository;
import com.signalops.incidents.domain.Incident;
import com.signalops.incidents.domain.IncidentRepository;
import com.signalops.incidents.domain.IncidentResponderRepository;
import com.signalops.organizations.api.MembershipDirectory;
import com.signalops.shared.api.ApiException;
import com.signalops.shared.domain.Role;
import com.signalops.shared.security.TenantContext;
import com.signalops.shared.security.TenantContextValue;
import com.signalops.users.api.UserDirectory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class FollowUpTaskServiceTest {

  private FollowUpTaskRepository taskRepository;
  private IncidentRepository incidentRepository;
  private IncidentResponderRepository responderRepository;
  private IncidentAccessPolicy accessPolicy;
  private ApplicationEventPublisher events;
  private Clock clock;
  private MembershipDirectory membershipDirectory;
  private UserDirectory userDirectory;

  private FollowUpTaskService service;

  private UUID orgId;
  private UUID actorId;

  @BeforeEach
  void setUp() {
    taskRepository = mock(FollowUpTaskRepository.class);
    incidentRepository = mock(IncidentRepository.class);
    responderRepository = mock(IncidentResponderRepository.class);
    accessPolicy = new IncidentAccessPolicy(responderRepository);
    events = mock(ApplicationEventPublisher.class);
    clock = Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC);
    membershipDirectory = mock(MembershipDirectory.class);
    userDirectory = mock(UserDirectory.class);

    service =
        new FollowUpTaskService(
            taskRepository,
            incidentRepository,
            accessPolicy,
            events,
            clock,
            membershipDirectory,
            userDirectory);

    orgId = UUID.randomUUID();
    actorId = UUID.randomUUID();
    TenantContext.set(new TenantContextValue(actorId, orgId, UUID.randomUUID(), Role.INCIDENT_COMMANDER, "Actor"));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void toggleDoneThrowsNotFoundWhenTaskDoesNotBelongToIncident() {
    UUID incidentId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();

    Incident incident = new Incident();
    ReflectionTestUtils.setField(incident, "id", incidentId);
    incident.setOrganizationId(orgId);
    incident.setCommanderUserId(actorId);

    when(incidentRepository.findByIdAndOrganizationId(incidentId, orgId))
        .thenReturn(Optional.of(incident));
    when(taskRepository.findByIdAndIncidentIdAndOrganizationId(taskId, incidentId, orgId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.toggleDone(incidentId, taskId))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(404));

    verify(taskRepository).findByIdAndIncidentIdAndOrganizationId(taskId, incidentId, orgId);
    verify(events, never()).publishEvent(any(Object.class));
  }

  @Test
  void toggleDoneSucceedsWhenTaskBelongsToIncident() {
    UUID incidentId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();

    Incident incident = new Incident();
    ReflectionTestUtils.setField(incident, "id", incidentId);
    incident.setOrganizationId(orgId);
    incident.setCommanderUserId(actorId);

    FollowUpTask task = new FollowUpTask();
    ReflectionTestUtils.setField(task, "id", taskId);
    task.setIncidentId(incidentId);
    task.setOrganizationId(orgId);
    task.setTitle("Task for Incident");
    task.setStatus(FollowUpTask.Status.OPEN);

    when(incidentRepository.findByIdAndOrganizationId(incidentId, orgId))
        .thenReturn(Optional.of(incident));
    when(taskRepository.findByIdAndIncidentIdAndOrganizationId(taskId, incidentId, orgId))
        .thenReturn(Optional.of(task));

    var view = service.toggleDone(incidentId, taskId);

    assertThat(view.status()).isEqualTo("DONE");
    assertThat(task.getStatus()).isEqualTo(FollowUpTask.Status.DONE);
    assertThat(task.getCompletedAt()).isEqualTo(Instant.now(clock));
    verify(events).publishEvent(any(Object.class));
  }
}
