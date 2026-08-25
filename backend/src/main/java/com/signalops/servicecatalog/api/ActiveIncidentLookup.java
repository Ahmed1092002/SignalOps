package com.signalops.servicecatalog.api;

import java.util.UUID;

/**
 * Port counting active (non-archived, non-resolved) incidents that affect a service.
 * Implemented by the incidents module; used to enforce R18 on archive.
 */
public interface ActiveIncidentLookup {

  long countActiveIncidentsForService(UUID organizationId, UUID serviceId);
}
