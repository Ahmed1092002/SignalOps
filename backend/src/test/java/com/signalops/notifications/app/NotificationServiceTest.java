package com.signalops.notifications.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signalops.notifications.domain.Notification;
import com.signalops.notifications.domain.NotificationRepository;
import com.signalops.shared.api.ApiException;
import com.signalops.shared.domain.Role;
import com.signalops.shared.security.TenantContext;
import com.signalops.shared.security.TenantContextValue;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class NotificationServiceTest {

  private NotificationRepository repository;
  private Clock clock;
  private NotificationService service;

  private final UUID userId = UUID.randomUUID();
  private final UUID orgA = UUID.randomUUID();
  private final UUID orgB = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    repository = mock(NotificationRepository.class);
    clock = Clock.systemUTC();
    service = new NotificationService(repository, clock);
    TenantContext.set(new TenantContextValue(userId, orgA, UUID.randomUUID(), Role.VIEWER, "Test User"));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void listForUserFiltersByTenantOrganizationId() {
    Notification n1 = new Notification();
    n1.setOrganizationId(orgA);
    n1.setUserId(userId);
    n1.setTitle("Org A Notif");

    when(repository.findByOrganizationIdAndUserIdOrderByCreatedAtDesc(eq(orgA), eq(userId), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(n1)));

    var response = service.listForUser(userId, 0, 10);

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().get(0).title()).isEqualTo("Org A Notif");
    verify(repository).findByOrganizationIdAndUserIdOrderByCreatedAtDesc(eq(orgA), eq(userId), any(Pageable.class));
  }

  @Test
  void unreadCountFiltersByTenantOrganizationId() {
    when(repository.countByOrganizationIdAndUserIdAndReadAtIsNull(orgA, userId)).thenReturn(3L);
    when(repository.countByOrganizationIdAndUserIdAndReadAtIsNull(orgB, userId)).thenReturn(7L);

    long countOrgA = service.unreadCount(userId);
    assertThat(countOrgA).isEqualTo(3L);

    // Switch tenant context to org B
    TenantContext.set(new TenantContextValue(userId, orgB, UUID.randomUUID(), Role.VIEWER, "Test User"));
    long countOrgB = service.unreadCount(userId);
    assertThat(countOrgB).isEqualTo(7L);
  }

  @Test
  void markReadSucceedsWhenBelongingToCurrentTenantOrg() {
    UUID notifId = UUID.randomUUID();
    Notification n = new Notification();
    n.setOrganizationId(orgA);
    n.setUserId(userId);

    when(repository.findByIdAndOrganizationIdAndUserId(notifId, orgA, userId))
        .thenReturn(Optional.of(n));

    service.markRead(userId, notifId);

    assertThat(n.getReadAt()).isNotNull();
  }

  @Test
  void markReadRejectsNotificationBelongingToAnotherOrg() {
    UUID notifId = UUID.randomUUID();
    when(repository.findByIdAndOrganizationIdAndUserId(notifId, orgA, userId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.markRead(userId, notifId))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Notification not found");
  }

  @Test
  void markAllReadAppliesOnlyToCurrentTenantOrg() {
    service.markAllRead(userId);

    verify(repository).markAllRead(eq(orgA), eq(userId), any(Instant.class));
  }
}
