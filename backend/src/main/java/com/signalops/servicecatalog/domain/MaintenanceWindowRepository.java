package com.signalops.servicecatalog.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaintenanceWindowRepository extends JpaRepository<MaintenanceWindow, UUID> {

  @Query(
      "select (count(m) > 0) from MaintenanceWindow m where m.organizationId = :organizationId"
          + " and m.serviceId = :serviceId and m.startsAt <= :now and m.endsAt > :now")
  boolean activeWindowExists(
      @Param("organizationId") UUID organizationId,
      @Param("serviceId") UUID serviceId,
      @Param("now") java.time.Instant now);

  List<MaintenanceWindow> findByOrganizationIdOrderByStartsAtDesc(UUID organizationId);

  Optional<MaintenanceWindow> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
