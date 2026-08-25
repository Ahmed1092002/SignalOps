package com.signalops.users.api;

import java.util.Optional;
import java.util.UUID;

/** Read-only user directory for other modules. Never exposes security fields. */
public interface UserDirectory {

  Optional<UserSummary> findSummary(UUID userId);

  Optional<UserSummary> findSummaryByEmail(String email);

  record UserSummary(UUID userId, String fullName, String email, boolean active) {}
}
