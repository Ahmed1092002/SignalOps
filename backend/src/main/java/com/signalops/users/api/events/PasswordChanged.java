package com.signalops.users.api.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Published when a user changes their password; audit records it per membership. */
public record PasswordChanged(UUID userId, List<UUID> organizationIds, Instant occurredAt)
    implements com.signalops.shared.domain.DomainEvent {}
