package com.signalops.notifications.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  Page<Notification> findByOrganizationIdAndUserIdOrderByCreatedAtDesc(
      UUID organizationId, UUID userId, Pageable pageable);

  long countByOrganizationIdAndUserIdAndReadAtIsNull(UUID organizationId, UUID userId);

  Optional<Notification> findByIdAndOrganizationIdAndUserId(
      UUID id, UUID organizationId, UUID userId);

  @Modifying
  @Query(
      "update Notification n set n.readAt = :now"
          + " where n.organizationId = :organizationId and n.userId = :userId and n.readAt is null")
  int markAllRead(
      @Param("organizationId") UUID organizationId,
      @Param("userId") UUID userId,
      @Param("now") Instant now);
}
