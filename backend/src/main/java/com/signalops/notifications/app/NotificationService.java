package com.signalops.notifications.app;

import com.signalops.notifications.domain.Notification;
import com.signalops.notifications.domain.NotificationRepository;
import com.signalops.shared.api.ApiException;
import com.signalops.shared.api.ErrorCodes;
import com.signalops.shared.api.PageResponse;
import com.signalops.shared.security.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

  public record NotificationView(
      UUID id,
      String type,
      String title,
      String body,
      String entityType,
      UUID entityId,
      Instant readAt,
      Instant createdAt) {}

  private final NotificationRepository notificationRepository;
  private final Clock clock;

  @Transactional(readOnly = true)
  public PageResponse<NotificationView> listForUser(UUID userId, int page, int size) {
    UUID orgId = TenantContext.require().organizationId();
    int safePage = Math.max(0, page);
    int safeSize = Math.clamp(size, 1, 50);
    Page<Notification> result =
        notificationRepository.findByOrganizationIdAndUserIdOrderByCreatedAtDesc(
            orgId, userId, PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
    return new PageResponse<>(
        result.getContent().stream().map(NotificationService::toView).toList(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public long unreadCount(UUID userId) {
    UUID orgId = TenantContext.require().organizationId();
    return notificationRepository.countByOrganizationIdAndUserIdAndReadAtIsNull(orgId, userId);
  }

  @Transactional
  public void markRead(UUID userId, UUID notificationId) {
    UUID orgId = TenantContext.require().organizationId();
    Notification notification =
        notificationRepository
            .findByIdAndOrganizationIdAndUserId(notificationId, orgId, userId)
            .orElseThrow(() -> ApiException.notFound("Notification not found."));
    if (notification.isUnread()) {
      notification.setReadAt(Instant.now(clock));
    }
  }

  @Transactional
  public void markAllRead(UUID userId) {
    UUID orgId = TenantContext.require().organizationId();
    notificationRepository.markAllRead(orgId, userId, Instant.now(clock));
  }

  private static NotificationView toView(Notification notification) {
    return new NotificationView(
        notification.getId(),
        notification.getType(),
        notification.getTitle(),
        notification.getBody(),
        notification.getEntityType(),
        notification.getEntityId(),
        notification.getReadAt(),
        notification.getCreatedAt());
  }
}
