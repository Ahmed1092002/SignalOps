package com.signalops.incidents.app;

import com.signalops.incidents.api.IncidentEvent;
import com.signalops.incidents.api.IncidentEvent.IncidentEventType;
import com.signalops.incidents.domain.FollowUpTask;
import com.signalops.incidents.domain.FollowUpTaskRepository;
import com.signalops.incidents.domain.Incident;
import com.signalops.incidents.domain.IncidentRepository;
import com.signalops.shared.api.ApiException;
import com.signalops.shared.api.ErrorCodes;
import com.signalops.shared.domain.Role;
import com.signalops.shared.security.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FollowUpTaskService {

  private final FollowUpTaskRepository taskRepository;
  private final IncidentRepository incidentRepository;
  private final IncidentAccessPolicy accessPolicy;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public record FollowUpTaskView(
      UUID id, String title, String status, UUID assigneeUserId, String assigneeName,
      Instant dueAt, Instant completedAt, Instant createdAt) {}

  @Transactional
  public FollowUpTaskView create(
      UUID incidentId, String title, UUID assigneeUserId, Instant dueAt) {
    var context = TenantContext.require();
    Incident incident = load(incidentId, context.organizationId());
    accessPolicy.requireOperate(context, incident, "create follow-up tasks on");
    if (assigneeUserId != null) {
      assertActiveMember(context.organizationId(), assigneeUserId);
    }
    FollowUpTask task = new FollowUpTask();
    task.setOrganizationId(context.organizationId());
    task.setIncidentId(incidentId);
    task.setTitle(title.trim());
    task.setAssigneeUserId(assigneeUserId);
    task.setCreatedByUserId(context.userId());
    task.setDueAt(dueAt);
    task = taskRepository.save(task);
    publish(IncidentEventType.FOLLOW_UP_CREATED, incident, context.userId(),
        Map.of("title", task.getTitle(), "body", task.getTitle()));
    return toView(task, context.userId());
  }

  @Transactional(readOnly = true)
  public List<FollowUpTaskView> list(UUID incidentId) {
    var context = TenantContext.require();
    requireExists(incidentId, context.organizationId());
    return taskRepository
        .findByIncidentIdAndOrganizationIdOrderByCreatedAtDesc(incidentId, context.organizationId())
        .stream()
        .map(task -> toView(task, context.userId()))
        .toList();
  }

  @Transactional
  public FollowUpTaskView toggleDone(UUID incidentId, UUID taskId) {
    var context = TenantContext.require();
    Incident incident = load(incidentId, context.organizationId());
    FollowUpTask task =
        taskRepository
            .findByIdAndIncidentIdAndOrganizationId(taskId, incidentId, context.organizationId())
            .orElseThrow(() -> ApiException.notFound("Task not found."));
    boolean mayComplete =
        context.isAdminLike()
            || accessPolicy.isCommander(context, incident)
            || context.userId().equals(task.getAssigneeUserId())
            || context.userId().equals(task.getCreatedByUserId());
    if (!mayComplete) {
      throw ApiException.forbidden(
          "Only the assignee, creator, commander, or an Admin can complete this task.");
    }
    if (task.isOpen()) {
      task.setStatus(FollowUpTask.Status.DONE);
      task.setCompletedAt(Instant.now(clock));
      publish(IncidentEventType.FOLLOW_UP_COMPLETED, incident, context.userId(),
          Map.of("title", task.getTitle(), "body", task.getTitle()));
    } else {
      task.setStatus(FollowUpTask.Status.OPEN);
      task.setCompletedAt(null);
    }
    return toView(task, context.userId());
  }

  private Incident load(UUID incidentId, UUID organizationId) {
    return incidentRepository
        .findByIdAndOrganizationId(incidentId, organizationId)
        .orElseThrow(() -> ApiException.notFound("Incident not found."));
  }

  private void requireExists(UUID incidentId, UUID organizationId) {
    if (!incidentRepository.existsByIdAndOrganizationId(incidentId, organizationId)) {
      throw ApiException.notFound("Incident not found.");
    }
  }

  private void assertActiveMember(UUID organizationId, UUID userId) {
    // Membership validity is enforced by the organizations module through the membership lookup;
    // the incidents module keeps a lightweight guard via the shared directory port.
    if (!membershipDirectory.isActiveMember(organizationId, userId)) {
      throw ApiException.badRequest(ErrorCodes.MEMBER_NOT_ACTIVE, "Assignee must be an active member.");
    }
  }

  private final com.signalops.organizations.api.MembershipDirectory membershipDirectory;

  private void publish(IncidentEventType type, Incident incident, UUID actorUserId, Map<String, Object> payload) {
    events.publishEvent(
        new IncidentEvent(
            type, incident.getOrganizationId(), incident.getId(), actorUserId, payload, Instant.now(clock)));
  }

  private FollowUpTaskView toView(FollowUpTask task, UUID currentUserId) {
    String assigneeName =
        task.getAssigneeUserId() == null
            ? null
            : userDirectory.findSummary(task.getAssigneeUserId()).map(summary -> summary.fullName()).orElse(null);
    return new FollowUpTaskView(
        task.getId(),
        task.getTitle(),
        task.getStatus().name(),
        task.getAssigneeUserId(),
        assigneeName,
        task.getDueAt(),
        task.getCompletedAt(),
        task.getCreatedAt());
  }

  private final com.signalops.users.api.UserDirectory userDirectory;

  private static String UserSummaryName(com.signalops.users.api.UserDirectory.UserSummary summary) {
    return summary.fullName();
  }
}
