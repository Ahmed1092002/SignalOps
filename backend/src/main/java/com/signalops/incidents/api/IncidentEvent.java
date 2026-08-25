package com.signalops.incidents.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Domain event published inside the mutation transaction. Same-transaction listeners project
 * timeline/audit rows (atomic, R9); after-commit listeners (Phase 2 realtime) broadcast only
 * when the transaction actually committed.
 */
public record IncidentEvent(
    IncidentEventType type,
    UUID organizationId,
    UUID incidentId,
    UUID actorUserId,
    Map<String, Object> payload,
    Instant occurredAt) {

  public enum IncidentEventType {
    CREATED,
    STATUS_CHANGED,
    SEVERITY_CHANGED,
    COMMANDER_ASSIGNED,
    RESPONDER_ASSIGNED,
    RESPONDER_REMOVED,
    COMMENT_ADDED,
    PUBLIC_UPDATE_PUBLISHED,
    RESOLVED,
    REOPENED,
    ARCHIVED,
    EDITED,
    FOLLOW_UP_CREATED,
    FOLLOW_UP_COMPLETED,
    ATTACHMENT_ADDED
  }
}
