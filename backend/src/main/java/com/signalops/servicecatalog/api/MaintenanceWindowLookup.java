package com.signalops.servicecatalog.api;

import java.util.UUID;

/** Port: is a maintenance window currently active for this service? (Phase 3) */
public interface MaintenanceWindowLookup {

  boolean activeWindowExists(UUID organizationId, UUID serviceId);
}
