package com.signalops.timeline.app;

import com.signalops.incidents.api.IncidentEvent;
import com.signalops.incidents.api.TimelineReader;
import com.signalops.shared.security.TenantContext;
import com.signalops.timeline.domain.TimelineEventEntity;
import com.signalops.timeline.domain.TimelineEventRepository;
import com.signalops.users.api.UserDirectory;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projects incident events into the append-only timeline INSIDE the mutation transaction (R9):
 * a rollback removes the timeline row together with the mutation. The payload carries safe
 * metadata only (ids, enum values, short bodies of the mutation itself) — never secrets.
 */
@Component
@RequiredArgsConstructor
public class TimelineProjector implements TimelineReader {

  private static final Map<IncidentEvent.IncidentEventType, String> TYPE_NAMES =
      Map.ofEntries(
          Map.entry(IncidentEvent.IncidentEventType.CREATED, "INCIDENT_CREATED"),
          Map.entry(IncidentEvent.IncidentEventType.STATUS_CHANGED, "STATUS_CHANGED"),
          Map.entry(IncidentEvent.IncidentEventType.SEVERITY_CHANGED, "SEVERITY_CHANGED"),
          Map.entry(IncidentEvent.IncidentEventType.COMMANDER_ASSIGNED, "COMMANDER_ASSIGNED"),
          Map.entry(IncidentEvent.IncidentEventType.RESPONDER_ASSIGNED, "RESPONDER_ASSIGNED"),
          Map.entry(IncidentEvent.IncidentEventType.RESPONDER_REMOVED, "RESPONDER_REMOVED"),
          Map.entry(IncidentEvent.IncidentEventType.COMMENT_ADDED, "COMMENT_ADDED"),
          Map.entry(IncidentEvent.IncidentEventType.PUBLIC_UPDATE_PUBLISHED, "PUBLIC_UPDATE_PUBLISHED"),
          Map.entry(IncidentEvent.IncidentEventType.RESOLVED, "INCIDENT_RESOLVED"),
          Map.entry(IncidentEvent.IncidentEventType.REOPENED, "INCIDENT_REOPENED"),
          Map.entry(IncidentEvent.IncidentEventType.ARCHIVED, "INCIDENT_ARCHIVED"),
          Map.entry(IncidentEvent.IncidentEventType.EDITED, "INCIDENT_EDITED"),
          Map.entry(IncidentEvent.IncidentEventType.FOLLOW_UP_CREATED, "FOLLOW_UP_CREATED"),
          Map.entry(IncidentEvent.IncidentEventType.FOLLOW_UP_COMPLETED, "FOLLOW_UP_COMPLETED"),
          Map.entry(IncidentEvent.IncidentEventType.ATTACHMENT_ADDED, "ATTACHMENT_ADDED"));

  private final TimelineEventRepository repository;
  private final UserDirectory userDirectory;

  @EventListener
  @Transactional
  public void project(IncidentEvent event) {
    TimelineEventEntity entity = new TimelineEventEntity();
    entity.setOrganizationId(event.organizationId());
    entity.setIncidentId(event.incidentId());
    entity.setType(TYPE_NAMES.getOrDefault(event.type(), event.type().name()));
    entity.setActorUserId(event.actorUserId());
    entity.setPayload(event.payload() == null ? Map.of() : event.payload());
    entity.setOccurredAt(event.occurredAt());
    repository.save(entity);
  }

  @Override
  @Transactional(readOnly = true)
  public List<TimelineItem> findByIncident(UUID organizationId, UUID incidentId, int page, int size) {
    return repository
        .findByIncidentIdAndOrganizationIdOrderByOccurredAtDesc(
            incidentId, organizationId, PageRequest.of(page, size))
        .stream()
        .map(this::toItem)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long countByIncident(UUID organizationId, UUID incidentId) {
    return repository.countByIncidentIdAndOrganizationId(incidentId, organizationId);
  }

  @Transactional(readOnly = true)
  public List<TimelineItem> recentForOrganization(UUID organizationId, int limit) {
    return repository
        .findByOrganizationIdOrderByOccurredAtDesc(organizationId, PageRequest.of(0, limit))
        .stream()
        .map(this::toItem)
        .toList();
  }

  private TimelineItem toItem(TimelineEventEntity entity) {
    String actorName =
        entity.getActorUserId() == null
            ? null
            : userDirectory.findSummary(entity.getActorUserId()).map(UserDirectory.UserSummary::fullName).orElse(null);
    return new TimelineItem(
        entity.getId(),
        entity.getIncidentId(),
        entity.getType(),
        entity.getActorUserId(),
        actorName,
        entity.getOccurredAt(),
        entity.getPayload());
  }
}
