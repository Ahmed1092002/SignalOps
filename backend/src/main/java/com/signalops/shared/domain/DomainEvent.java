package com.signalops.shared.domain;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {
  Instant occurredAt();

  default UUID organizationId() {
    return null;
  }
}
