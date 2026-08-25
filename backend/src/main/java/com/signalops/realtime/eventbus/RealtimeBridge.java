package com.signalops.realtime.eventbus;

import com.signalops.incidents.api.IncidentEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Broadcasts incident events AFTER the transaction commits — a rolled-back mutation can never
 * reach a socket. Payloads are invalidation signals; PostgreSQL and REST remain the source of
 * truth and clients refetch after receiving (or after reconnecting).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeBridge {

  private final SimpMessagingTemplate messagingTemplate;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onIncidentEvent(IncidentEvent event) {
    try {
      String orgTopic = "/topic/org." + event.organizationId();
      messagingTemplate.convertAndSend(
          orgTopic + ".incidents", envelope(event.type().name(), event.organizationId(), event.incidentId(), event.payload()));

    } catch (Exception ex) {
      log.debug("Realtime broadcast skipped: {}", ex.getMessage());
    }
  }


  private Map<String, Object> envelope(
      String type, UUID organizationId, UUID entityId, Map<String, Object> data) {
    Map<String, Object> envelope = new HashMap<>();
    envelope.put("type", type);
    envelope.put("orgId", organizationId.toString());
    envelope.put("entityId", entityId.toString());
    envelope.put("occurredAt", java.time.Instant.now().toString());
    if (data != null) {
      envelope.put("data", data);
    }
    return envelope;
  }
}
