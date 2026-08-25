package com.signalops.servicecatalog.api;

import java.time.Instant;
import java.util.UUID;

/** Events published by the service catalog; consumed by audit (in-transaction). */
public final class ServiceCatalogEvents {

  public record ServiceCreated(
      UUID organizationId, UUID actorUserId, UUID serviceId, String serviceName, Instant occurredAt)
      implements com.signalops.shared.domain.DomainEvent {}

  public record ServiceUpdated(
      UUID organizationId, UUID actorUserId, UUID serviceId, String serviceName, Instant occurredAt)
      implements com.signalops.shared.domain.DomainEvent {}

  public record ServiceArchived(
      UUID organizationId, UUID actorUserId, UUID serviceId, String serviceName, Instant occurredAt)
      implements com.signalops.shared.domain.DomainEvent {}

  private ServiceCatalogEvents() {}
}
