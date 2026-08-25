package com.signalops.notifications.app;

import com.signalops.incidents.api.IncidentEvent;
import com.signalops.incidents.domain.Incident;
import com.signalops.incidents.domain.IncidentRepository;
import com.signalops.incidents.domain.IncidentResponderRepository;
import com.signalops.notifications.domain.Notification;
import com.signalops.notifications.domain.NotificationRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Generates in-app notifications from incident events, in the same transaction as the mutation
 * (a rollback removes them together). After commit, a lightweight signal is pushed to each
 * recipient over STOMP; clients treat it as an invalidation signal and refetch via REST.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationGenerator {

  private final NotificationRepository notificationRepository;
  private final IncidentRepository incidentRepository;
  private final IncidentResponderRepository responderRepository;
  private final ApplicationEventPublisher events;

  @EventListener
  @Transactional
  public void onIncidentEvent(IncidentEvent event) {
    switch (event.type()) {
      case RESPONDER_ASSIGNED -> onResponderAssigned(event);
      case SEVERITY_CHANGED -> onSeverityChanged(event);
      case PUBLIC_UPDATE_PUBLISHED -> onPublicUpdatePublished(event);
      default -> { }
    }
  }

  private void onResponderAssigned(IncidentEvent event) {
    Object userIdValue = event.payload().get("userId");
    if (userIdValue == null) {
      return;
    }
    UUID recipient = UUID.fromString(userIdValue.toString());
    if (recipient.equals(event.actorUserId())) {
      return;
    }
    create(event, recipient, "RESPONDER_ASSIGNED",
        "You were assigned to " + incidentLabel(event),
        "You have been added as a responder.");
  }

  private void onSeverityChanged(IncidentEvent event) {
    if (!"CRITICAL".equals(event.payload().get("to"))) {
      return;
    }
    for (UUID recipient : participants(event, event.actorUserId())) {
      create(event, recipient, "SEVERITY_CRITICAL",
          incidentLabel(event) + " is now CRITICAL",
          "Severity was raised to Critical. Immediate response expected.");
    }
  }

  private void onPublicUpdatePublished(IncidentEvent event) {
    for (UUID recipient : participants(event, event.actorUserId())) {
      create(event, recipient, "PUBLIC_UPDATE_PUBLISHED",
          "Public update published on " + incidentLabel(event),
          "A customer-facing update was published.");
    }
  }

  private List<UUID> participants(IncidentEvent event, UUID excludeUserId) {
    List<UUID> recipients = new ArrayList<>();
    incidentRepository.findById(event.incidentId()).ifPresent(incident -> {
      if (incident.getCommanderUserId() != null) {
        recipients.add(incident.getCommanderUserId());
      }
      responderRepository
          .findByIncidentIdAndOrganizationId(incident.getId(), incident.getOrganizationId())
          .forEach(responder -> {
            if (!recipients.contains(responder.getUserId())) {
              recipients.add(responder.getUserId());
            }
          });
    });
    recipients.removeIf(excludeUserId::equals);
    return recipients;
  }

  private void create(IncidentEvent event, UUID recipient, String type, String title, String body) {
    Notification notification = new Notification();
    notification.setOrganizationId(event.organizationId());
    notification.setUserId(recipient);
    notification.setType(type);
    notification.setTitle(title);
    notification.setBody(body);
    notification.setEntityType("Incident");
    notification.setEntityId(event.incidentId());
    notificationRepository.save(notification);
    events.publishEvent(new NotificationCreatedSignal(recipient, type, title, event.incidentId()));
  }

  private String incidentLabel(IncidentEvent event) {
    Object key = event.payload().get("key");
    return key == null || key.toString().isBlank() ? "an incident" : key.toString();
  }

  public record NotificationCreatedSignal(UUID userId, String type, String title, UUID incidentId) {}

  /**
   * After-commit STOMP push. fallbackExecution=true keeps the app healthy when no transaction
   * is active (e.g. tests); failures never break the request that produced the notification.
   */
  @Slf4j
  @Component
  @RequiredArgsConstructor
  public static class RealtimeNotifier {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotificationCreated(NotificationCreatedSignal signal) {
      try {
        messagingTemplate.convertAndSendToUser(
            signal.userId().toString(),
            "/queue/notifications",
            Map.of(
                "type", signal.type(),
                "title", signal.title(),
                "incidentId", String.valueOf(signal.incidentId()),
                "occurredAt", Instant.now().toString()));
      } catch (Exception ex) {
        log.debug("Realtime notification push skipped: {}", ex.getMessage());
      }
    }
  }
}
